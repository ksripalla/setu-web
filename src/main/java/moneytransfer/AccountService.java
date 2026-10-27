package moneytransfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read-only account queries. Every query is scoped to the logged-in user in SQL itself
 * (WHERE ... user_id = ?), so one user can never read another user's balance or statement.
 */
public final class AccountService {

    public record AccountSummary(int accountId, BigDecimal balance, String status) {
        public int getAccountId() { return accountId; }
        public BigDecimal getBalance() { return balance; }
        public String getStatus() { return status; }
    }

    /** One line of an account statement. type is DEBIT or CREDIT. */
    public record StatementLine(long transactionId, String type, int counterpartyAccountId,
                                BigDecimal amount, BigDecimal balanceAfter,
                                LocalDateTime createdAt, String requestId) {
        public long getTransactionId() { return transactionId; }
        public String getType() { return type; }
        public int getCounterpartyAccountId() { return counterpartyAccountId; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getBalanceAfter() { return balanceAfter; }
        public String getWhen() { return createdAt.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")); }
    }

    private static final Logger LOG = Logger.getLogger(AccountService.class.getName());
    private static final int MAX_PAGE_SIZE = 100;

    public List<AccountSummary> getAccounts(int userId) throws BankException {
        String sql = "SELECT account_id, balance, status FROM accounts WHERE user_id = ? ORDER BY account_id";
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<AccountSummary> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new AccountSummary(rs.getInt("account_id"),
                            rs.getBigDecimal("balance"), rs.getString("status")));
                }
                return list;
            }
        } catch (SQLException e) {
            throw failed("getAccounts", e);
        }
    }

    public BigDecimal getBalance(int userId, int accountId) throws BankException {
        String sql = "SELECT balance FROM accounts WHERE account_id = ? AND user_id = ?";
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Same message whether the account does not exist or belongs to someone else.
                    throw new BankException("Account not found.");
                }
                return rs.getBigDecimal("balance");
            }
        } catch (SQLException e) {
            throw failed("getBalance", e);
        }
    }

    /** Newest first. page starts at 1. */
    public List<StatementLine> getStatement(int userId, int accountId, int page, int pageSize)
            throws BankException {
        if (page < 1) {
            page = 1;
        }
        pageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));

        String sql =
            "SELECT l.transaction_id, l.entry_type, l.amount, l.balance_after, l.created_at, t.request_id, "
          + "       CASE WHEN l.entry_type = 'DEBIT' THEN t.receiver_account_id "
          + "            ELSE t.sender_account_id END AS counterparty "
          + "FROM ledger_entries l "
          + "JOIN transactions t ON t.transaction_id = l.transaction_id "
          + "JOIN accounts a ON a.account_id = l.account_id "
          + "WHERE l.account_id = ? AND a.user_id = ? "
          + "ORDER BY l.entry_id DESC LIMIT ? OFFSET ?";
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, userId);
            ps.setInt(3, pageSize);
            ps.setInt(4, (page - 1) * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                List<StatementLine> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new StatementLine(
                        rs.getLong("transaction_id"),
                        rs.getString("entry_type"),
                        rs.getInt("counterparty"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance_after"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("request_id")));
                }
                return list;
            }
        } catch (SQLException e) {
            throw failed("getStatement", e);
        }
    }

    private static BankException failed(String what, SQLException e) {
        LOG.log(Level.SEVERE, what + " failed", e);
        return new BankException("Could not load data. Please try again later.", e);
    }
}
