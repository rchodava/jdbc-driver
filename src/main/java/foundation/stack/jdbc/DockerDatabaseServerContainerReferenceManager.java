package foundation.stack.jdbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import foundation.stack.docker.Bootstrap;
import foundation.stack.docker.DockerClient;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public abstract class DockerDatabaseServerContainerReferenceManager<ReferenceType> {
    private static final String MYSQL_IMAGE_NAME_PROPERTY = "MYSQL_IMAGE_NAME";
    private static final String MYSQL_IMAGE_NAME = "mysql";

    private static final String MYSQL_IMAGE_TAG_PROPERTY = "MYSQL_IMAGE_TAG";
    private static final String MYSQL_VERSION = "5.7";

    private static final String ROOT_PASSWORD_PROPERTY = "ROOT_PASSWORD";
    private static final String DEFAULT_ROOT_PASSWORD = null;

    private static final String APPLICATION_USER_NAME_PROPERTY = "APPLICATION_USER_NAME";
    private static final String DEFAULT_APPLICATION_USER_NAME = null;

    private static final String APPLICATION_USER_PASSWORD_PROPERTY = "APPLICATION_USER_PASSWORD";
    private static final String DEFAULT_APPLICATION_PASSWORD = null;

    private static final String DOCKER_HOST_NAME = "stackfoundation";

    private static int findFreePort() throws IOException {
        try (ServerSocket portSearch = new ServerSocket(0)) {
            return portSearch.getLocalPort();
        }
    }

    private static void suppressDockerClientVerboseLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("org.apache.http").setLevel(Level.ERROR);
        context.getLogger(PullImageResultCallback.class).setLevel(Level.ERROR);
    }

    private static void waitBrieflyForDatabaseServerConnect(DockerClient dockerClient, int sqlServerPort) throws InterruptedException {
        int retries = 10;
        boolean connected = false;
        while (!connected) {
            try (Socket clientConnect = new Socket(dockerClient.getHostIpAddress(), sqlServerPort)) {
                connected = true;
            } catch (IOException e) {
                Thread.sleep(100);
                if (retries == 0) {
                    break;
                }

                retries--;
            }
        }
    }

    private final LoadingCache<String, ReferenceType> databaseServerContainerReferences =
            CacheBuilder.newBuilder().build(new CacheLoader<String, ReferenceType>() {
                @Override
                public ReferenceType load(String applicationName) throws Exception {
                    return createReference(createContainerAndGetConnectionString(applicationName));
                }
            });

    protected abstract ReferenceType createReference(String connectionString);

    protected String getApplicationUserPassword(String applicationName) {
        return System.getProperty(APPLICATION_USER_PASSWORD_PROPERTY, DEFAULT_APPLICATION_PASSWORD);
    }

    protected String getApplicationUserName(String applicationName) {
        return System.getProperty(APPLICATION_USER_NAME_PROPERTY, DEFAULT_APPLICATION_USER_NAME);
    }

    protected String getRootPassword(String applicationName) {
        return System.getProperty(ROOT_PASSWORD_PROPERTY, DEFAULT_ROOT_PASSWORD);
    }

    private String createContainerAndGetConnectionString(String applicationName) throws Exception {
        suppressDockerClientVerboseLogging();

        DockerClient dockerClient = Bootstrap.bootstrapAndConnect(DOCKER_HOST_NAME);
        String imageName = System.getProperty(MYSQL_IMAGE_NAME_PROPERTY, MYSQL_IMAGE_NAME);
        String versionTag = System.getProperty(MYSQL_IMAGE_TAG_PROPERTY, MYSQL_VERSION);

        boolean imageExists = DockerUtilities.doesDockerHostHaveImage(dockerClient, imageName, versionTag);
        if (!imageExists) {
            DockerUtilities.pullImageToDockerHost(dockerClient, imageName, versionTag);
        }

        String containerName = "mysql-" + applicationName;
        int sqlServerPort = findFreePort();

        String rootPassword = getRootPassword(applicationName);
        String applicationUserName = getApplicationUserName(applicationName);
        String applicationUserPassword = getApplicationUserPassword(applicationName);

        DockerUtilities.createMySqlDockerContainerIfNotCreated(dockerClient, imageName, versionTag, containerName, sqlServerPort,
                rootPassword, applicationUserName, applicationUserPassword);

        DockerUtilities.startDockerContainer(dockerClient, containerName);
        sqlServerPort = DockerUtilities.findExposedContainerPort(dockerClient, containerName);

        waitBrieflyForDatabaseServerConnect(dockerClient, sqlServerPort);

        StringBuilder connectionString = new StringBuilder("jdbc:mysql://");
        connectionString.append(dockerClient.getHostIpAddress());
        connectionString.append(':');
        connectionString.append(sqlServerPort);

        if (applicationUserName != null && applicationUserPassword != null) {
            connectionString.append("?user=");
            connectionString.append(applicationUserName);
            connectionString.append("&password=");
            connectionString.append(applicationUserPassword);
        } else {
            connectionString.append("?user=root");
            if (rootPassword != null) {
                connectionString.append("&password=");
                connectionString.append(rootPassword);
            }
        }

        return connectionString.toString();
    }

    public ReferenceType getContainerReference(String applicationName) throws ExecutionException {
        return databaseServerContainerReferences.get(applicationName);
    }
}
