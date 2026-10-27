package moneytransfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves the ACID / concurrency claims instead of just stating them.
 * Run it against a test database - it creates a few demo users.
 *
 *   Test 1: 40 threads x 25 random transfers between 4 accounts (including A->B and B->A at the
 *           same time) -> no deadlock failures, total money unchanged, no negative balance.
 *   Test 2: 10 threads send the SAME request id at once -> money moves exactly once.
 *   Test 3: 20 threads each try to send Rs.100 from an account holding Rs.1000 -> exactly 10 succeed.
 *   Test 4: ledger matches balances; every transaction has exactly one DEBIT and one CREDIT.
 */
public class ConcurrencyTest {

    private static final AuthService AUTH = new AuthService();
    private static final TransferService TRANSFERS = new TransferService();
    private static final AccountService ACCOUNTS = new AccountService();
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        String tag = Long.toString(System.currentTimeMillis());
        int n = 4;
        int[] userIds = new int[n];
        int[] acctIds = new int[n];
        for (int i = 0; i < n; i++) {
            String email = "test" + i + "_" + tag + "@example.com";
            acctIds[i] = AUTH.register("Tester " + i, email, "password123");
            userIds[i] = AUTH.login(email, "password123").userId();
        }
        BigDecimal startTotal = total(acctIds);
        System.out.println("Accounts " + java.util.Arrays.toString(acctIds) + ", total Rs. " + startTotal);

        // ---- Test 1: random storm, many opposite-direction transfers
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        run(40, t -> {
            Random rnd = new Random(t);
            for (int k = 0; k < 25; k++) {
                int a = rnd.nextInt(n);
                int b = (a + 1 + rnd.nextInt(n - 1)) % n;
                BigDecimal amt = new BigDecimal(1 + rnd.nextInt(300)).setScale(2);
                try {
                    TRANSFERS.transfer(userIds[a], acctIds[a], acctIds[b], amt, UUID.randomUUID().toString());
                    ok.incrementAndGet();
                } catch (BankException e) {
                    if (e.getMessage().startsWith("Insufficient")) {
                        rejected.incrementAndGet();
                    } else {
                        unexpected.incrementAndGet();
                        System.out.println("  unexpected: " + e.getMessage());
                    }
                }
            }
            return null;
        });
        check("Test 1: storm finished with no unexpected failures (ok=" + ok + ", insufficient=" + rejected + ")",
              unexpected.get() == 0);
        check("Test 1: total money unchanged (" + total(acctIds) + ")", total(acctIds).compareTo(startTotal) == 0);
        check("Test 1: no negative balance", noNegative(acctIds));

        // ---- Test 2: same request id from 10 threads at once
        BigDecimal before = ACCOUNTS.getBalance(userIds[0], acctIds[0]);
        String sharedRequest = "dup-" + tag;
        AtomicInteger fresh = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        BigDecimal ten = new BigDecimal("10.00");
        run(10, t -> {
            try {
                TransferResult r = TRANSFERS.transfer(userIds[0], acctIds[0], acctIds[1], ten, sharedRequest);
                (r.replayed() ? replayed : fresh).incrementAndGet();
            } catch (BankException e) {
                System.out.println("  unexpected: " + e.getMessage());
                unexpected.incrementAndGet();
            }
            return null;
        });
        BigDecimal after = ACCOUNTS.getBalance(userIds[0], acctIds[0]);
        check("Test 2: exactly 1 real transfer, 9 replays (fresh=" + fresh + ", replayed=" + replayed + ")",
              fresh.get() == 1 && replayed.get() == 9);
        check("Test 2: sender debited exactly once (" + before + " -> " + after + ")",
              before.subtract(after).compareTo(ten) == 0);

