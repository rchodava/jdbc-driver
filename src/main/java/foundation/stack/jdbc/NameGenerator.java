package foundation.stack.jdbc;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class NameGenerator {
    private static final String JDBC_DRIVER_PACKAGE = DockerDatabaseServerPerApplicationConnectionLookup.class.getPackage().getName();

    private static String getCallerClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace != null && stackTrace.length > 0) {
            for (int i = 1; i < stackTrace.length; i++) {
                StackTraceElement element = stackTrace[i];
                String className = element.getClassName();

                if (!className.startsWith(JDBC_DRIVER_PACKAGE)) {
                    return className;
                }
            }
        }

        return null;
    }

    private static Class<?> getCallerClass() throws ClassNotFoundException {
        String className = getCallerClassName();
        if (className != null) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = DockerDatabaseServerPerApplicationConnectionLookup.class.getClassLoader();
            }

            return Class.forName(className, true, classLoader);
        }

        return null;
    }

    private static File findGitRoot(File file) {
        while (file != null) {
            File gitDirectory = new File(file, ".git");
            if (gitDirectory.exists() && gitDirectory.isDirectory()) {
                return gitDirectory;
            }

            file = file.getParentFile();
        }

        return null;
    }

    private static String findCodePathOfClass(Class<?> clazz) {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URL codeLocation = codeSource.getLocation();
            if (codeLocation != null) {
                return codeLocation.getPath();
            }
        }

        return null;
    }

    private static File findGitRootOfClassSource(Class<?> clazz) {
        String codePath = findCodePathOfClass(clazz);
        if (codePath != null) {
            File codeFile = new File(codePath);

            if (codeFile.exists() && codeFile.isDirectory()) {
                // Running from a class file (probably an IDE)
                return findGitRoot(codeFile);
            } else if (codeFile.exists()) {
                // Running from a JAR (maybe Maven?)
                return findGitRoot(codeFile.getParentFile());
            }
        }

        return null;
    }

    private static String determineGitBranch(File gitRoot) {
        try (Repository gitRepository = FileRepositoryBuilder.create(gitRoot)) {
            String fullBranch = getHeadBranch(gitRepository);
            if (fullBranch != null) return fullBranch;
        } catch (IOException e) {
        }

        return null;
    }

    private static String getHeadBranch(Repository gitRepository) throws IOException {
        String fullBranch = gitRepository.getFullBranch();
        if (fullBranch != null && fullBranch.startsWith(Constants.R_HEADS)) {
            return fullBranch.substring(Constants.R_HEADS.length());
        }

        return null;
    }

    private static File findGitRootOfCallerClass() {
        Class<?> callerClass = null;
        try {
            callerClass = getCallerClass();
        } catch (ClassNotFoundException e) {
        }

        if (callerClass != null) {
            return findGitRootOfClassSource(callerClass);
        }

        return null;
    }

    private static String determineRemoteUri() {
        File gitRoot = findGitRootOfCallerClass();
        if (gitRoot != null) {
            try (Repository gitRepository = FileRepositoryBuilder.create(gitRoot)) {
                return getRemoteUri(gitRepository, getHeadBranch(gitRepository));
            } catch (IOException e) {
            }
        }

        return null;
    }

    private static String getRemoteUri(Repository gitRepository, String branchName) {
        Config configuration = gitRepository.getConfig();

        String remote = configuration.getString(
                    ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
                    ConfigConstants.CONFIG_KEY_REMOTE);

        if (remote == null) {
            remote = Constants.DEFAULT_REMOTE_NAME;
        }

        if (!remote.equals(".")) {
            String remoteUri = configuration.getString(
                    ConfigConstants.CONFIG_REMOTE_SECTION, remote,
                    ConfigConstants.CONFIG_KEY_URL);

            return remoteUri;
        }

        return null;
    }

    private static String generateApplicationNameFromCodeLocation() {
        Class<?> callerClass = null;
        try {
            callerClass = getCallerClass();
        } catch (ClassNotFoundException e) {
        }

        if (callerClass != null) {
            String codePath = findCodePathOfClass(callerClass);
            if (codePath != null) {
                return new File(codePath).getName();
            }
        }

        return null;
    }

    private static String generateApplicationNameFromGitRemoteUri(String gitUri) {
        StringBuilder applicationName = new StringBuilder();

        String[] segments = gitUri.split("/");
        if (segments.length > 0) {
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];

                if (segment.endsWith(".git")) {
                    segment = segment.substring(0, segment.length() - 4);
                }

                if (!segment.isEmpty()) {
                    applicationName.append(segment);
                    if (i + 1 < segments.length) {
                        applicationName.append('-');
                    }
                }
            }
        } else {
            return null;
        }

        return applicationName.toString();
    }

    public static String generateContextApplicationName() {
        File gitRoot = findGitRootOfCallerClass();
        if (gitRoot != null) {
            String remoteUri = determineRemoteUri();
            String path = URI.create(remoteUri).getPath();
            if (path != null) {
                String applicationName = generateApplicationNameFromGitRemoteUri(path);
                if (applicationName != null) {
                    return applicationName;
                }
            }
        }

        String applicationName = generateApplicationNameFromCodeLocation();
        if (applicationName != null) {
            return applicationName;
        }

        return getCallerClassName();
    }

    public static String generateDatabaseName() {
        File gitRoot = findGitRootOfCallerClass();
        if (gitRoot != null) {
            return determineGitBranch(gitRoot);
        }

        return "master";
    }
}
