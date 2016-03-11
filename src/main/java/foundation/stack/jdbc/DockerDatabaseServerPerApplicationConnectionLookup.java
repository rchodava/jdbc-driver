package foundation.stack.jdbc;

import java.util.concurrent.ExecutionException;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerDatabaseServerPerApplicationConnectionLookup implements ConnectionLookup {
    private static final String BRANCH_DATABASE_NAME = "branch";

    private final DockerDatabaseServerContainerManager containerManager = new DockerDatabaseServerContainerManager();

    @Override
    public String find(String query) {
        if (BRANCH_DATABASE_NAME.equals(query)) {
            String applicationName = NameGenerator.generateContextApplicationName();
            try {
                String containerConnectionString = containerManager.getContainerConnectionString(applicationName);
                return containerConnectionString + '/' + NameGenerator.generateDatabaseName();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }
}
