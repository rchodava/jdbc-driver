package foundation.stack.jdbc;

import foundation.stack.docker.management.ContainerProperties;
import foundation.stack.docker.management.DockerClient;
import foundation.stack.docker.management.ContainerManager;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerDatabaseServerContainerReferenceManager extends ContainerManager<String> {

    private static Logger logger = Logger.getLogger(DockerDatabaseServerContainerReferenceManager.class.getName());

    public static final String ROOT_PASSWORD_PROPERTY = "ROOT_PASSWORD";
    private static final String DEFAULT_ROOT_PASSWORD = null;

    private static final String APPLICATION_USER_NAME_PROPERTY = "APPLICATION_USER_NAME";
    private static final String DEFAULT_APPLICATION_USER_NAME = null;

    private static final String APPLICATION_USER_PASSWORD_PROPERTY = "APPLICATION_USER_PASSWORD";
    private static final String DEFAULT_APPLICATION_PASSWORD = null;

    protected DockerDatabaseServerContainerReferenceManager(DockerClient dockerClient) {
        super(dockerClient);
    }

    private void waitBrieflyForDatabaseServerConnect(String containerIp, int sqlServerPort) throws InterruptedException {
        int retries = 25;
        boolean connected = false;
        logger.log(java.util.logging.Level.INFO, "Trying to connect to DB..");
        while (!connected) {
            try (Socket ignored = new Socket(containerIp, sqlServerPort)) {
                connected = true;
                logger.log(java.util.logging.Level.INFO, "Successfully connected to DB on {0}:{1} after {2} retries",
                        new Object[]{containerIp, sqlServerPort, (100 - retries)});
            } catch (IOException e) {
                Thread.sleep(500);
                if (retries == 0) {
                    break;
                }
                retries--;
            }
        }
        if (!connected) {
            logger.log(java.util.logging.Level.INFO, "Could not connect to database!!");
        }
    }

    protected String getApplicationUserPassword() {
        return System.getProperty(APPLICATION_USER_PASSWORD_PROPERTY, DEFAULT_APPLICATION_PASSWORD);
    }

    protected String getApplicationUserName() {
        return System.getProperty(APPLICATION_USER_NAME_PROPERTY, DEFAULT_APPLICATION_USER_NAME);
    }

    protected String getRootPassword() {
        return System.getProperty(ROOT_PASSWORD_PROPERTY, DEFAULT_ROOT_PASSWORD);
    }

    protected String createContainerReference(ContainerProperties containerProperties) {
        int sqlServerPort = containerProperties.getPublishedPorts().entrySet().iterator().next().getValue().getPort();
        try {
            waitBrieflyForDatabaseServerConnect(containerProperties.getHostIp(), sqlServerPort);
        } catch (InterruptedException e) {
            logger.log(Level.FINE, "Error testing connection to database server ", e);
            throw new RuntimeException(e);
        }

        return buildConnectionString(containerProperties, sqlServerPort);
    }

    private String buildConnectionString(ContainerProperties containerProperties, Integer sqlServerPort) {
        String rootPassword = getRootPassword();
        String applicationUserName = getApplicationUserName();
        String applicationUserPassword = getApplicationUserPassword();

        StringBuilder connectionString = new StringBuilder("jdbc:mysql://");
        connectionString.append(containerProperties.getHostIp());
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
}
