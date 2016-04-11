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
import java.util.logging.Logger;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public abstract class DockerDatabaseServerContainerReferenceManager<ReferenceType> {

    private static Logger logger = Logger.getLogger(DockerDatabaseServerContainerReferenceManager.class.getName());

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

    private static final String BYPASS_INSTALLATION = "BYPASS_INSTALLATION";

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

    private static void waitBrieflyForDatabaseServerConnect(String containerIp, int sqlServerPort) throws InterruptedException {
        int retries = 10;
        boolean connected = false;
        while (!connected) {
            try (Socket clientConnect = new Socket(containerIp, sqlServerPort)) {
                connected = true;
                logger.log(java.util.logging.Level.FINE, "Successfully connected to DB on {0}:{1}", new Object[]{containerIp, sqlServerPort});
            } catch (IOException e) {
                Thread.sleep(100);
                if (retries == 0) {
                    break;
                }

                retries--;
            }
        }
    }

    private final LoadingCache<ContainerReferenceKey, ReferenceType> databaseServerContainerReferences =
            CacheBuilder.newBuilder().build(new CacheLoader<ContainerReferenceKey, ReferenceType>() {
                @Override
                public ReferenceType load(ContainerReferenceKey key) throws Exception {
                    return createReference(createContainerAndGetConnectionString(key.getApplicationName(), key.getProgressMonitor()));
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

    private String createContainerAndGetConnectionString(String applicationName, ProgressMonitor progressMonitor) throws Exception {
        suppressDockerClientVerboseLogging();

        progressMonitor.workChanged(5, 100);
        progressMonitor.statusChanged("STATUS_LOCAL_DOCKER_MACHINE_BOOT");

        DockerClient dockerClient = Bootstrap.bootstrapAndConnect(DOCKER_HOST_NAME, System.getenv().containsKey(BYPASS_INSTALLATION));
        String imageName = System.getProperty(MYSQL_IMAGE_NAME_PROPERTY, MYSQL_IMAGE_NAME);
        String versionTag = System.getProperty(MYSQL_IMAGE_TAG_PROPERTY, MYSQL_VERSION);

        boolean imageExists = DockerUtilities.doesDockerHostHaveImage(dockerClient, imageName, versionTag);
        if (!imageExists) {
            progressMonitor.workChanged(45, 100);
            progressMonitor.statusChanged("STATUS_LOCAL_DOCKER_IMAGE_PULL");

            DockerUtilities.pullImageToDockerHost(dockerClient, imageName, versionTag);
        }

        String containerName = "mysql-" + applicationName;
        Integer sqlServerPort = null;

        String containerIp = dockerClient.getHostIpAddress();

        // If no host ip, assume container is not running within host
        if (containerIp != null) {
            // Only search for a local free port if running container within host
            sqlServerPort = findFreePort();
        }

        String rootPassword = getRootPassword(applicationName);
        String applicationUserName = getApplicationUserName(applicationName);
        String applicationUserPassword = getApplicationUserPassword(applicationName);

        progressMonitor.workChanged(75, 100);
        progressMonitor.statusChanged("STATUS_LOCAL_DOCKER_CONTAINER_CREATE");

        DockerUtilities.createMySqlDockerContainerIfNotCreated(dockerClient, imageName, versionTag, containerName, sqlServerPort,
                rootPassword, applicationUserName, applicationUserPassword);

        progressMonitor.workChanged(85, 100);
        progressMonitor.statusChanged("STATUS_LOCAL_DOCKER_CONTAINER_START");

        DockerUtilities.startDockerContainer(dockerClient, containerName);

        sqlServerPort = DockerUtilities.findExposedContainerPort(dockerClient, containerName);

        if (containerIp == null) {
            containerIp = DockerUtilities.getContainerIp(dockerClient, containerName);
        }

        progressMonitor.workChanged(90, 100);
        progressMonitor.statusChanged("STATUS_LOCAL_DOCKER_DB_CONNECTION");

        waitBrieflyForDatabaseServerConnect(containerIp, sqlServerPort);

        StringBuilder connectionString = new StringBuilder("jdbc:mysql://");
        connectionString.append(containerIp);
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
        return getContainerReference(applicationName, null);
    }

    public ReferenceType getContainerReference(String applicationName, ProgressMonitor progressMonitor) throws ExecutionException {
        return databaseServerContainerReferences.get(new ContainerReferenceKey(applicationName,
                progressMonitor != null ? progressMonitor : ProgressMonitor.NULL));
    }

    private static class ContainerReferenceKey {
        private final String applicationName;
        private final ProgressMonitor progressMonitor;

        public ContainerReferenceKey(String applicationName, ProgressMonitor progressMonitor) {
            this.applicationName = applicationName;
            this.progressMonitor = progressMonitor;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ContainerReferenceKey) {
                return applicationName.equals(((ContainerReferenceKey) obj).applicationName);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return applicationName.hashCode();
        }

        public String getApplicationName() {
            return applicationName;
        }

        public ProgressMonitor getProgressMonitor() {
            return progressMonitor;
        }
    }
}
