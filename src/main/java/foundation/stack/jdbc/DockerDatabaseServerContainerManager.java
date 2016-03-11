package foundation.stack.jdbc;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import foundation.stack.docker.Bootstrap;
import foundation.stack.docker.DockerClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerDatabaseServerContainerManager {
    private static final String MYSQL_IMAGE_NAME_PROPERTY = "MYSQL_IMAGE_NAME";
    private static final String MYSQL_IMAGE_NAME = "mysql";

    private static final String MYSQL_IMAGE_TAG_PROPERTY = "MYSQL_IMAGE_TAG";
    private static final String MYSQL_VERSION = "5.7";

    private static final String ROOT_PASSWORD_PROPERTY = "ROOT_PASSWORD";
    private static final String DEFAULT_ROOT_PASSWORD = "^r00t$p4ssw0rd$";

    private static final String APPLICATION_USER_NAME_PROPERTY = "APPLICATION_USER_NAME";
    private static final String DEFAULT_APPLICATION_USER_NAME = "application";

    private static final String APPLICATION_USER_PASSWORD_PROPERTY = "APPLICATION_USER_PASSWORD";
    private static final String DEFAULT_APPLICATION_PASSWORD = "^p4ssw0rd$";

    private static int findFreePort() throws IOException {
        try (ServerSocket portSearch = new ServerSocket(0)) {
            return portSearch.getLocalPort();
        }
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

    private final LoadingCache<String, String> databaseServerContainers =
            CacheBuilder.newBuilder().build(new CacheLoader<String, String>() {
                @Override
                public String load(String applicationName) throws Exception {
                    return createContainerAndGetConnectionString(applicationName);
                }
            });

    private String createContainerAndGetConnectionString(String applicationName) throws Exception {
        DockerClient dockerClient = Bootstrap.bootstrapAndConnect("stackfoundation-platform");
        String imageName = System.getProperty(MYSQL_IMAGE_NAME_PROPERTY, MYSQL_IMAGE_NAME);
        String versionTag = System.getProperty(MYSQL_IMAGE_TAG_PROPERTY, MYSQL_VERSION);

        boolean imageExists = DockerUtilities.doesDockerHostHaveImage(dockerClient, imageName, versionTag);
        if (!imageExists) {
            DockerUtilities.pullImageToDockerHost(dockerClient, imageName, versionTag);
        }

        String containerName = "mysql-" + applicationName;
        int sqlServerPort = findFreePort();

        String rootPassword = System.getProperty(ROOT_PASSWORD_PROPERTY, DEFAULT_ROOT_PASSWORD);
        String applicationUserName = System.getProperty(APPLICATION_USER_NAME_PROPERTY, DEFAULT_APPLICATION_USER_NAME);
        String applicationUserPassword = System.getProperty(APPLICATION_USER_PASSWORD_PROPERTY, DEFAULT_APPLICATION_PASSWORD);

        DockerUtilities.createMySqlDockerContainerIfNotCreated(dockerClient, imageName, versionTag, containerName, sqlServerPort,
                rootPassword, applicationUserName, applicationUserPassword);

        DockerUtilities.startDockerContainer(dockerClient, containerName);
        sqlServerPort = DockerUtilities.findExposedContainerPort(dockerClient, containerName);

        waitBrieflyForDatabaseServerConnect(dockerClient, sqlServerPort);

        return "jdbc:mysql://" + dockerClient.getHostIpAddress() + ":" + sqlServerPort;
    }

    public String getContainerConnectionString(String applicationName) throws ExecutionException {
        return databaseServerContainers.get(applicationName);
    }
}
