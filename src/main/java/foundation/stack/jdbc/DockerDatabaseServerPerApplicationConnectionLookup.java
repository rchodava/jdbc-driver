package foundation.stack.jdbc;

import foundation.stack.docker.Bootstrap;
import foundation.stack.docker.ContainerDefinition;
import foundation.stack.docker.ContainerProperties;
import foundation.stack.docker.DockerClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerDatabaseServerPerApplicationConnectionLookup implements ConnectionLookup {

    private static Logger logger = Logger.getLogger(DockerDatabaseServerContainerReferenceManager.class.getName());

    private static final String BRANCH_DATABASE_NAME = "branch";

    private static final String MYSQL_IMAGE_NAME_PROPERTY = "MYSQL_IMAGE_NAME";
    private static final String MYSQL_IMAGE_NAME = "mysql";

    private static final String MYSQL_IMAGE_TAG_PROPERTY = "MYSQL_IMAGE_TAG";
    private static final String MYSQL_VERSION = "5.7";

    private static final int MYSQL_PORT = 3306;

    private static final String DOCKER_HOST_NAME = "stackfoundation";
    private static final String BYPASS_INSTALLATION = "BYPASS_INSTALLATION";

    private DockerClient dockerClient;
    private DockerDatabaseServerContainerReferenceManager<String> containerManager;

    private final MySqlDatabaseManager databaseManager = new MySqlDatabaseManager();

    private DockerClient getDockerClient() {
        if (dockerClient == null) {
            try {
                dockerClient = Bootstrap.bootstrapAndConnect(DOCKER_HOST_NAME, System.getenv().containsKey(BYPASS_INSTALLATION));
            } catch (Exception e) {
                logger.log(Level.FINE, "Error building docker client", e);
                throw new RuntimeException(e);
            }
        }
        return dockerClient;
    }

    private DockerDatabaseServerContainerReferenceManager<String> getContainerReferenceManager() {
        if (this.containerManager == null) {
            this.containerManager = new DockerDatabaseServerContainerReferenceManager<String>(getDockerClient()) {
                @Override
                protected String createReference(String connectionString) {
                    return connectionString;
                }
            };
        }
        return containerManager;
    }

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
            String containerConnectionString = getContainerReferenceManager().createContainerReference(buildContainerProperties(applicationName));
            if (BRANCH_DATABASE_NAME.equals(query)) {
                String databaseName = databaseManager.getOrCreateBranchDatabase(containerConnectionString, NameGenerator.generateDatabaseName());
                return appendDatabaseName(containerConnectionString, databaseName);
            } else {
                String databaseName = databaseManager.getOrCreateNamedDatabase(containerConnectionString, NameGenerator.generateDatabaseName());
                return appendDatabaseName(containerConnectionString, databaseName);
            }
        } catch (SQLException e) {
            logger.log(Level.FINE, "Error getting/creating database for application {0}", applicationName);
            throw new RuntimeException(e);
        }
    }

    private ContainerProperties buildContainerProperties(String applicationName) {
        String containerName = "mysql-" + applicationName;
        String imageName = System.getProperty(MYSQL_IMAGE_NAME_PROPERTY, MYSQL_IMAGE_NAME);
        String versionTag = System.getProperty(MYSQL_IMAGE_TAG_PROPERTY, MYSQL_VERSION);

        ContainerDefinition containerDefinition = new ContainerDefinition(imageName, versionTag, new String[0],
                Collections.emptyMap(), Collections.singletonMap(getPort(), MYSQL_PORT));

        return new ContainerProperties(containerName, getDockerClient().getHostIpAddress(), containerDefinition);
    }

    private int getPort() {
        Integer sqlServerPort = null;
        String containerIp = dockerClient.getHostIpAddress();
        // If no host ip, assume container is not running within host
        if (containerIp != null) {
            // Only search for a local free port if running container within host
            try {
                sqlServerPort = findFreePort();
            } catch (IOException e) {
                logger.log(Level.FINE, "Error trying to find an available port");
                throw new RuntimeException(e);
            }
        }
        return sqlServerPort;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket portSearch = new ServerSocket(0)) {
            return portSearch.getLocalPort();
        }
    }
}
