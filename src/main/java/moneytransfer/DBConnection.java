package moneytransfer;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Opens DB connections. Settings come from db.properties on the classpath, or DB_URL/DB_USER/DB_PASSWORD env vars. */
public final class DBConnection {
    private static final String URL, USER, PASSWORD;

    static {
        Properties p = new Properties();
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            // fall through to env vars
        }
        URL = pick("DB_URL", p.getProperty("db.url"));
        USER = pick("DB_USER", p.getProperty("db.user"));
        PASSWORD = pick("DB_PASSWORD", p.getProperty("db.password"));
        if (URL == null || USER == null || PASSWORD == null) {
            throw new IllegalStateException("Database config missing: create src/main/java/db.properties "
                + "(copy db.properties.example) or set DB_URL, DB_USER, DB_PASSWORD.");
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");   // makes sure the driver loads inside Tomcat
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL driver jar missing from WEB-INF/lib", e);
        }
    }

    private DBConnection() { }

    private static String pick(String env, String file) {
        String v = System.getenv(env);
        return (v != null && !v.isBlank()) ? v : file;
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
