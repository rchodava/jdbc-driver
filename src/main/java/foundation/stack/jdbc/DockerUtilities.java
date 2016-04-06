package foundation.stack.jdbc;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.command.PullImageResultCallback;
import foundation.stack.docker.DockerClient;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class DockerUtilities {
    private static Logger logger = Logger.getLogger(DockerUtilities.class.getName());

    private static final int MYSQL_PORT = 3306;

    public static void createMySqlDockerContainerIfNotCreated(
            DockerClient dockerClient, String imageName, String tag, String containerName, Integer sqlPort,
            String rootPassword, String applicationUserName, String applicationUserPassword) {
        try {
            logger.log(Level.FINER, "Trying to create container {0}", containerName);

            String[] environmentVariables;
            if (rootPassword != null) {
                if (applicationUserName != null && applicationUserPassword != null) {
                    environmentVariables = new String[] {
                            "MYSQL_ROOT_PASSWORD=" + rootPassword,
                            "MYSQL_USER=" + applicationUserName,
                            "MYSQL_PASSWORD=" + applicationUserPassword };
                } else {
                    environmentVariables = new String[] { "MYSQL_ROOT_PASSWORD=" + rootPassword };
                }
            } else {
                if (applicationUserName != null && applicationUserPassword != null) {
                    environmentVariables = new String[] {
                            "MYSQL_ALLOW_EMPTY_PASSWORD=yes",
                            "MYSQL_USER=" + applicationUserName,
                            "MYSQL_PASSWORD=" + applicationUserPassword };
                } else {
                    environmentVariables = new String[] { "MYSQL_ALLOW_EMPTY_PASSWORD=yes" };
                }
            }

            CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(imageName + ":" + tag)
                    .withName(containerName)
                    .withEnv(environmentVariables);

            if (sqlPort != null) {
                // Don't expose ports nor create bindings unless we can assign an available local port
                createContainerCmd = createContainerCmd.withExposedPorts(ExposedPort.tcp(sqlPort))
                        .withPortBindings(new PortBinding(new Ports.Binding(sqlPort), ExposedPort.tcp(MYSQL_PORT)));
            }

            createContainerCmd.exec();
        } catch (ConflictException e) {
            logger.log(Level.FINER, "Container {0} already exists, continuing", containerName);
        }
    }

    public static boolean doesDockerHostHaveImage(DockerClient dockerClient, String imageName, String version) {
        List<Image> images = dockerClient.listImagesCmd().withImageNameFilter(imageName).exec();
        if (images != null && images.size() > 0) {
            String tagName = imageName + ":" + version;
            for (Image image : images) {
                String[] tags = image.getRepoTags();
                if (tags != null) {
                    for (String tag : tags) {
                        if (tag.contains(tagName)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static void pullImageToDockerHost(DockerClient dockerClient, String imageName, String tag) {
        logger.log(Level.FINER, "Docker host does not have {0} image, getting it now (this may take a bit of time)", imageName + ":" + tag);
        PullImageResultCallback callback = new PullImageResultCallback();

        dockerClient.pullImageCmd(imageName)
                .withTag(tag)
                .exec(callback);

        callback.awaitSuccess();
    }

    public static void startDockerContainer(DockerClient dockerClient, String containerName) {
        logger.log(Level.FINER, "Trying to start container {0}", containerName);
        try {
            dockerClient.startContainerCmd(containerName).exec();
        } catch (NotModifiedException e) {
            logger.log(Level.FINER, "Container {0} is already started, continuing", containerName);
        }
    }

    public static int findExposedContainerPort(DockerClient dockerClient, String containerName) {
        int port = -1;

        try {
            InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(containerName).exec();
            NetworkSettings networkSettings = inspectContainerResponse.getNetworkSettings();
            if (networkSettings != null) {
                Ports ports = networkSettings.getPorts();
                if (ports != null) {
                    Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();
                    if (bindings != null && bindings.size() > 0) {
                        for (Map.Entry<ExposedPort, Ports.Binding[]> binding : bindings.entrySet()) {
                            ExposedPort exposedPort = binding.getKey();
                            if (exposedPort != null) {
                                Ports.Binding[] portBindings = binding.getValue();
                                if (portBindings == null) {
                                    port = exposedPort.getPort();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (NotFoundException e) {
        }

        if (port != -1) {
            logger.log(Level.FINER, "Found existing container with exposed MySQL port {0}", port);
        }
        return port;
    }

    public static String getContainerIp(DockerClient dockerClient, String containerName) {
        String containerIp = null;
        try {
            InspectContainerResponse inspectContainerResponse = dockerClient.inspectContainerCmd(containerName).exec();
            NetworkSettings networkSettings = inspectContainerResponse.getNetworkSettings();
            if (networkSettings != null) {
                containerIp = networkSettings.getIpAddress();
            }
        } catch (NotFoundException e) {
        }

        if (containerIp != null) {
            logger.log(Level.FINER, "Found {0} ip for container with name {1}",
                    new Object[]{containerIp, containerName});
        }
        return containerIp;
    }
}
