package foundation.stack.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class MySqlDatabaseManager {
    private static String composeBranchDatabaseName(String branchName) {
        return "branch_" + branchName;
    }

    private static String sanitize(String name) {
        int length = name.length();
        int sanitizedLength = 0;
        char[] sanitized = new char[length];
        for (int i = 0; i < length; i++) {
            char character = name.charAt(i);
            if (Character.isLetterOrDigit(character) || character == '_') {
                sanitized[sanitizedLength] = character;
                sanitizedLength++;
            }
        }

        return new String(sanitized, 0, sanitizedLength);
    }

    private static Connection openRawConnection(String connectionString) throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException(e);
        }

        return DriverManager.getConnection(connectionString);
    }

    private static void executeUpdateStatement(String connectionString, String sql) throws SQLException {
        try (Connection dbConnection = openRawConnection(connectionString);
             Statement statement = dbConnection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void createDatabaseIfNotExists(String serverConnectionString, String databaseName) throws SQLException {
        String sanitizedName = sanitize(databaseName);
        executeUpdateStatement(serverConnectionString, "CREATE DATABASE IF NOT EXISTS " + sanitizedName);
    }

    public String getOrCreateBranchDatabase(String serverConnectionString, String branchName) throws SQLException {
        String databaseName = composeBranchDatabaseName(branchName);
        createDatabaseIfNotExists(serverConnectionString, databaseName);
        return databaseName;
    }

    public String getOrCreateNamedDatabase(String serverConnectionString, String name) throws SQLException {
        createDatabaseIfNotExists(serverConnectionString, name);
        return name;
    }
}
