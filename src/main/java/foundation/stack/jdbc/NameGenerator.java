package foundation.stack.jdbc;

import com.google.common.base.Strings;
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
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ravi Chodavarapu (rchodava@gmail.com)
 */
public class NameGenerator {
	private static final Logger logger = Logger.getLogger(DelegatingDriver.class.getName());

	private static final String APPLICATION_NAME = "applicationName";
	private final static String GENERATED_APP_NAME = "APP-NAME-%s";

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

    private static File findGitRootInStack() {
	    File gitRoot = null;
	    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
	    int index = stackTrace.length;
	    while (gitRoot == null && index > 1) {
		    Class<?> callerClass;
		    try {
			    callerClass = getCallerClassForName(stackTrace[--index].getClassName());
			    gitRoot = findGitRootOfClassSource(callerClass);
		    } catch (ClassNotFoundException e) {
		    }
	    }
	    if (gitRoot != null) {
		    logger.log(Level.FINE, "Found git root on {0}", gitRoot.getAbsolutePath());
	    }
	    else {
		    logger.log(Level.FINE, "Could not find git root");
	    }

	    return gitRoot;
    }

	private static Class<?> getCallerClassForName(String className) throws ClassNotFoundException {
		if (className != null) {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			if (classLoader == null) {
				classLoader = DockerDatabaseServerPerApplicationConnectionLookup.class.getClassLoader();
			}

			return Class.forName(className, true, classLoader);
		}

		return null;
	}


	private static String determineRemoteUri(File gitRoot) {
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
	    String applicationName = getApplicationName();

	    if (Strings.isNullOrEmpty(applicationName)) {
		    File gitRoot = findGitRootInStack();
		    if (gitRoot != null) {
			    String remoteUri = determineRemoteUri(gitRoot);
			    String path = URI.create(remoteUri).getPath();
			    if (path != null) {
				    applicationName = generateApplicationNameFromGitRemoteUri(path);
				    if (applicationName != null) {
					    logger.log(Level.FINE, "Derived application {0} name from git ", applicationName);
					    return applicationName;
				    }
			    }
		    }
	    }

	    if (Strings.isNullOrEmpty(applicationName)) {
		    String generatedName = generateRandomApplicationName();
		    logger.log(Level.FINE, "Using generated application name {0}", generatedName);
		    return generatedName;
	    }

	    logger.log(Level.FINE, "Using user provided application name {0}", applicationName);
        return applicationName;
    }

	private static String generateRandomApplicationName() {
		return String.format(GENERATED_APP_NAME, UUID.randomUUID().toString());
	}

	private static String getApplicationName() {
		return System.getProperty(APPLICATION_NAME);
	}

	public static String generateDatabaseName() {
        File gitRoot = findGitRootInStack();
        if (gitRoot != null) {
            return determineGitBranch(gitRoot);
        }

        return "master";
    }
}
