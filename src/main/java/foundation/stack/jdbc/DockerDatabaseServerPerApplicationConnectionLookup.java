package foundation.stack.jdbc;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerDatabaseServerPerApplicationConnectionLookup implements ConnectionLookup {
    private static final String BRANCH_DATABASE_NAME = "branch";

    private final DockerDatabaseServerContainerReferenceManager<String> containerManager =
            new DockerDatabaseServerContainerReferenceManager<String>() {
                @Override
                protected String createReference(String connectionString) {
                    return connectionString;
                }
            };

    private final MySqlDatabaseManager databaseManager = new MySqlDatabaseManager();

    private String appendDatabaseName(String connectionString, String databaseName) {
        int querySeparator = connectionString.indexOf('?');
        if (querySeparator > 0) {
            return connectionString.substring(0, querySeparator) + '/' + databaseName + connectionString.substring(querySeparator);
        }

        return connectionString + '/' + databaseName;
    }

    @Override
    public String find(String query) {
        String applicationName = NameGenerator.generateContextApplicationName();
        try {
            String containerConnectionString = containerManager.getContainerReference(applicationName);
            if (BRANCH_DATABASE_NAME.equals(query)) {
                String databaseName = databaseManager.getOrCreateBranchDatabase(containerConnectionString, NameGenerator.generateDatabaseName());
                return appendDatabaseName(containerConnectionString, databaseName);
            } else {
                String databaseName = databaseManager.getOrCreateNamedDatabase(containerConnectionString, NameGenerator.generateDatabaseName());
                return appendDatabaseName(containerConnectionString, databaseName);
            }
        } catch (ExecutionException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
