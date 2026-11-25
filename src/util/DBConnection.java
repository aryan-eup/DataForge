package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// CONCEPT: JDBC DriverManager
// DBConnection manages database connectivity using standard JDBC DriverManager.
public class DBConnection {

    // Default configuration with support for environment variable overrides
    private static final String DEFAULT_HOST = System.getenv().getOrDefault("DB_HOST", "127.0.0.1");
    private static final String DEFAULT_DB = System.getenv().getOrDefault("DB_NAME", "dataforge");
    private static final String DEFAULT_USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String DEFAULT_PASS = System.getenv().getOrDefault("DB_PASS", "");

    // Static initializer to register MySQL JDBC driver
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] MySQL JDBC Driver not found in classpath: " + e.getMessage());
        }
    }

    /**
     * Establishes and returns a new JDBC database connection.
     * Tries configured port (or 3306 first, falling back to 3307 if 3306 fails).
     */
    public static Connection getConnection() throws SQLException {
        String customPort = System.getenv("DB_PORT");
        if (customPort != null && !customPort.trim().isEmpty()) {
            String url = "jdbc:mysql://" + DEFAULT_HOST + ":" + customPort + "/" + DEFAULT_DB +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            return DriverManager.getConnection(url, DEFAULT_USER, DEFAULT_PASS);
        }

        // Try standard port 3306 first
        try {
            String url3306 = "jdbc:mysql://" + DEFAULT_HOST + ":3306/" + DEFAULT_DB +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            return DriverManager.getConnection(url3306, DEFAULT_USER, DEFAULT_PASS);
        } catch (SQLException e1) {
            // Fallback to local custom port 3307
            try {
                String url3307 = "jdbc:mysql://" + DEFAULT_HOST + ":3307/" + DEFAULT_DB +
                        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                return DriverManager.getConnection(url3307, DEFAULT_USER, DEFAULT_PASS);
            } catch (SQLException e2) {
                // Also attempt with dataforge_user if root failed
                try {
                    String urlUser = "jdbc:mysql://" + DEFAULT_HOST + ":3307/" + DEFAULT_DB +
                            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
                    return DriverManager.getConnection(urlUser, "dataforge_user", "dataforge");
                } catch (SQLException e3) {
                    throw new SQLException("Could not connect to MySQL on port 3306 or 3307: " + e1.getMessage());
                }
            }
        }
    }
}