        // ---- Test 3: overdraft race. Make account 2 hold exactly Rs.1000 first.
        BigDecimal bal2 = ACCOUNTS.getBalance(userIds[2], acctIds[2]);
        BigDecimal thousand = new BigDecimal("1000.00");
        if (bal2.compareTo(thousand) > 0) {   // move the surplus away so the test is exact
            TRANSFERS.transfer(userIds[2], acctIds[2], acctIds[3], bal2.subtract(thousand), "trim-" + tag);
        } else if (bal2.compareTo(thousand) < 0) {
            TRANSFERS.transfer(userIds[3], acctIds[3], acctIds[2], thousand.subtract(bal2), "topup-" + tag);
        }
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        BigDecimal hundred = new BigDecimal("100.00");
        run(20, t -> {
            try {
                TRANSFERS.transfer(userIds[2], acctIds[2], acctIds[0], hundred, "race-" + tag + "-" + t);
                succeeded.incrementAndGet();
            } catch (BankException e) {
                if (e.getMessage().startsWith("Insufficient")) {
                    insufficient.incrementAndGet();
                } else {
                    System.out.println("  unexpected: " + e.getMessage());
                    unexpected.incrementAndGet();
                }
            }
            return null;
        });
        check("Test 3: exactly 10 of 20 succeed (succeeded=" + succeeded + ", insufficient=" + insufficient + ")",
              succeeded.get() == 10 && insufficient.get() == 10);
        check("Test 3: final sender balance is 0.00 (" + ACCOUNTS.getBalance(userIds[2], acctIds[2]) + ")",
              ACCOUNTS.getBalance(userIds[2], acctIds[2]).compareTo(BigDecimal.ZERO) == 0);

        // ---- Test 4: ledger integrity
        check("Test 4: total money still unchanged", total(acctIds).compareTo(startTotal) == 0);
        check("Test 4: last ledger balance_after equals real balance for every account", ledgerMatches(acctIds));
        check("Test 4: every transaction has exactly 1 DEBIT + 1 CREDIT", ledgerPaired());
        check("Test 4: no unexpected errors anywhere", unexpected.get() == 0);

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------ helpers

    private static void run(int threads, ThrowingTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int id = i;
            futures.add(pool.submit((Callable<Void>) () -> {
                startGun.await();          // all threads start at the same instant
                return task.run(id);
            }));
        }
        startGun.countDown();
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();
    }

    private interface ThrowingTask {
        Void run(int threadId) throws Exception;
    }

    private static void check(String label, boolean pass) {
        System.out.println((pass ? "PASS  " : "FAIL  ") + label);
        if (!pass) {
            failures++;
        }
    }

    private static String inList(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            sb.append(sb.length() == 0 ? "" : ",").append(id);
        }
        return sb.toString();
    }

    private static BigDecimal total(int[] ids) throws Exception {
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT SUM(balance) FROM accounts WHERE account_id IN (" + inList(ids) + ")");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private static boolean noNegative(int[] ids) throws Exception {
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM accounts WHERE balance < 0 AND account_id IN (" + inList(ids) + ")");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    private static boolean ledgerMatches(int[] ids) throws Exception {
        String sql = "SELECT COUNT(*) FROM accounts a WHERE a.account_id IN (" + inList(ids) + ") "
                   + "AND a.balance <> COALESCE((SELECT l.balance_after FROM ledger_entries l "
                   + "WHERE l.account_id = a.account_id ORDER BY l.entry_id DESC LIMIT 1), 1000.00)";
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    private static boolean ledgerPaired() throws Exception {
        String sql = "SELECT COUNT(*) FROM transactions t WHERE "
                   + "(SELECT COUNT(*) FROM ledger_entries l WHERE l.transaction_id = t.transaction_id "
                   + " AND l.entry_type = 'DEBIT'  AND l.account_id = t.sender_account_id   AND l.amount = t.amount) <> 1 OR "
                   + "(SELECT COUNT(*) FROM ledger_entries l WHERE l.transaction_id = t.transaction_id "
                   + " AND l.entry_type = 'CREDIT' AND l.account_id = t.receiver_account_id AND l.amount = t.amount) <> 1";
        try (Connection c = DBConnection.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }
}
