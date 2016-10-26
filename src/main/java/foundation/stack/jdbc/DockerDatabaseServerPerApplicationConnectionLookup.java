package foundation.stack.jdbc;

import com.google.common.base.Strings;
import foundation.stack.docker.bootstrap.Bootstrap;
import foundation.stack.docker.management.ContainerSpecification;
import foundation.stack.docker.management.DockerClient;
import foundation.stack.docker.management.SpecificationBuilder;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static foundation.stack.jdbc.DockerDatabaseServerContainerReferenceManager.ROOT_PASSWORD_PROPERTY;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerDatabaseServerPerApplicationConnectionLookup implements ConnectionLookup {

    private static Logger logger = Logger.getLogger(DockerDatabaseServerPerApplicationConnectionLookup.class.getName());

    private static final String BRANCH_DATABASE_NAME = "branch";

    private static final String MYSQL_IMAGE_NAME_PROPERTY = "MYSQL_IMAGE_NAME";
    private static final String MYSQL_IMAGE_NAME = "mysql";

    private static final String MYSQL_IMAGE_TAG_PROPERTY = "MYSQL_IMAGE_TAG";
    private static final String MYSQL_VERSION = "5.7";

    private static final int MYSQL_PORT = 3306;

    private static final String DOCKER_HOST_NAME = "stackfoundation";
    private static final String BYPASS_INSTALLATION = "BYPASS_INSTALLATION";

    private static final String MYSQL_ROOT_PASSWORD = "MYSQL_ROOT_PASSWORD";

    private DockerClient dockerClient;
    private DockerDatabaseServerContainerReferenceManager containerManager;

    private final MySqlDatabaseManager databaseManager = new MySqlDatabaseManager();

    private final Bootstrap bootstrap = new Bootstrap();

    private DockerClient getDockerClient() {
        if (dockerClient == null) {
            try {
                dockerClient = bootstrap.bootstrap(System.getenv().containsKey(BYPASS_INSTALLATION), null)
                        .connect(DOCKER_HOST_NAME);
            } catch (Exception e) {
                logger.log(Level.FINE, "Error building docker client", e);
                throw new RuntimeException(e);
            }
        }
        return dockerClient;
    }

    private DockerDatabaseServerContainerReferenceManager getContainerReferenceManager() {
        if (this.containerManager == null) {
            this.containerManager = new DockerDatabaseServerContainerReferenceManager(getDockerClient());
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
    public String getDefaultPassword() {
        return null;
    }

    @Override
    public String getDefaultUsername() {
        return null;
    }

    @Override
    public String find(String query) {
        String applicationName = NameGenerator.generateContextApplicationName();
        try {
            String imageName = System.getProperty(MYSQL_IMAGE_NAME_PROPERTY, MYSQL_IMAGE_NAME);
            String versionTag = System.getProperty(MYSQL_IMAGE_TAG_PROPERTY, MYSQL_VERSION);

            ContainerSpecification containerSpecification = new ContainerSpecification(imageName, versionTag);
            containerSpecification.addPortMapping(MYSQL_PORT, getAvailablePort());
            addRootPasswordEnvironmentVariable(containerSpecification, applicationName);

            String containerConnectionString = getContainerReferenceManager()
                    .getOrCreateContainer(applicationName,
                            SpecificationBuilder.just(containerSpecification));

            if (BRANCH_DATABASE_NAME.equals(query)) {
                String databaseName = databaseManager.getOrCreateBranchDatabase(containerConnectionString,
                        NameGenerator.generateDatabaseName());
                return appendDatabaseName(containerConnectionString, databaseName);
            } else {
                String databaseName = databaseManager.getOrCreateNamedDatabase(containerConnectionString, query);
                return appendDatabaseName(containerConnectionString, databaseName);
            }
        } catch (ExecutionException | SQLException e) {
            logger.log(Level.FINE, "Error getting/creating database for application {0}", applicationName);
            throw new RuntimeException(e);
        }
    }

    private void addRootPasswordEnvironmentVariable(ContainerSpecification containerSpecification, String applicationName) {
        String rootPassword = System.getProperty(ROOT_PASSWORD_PROPERTY);
        if (Strings.isNullOrEmpty(rootPassword)) {
            rootPassword = applicationName;
        }
        containerSpecification.addEnvironmentVariable(MYSQL_ROOT_PASSWORD, rootPassword);
        System.setProperty(ROOT_PASSWORD_PROPERTY, rootPassword);
    }

    private Integer getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            logger.log(Level.FINE, "Could not get a random available port");
            throw new RuntimeException(e);
        }
    }
}
