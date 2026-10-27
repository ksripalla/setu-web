package moneytransfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Registration and login. Stateless: the web layer will keep the logged-in User in the HttpSession. */
public final class AuthService {

    public record User(int userId, String name, String email) {
        public int getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
    }

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    /** DEMO ONLY: gives every new account some money so transfers can be tried. Remove for real use. */
    private static final BigDecimal DEMO_OPENING_BALANCE = new BigDecimal("1000.00");

    /** Used to burn the same CPU time when the email does not exist (prevents user enumeration by timing). */
    private static final class DummyHash {
        static final String VALUE = PasswordUtil.hashPassword("not-a-real-password");
    }

    /** Creates the user AND their first account in one transaction. Returns the new account id. */
    public int register(String name, String email, String password) throws BankException {
        name = name == null ? "" : name.trim();
        email = email == null ? "" : email.trim().toLowerCase();
        if (name.isEmpty() || name.length() > 100) {
            throw new BankException("Name must be 1 to 100 characters.");
        }
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            throw new BankException("Please enter a valid email address.");
        }
        if (password == null || password.length() < 8) {
            throw new BankException("Password must be at least 8 characters.");
        }

        String hash = PasswordUtil.hashPassword(password);

        try (Connection c = DBConnection.get()) {
            c.setAutoCommit(false);
            try {
                int userId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, email);
                    ps.setString(3, hash);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        userId = keys.getInt(1);
                    }
                }
                int accountId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO accounts (user_id, balance) VALUES (?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, userId);
                    ps.setBigDecimal(2, DEMO_OPENING_BALANCE);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        accountId = keys.getInt(1);
                    }
                }
                c.commit();
                return accountId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {   // duplicate entry on unique email
                throw new BankException("This email is already registered.");
            }
            LOG.log(Level.SEVERE, "register failed", e);
            throw new BankException("Registration failed. Please try again later.", e);
        }
    }

    public User login(String email, String password) throws BankException {
        email = email == null ? "" : email.trim().toLowerCase();
        if (password == null) {
            password = "";
        }

        try (Connection c = DBConnection.get()) {
            int userId;
            String name;
            String hash;
            boolean locked;

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT user_id, name, password_hash, "
                  + "(locked_until IS NOT NULL AND locked_until > NOW()) AS is_locked "
                  + "FROM users WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        PasswordUtil.checkPassword(password, DummyHash.VALUE);
                        throw new BankException("Invalid email or password.");
                    }
                    userId = rs.getInt("user_id");
                    name = rs.getString("name");
                    hash = rs.getString("password_hash");
                    locked = rs.getBoolean("is_locked");
                }
            }

            if (locked) {
                throw new BankException("Too many failed attempts. Account locked for a while - try again later.");
            }

            if (PasswordUtil.checkPassword(password, hash)) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?")) {
                    ps.setInt(1, userId);
                    ps.executeUpdate();
                }
                return new User(userId, name, email);
            }

            // Wrong password: count it, and lock the account after too many.
            // NOTE: MySQL evaluates SET assignments left to right, so locked_until must come
            // BEFORE failed_attempts to see the old counter value.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE users SET "
                  + "locked_until = IF(failed_attempts + 1 >= ?, NOW() + INTERVAL ? MINUTE, locked_until), "
                  + "failed_attempts = failed_attempts + 1 "
                  + "WHERE user_id = ?")) {
                ps.setInt(1, MAX_FAILED_ATTEMPTS);
                ps.setInt(2, LOCK_MINUTES);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }
            throw new BankException("Invalid email or password.");

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "login failed", e);
            throw new BankException("Login failed. Please try again later.", e);
        }
    }
}
