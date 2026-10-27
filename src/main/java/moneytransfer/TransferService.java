package moneytransfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Moves money between two accounts with full ACID guarantees.
 *
 * ATOMICITY    - debit, credit, transaction row and ledger rows are ONE database transaction:
 *                all commit together or all roll back.
 * CONSISTENCY  - CHECK (balance >= 0), foreign keys and a UNIQUE request_id in the schema, plus
 *                validation here. Money is never created or destroyed.
 * ISOLATION    - both account rows are locked with SELECT ... FOR UPDATE, always in ascending
 *                account-id order. Two transfers touching the same account run one after the other,
 *                and A->B / B->A at the same moment cannot deadlock (same lock order).
 * DURABILITY   - InnoDB writes the commit to its redo log before commit() returns.
 *
 * IDEMPOTENCY  - the caller supplies a request id; repeating the same request never moves money twice.
 */
public final class TransferService {

    private static final Logger LOG = Logger.getLogger(TransferService.class.getName());
    private static final Pattern REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final BigDecimal MAX_SINGLE_TRANSFER = new BigDecimal("100000.00");
    private static final int MAX_ATTEMPTS = 3;

    private record Acct(int userId, BigDecimal balance, String status) { }

    /**
     * @param userId    the LOGGED-IN user; must own the sender account (prevents moving other people's money)
     * @param requestId unique id for this request (e.g. a UUID generated when the form is shown)
     */
    public TransferResult transfer(int userId, int fromAccount, int toAccount,
                                   BigDecimal amount, String requestId) throws BankException {
        BigDecimal amt = validate(fromAccount, toAccount, amount, requestId);

        for (int attempt = 1; ; attempt++) {
            try {
                return attemptTransfer(userId, fromAccount, toAccount, amt, requestId);
            } catch (SQLException e) {
                if (isRetryable(e) && attempt < MAX_ATTEMPTS) {
                    LOG.log(Level.WARNING, "Retrying transfer (attempt " + attempt + "): " + e.getMessage());
                    pause(attempt * 50L);
                    continue;
                }
                LOG.log(Level.SEVERE, "transfer failed", e);
                throw new BankException("Transfer could not be completed. No money was moved. Please try again.", e);
            }
        }
    }

    // ---------------------------------------------------------------- validation

    private static BigDecimal validate(int from, int to, BigDecimal amount, String requestId)
            throws BankException {
        if (from <= 0 || to <= 0) {
            throw new BankException("Invalid account number.");
        }
        if (from == to) {
            throw new BankException("Sender and receiver must be different accounts.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BankException("Amount must be greater than zero.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BankException("Amount can have at most 2 decimal places.");
        }
        BigDecimal amt = amount.setScale(2);   // exact: scale <= 2 was verified above
        if (amt.compareTo(MAX_SINGLE_TRANSFER) > 0) {
            throw new BankException("Single transfer limit is Rs. " + MAX_SINGLE_TRANSFER + ".");
        }
        if (requestId == null || !REQUEST_ID.matcher(requestId).matches()) {
            throw new BankException("Request id must be 1-64 characters: letters, digits, - or _.");
        }
        return amt;
    }

    // ---------------------------------------------------------------- transaction boundary

    private TransferResult attemptTransfer(int userId, int from, int to, BigDecimal amt, String requestId)
            throws SQLException, BankException {
        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            // READ_COMMITTED: every statement sees the latest committed data, so the duplicate-request
            // check below is never answered from a stale snapshot. Correctness of the balance comes
            // from the row locks, not from the isolation level.
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try {
                TransferResult result = doTransfer(c, userId, from, to, amt, requestId);
                c.commit();
                return result;
            } catch (SQLException | BankException | RuntimeException e) {
                rollbackQuietly(c);
                throw e;
            }
        }
    }

