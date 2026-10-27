package moneytransfer;

/**
 * A business-rule or system failure whose message is SAFE to show to the user.
 * Low-level SQLExceptions are never shown directly (they can leak table names, SQL, etc.);
 * services catch them, log them, and wrap them in a BankException with a friendly message.
 */
public class BankException extends Exception {

    private static final long serialVersionUID = 1L;

    public BankException(String message) {
        super(message);
    }

    public BankException(String message, Throwable cause) {
        super(message, cause);
    }
}