    private TransferResult doTransfer(Connection c, int userId, int from, int to,
                                      BigDecimal amt, String requestId)
            throws SQLException, BankException {

        // 1. Lock BOTH accounts in ascending id order (deadlock prevention).
        Map<Integer, Acct> accounts = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT account_id, user_id, balance, status FROM accounts "
              + "WHERE account_id IN (?, ?) ORDER BY account_id FOR UPDATE")) {
            ps.setInt(1, Math.min(from, to));
            ps.setInt(2, Math.max(from, to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    accounts.put(rs.getInt("account_id"),
                        new Acct(rs.getInt("user_id"), rs.getBigDecimal("balance"), rs.getString("status")));
                }
            }
        }
        Acct sender = accounts.get(from);
        Acct receiver = accounts.get(to);
        if (sender == null) {
            throw new BankException("Sender account not found.");
        }
        if (sender.userId() != userId) {
            throw new BankException("You can only transfer from your own account.");
        }
        if (receiver == null) {
            throw new BankException("Receiver account not found.");
        }

        // 2. Idempotency: was this request already processed? (checked BEFORE the balance check,
        //    so a safe retry still succeeds after the money has already been spent.)
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT transaction_id, sender_account_id, receiver_account_id, amount "
              + "FROM transactions WHERE request_id = ?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    boolean sameTransfer = rs.getInt("sender_account_id") == from
                            && rs.getInt("receiver_account_id") == to
                            && rs.getBigDecimal("amount").compareTo(amt) == 0;
                    if (!sameTransfer) {
                        throw new BankException("This request id was already used for a different transfer.");
                    }
                    return new TransferResult(rs.getLong("transaction_id"), true);
                }
            }
        }

        // 3. Business rules.
        if (!"ACTIVE".equals(sender.status()) || !"ACTIVE".equals(receiver.status())) {
            throw new BankException("One of the accounts is frozen.");
        }
        if (sender.balance().compareTo(amt) < 0) {
            throw new BankException("Insufficient balance.");
        }

        // 4. Move the money. Relative updates; "balance >= ?" is a second guard on top of the lock.
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET balance = balance - ? WHERE account_id = ? AND balance >= ?")) {
            ps.setBigDecimal(1, amt);
            ps.setInt(2, from);
            ps.setBigDecimal(3, amt);
            if (ps.executeUpdate() != 1) {
                throw new BankException("Insufficient balance.");
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE accounts SET balance = balance + ? WHERE account_id = ?")) {
            ps.setBigDecimal(1, amt);
            ps.setInt(2, to);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Receiver update failed");   // rolls back everything
            }
        }

        // 5. Record the transaction.
        long txId;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO transactions (sender_account_id, receiver_account_id, amount, request_id) "
              + "VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, from);
            ps.setInt(2, to);
            ps.setBigDecimal(3, amt);
            ps.setString(4, requestId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                txId = keys.getLong(1);
            }
        }

        // 6. Double-entry ledger rows (balances are known exactly because the rows are locked).
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount, balance_after) "
              + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, txId);
            ps.setInt(2, from);
            ps.setString(3, "DEBIT");
            ps.setBigDecimal(4, amt);
            ps.setBigDecimal(5, sender.balance().subtract(amt));
            ps.addBatch();

            ps.setLong(1, txId);
            ps.setInt(2, to);
            ps.setString(3, "CREDIT");
            ps.setBigDecimal(4, amt);
            ps.setBigDecimal(5, receiver.balance().add(amt));
            ps.addBatch();
            ps.executeBatch();
        }

        return new TransferResult(txId, false);
    }

    // ---------------------------------------------------------------- helpers

    /** Deadlock (1213 / SQLState 40001), lock wait timeout (1205), or a duplicate request id race (1062). */
    private static boolean isRetryable(SQLException e) {
        return "40001".equals(e.getSQLState())
            || e.getErrorCode() == 1213
            || e.getErrorCode() == 1205
            || e.getErrorCode() == 1062;   // on retry the idempotency check finds the winner and replays it
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "rollback failed", ex);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
