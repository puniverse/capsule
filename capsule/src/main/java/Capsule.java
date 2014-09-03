/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManagerImpl;
import capsule.DependencyManager;
import capsule.JarClassLoader;
import capsule.PomReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * An application capsule.
 * <p>
 * This API is to be used by custom capsules to programmatically (rather than declaratively) configure the capsule and possibly provide custom behavior.
 * <p>
 * All non-final protected methods may be overridden by custom capsules. These methods will usually be called once, but they must be idempotent,
 * i.e. if called numerous times they must always return the same value, and produce the same effect as if called once. The only exception to this
 * rule is the {@link #launch(List) launch} method.
 * <br>
 * Overridden methods need not be thread-safe, and are guaranteed to be called by a single thread at a time.
 * <p>
 * Final methods implement various utility or accessors, which may be freely used by custom capsules.
 *
 * @author pron
 */
public class Capsule implements Runnable {
    /*
     * This class contains several strange hacks to avoid creating more classes,  
     * as we'd like this file to compile to a single .class file.
     *
     * Also, the code is not meant to be the most efficient, but methods should be as independent and stateless as possible.
     * Other than those few methods called in the constructor, all others are can be called in any order, and don't rely on any state.
     *
     * We do a lot of data transformations that would have really benefited from Java 8's lambdas and streams, 
     * but we want Capsule to support Java 7.
     */
    private static final String VERSION = "0.9.0";

    //<editor-fold defaultstate="collapsed" desc="Constants">
    /////////// Constants ///////////////////////////////////
    private static final String PROP_VERSION = "capsule.version";
    private static final String PROP_TREE = "capsule.tree";
    private static final String PROP_RESOLVE = "capsule.resolve";
    private static final String PROP_MODES = "capsule.modes";
    private static final String PROP_TRAMPOLINE = "capsule.trampoline";
    private static final String PROP_RESET = "capsule.reset";
    private static final String PROP_LOG_LEVEL = "capsule.log";
    private static final String PROP_APP_ID = "capsule.app.id";
    private static final String PROP_PRINT_JRES = "capsule.jvms";
    private static final String PROP_CAPSULE_JAVA_HOME = "capsule.java.home";
    private static final String PROP_MODE = "capsule.mode";
    private static final String PROP_USE_LOCAL_REPO = "capsule.local";
    private static final String PROP_JVM_ARGS = "capsule.jvm.args";

    private static final String PROP_JAVA_VERSION = "java.version";
    private static final String PROP_JAVA_HOME = "java.home";
    private static final String PROP_OS_NAME = "os.name";
    private static final String PROP_USER_HOME = "user.home";
    private static final String PROP_JAVA_LIBRARY_PATH = "java.library.path";
    private static final String PROP_FILE_SEPARATOR = "file.separator";
    private static final String PROP_PATH_SEPARATOR = "path.separator";
    private static final String PROP_JAVA_SECURITY_POLICY = "java.security.policy";
    private static final String PROP_JAVA_SECURITY_MANAGER = "java.security.manager";

    private static final String ENV_CACHE_DIR = "CAPSULE_CACHE_DIR";
    private static final String ENV_CACHE_NAME = "CAPSULE_CACHE_NAME";
    private static final String ENV_CAPSULE_REPOS = "CAPSULE_REPOS";
    private static final String ENV_CAPSULE_LOCAL_REPO = "CAPSULE_LOCAL_REPO";

    private static final String ATTR_APP_NAME = "Application-Name";
    private static final String ATTR_APP_VERSION = "Application-Version";
    private static final String ATTR_MODE_DESC = "Description";
    private static final String ATTR_APP_CLASS = "Application-Class";
    private static final String ATTR_APP_ARTIFACT = "Application";
    private static final String ATTR_UNIX_SCRIPT = "Unix-Script";
    private static final String ATTR_WINDOWS_SCRIPT = "Windows-Script";
    private static final String ATTR_EXTRACT = "Extract-Capsule";
    private static final String ATTR_MIN_JAVA_VERSION = "Min-Java-Version";
    private static final String ATTR_JAVA_VERSION = "Java-Version";
    private static final String ATTR_MIN_UPDATE_VERSION = "Min-Update-Version";
    private static final String ATTR_JDK_REQUIRED = "JDK-Required";
    private static final String ATTR_JVM_ARGS = "JVM-Args";
    private static final String ATTR_ARGS = "Args";
    private static final String ATTR_ENV = "Environment-Variables";
    private static final String ATTR_SYSTEM_PROPERTIES = "System-Properties";
    private static final String ATTR_APP_CLASS_PATH = "App-Class-Path";
    private static final String ATTR_CAPSULE_IN_CLASS_PATH = "Capsule-In-Class-Path";
    private static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";
    private static final String ATTR_BOOT_CLASS_PATH_A = "Boot-Class-Path-A";
    private static final String ATTR_BOOT_CLASS_PATH_P = "Boot-Class-Path-P";
    private static final String ATTR_LIBRARY_PATH_A = "Library-Path-A";
    private static final String ATTR_LIBRARY_PATH_P = "Library-Path-P";
    private static final String ATTR_SECURITY_MANAGER = "Security-Manager";
    private static final String ATTR_SECURITY_POLICY = "Security-Policy";
    private static final String ATTR_SECURITY_POLICY_A = "Security-Policy-A";
    private static final String ATTR_JAVA_AGENTS = "Java-Agents";
    private static final String ATTR_REPOSITORIES = "Repositories";
    private static final String ATTR_ALLOW_SNAPSHOTS = "Allow-Snapshots";
    private static final String ATTR_DEPENDENCIES = "Dependencies";
    private static final String ATTR_NATIVE_DEPENDENCIES_LINUX = "Native-Dependencies-Linux";
    private static final String ATTR_NATIVE_DEPENDENCIES_WIN = "Native-Dependencies-Win";
    private static final String ATTR_NATIVE_DEPENDENCIES_MAC = "Native-Dependencies-Mac";
    private static final String ATTR_MAIN_CLASS = "Main-Class";
    private static final String ATTR_IMPLEMENTATION_VERSION = "Implementation-Version";
    private static final String ATTR_LOG_LEVEL = "Capsule-Log-Level";

    private static final Set<String> NON_MODAL_ATTRS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            new String[]{ATTR_APP_NAME, ATTR_APP_VERSION}
    )));

    // outgoing
    private static final String VAR_CAPSULE_APP = "CAPSULE_APP";
    private static final String VAR_CAPSULE_DIR = "CAPSULE_DIR";
    private static final String VAR_CAPSULE_JAR = "CAPSULE_JAR";
    private static final String VAR_CLASSPATH = "CLASSPATH";
    private static final String VAR_JAVA_HOME = "JAVA_HOME";

    private static final String PROP_CAPSULE_JAR = "capsule.jar";
    private static final String PROP_CAPSULE_DIR = "capsule.dir";
    private static final String PROP_CAPSULE_APP = "capsule.app";

    private static final String PROP_CAPSULE_APP_PID = "capsule.app.pid";

    // misc
    private static final String CACHE_DEFAULT_NAME = "capsule";
    private static final String DEPS_CACHE_NAME = "deps";
    private static final String APP_CACHE_NAME = "apps";
    private static final String POM_FILE = "pom.xml";
    private static final String SEPARATOR_DOT = "\\.";
    private static final String SEPARATOR_PIPE = "\\|";
    private static final String LOCK_FILE_NAME = ".lock";
    private static final String TIMESTAMP_FILE_NAME = ".extracted";
    private static final String MANIFEST_NAME = java.util.jar.JarFile.MANIFEST_NAME;
    private static final String FILE_SEPARATOR = System.getProperty(PROP_FILE_SEPARATOR);
    private static final String PATH_SEPARATOR = System.getProperty(PROP_PATH_SEPARATOR);
    private static final Path WINDOWS_PROGRAM_FILES_1 = Paths.get("C:", "Program Files");
    private static final Path WINDOWS_PROGRAM_FILES_2 = Paths.get("C:", "Program Files (x86)");
    private static final Object DEFAULT = new Object();

    // logging
    private static final String LOG_PREFIX = "CAPSULE: ";
    protected static final int LOG_NONE = 0;
    protected static final int LOG_QUIET = 1;
    protected static final int LOG_VERBOSE = 2;
    protected static final int LOG_DEBUG = 3;
    //</editor-fold>

    //<editor-fold desc="Main">
    /////////// Main ///////////////////////////////////
    private static Capsule CAPSULE;

    protected static Capsule myCapsule() {
        if (CAPSULE == null)
            CAPSULE = newCapsule(findMyJarFile(), getCacheDir());
        return CAPSULE;
    }

    /**
     * Launches the capsule
     */
    @SuppressWarnings({"BroadCatchBlock", "CallToPrintStackTrace"})
    public static final void main(String[] args0) {
        try {
            Capsule capsule = myCapsule();
            final List<String> args = Arrays.asList(args0);
            if (propertyDefined(PROP_VERSION, PROP_PRINT_JRES, PROP_TREE, PROP_RESOLVE)) {
                if (propertyDefined(PROP_VERSION))
                    capsule.printVersion(args);

                if (propertyDefined(PROP_MODES))
                    capsule.printModes(args);

                if (propertyDefined(PROP_TREE))
                    capsule.printDependencyTree(args);

                if (propertyDefined(PROP_RESOLVE))
                    capsule.resolve(args);

                if (propertyDefined(PROP_PRINT_JRES))
                    capsule.printJVMs(args);

                return;
            }

            if (propertyDefined(PROP_TRAMPOLINE))
                capsule.trampoline(args); // never returns

            final Process p = capsule.launch(args);
            if (p != null)
                System.exit(p.waitFor());
        } catch (Throwable t) {
            System.err.print("CAPSULE EXCEPTION: " + t.getMessage());
            if (getLogLevel(System.getProperty(PROP_LOG_LEVEL)) >= LOG_VERBOSE) {
                System.err.println();
                t.printStackTrace(System.err);
            } else
                System.err.println(" (for stack trace, run with -D" + PROP_LOG_LEVEL + "=verbose)");
            System.exit(1);
        }
    }
    //</editor-fold>

    private static Map<String, Path> JAVA_HOMES; // an optimization trick (can be injected by CapsuleLauncher)

    private final Path cacheDir;     // never null
    private final Path jarFile;      // never null
    private final Manifest manifest; // never null
    private final String appId;      // null iff isEmptyCapsule()

    private String mode;

    private final Path appCache;     // non-null iff capsule is extracted
    private final boolean cacheUpToDate;
    private FileLock appCacheLock;

    private final Object pom;               // non-null iff jar has pom AND manifest doesn't have ATTR_DEPENDENCIES 
    private final Object dependencyManager; // non-null iff needsDependencyManager is true
    private final int logLevel;
    private Process child;

    //<editor-fold defaultstate="collapsed" desc="Constructors">
    /////////// Constructors ///////////////////////////////////
    /**
     * Constructs a capsule from the given JAR file
     *
     * @param jarFile  the path to the JAR file
     * @param cacheDir the path to the (shared) Capsule cache directory
     */
    protected Capsule(Path jarFile, Path cacheDir) {
        this(jarFile, cacheDir, DEFAULT);
    }

    // Used directly by tests
    @SuppressWarnings("OverridableMethodCallInConstructor")
    private Capsule(Path jarFile, Path cacheDir, Object dependencyManager) {
        Objects.requireNonNull(jarFile, "jarFile can't be null");
        Objects.requireNonNull(cacheDir, "cacheDir can't be null");
        this.jarFile = jarFile;

        try (JarInputStream jis = openJarInputStream()) {
            this.manifest = jis.getManifest();
            if (manifest == null)
                throw new RuntimeException("Capsule " + jarFile + " does not have a manifest");
            verifyNonModalAttributes();

            this.pom = !hasAttribute(ATTR_DEPENDENCIES) ? createPomReader(jis) : null;
        } catch (IOException e) {
            throw new RuntimeException("Could not read JAR file " + jarFile, e);
        }

        this.logLevel = chooseLogLevel();
        this.cacheDir = initCacheDir(cacheDir);

        if (dependencyManager != DEFAULT)
            this.dependencyManager = dependencyManager;
        else
            this.dependencyManager = needsDependencyManager() ? createDependencyManager(getRepositories()) : null;
        this.appId = buildAppId();
        this.appCache = needsAppCache() ? getAppCacheDir() : null;
        this.cacheUpToDate = appCache != null ? isUpToDate() : false;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Properties">
    /////////// Properties ///////////////////////////////////
    /**
     * Tests if this is an empty capsule.
     */
    protected final boolean isEmptyCapsule() {
        return !hasAttribute(ATTR_APP_ARTIFACT) && !hasAttribute(ATTR_APP_CLASS) && getScript() == null;
    }

    /**
     * This capsule's current mode.
     */
    protected final String getMode() {
        return mode;
    }

    /**
     * This capsule's cache directory, or {@code null} if capsule has been configured not to extract.
     */
    protected final Path getAppCache() {
        return appCache;
    }

    /**
     * This capsule's JAR file.
     */
    protected final Path getJarFile() {
        return jarFile;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Capsule JAR">
    /////////// Capsule JAR ///////////////////////////////////
    private static Path findMyJarFile() {
        final URL url = Capsule.class.getClassLoader().getResource(Capsule.class.getName().replace('.', '/') + ".class");
        if (!"jar".equals(url.getProtocol()))
            throw new IllegalStateException("The Capsule class must be in a JAR file, but was loaded from: " + url);
        final String path = url.getPath();
        if (path == null || !path.startsWith("file:"))
            throw new IllegalStateException("The Capsule class must be in a local JAR file, but was loaded from: " + url);

        try {
            final URI jarUri = new URI(path.substring(0, path.indexOf('!')));
            return Paths.get(jarUri);
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private String toJarUrl(String relPath) {
        return "jar:file:" + jarFile.toAbsolutePath() + "!/" + relPath;
    }

    private JarInputStream openJarInputStream() throws IOException {
        return new JarInputStream(skipToZipStart(Files.newInputStream(jarFile)));
    }

    private InputStream getEntry(ZipInputStream zis, String name) throws IOException {
        for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
            if (entry.getName().equals(name))
                return zis;
        }
        return null;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Main Operations">
    /////////// Main Operations ///////////////////////////////////
    private void printVersion(List<String> args) {
        System.out.println(LOG_PREFIX + "Application " + getAppId(args));
        System.out.println(LOG_PREFIX + "Capsule Version " + VERSION);
    }

    private void printModes(List<String> args) {
        System.out.println(LOG_PREFIX + "Application " + getAppId(args));
        System.out.println("Available modes:");
        final Set<String> modes = getModes();
        if (modes.isEmpty())
            System.out.println("Default mode only");
        else {
            for (String m : modes) {
                final String desc = getModeDescription(m);
                System.out.println("* " + m + (desc != null ? ": " + desc : ""));
            }
        }
    }

    private void printJVMs(List<String> args) {
        final Map<String, Path> jres = getJavaHomes();
        if (jres == null)
            println("No detected Java installations");
        else {
            System.out.println(LOG_PREFIX + "Detected Java installations:");
            for (Map.Entry<String, Path> j : jres.entrySet())
                System.out.println(j.getKey() + (j.getKey().length() < 8 ? "\t\t" : "\t") + j.getValue());
        }
        final Path javaHome = chooseJavaHome();
        System.out.println(LOG_PREFIX + "selected " + (javaHome != null ? javaHome : (System.getProperty(PROP_JAVA_HOME) + " (current)")));
    }

    private void printDependencyTree(List<String> args) {
        System.out.println("Dependencies for " + getAppId(args));
        if (dependencyManager == null)
            System.out.println("No dependencies declared.");
        else if (hasAttribute(ATTR_APP_ARTIFACT) || isEmptyCapsule()) {
            final String appArtifact = isEmptyCapsule() ? getCommandLineArtifact(args) : getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact == null)
                throw new IllegalStateException("capsule " + jarFile + " has nothing to run");
            printDependencyTree(appArtifact, "jar");
        } else
            printDependencyTree(getDependencies(), "jar");

        final List<String> nativeDeps = getNativeDependencies();
        if (nativeDeps != null) {
            System.out.println("\nNative Dependencies:");
            printDependencyTree(nativeDeps, getNativeLibExtension());
        }
    }

    private void resolve(List<String> args) throws IOException, InterruptedException {
        ensureExtractedIfNecessary();
        resolveAppArtifact(getAppArtifact(args), "jar");
        resolveDependencies(getDependencies(), "jar");
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH));
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH_P));
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH_A));

        resolveNativeDependencies();
        log(LOG_QUIET, "Capsule resolved");
    }

    private void trampoline(List<String> args) {
        if (hasAttribute(ATTR_ENV)) {
            println("Capsule cannot trampoline because manifest defines the " + ATTR_ENV + " attribute.");
            System.exit(1);
        }

        final ProcessBuilder pb = prelaunch(args);
        pb.command().remove("-D" + PROP_TRAMPOLINE);
        System.out.println(join(pb.command(), " "));
        System.exit(0);
    }

    /**
     * Creates the {@link Process} this capsule will launch.
     * Custom capsules may override this method to display a message prior to launch, or to configure the process's IO streams.
     *
     * @param args the application's command-line arguments (does not include JVM args)
     * @return the process this capsule will launch
     */
    protected Process launch(List<String> args) throws IOException, InterruptedException {
        final ProcessBuilder pb = prelaunch(args);

        Runtime.getRuntime().addShutdownHook(new Thread(this));

        if (!isInheritIoBug())
            pb.inheritIO();

        this.child = pb.start();
        if (isInheritIoBug())
            pipeIoStreams();

        final int pid = getPid(child);
        if (pid > 0)
            System.setProperty(PROP_CAPSULE_APP_PID, Integer.toString(pid));

        return child;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Launch">
    /////////// Launch ///////////////////////////////////
    private ProcessBuilder prelaunch(List<String> args) {
        final List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        ProcessBuilder pb = launchCapsuleArtifact(jvmArgs, args);
        if (pb == null)
            pb = prepareForLaunch(jvmArgs, args);
        return pb;
    }

    // directly used by CapsuleLauncher
    final ProcessBuilder prepareForLaunch(List<String> jvmArgs, List<String> args) {
        chooseMode1();
        ensureExtractedIfNecessary();
        final ProcessBuilder pb = buildProcess(jvmArgs, args);
        if (appCache != null && !cacheUpToDate)
            markCache();
        log(LOG_VERBOSE, "Launching app " + appId + (mode != null ? " in mode " + mode : ""));
        return pb;
    }

    private void chooseMode1() {
        this.mode = chooseMode();
        if (mode != null && !hasMode(mode))
            throw new IllegalArgumentException("Capsule " + jarFile + " does not have mode " + mode);
    }

    /**
     * Chooses this capsule's mode.
     * The mode is chosen during the preparations for launch (not at construction time).
     */
    protected String chooseMode() {
        return emptyToNull(System.getProperty(PROP_MODE));
    }

    private void ensureExtractedIfNecessary() {
        if (appCache != null) {
            if (!cacheUpToDate) {
                resetAppCache();
                if (shouldExtract())
                    extractCapsule();
            } else
                log(LOG_VERBOSE, "App cache " + appCache + " is up to date.");
        }
    }

    private ProcessBuilder buildProcess(List<String> jvmArgs, List<String> args) {
        final ProcessBuilder pb = new ProcessBuilder();
        if (!buildScriptProcess(pb))
            buildJavaProcess(pb, jvmArgs);

        final List<String> command = pb.command();
        command.addAll(buildArgs(args));

        buildEnvironmentVariables(pb.environment());

        log(LOG_VERBOSE, join(command, " "));

        return pb;
    }

    /**
     * Returns a list of command line arguments to pass to the application.
     *
     * @param args The command line arguments passed to the capsule at launch
     */
    protected List<String> buildArgs(List<String> args) {
        return expandArgs(nullToEmpty(expand(getListAttribute(ATTR_ARGS))), args);
    }

    // visible for testing
    static List<String> expandArgs(List<String> args0, List<String> args) {
        final List<String> args1 = new ArrayList<String>();
        boolean expanded = false;
        for (String a : args0) {
            if (a.startsWith("$")) {
                if (a.equals("$*")) {
                    args1.addAll(args);
                    expanded = true;
                    continue;
                } else {
                    try {
                        final int i = Integer.parseInt(a.substring(1));
                        args1.add(args.get(i - 1));
                        expanded = true;
                        continue;
                    } catch (NumberFormatException e) {
                    }
                }
            }
            args1.add(a);
        }
        if (!expanded)
            args1.addAll(args);
        return args1;
    }

    /**
     * Returns a map of environment variables (property-value pairs).
     *
     * @param env the current environment
     */
    protected void buildEnvironmentVariables(Map<String, String> env) {
        final List<String> jarEnv = getListAttribute(ATTR_ENV);
        if (jarEnv != null) {
            for (String e : jarEnv) {
                String var = getBefore(e, '=');
                String value = getAfter(e, '=');

                if (var == null)
                    throw new IllegalArgumentException("Malformed env variable definition: " + e);

                boolean overwrite = false;
                if (var.endsWith(":")) {
                    overwrite = true;
                    var = var.substring(0, var.length() - 1);
                }

                if (overwrite || !env.containsKey(var))
                    env.put(var, value != null ? value : "");
            }
        }
        if (appCache != null)
            env.put(VAR_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        env.put(VAR_CAPSULE_JAR, jarFile.toString());
        assert appId != null;
        env.put(VAR_CAPSULE_APP, appId);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Script">
    /////////// Script ///////////////////////////////////
    private String getScript() {
        return getAttribute(isWindows() ? ATTR_WINDOWS_SCRIPT : ATTR_UNIX_SCRIPT);
    }

    private boolean buildScriptProcess(ProcessBuilder pb) {
        final String script = getScript();
        if (script == null)
            return false;

        if (appCache == null)
            throw new IllegalStateException("Cannot run the startup script " + script + " when the "
                    + ATTR_EXTRACT + " attribute is set to false");

        setJavaHomeEnv(pb);

        final List<Path> classPath = buildClassPath();
        resolveNativeDependencies();
        pb.environment().put(VAR_CLASSPATH, compileClassPath(classPath));

        final Path scriptPath = appCache.resolve(sanitize(script)).toAbsolutePath();
        ensureExecutable(scriptPath);
        pb.command().add(scriptPath.toString());
        return true;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Capsule Artifact">
    /////////// Capsule Artifact ///////////////////////////////////
    // visible for testing
    final ProcessBuilder launchCapsuleArtifact(List<String> cmdLine, List<String> args) {
        if (getScript() == null) {
            final String appArtifact = getAppArtifact(args);
            if (appArtifact != null && isDependency(appArtifact)) {
                try {
                    final List<Path> jars = resolveAppArtifact(appArtifact, "jar");
                    if (jars == null || jars.isEmpty())
                        return null;
                    if (isCapsule(jars.get(0))) {
                        log(LOG_VERBOSE, "Running capsule " + jars.get(0));
                        return launchCapsule(jars.get(0), cacheDir,
                                cmdLine, isEmptyCapsule() ? args.subList(1, args.size()) : buildArgs(args));
                    } else if (isEmptyCapsule())
                        throw new IllegalArgumentException("Artifact " + appArtifact + " is not a capsule.");
                } catch (RuntimeException e) {
                    if (isEmptyCapsule())
                        throw new RuntimeException("Usage: java -jar capsule.jar CAPSULE_ARTIFACT_COORDINATES args...\n" + e, e);
                    else
                        throw e;
                }
            }
        }
        return null;
    }

    private static boolean isCapsule(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                return hasEntry(path, "Capsule.class");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="App ID">
    /////////// App ID ///////////////////////////////////
    /**
     * Computes and returns application's ID
     */
    protected String buildAppId() {
        if (isEmptyCapsule())
            return null;
        String appName = System.getProperty(PROP_APP_ID);
        if (appName == null)
            appName = getAttribute(ATTR_APP_NAME);

        if (appName == null) {
            final String appArtifact = getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact != null && isDependency(appArtifact)) {
                if (hasModalAttribute(ATTR_APP_ARTIFACT))
                    throw new IllegalArgumentException("App ID-related attribute " + ATTR_APP_ARTIFACT + " is defined in a modal section of the manifest. "
                            + " In this case, you must add the " + ATTR_APP_NAME + " attribute to the manifest's main section.");
                return getAppArtifactId(getAppArtifactSpecificVersion(appArtifact));
            }
        }
        if (appName == null) {
            if (pom != null)
                return getPomAppName();
            appName = getAttribute(ATTR_APP_CLASS);
            if (appName != null && hasModalAttribute(ATTR_APP_CLASS))
                throw new IllegalArgumentException("App ID-related attribute " + ATTR_APP_CLASS + " is defined in a modal section of the manifest. "
                        + " In this case, you must add the " + ATTR_APP_NAME + " attribute to the manifest's main section.");
        }
        if (appName == null) {
            if (isEmptyCapsule())
                return null;
            throw new IllegalArgumentException("Capsule jar " + jarFile + " must either have the " + ATTR_APP_NAME + " manifest attribute, "
                    + "the " + ATTR_APP_CLASS + " attribute, or contain a " + POM_FILE + " file.");
        }

        final String version = hasAttribute(ATTR_APP_VERSION) ? getAttribute(ATTR_APP_VERSION) : getAttribute(ATTR_IMPLEMENTATION_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    /**
     * Returns the app's ID.
     * The arguments are required in case this is an empty capsule, in which case the ID is determined by the artifact supplied at the command line.
     *
     * @param args the command line arguments; may be {@code null}.
     * @return the app ID
     */
    protected final String getAppId(List<String> args) {
        if (appId != null)
            return appId;
        assert isEmptyCapsule();

        String appArtifact = getAppArtifact(args);
        if (appArtifact == null)
            throw new RuntimeException("No application to run");
        return getAppArtifactId(getAppArtifactSpecificVersion(appArtifact));
    }

    private static String getAppArtifactId(String coords) {
        if (coords == null)
            return null;
        final String[] cs = coords.split(":");
        if (cs.length < 2)
            throw new IllegalArgumentException("Illegal main artifact coordinates: " + coords);
        String id = cs[0] + "_" + cs[1];
        if (cs.length > 2)
            id += "_" + cs[2];
        return id;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Capsule Cache">
    /////////// Capsule Cache ///////////////////////////////////
    private static Path getCacheDir() {
        final Path cache;
        final String cacheDirEnv = System.getenv(ENV_CACHE_DIR);
        if (cacheDirEnv != null)
            cache = Paths.get(cacheDirEnv);
        else {
            final String cacheNameEnv = System.getenv(ENV_CACHE_NAME);
            final String cacheName = cacheNameEnv != null ? cacheNameEnv : CACHE_DEFAULT_NAME;
            cache = getCacheHome().resolve((isWindows() ? "" : ".") + cacheName);
        }
        return cache;
    }

    private static Path initCacheDir(Path cache) {
        try {
            if (!Files.exists(cache))
                Files.createDirectory(cache);
            if (!Files.exists(cache.resolve(APP_CACHE_NAME)))
                Files.createDirectory(cache.resolve(APP_CACHE_NAME));
            if (!Files.exists(cache.resolve(DEPS_CACHE_NAME)))
                Files.createDirectory(cache.resolve(DEPS_CACHE_NAME));

            return cache;
        } catch (IOException e) {
            throw new RuntimeException("Error opening cache directory " + cache.toAbsolutePath(), e);
        }
    }

    private static Path getCacheHome() {
        final Path userHome = Paths.get(System.getProperty(PROP_USER_HOME));
        if (!isWindows())
            return userHome;

        Path localData;
        final String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            localData = Paths.get(localAppData);
            if (!Files.isDirectory(localData))
                throw new RuntimeException("%LOCALAPPDATA% set to nonexistent directory " + localData);
        } else {
            localData = userHome.resolve(Paths.get("AppData", "Local"));
            if (!Files.isDirectory(localData))
                localData = userHome.resolve(Paths.get("Local Settings", "Application Data"));
            if (!Files.isDirectory(localData))
                throw new RuntimeException("%LOCALAPPDATA% is undefined, and neither "
                        + userHome.resolve(Paths.get("AppData", "Local")) + " nor "
                        + userHome.resolve(Paths.get("Local Settings", "Application Data")) + " have been found");
        }
        return localData;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="App Cache">
    /////////// App Cache ///////////////////////////////////
    private Path getAppCacheDir() {
        assert appId != null;
        Path appDir = cacheDir.resolve(APP_CACHE_NAME).resolve(appId);
        try {
            if (!Files.exists(appDir))
                Files.createDirectory(appDir);
            return appDir;
        } catch (IOException e) {
            throw new RuntimeException("Application cache directory " + appDir.toAbsolutePath() + " could not be created.");
        }
    }

    private boolean needsAppCache() {
        if (isEmptyCapsule())
            return false;
        if (hasRenamedNativeDependencies())
            return true;
        if (hasAttribute(ATTR_APP_ARTIFACT) && isDependency(getAttribute(ATTR_APP_ARTIFACT)))
            return false;
        return shouldExtract();
    }

    private boolean shouldExtract() {
        final String extract = getAttribute(ATTR_EXTRACT);
        return extract == null || Boolean.parseBoolean(extract);
    }

    private void resetAppCache() {
        try {
            log(LOG_DEBUG, "Creating cache for " + jarFile + " in " + appCache.toAbsolutePath());
            final Path lockFile = appCache.resolve(LOCK_FILE_NAME);
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(appCache)) {
                for (Path f : ds) {
                    if (lockFile.equals(f))
                        continue;
                    delete(f);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Exception while extracting jar " + jarFile + " to app cache directory " + appCache.toAbsolutePath(), e);
        }
    }

    private boolean isUpToDate() {
        try {
            boolean res = isUpToDate0();
            if (!res) {
                lockAppCache();
                res = isUpToDate0();
                if (res)
                    unlockAppCache();
            }
            return res;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private boolean isUpToDate0() {
        if (Boolean.parseBoolean(System.getProperty(PROP_RESET, "false")))
            return false;
        try {
            Path extractedFile = appCache.resolve(TIMESTAMP_FILE_NAME);
            if (!Files.exists(extractedFile))
                return false;
            FileTime extractedTime = Files.getLastModifiedTime(extractedFile);
            FileTime jarTime = Files.getLastModifiedTime(jarFile);
            return extractedTime.compareTo(jarTime) >= 0;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void extractCapsule() {
        try {
            log(LOG_VERBOSE, "Extracting " + jarFile + " to app cache directory " + appCache.toAbsolutePath());
            extractJar(openJarInputStream(), appCache);
        } catch (IOException e) {
            throw new RuntimeException("Exception while extracting jar " + jarFile + " to app cache directory " + appCache.toAbsolutePath(), e);
        }
    }

    private void markCache() {
        try {
            Files.createFile(appCache.resolve(TIMESTAMP_FILE_NAME));
            unlockAppCache();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void lockAppCache() throws IOException {
        final Path lockFile = appCache.resolve(LOCK_FILE_NAME);
        log(LOG_VERBOSE, "Locking " + lockFile);
        final FileChannel c = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        this.appCacheLock = c.lock();
    }

    private void unlockAppCache() throws IOException {
        if (appCacheLock != null) {
            log(LOG_VERBOSE, "Unocking " + appCache.resolve(LOCK_FILE_NAME));
            appCacheLock.release();
            appCacheLock.acquiredBy().close();
            appCacheLock = null;
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Build Java Process">
    /////////// Build Java Process ///////////////////////////////////
    private boolean buildJavaProcess(ProcessBuilder pb, List<String> cmdLine) {
        final Path javaHome = setJavaHomeEnv(pb);

        final List<String> command = pb.command();

        command.add(getJavaProcessImage(javaHome).toString());
        command.addAll(buildJVMArgs(cmdLine));
        command.addAll(compileSystemProperties(buildSystemProperties(cmdLine)));

        addOption(command, "-Xbootclasspath:", compileClassPath(buildBootClassPath(cmdLine)));
        addOption(command, "-Xbootclasspath/p:", compileClassPath(buildBootClassPathP()));
        addOption(command, "-Xbootclasspath/a:", compileClassPath(buildBootClassPathA()));

        final List<Path> classPath = buildClassPath();
        command.add("-classpath");
        command.add(compileClassPath(classPath));

        for (String jagent : nullToEmpty(buildJavaAgents()))
            command.add("-javaagent:" + jagent);

        command.add(getMainClass(classPath));
        return true;
    }

    private Path setJavaHomeEnv(ProcessBuilder pb) {
        final Path javaHome = getJavaHome();
        log(LOG_VERBOSE, "Using JVM: " + javaHome);
        pb.environment().put(VAR_JAVA_HOME, javaHome.toString());
        return javaHome;
    }

    private static List<String> compileSystemProperties(Map<String, String> ps) {
        final List<String> command = new ArrayList<String>();
        for (Map.Entry<String, String> entry : ps.entrySet())
            command.add("-D" + entry.getKey() + (entry.getValue() != null && !entry.getValue().isEmpty() ? "=" + entry.getValue() : ""));
        return command;
    }

    private static String compileClassPath(List<Path> cp) {
        return join(cp, PATH_SEPARATOR);
    }

    private static void addOption(List<String> cmdLine, String prefix, String value) {
        if (value == null)
            return;
        cmdLine.add(prefix + value);
    }

    /**
     * Compiles and returns the application's classpath as a list of paths.
     */
    protected List<Path> buildClassPath() {
        final List<Path> classPath = new ArrayList<Path>();

        if (!isEmptyCapsule() && !hasAttribute(ATTR_APP_ARTIFACT)) {
            // the capsule jar
            final String isCapsuleInClassPath = getAttribute(ATTR_CAPSULE_IN_CLASS_PATH);
            if (isCapsuleInClassPath == null || Boolean.parseBoolean(isCapsuleInClassPath))
                classPath.add(jarFile);
            else if (appCache == null)
                throw new IllegalStateException("Cannot set the " + ATTR_CAPSULE_IN_CLASS_PATH + " attribute to false when the "
                        + ATTR_EXTRACT + " attribute is also set to false");
        }

        if (hasAttribute(ATTR_APP_ARTIFACT)) {
            if (isDependency(getAttribute(ATTR_APP_ARTIFACT)))
                classPath.addAll(nullToEmpty(resolveAppArtifact(getAttribute(ATTR_APP_ARTIFACT), "jar")));
            else
                classPath.add(getPath(getAttribute(ATTR_APP_ARTIFACT)));
        }

        if (hasAttribute(ATTR_APP_CLASS_PATH)) {
            for (String sp : getListAttribute(ATTR_APP_CLASS_PATH)) {
                Path p = path(expand(sanitize(sp)));

                if (appCache == null && (!p.isAbsolute() || p.startsWith(appCache)))
                    throw new IllegalStateException("Cannot resolve " + sp + "  in " + ATTR_APP_CLASS_PATH + " attribute when the "
                            + ATTR_EXTRACT + " attribute is set to false");

                p = appCache.resolve(p);
                classPath.add(p);
            }
        }

        if (appCache != null)
            addAllIfNotContained(classPath, nullToEmpty(getDefaultCacheClassPath()));

        classPath.addAll(nullToEmpty(resolveDependencies(getDependencies(), "jar")));

        return classPath;
    }

    private List<Path> getDefaultCacheClassPath() {
        final List<Path> cp = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(appCache)) {
            for (Path f : ds) {
                if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".jar"))
                    cp.add(f.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }

        // sort to give same reults on all platforms
        // doing it this way isn't quite right as Path's Javadoc ensures lexicographic ordering, yet warns different results on different platforms
        // ... but I hope this works well enough. If not, we need to convert to strings, sort, and then convert back.
        // (I assume the platform dependence has to do with case-sensitivity, and we don't care about that)
        Collections.sort(cp);

        cp.add(0, appCache);

        return cp;
    }

    /**
     * Returns a list of dependencies, each in the format {@code groupId:artifactId:version[:classifier]} (classifier is optional)
     */
    protected List<String> getDependencies() {
        List<String> deps = getListAttribute(ATTR_DEPENDENCIES);
        if ((deps == null || deps.isEmpty()) && pom != null)
            deps = getPomDependencies();

        return (deps != null && !deps.isEmpty()) ? Collections.unmodifiableList(deps) : null;
    }

    /**
     * Compiles and returns the application's boot classpath as a list of paths.
     */
    private List<Path> buildBootClassPath(List<String> cmdLine) {
        String option = null;
        for (String o : cmdLine) {
            if (o.startsWith("-Xbootclasspath:"))
                option = o.substring("-Xbootclasspath:".length());
        }
        if (option != null)
            return toPath(Arrays.asList(option.split(PATH_SEPARATOR)));
        return buildBootClassPath();
    }

    /**
     * Compiles and returns the application's boot classpath as a list of paths.
     */
    protected List<Path> buildBootClassPath() {
        return getPath(getListAttribute(ATTR_BOOT_CLASS_PATH));
    }

    /**
     * Compiles and returns the paths to be prepended to the application's boot classpath.
     */
    protected List<Path> buildBootClassPathP() {
        return buildClassPath(ATTR_BOOT_CLASS_PATH_P);
    }

    /**
     * Compiles and returns the paths to be appended to the application's boot classpath.
     */
    protected List<Path> buildBootClassPathA() {
        return buildClassPath(ATTR_BOOT_CLASS_PATH_A);
    }

    private List<Path> buildClassPath(String attr) {
        return getPath(getListAttribute(attr));
    }

    private Map<String, String> buildSystemProperties(List<String> cmdLine) {
        final Map<String, String> systemProperties = buildSystemProperties();

        // command line overrides everything
        for (String option : cmdLine) {
            if (option.startsWith("-D"))
                addSystemProperty(option.substring(2), systemProperties);
        }

        return systemProperties;
    }

    /**
     * Returns a map of system properties (property-value pairs).
     */
    protected Map<String, String> buildSystemProperties() {
        final Map<String, String> systemProperties = new HashMap<String, String>();

        // attribute
        for (Map.Entry<String, String> pv : nullToEmpty(getMapAttribute(ATTR_SYSTEM_PROPERTIES, "")).entrySet())
            systemProperties.put(pv.getKey(), expand(pv.getValue()));

        // library path
        final List<Path> libraryPath = buildNativeLibraryPath();
        systemProperties.put(PROP_JAVA_LIBRARY_PATH, compileClassPath(libraryPath));

        if (hasAttribute(ATTR_SECURITY_POLICY) || hasAttribute(ATTR_SECURITY_POLICY_A)) {
            systemProperties.put(PROP_JAVA_SECURITY_MANAGER, "");
            if (hasAttribute(ATTR_SECURITY_POLICY_A))
                systemProperties.put(PROP_JAVA_SECURITY_POLICY, toJarUrl(getAttribute(ATTR_SECURITY_POLICY_A)));
            if (hasAttribute(ATTR_SECURITY_POLICY))
                systemProperties.put(PROP_JAVA_SECURITY_POLICY, "=" + toJarUrl(getAttribute(ATTR_SECURITY_POLICY)));
        }
        if (hasAttribute(ATTR_SECURITY_MANAGER))
            systemProperties.put(PROP_JAVA_SECURITY_MANAGER, getAttribute(ATTR_SECURITY_MANAGER));

        // Capsule properties
        if (appCache != null)
            systemProperties.put(PROP_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        systemProperties.put(PROP_CAPSULE_JAR, jarFile.toString());
        assert appId != null;
        systemProperties.put(PROP_CAPSULE_APP, appId);

        return systemProperties;
    }

    private static void addSystemProperty(String p, Map<String, String> ps) {
        try {
            String name = getBefore(p, '=');
            String value = getAfter(p, '=');
            ps.put(name, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal system property definition: " + p);
        }
    }

    //<editor-fold desc="Native Dependencies">
    /////////// Native Dependencies ///////////////////////////////////
    private List<Path> buildNativeLibraryPath() {
        final List<Path> libraryPath = new ArrayList<Path>(toPath(Arrays.asList(System.getProperty(PROP_JAVA_LIBRARY_PATH).split(PATH_SEPARATOR))));

        resolveNativeDependencies();
        if (appCache != null) {
            libraryPath.addAll(0, nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_P))));
            libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_A))));
            libraryPath.add(appCache);
        } else if (hasAttribute(ATTR_LIBRARY_PATH_P) || hasAttribute(ATTR_LIBRARY_PATH_A))
            throw new IllegalStateException("Cannot use the " + ATTR_LIBRARY_PATH_P + " or the " + ATTR_LIBRARY_PATH_A
                    + " attributes when the " + ATTR_EXTRACT + " attribute is set to false");
        return libraryPath;
    }

    private void resolveNativeDependencies() {
        final List<String> depsAndRename = getNativeDependencies();
        if (depsAndRename == null || depsAndRename.isEmpty())
            return;
        if (appCache == null)
            throw new IllegalStateException("Cannot have native dependencies when the " + ATTR_EXTRACT + " attribute is set to false");
        final List<String> deps = new ArrayList<String>(depsAndRename.size());
        final List<String> renames = new ArrayList<String>(depsAndRename.size());
        for (String depAndRename : depsAndRename) {
            String[] dna = depAndRename.split(",");
            deps.add(dna[0]);
            renames.add(dna.length > 1 ? dna[1] : null);
        }
        log(LOG_VERBOSE, "Resolving native libs " + deps);
        final List<Path> resolved = resolveDependencies(deps, getNativeLibExtension());
        if (resolved.size() != deps.size())
            throw new RuntimeException("One of the native artifacts " + deps + " reolved to more than a single file or to none");

        assert appCache != null;
        if (!cacheUpToDate) {
            if (isLogging(LOG_DEBUG))
                System.err.println("Copying native libs to " + appCache);
            try {
                for (int i = 0; i < deps.size(); i++) {
                    final Path lib = resolved.get(i);
                    final String rename = sanitize(renames.get(i));
                    Files.copy(lib, appCache.resolve(rename != null ? rename : lib.getFileName().toString()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception while copying native libs", e);
            }
        }
    }

    /**
     * Constructs this capsule's native dependency list.
     *
     * @return a list of native dependencies, each in the format {@code groupId:artifactId:version[:classifier][,renameTo]}
     *         (classifier and renameTo are optional).
     */
    protected List<String> getNativeDependencies() {
        if (isWindows())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_WIN);
        if (isMac())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_MAC);
        if (isUnix())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_LINUX);
        return null;
    }

    private List<String> getStrippedNativeDependencies() {
        return stripNativeDependencies(getNativeDependencies());
    }

    private List<String> stripNativeDependencies(List<String> nativeDepsAndRename) {
        if (nativeDepsAndRename == null)
            return null;
        final List<String> deps = new ArrayList<String>(nativeDepsAndRename.size());
        for (String depAndRename : nativeDepsAndRename) {
            String[] dna = depAndRename.split(",");
            deps.add(dna[0]);
        }
        return deps;
    }

    private boolean hasRenamedNativeDependencies() {
        final List<String> depsAndRename = getNativeDependencies();
        if (depsAndRename == null)
            return false;
        for (String depAndRename : depsAndRename) {
            if (depAndRename.contains(","))
                return true;
        }
        return false;
    }
    //</editor-fold>

    private List<String> buildJVMArgs(List<String> cmdLine) {
        final Map<String, String> jvmArgs = new LinkedHashMap<String, String>();

        for (String option : buildJVMArgs())
            addJvmArg(option, jvmArgs);

        for (String option : nullToEmpty(split(System.getProperty(PROP_JVM_ARGS), " ")))
            addJvmArg(option, jvmArgs);

        // command line overrides everything
        for (String option : cmdLine) {
            if (!option.startsWith("-D") && !option.startsWith("-Xbootclasspath:"))
                addJvmArg(option, jvmArgs);
        }
        return new ArrayList<String>(jvmArgs.values());
    }

    /**
     * Returns a list of JVM arguments.
     */
    protected List<String> buildJVMArgs() {
        final Map<String, String> jvmArgs = new LinkedHashMap<String, String>();

        for (String a : nullToEmpty(getListAttribute(ATTR_JVM_ARGS))) {
            a = a.trim();
            if (!a.isEmpty() && !a.startsWith("-Xbootclasspath:") && !a.startsWith("-javaagent:"))
                addJvmArg(expand(a), jvmArgs);
        }

        return new ArrayList<String>(jvmArgs.values());
    }

    private static void addJvmArg(String a, Map<String, String> args) {
        args.put(getJvmArgKey(a), a);
    }

    private static String getJvmArgKey(String a) {
        if (a.equals("-client") || a.equals("-server"))
            return "compiler";
        if (a.equals("-enablesystemassertions") || a.equals("-esa")
                || a.equals("-disablesystemassertions") || a.equals("-dsa"))
            return "systemassertions";
        if (a.equals("-jre-restrict-search") || a.equals("-no-jre-restrict-search"))
            return "-jre-restrict-search";
        if (a.startsWith("-Xloggc:"))
            return "-Xloggc";
        if (a.startsWith("-Xss"))
            return "-Xss";
        if (a.startsWith("-Xmx"))
            return "-Xmx";
        if (a.startsWith("-Xms"))
            return "-Xms";
        if (a.startsWith("-XX:+") || a.startsWith("-XX:-"))
            return "-XX:" + a.substring("-XX:+".length());
        if (a.contains("="))
            return a.substring(0, a.indexOf("="));
        return a;
    }

    private List<String> buildJavaAgents() {
        final Map<String, String> agents0 = getMapAttribute(ATTR_JAVA_AGENTS, "");
        if (agents0 == null)
            return null;
        final List<String> agents = new ArrayList<String>(agents0.size());
        for (Map.Entry<String, String> agent : agents0.entrySet()) {
            final String agentJar = agent.getKey();
            final String agentOptions = agent.getValue();
            try {
                final Path agentPath = getPath(agent.getKey());
                agents.add(agentPath + ((agentOptions != null && !agentOptions.isEmpty()) ? "=" + agentOptions : ""));
            } catch (IllegalStateException e) {
                if (appCache == null)
                    throw new RuntimeException("Cannot run the embedded Java agent " + agentJar + " when the " + ATTR_EXTRACT + " attribute is set to false");
                throw e;
            }
        }
        return agents;
    }

    private String getMainClass(List<Path> classPath) {
        try {
            String mainClass = getAttribute(ATTR_APP_CLASS);
            if (mainClass == null && hasAttribute(ATTR_APP_ARTIFACT))
                mainClass = getMainClass(classPath.get(0));
            if (mainClass == null)
                throw new RuntimeException("Jar " + classPath.get(0).toAbsolutePath() + " does not have a main class defined in the manifest.");
            return mainClass;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getAppArtifact(List<String> args) {
        String appArtifact = null;
        if (isEmptyCapsule()) {
            if (args == null)
                return null;
            appArtifact = getCommandLineArtifact(args);
            if (appArtifact == null)
                throw new IllegalStateException("Capsule " + jarFile + " has nothing to run");
        }
        if (appArtifact == null)
            appArtifact = getAttribute(ATTR_APP_ARTIFACT);
        return appArtifact;
    }

    private String getCommandLineArtifact(List<String> args) {
        if (!args.isEmpty())
            return args.get(0);
        return null;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Get Java Home">
    /////////// Get Java Home ///////////////////////////////////
    private Path javaHome_; // cached value

    /**
     * The path to the Java installation this capsule's app will use.
     */
    protected final Path getJavaHome() {
        if (javaHome_ == null) {
            final Path jhome = chooseJavaHome();
            this.javaHome_ = jhome != null ? jhome : Paths.get(System.getProperty(PROP_JAVA_HOME));
        }
        return javaHome_;
    }

    /**
     * Chooses which Java installation to use for running the app.
     *
     * @return the path of the Java installation to use for launching the app, or {@code null} if the current JVM is to be used.
     */
    protected Path chooseJavaHome() {
        Path jhome = System.getProperty(PROP_CAPSULE_JAVA_HOME) != null ? Paths.get(System.getProperty(PROP_CAPSULE_JAVA_HOME)) : null;
        if (jhome == null && !isMatchingJavaVersion(System.getProperty(PROP_JAVA_VERSION))) {
            final boolean jdk = hasAttribute(ATTR_JDK_REQUIRED) && Boolean.parseBoolean(getAttribute(ATTR_JDK_REQUIRED));
            jhome = findJavaHome(jdk);
            if (jhome == null) {
                throw new RuntimeException("Could not find Java installation for requested version "
                        + '[' + "Min. Java version: " + getAttribute(ATTR_MIN_JAVA_VERSION)
                        + " JavaVersion: " + getAttribute(ATTR_JAVA_VERSION)
                        + " Min. update version: " + getAttribute(ATTR_MIN_UPDATE_VERSION) + ']'
                        + " (JDK required: " + jdk + ")"
                        + ". You can override the used Java version with the -D" + PROP_CAPSULE_JAVA_HOME + " flag.");
            }
        }
        return jhome != null ? jhome.toAbsolutePath() : jhome;
    }

    private Path findJavaHome(boolean jdk) {
        Map<String, Path> homes = getJavaHomes();
        if (jdk)
            homes = getJDKs(homes);
        if (homes == null)
            return null;
        Path best = null;
        String bestVersion = null;
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            final String v = e.getKey();
            log(LOG_DEBUG, "Trying JVM: " + e.getValue() + " (version " + e.getKey() + ")");
            if (isMatchingJavaVersion(v)) {
                log(LOG_DEBUG, "JVM " + e.getValue() + " (version " + e.getKey() + ") matches");
                if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                    log(LOG_DEBUG, "JVM " + e.getValue() + " (version " + e.getKey() + ") is best so far");
                    bestVersion = v;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    private boolean isMatchingJavaVersion(String javaVersion) {
        try {
            if (hasAttribute(ATTR_MIN_JAVA_VERSION) && compareVersions(javaVersion, getAttribute(ATTR_MIN_JAVA_VERSION)) < 0) {
                log(LOG_DEBUG, "Java version " + javaVersion + " fails to match due to " + ATTR_MIN_JAVA_VERSION + ": " + getAttribute(ATTR_MIN_JAVA_VERSION));
                return false;
            }
            if (hasAttribute(ATTR_JAVA_VERSION) && compareVersions(javaVersion, shortJavaVersion(getAttribute(ATTR_JAVA_VERSION)), 3) > 0) {
                log(LOG_DEBUG, "Java version " + javaVersion + " fails to match due to " + ATTR_JAVA_VERSION + ": " + getAttribute(ATTR_JAVA_VERSION));
                return false;
            }
            if (getMinUpdateFor(javaVersion) > parseJavaVersion(javaVersion)[3]) {
                log(LOG_DEBUG, "Java version " + javaVersion + " fails to match due to " + ATTR_MIN_UPDATE_VERSION + ": " + getAttribute(ATTR_MIN_UPDATE_VERSION) + " (" + getMinUpdateFor(javaVersion) + ")");
                return false;
            }
            log(LOG_DEBUG, "Java version " + javaVersion + " matches");
            return true;
        } catch (IllegalArgumentException ex) {
            log(LOG_VERBOSE, "Error parsing Java version " + javaVersion);
            return false;
        }
    }

    private int getMinUpdateFor(String version) {
        final Map<String, String> m = getMapAttribute(ATTR_MIN_UPDATE_VERSION, null);
        if (m == null)
            return 0;
        final int[] ver = parseJavaVersion(version);
        for (Map.Entry<String, String> entry : m.entrySet()) {
            if (equals(ver, toInt(shortJavaVersion(entry.getKey()).split(SEPARATOR_DOT)), 3))
                return Integer.parseInt(entry.getValue());
        }
        return 0;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="POM">
    /////////// POM ///////////////////////////////////
    private Object createPomReader(ZipInputStream zis) throws IOException {
        final InputStream is = getEntry(zis, POM_FILE);
        if (is == null)
            return null;
        try {
            return new PomReader(is);
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jarFile
                    + " contains a pom.xml file, while the necessary dependency management classes are not found in the jar");
        }
    }

    private List<String> getPomRepositories() {
        return ((PomReader) pom).getRepositories();
    }

    private List<String> getPomDependencies() {
        return ((PomReader) pom).getDependencies();
    }

    private String getPomAppName() {
        final PomReader pr = (PomReader) pom;
        return pr.getGroupId() + "_" + pr.getArtifactId() + "_" + pr.getVersion();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Dependency Manager">
    /////////// Dependency Manager ///////////////////////////////////
    private boolean needsDependencyManager() {
        return hasAttribute(ATTR_APP_ARTIFACT)
                || isEmptyCapsule()
                || getDependencies() != null
                || getStrippedNativeDependencies() != null;
    }

    private List<String> getRepositories() {
        final List<String> repos = new ArrayList<String>();

        final List<String> envRepos = split(System.getenv(ENV_CAPSULE_REPOS), "[,\\s]\\s*");
        final List<String> attrRepos = getListAttribute(ATTR_REPOSITORIES);

        if (envRepos != null)
            repos.addAll(envRepos);
        if (attrRepos != null)
            repos.addAll(attrRepos);

        if (pom != null) {
            for (String repo : nullToEmpty(getPomRepositories())) {
                if (!repos.contains(repo))
                    repos.add(repo);
            }
        }

        return !repos.isEmpty() ? Collections.unmodifiableList(repos) : null;
    }

    private Object createDependencyManager(List<String> repositories) {
        try {
            final boolean reset = Boolean.parseBoolean(System.getProperty(PROP_RESET, "false"));

            final Path localRepo = getLocalRepo();
            log(LOG_DEBUG, "Local repo: " + localRepo);

            final boolean allowSnapshots = hasAttribute(ATTR_ALLOW_SNAPSHOTS) && Boolean.parseBoolean(getAttribute(ATTR_ALLOW_SNAPSHOTS));
            log(LOG_DEBUG, "Allow snapshots: " + allowSnapshots);

            return new DependencyManagerImpl(localRepo.toAbsolutePath(), repositories, reset, allowSnapshots, logLevel);
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jarFile
                    + " specifies dependencies, while the necessary dependency management classes are not found in the jar");
        }
    }

    private Path getLocalRepo() {
        Path localRepo = cacheDir.resolve(DEPS_CACHE_NAME);
        final String local = expandCommandLinePath(propertyOrEnv(PROP_USE_LOCAL_REPO, ENV_CAPSULE_LOCAL_REPO));
        if (local != null)
            localRepo = !local.isEmpty() ? Paths.get(local) : null;
        return localRepo;
    }

    private void printDependencyTree(String root, String type) {
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(root, type, System.out);
    }

    private void printDependencyTree(List<String> dependencies, String type) {
        if (dependencies == null)
            return;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(dependencies, type, System.out);
    }

    private List<Path> resolveDependencies(List<String> dependencies, String type) {
        if (dependencies == null)
            return null;

        return ((DependencyManager) dependencyManager).resolveDependencies(dependencies, type);
    }

    private String getAppArtifactSpecificVersion(String appArtifact) {
        return getArtifactLatestVersion(appArtifact, "jar");
    }

    private String getArtifactLatestVersion(String coords, String type) {
        if (coords == null)
            return null;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        return dm.getLatestVersion(coords, type);
    }

    private List<Path> resolveAppArtifact(String coords, String type) {
        if (coords == null)
            return null;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        return dm.resolveDependency(coords, type);
    }

    private static Path getDependencyPath(Object dependencyManager, String p) {
        if (dependencyManager == null)
            throw new RuntimeException("No dependencies specified in the capsule. Cannot resolve dependency " + p);
        final DependencyManager dm = (DependencyManager) dependencyManager;
        List<Path> depsJars = dm.resolveDependency(p, "jar");

        if (depsJars == null || depsJars.isEmpty())
            throw new RuntimeException("Dependency " + p + " was not found.");
        return depsJars.iterator().next().toAbsolutePath();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Attributes">
    /////////// Attributes ///////////////////////////////////
    /*
     * The methods in this section are the only ones accessing the manifest. Therefore other means of
     * setting attributes can be added by changing these methods alone.
     */
    private void verifyNonModalAttributes() {
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            for (String attr : NON_MODAL_ATTRS) {
                if (entry.getValue().containsKey(new Attributes.Name(attr)))
                    throw new IllegalStateException("Manifest section " + entry.getKey() + " contains non-modal attribute " + attr);
            }
        }
    }

    private boolean hasModalAttribute(String attr) {
        final Attributes.Name key = new Attributes.Name(attr);
        for (Map.Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
            if (entry.getValue().containsKey(key))
                return true;
        }
        return false;
    }

    private boolean hasMode(String mode) {
        return manifest.getAttributes(mode) != null;
    }

    /**
     * Returns the names of all modes defined in this capsule's manifest.
     */
    protected final Set<String> getModes() {
        return Collections.unmodifiableSet(manifest.getEntries().keySet());
    }

    /**
     * Returns the description of the given mode.
     *
     * @param mode
     * @return the description of the given mode, or {@code null} if no description is found.
     */
    protected final String getModeDescription(String mode) {
        return manifest.getAttributes(mode).getValue(ATTR_MODE_DESC);
    }

    /**
     * Returns the value of the given manifest attribute with consideration to the capsule's mode.
     *
     * @param attr the attribute
     */
    protected final String getAttribute(String attr) {
        String value = null;
        if (mode != null && !NON_MODAL_ATTRS.contains(attr))
            value = manifest.getAttributes(mode).getValue(attr);
        if (value == null)
            value = manifest.getMainAttributes().getValue(attr);
        return value;
    }

    /**
     * Tests whether the given attribute is found in the manifest.
     *
     * @param attr the attribute
     */
    protected final boolean hasAttribute(String attr) {
        final Attributes.Name key = new Attributes.Name(attr);
        if (mode != null && !NON_MODAL_ATTRS.contains(attr)) {
            if (manifest.getAttributes(mode).containsKey(key))
                return true;
        }
        return manifest.getMainAttributes().containsKey(new Attributes.Name(attr));
    }

    /**
     * Returns the value of the given attribute (with consideration to the capsule's mode) as a list.
     * The items comprising attribute's value must be whitespace-separated.
     *
     * @param attr the attribute
     */
    protected final List<String> getListAttribute(String attr) {
        return split(getAttribute(attr), "\\s+");
    }

    /**
     * Returns the value of the given attribute (with consideration to the capsule's mode) as a map.
     * The key-value pairs comprising attribute's value must be whitespace-separated, with each pair written as <i>key</i>=<i>value</i>.
     *
     * @param attr the attribute
     */
    protected final Map<String, String> getMapAttribute(String attr, String defaultValue) {
        return mapSplit(getAttribute(attr), '=', "\\s+", defaultValue);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Dependency Utils">
    /////////// Dependency Utils ///////////////////////////////////
    private static boolean isDependency(String lib) {
        return lib.contains(":");
    }

    private String dependencyToLocalJar(boolean withGroupId, String p) {
        String[] coords = p.split(":");
        StringBuilder sb = new StringBuilder();
        if (withGroupId)
            sb.append(coords[0]).append('-');
        sb.append(coords[1]).append('-');
        sb.append(coords[2]);
        if (coords.length > 3)
            sb.append('-').append(coords[3]);
        sb.append(".jar");
        return sb.toString();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Paths">
    /////////// Paths ///////////////////////////////////
    private Path getPath(String p) {
        if (p == null)
            return null;
        if (isDependency(p) && dependencyManager != null)
            return getDependencyPath(dependencyManager, p);

        if (appCache == null)
            throw new IllegalStateException(
                    (isDependency(p) ? "Dependency manager not found. Cannot resolve" : "Capsule not extracted. Cannot obtain path")
                    + " " + p);
        if (isDependency(p)) {
            Path f = appCache.resolve(dependencyToLocalJar(true, p));
            if (Files.isRegularFile(f))
                return f;
            f = appCache.resolve(dependencyToLocalJar(false, p));
            if (Files.isRegularFile(f))
                return f;
            throw new IllegalArgumentException("Dependency manager not found, and could not locate artifact " + p + " in capsule");
        } else
            return toAbsolutePath(appCache, p);
    }

    private List<Path> getPath(List<String> ps) {
        if (ps == null)
            return null;
        final List<Path> res = new ArrayList<Path>(ps.size());
        for (String p : ps)
            res.add(getPath(p));
        return res;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="JAR Extraction">
    /////////// JAR Extraction ///////////////////////////////////
    private static void extractJar(JarInputStream jar, Path targetDir) throws IOException {
        for (JarEntry entry; (entry = jar.getNextJarEntry()) != null;) {
            if (entry.isDirectory() || !shouldExtractFile(entry.getName()))
                continue;

            writeFile(targetDir, entry.getName(), jar);
        }
    }

    private static boolean shouldExtractFile(String fileName) {
        if (fileName.equals(Capsule.class.getName().replace('.', '/') + ".class")
                || (fileName.startsWith(Capsule.class.getName().replace('.', '/') + "$") && fileName.endsWith(".class")))
            return false;
        if (fileName.endsWith(".class"))
            return false;
        if (fileName.startsWith("capsule/"))
            return false;
        if (fileName.startsWith("META-INF/"))
            return false;
        return true;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Path Utils">
    /////////// Path Utils ///////////////////////////////////
    private Path path(String p, String... more) {
        return cacheDir.getFileSystem().getPath(p, more);
    }

    private static Path relativeToRoot(Path p) {
        return p != null ? p.getRoot().relativize(p) : null;
    }

    private List<Path> toPath(List<String> ps) {
        if (ps == null)
            return null;
        final List<Path> aps = new ArrayList<Path>(ps.size());
        for (String p : ps)
            aps.add(path(p));
        return aps;
    }

    private static List<Path> toAbsolutePath(Path root, List<String> ps) {
        if (ps == null)
            return null;
        final List<Path> aps = new ArrayList<Path>(ps.size());
        for (String p : ps)
            aps.add(toAbsolutePath(root, p));
        return aps;
    }

    private static Path toAbsolutePath(Path root, String p) {
        return root.resolve(sanitize(p)).toAbsolutePath();
    }

    private static String sanitize(String path) {
        if (path.startsWith("/") || path.startsWith("../") || path.contains("/../"))
            throw new IllegalArgumentException("Path " + path + " is not local");
        return path;
    }

    private static String expandCommandLinePath(String str) {
        if (str == null)
            return null;
//        if (isWindows())
//            return str;
//        else
        return str.startsWith("~/") ? str.replace("~", System.getProperty(PROP_USER_HOME)) : str;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="OS">
    /////////// OS ///////////////////////////////////
    /**
     * Tests whether the current OS is Windows.
     */
    protected static final boolean isWindows() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("windows");
    }

    /**
     * Tests whether the current OS is MacOS.
     */
    protected static final boolean isMac() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("mac");
    }

    /**
     * Tests whether the current OS is UNIX/Linux.
     */
    protected static final boolean isUnix() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().contains("nux")
                || System.getProperty(PROP_OS_NAME).toLowerCase().contains("solaris")
                || System.getProperty(PROP_OS_NAME).toLowerCase().contains("aix");
    }

    private String getNativeLibExtension() {
        if (isWindows())
            return "dll";
        if (isMac())
            return "dylib";
        if (isUnix())
            return "so";
        throw new RuntimeException("Unsupported operating system: " + System.getProperty(PROP_OS_NAME));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="JAR Utils">
    /////////// JAR Utils ///////////////////////////////////
    private static String getMainClass(Path jar) throws IOException {
        return getMainClass(getManifest(jar));
    }

    private static String getMainClass(Manifest manifest) {
        if (manifest == null)
            return null;
        return manifest.getMainAttributes().getValue(ATTR_MAIN_CLASS);
    }

    private static Manifest getManifest(Path jar) throws IOException {
        try (final JarInputStream jis = new JarInputStream(skipToZipStart(Files.newInputStream(jar)))) {
            return jis.getManifest();
        }
    }

    private static boolean hasEntry(Path jarFile, String name) throws IOException {
        try (final JarInputStream jis = new JarInputStream(skipToZipStart(Files.newInputStream(jarFile)))) {
            for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
                if (name.equals(entry.getName()))
                    return true;
            }
            return false;
        }
    }

    private static int[] ZIP_HEADER = new int[]{'\n', 'P', 'K', 0x03, 0x04};

    private static InputStream skipToZipStart(InputStream is) throws IOException {
        if (!is.markSupported())
            is = new BufferedInputStream(is);
        int state = 1;
        for (;;) {
            if (state == 1)
                is.mark(ZIP_HEADER.length);
            final int b = is.read();
            if (b < 0)
                throw new IllegalArgumentException("Not a JAR/ZIP file");
            if (b == ZIP_HEADER[state]) {
                state++;
                if (state == ZIP_HEADER.length)
                    break;
            } else {
                state = 0;
                if (b == ZIP_HEADER[state]) // consecutive '\n'
                    state++;
            }
        }
        is.reset();
        return is;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="File Utils">
    /////////// File Utils ///////////////////////////////////
    /**
     * Returns the contents of a directory
     *
     * @param dir the directory
     */
    protected static final List<Path> listDir(Path dir) {
        final List<Path> list = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f : ds)
                list.add(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private static String getDirectory(String filename) {
        final int index = filename.lastIndexOf('/');
        if (index < 0)
            return null;
        return filename.substring(0, index);
    }

    private static void writeFile(Path targetDir, String fileName, InputStream is) throws IOException {
        final String dir = getDirectory(fileName);
        if (dir != null)
            Files.createDirectories(targetDir.resolve(dir));

        final Path targetFile = targetDir.resolve(fileName);
        Files.copy(is, targetFile);
    }

    // visible for testing
    static void delete(Path file) throws IOException {
        // not using FileWalker so as not to create another class
        if (Files.isDirectory(file)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(file)) {
                for (Path f : ds)
                    delete(f);
            }
        }
        Files.delete(file);
    }

    private static void ensureExecutable(Path file) {
        if (!Files.isExecutable(file)) {
            try {
                Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
                if (!perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
                    Set<PosixFilePermission> newPerms = EnumSet.copyOf(perms);
                    newPerms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(file, newPerms);
                }
            } catch (UnsupportedOperationException e) {
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="JRE Installations">
    /////////// JRE Installations ///////////////////////////////////
    private static Map<String, Path> getJDKs(Map<String, Path> homes) {
        Map<String, Path> jdks = new HashMap<>();
        for (Map.Entry<String, Path> entry : homes.entrySet()) {
            Path home = entry.getValue();
            if (isJDK(home))
                jdks.put(entry.getKey(), entry.getValue());
        }
        return jdks.isEmpty() ? null : jdks;
    }

    private static boolean isJDK(Path javaHome) {
        final String name = javaHome.toString().toLowerCase();
        return name.contains("jdk") && !name.contains("jre");
    }

    /**
     * Returns all found Java installations.
     *
     * @return a map from installations' versions to their respective paths
     */
    protected static Map<String, Path> getJavaHomes() {
        if (JAVA_HOMES != null)
            return JAVA_HOMES;
        Path dir = Paths.get(System.getProperty(PROP_JAVA_HOME)).getParent();
        while (dir != null) {
            Map<String, Path> homes = getJavaHomes(dir);
            if (homes != null) {
                if (isWindows())
                    homes = windowsJavaHomesHeuristics(dir, homes);
                JAVA_HOMES = homes;
                return homes;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static Map<String, Path> windowsJavaHomesHeuristics(Path dir, Map<String, Path> homes) {
        Path dir2 = null;
        if (dir.startsWith(WINDOWS_PROGRAM_FILES_1))
            dir2 = WINDOWS_PROGRAM_FILES_2.resolve(WINDOWS_PROGRAM_FILES_1.relativize(dir));
        else if (dir.startsWith(WINDOWS_PROGRAM_FILES_2))
            dir2 = WINDOWS_PROGRAM_FILES_1.resolve(WINDOWS_PROGRAM_FILES_2.relativize(dir));
        if (dir2 != null) {
            Map<String, Path> allHomes = new HashMap<>(homes);
            allHomes.putAll(getJavaHomes(dir2));
            return allHomes;
        } else
            return homes;
    }

    private static Map<String, Path> getJavaHomes(Path dir) {
        if (!Files.isDirectory(dir))
            return null;
        Map<String, Path> dirs = new HashMap<String, Path>();
        for (Path f : listDir(dir)) {
            if (Files.isDirectory(f)) {
                String dirName = f.getFileName().toString();
                String ver = isJavaDir(dirName);
                if (ver != null) {
                    final Path home = searchJavaHomeInDir(f).toAbsolutePath();
                    if (home != null) {
                        if (parseJavaVersion(ver)[3] == 0)
                            ver = getActualJavaVersion(home);
                        dirs.put(ver, home);
                    }
                }
            }
        }
        return !dirs.isEmpty() ? dirs : null;
    }

    // visible for testing
    static String isJavaDir(String fileName) {
        fileName = fileName.toLowerCase();
        if (fileName.startsWith("jdk") || fileName.startsWith("jre") || fileName.endsWith(".jdk") || fileName.endsWith(".jre")) {
            if (fileName.startsWith("jdk") || fileName.startsWith("jre"))
                fileName = fileName.substring(3);
            if (fileName.endsWith(".jdk") || fileName.endsWith(".jre"))
                fileName = fileName.substring(0, fileName.length() - 4);
            return shortJavaVersion(fileName);
        } else
            return null;
    }

    private static Path searchJavaHomeInDir(Path dir) {
        if (!Files.isDirectory(dir))
            return null;
        for (Path f : listDir(dir)) {
            if (isJavaHome(f))
                return f;
            Path home = searchJavaHomeInDir(f);
            if (home != null)
                return home;
        }
        return null;
    }

    private static boolean isJavaHome(Path dir) {
        if (Files.isDirectory(dir)) {
            for (Path f : listDir(dir)) {
                if (Files.isDirectory(f) && f.getFileName().toString().equals("bin")) {
                    for (Path f0 : listDir(f)) {
                        if (Files.isRegularFile(f0)) {
                            String fname = f0.getFileName().toString().toLowerCase();
                            if (fname.equals("java") || fname.equals("java.exe"))
                                return true;
                        }
                    }
                    break;
                }
            }
        }
        return false;
    }

    private static Path getJavaProcessImage(Path javaHome) {
        return javaHome.resolve("bin").resolve("java" + (isWindows() ? ".exe" : ""));
    }

    private static final Pattern PAT_JAVA_VERSION_LINE = Pattern.compile(".*?\"(.+?)\"");

    private static String getActualJavaVersion(Path javaHome) {
        try {
            final ProcessBuilder pb = new ProcessBuilder(getJavaProcessImage(javaHome).toString(), "-version");
            if (javaHome != null)
                pb.environment().put("JAVA_HOME", javaHome.toString());
            final Process p = pb.start();
            final String version;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                final String versionLine = reader.readLine();
                final Matcher m = PAT_JAVA_VERSION_LINE.matcher(versionLine);
                if (!m.matches())
                    throw new IllegalArgumentException("Could not parse version line: " + versionLine);
                version = m.group(1);
            }
            // p.waitFor();
            return version;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Version Strings">
    /////////// Version Strings ///////////////////////////////////
    // visible for testing
    static String shortJavaVersion(String v) {
        try {
            final String[] vs = v.split(SEPARATOR_DOT);
            if (vs.length == 1) {
                if (Integer.parseInt(vs[0]) < 5)
                    throw new RuntimeException("Unrecognized major Java version: " + v);
                v = "1." + v + ".0";
            }
            if (vs.length == 2)
                v += ".0";
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static final int compareVersions(String a, String b, int n) {
        return compareVersions(parseJavaVersion(a), parseJavaVersion(b), n);
    }

    static final int compareVersions(String a, String b) {
        return compareVersions(parseJavaVersion(a), parseJavaVersion(b));
    }

    private static int compareVersions(int[] a, int[] b) {
        return compareVersions(a, b, 5);
    }

    private static int compareVersions(int[] a, int[] b, int n) {
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i])
                return a[i] - b[i];
        }
        return 0;
    }

    private static boolean equals(int[] a, int[] b, int n) {
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i])
                return false;
        }
        return true;
    }

    private static final Pattern PAT_JAVA_VERSION = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(_(?<update>\\d+))?(-(?<pre>[^-]+))?(-(?<build>.+))?");

    // visible for testing
    static int[] parseJavaVersion(String v) {
        final Matcher m = PAT_JAVA_VERSION.matcher(v);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse version: " + v);
        final int[] ver = new int[5];
        ver[0] = toInt(m.group("major"));
        ver[1] = toInt(m.group("minor"));
        ver[2] = toInt(m.group("patch"));
        ver[3] = toInt(m.group("update"));
        final String pre = m.group("pre");
        if (pre != null) {
            if (pre.startsWith("rc"))
                ver[4] = -1;
            else if (pre.startsWith("beta"))
                ver[4] = -2;
            else if (pre.startsWith("ea"))
                ver[4] = -3;
        }
        return ver;
    }

    // visible for testing
    static String toJavaVersionString(int[] version) {
        final StringBuilder sb = new StringBuilder();
        sb.append(version[0]).append('.');
        sb.append(version[1]).append('.');
        sb.append(version[2]);
        if (version.length > 3 && version[3] > 0)
            sb.append('_').append(version[3]);
        if (version.length > 4 && version[4] != 0) {
            final String pre;
            switch (version[4]) {
                case -1:
                    pre = "rc";
                    break;
                case -2:
                    pre = "beta";
                    break;
                case -3:
                    pre = "ea";
                    break;
                default:
                    pre = "?";
            }
            sb.append('-').append(pre);
        }
        return sb.toString();
    }

    private static int toInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private static int[] toInt(String[] ss) {
        int[] res = new int[ss.length];
        for (int i = 0; i < ss.length; i++)
            res[i] = ss[i] != null ? Integer.parseInt(ss[i]) : 0;
        return res;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="String Expansion">
    /////////// String Expansion ///////////////////////////////////
    private List<String> expand(List<String> strs) {
        if (strs == null)
            return null;
        final List<String> res = new ArrayList<String>(strs.size());
        for (String s : strs)
            res.add(expand(s));
        return res;
    }

    /**
     * Expands occurrences of {@code $VARNAME} in attribute values.
     *
     * @param str the original string
     * @return the expanded string
     */
    protected String expand(String str) {
        if ("$0".equals(str))
            return jarFile.toString();

        if (appCache != null)
            str = str.replaceAll("\\$" + VAR_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        else if (str.contains("$" + VAR_CAPSULE_DIR))
            throw new IllegalStateException("The $" + VAR_CAPSULE_DIR + " variable cannot be expanded when the "
                    + ATTR_EXTRACT + " attribute is set to false");

        str = expandCommandLinePath(str);
        assert appId != null;
        str = str.replaceAll("\\$" + VAR_CAPSULE_APP, appId);
        str = str.replaceAll("\\$" + VAR_CAPSULE_JAR, jarFile.toString());
        str = str.replaceAll("\\$" + VAR_JAVA_HOME, getJavaHome().toString());
        str = str.replace('/', FILE_SEPARATOR.charAt(0));
        return str;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="String Utils">
    /////////// String Utils ///////////////////////////////////
    /**
     * Splits a string into a list using a regex separator
     */
    protected final static List<String> split(String str, String separator) {
        if (str == null)
            return null;
        String[] es = str.split(separator);
        final List<String> list = new ArrayList<>(es.length);
        for (String e : es) {
            e = e.trim();
            if (!e.isEmpty())
                list.add(e);
        }
        return list;
    }

    /**
     * Splits a string into a map
     *
     * @param map          the string
     * @param kvSeparator  the character separating each key from its corresponding value
     * @param separator    a regex separator between key-value pairs
     * @param defaultValue A default value to use for keys without a value, or {@code null} if such an event should throw an exception
     * @return the map
     */
    protected final static Map<String, String> mapSplit(String map, char kvSeparator, String separator, String defaultValue) {
        if (map == null)
            return null;
        Map<String, String> m = new HashMap<>();
        for (String entry : split(map, separator)) {
            final String key = getBefore(entry, kvSeparator);
            String value = getAfter(entry, kvSeparator);
            if (value == null) {
                if (defaultValue != null)
                    value = defaultValue;
                else
                    throw new IllegalArgumentException("Element " + entry + " in \"" + map + "\" is not a key-value entry separated with " + kvSeparator + " and no default value provided");
            }
            m.put(key.trim(), value.trim());
        }
        return m;
    }

    /**
     * Joins a collections into a string, separating elements with the given separator string.
     */
    protected final static String join(Collection<?> coll, String separator) {
        if (coll == null)
            return null;
        if (coll.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (Object e : coll) {
            if (e != null)
                sb.append(e).append(separator);
        }
        sb.delete(sb.length() - separator.length(), sb.length());
        return sb.toString();
    }

    private static String getBefore(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return s;
        return s.substring(0, i);
    }

    private static String getAfter(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return null;
        return s.substring(i + 1);
    }

//    private static String globToRegex(String line) {
//        line = line.trim();
//        int strLen = line.length();
//        StringBuilder sb = new StringBuilder(strLen);
//        // Remove beginning and ending * globs because they're useless
//        if (line.startsWith("*")) {
//            line = line.substring(1);
//            strLen--;
//        }
//        if (line.endsWith("*")) {
//            line = line.substring(0, strLen - 1);
//            strLen--;
//        }
//        boolean escaping = false;
//        int inCurlies = 0;
//        for (char currentChar : line.toCharArray()) {
//            switch (currentChar) {
//                case '*':
//                    if (escaping)
//                        sb.append("\\*");
//                    else
//                        sb.append(".*");
//                    escaping = false;
//                    break;
//                case '?':
//                    if (escaping)
//                        sb.append("\\?");
//                    else
//                        sb.append('.');
//                    escaping = false;
//                    break;
//                case '.':
//                case '(':
//                case ')':
//                case '+':
//                case '|':
//                case '^':
//                case '$':
//                case '@':
//                case '%':
//                    sb.append('\\');
//                    sb.append(currentChar);
//                    escaping = false;
//                    break;
//                case '\\':
//                    if (escaping) {
//                        sb.append("\\\\");
//                        escaping = false;
//                    } else
//                        escaping = true;
//                    break;
//                case '{':
//                    if (escaping)
//                        sb.append("\\{");
//                    else {
//                        sb.append('(');
//                        inCurlies++;
//                    }
//                    escaping = false;
//                    break;
//                case '}':
//                    if (inCurlies > 0 && !escaping) {
//                        sb.append(')');
//                        inCurlies--;
//                    } else if (escaping)
//                        sb.append("\\}");
//                    else
//                        sb.append("}");
//                    escaping = false;
//                    break;
//                case ',':
//                    if (inCurlies > 0 && !escaping)
//                        sb.append('|');
//                    else if (escaping)
//                        sb.append("\\,");
//                    else
//                        sb.append(",");
//                    break;
//                default:
//                    escaping = false;
//                    sb.append(currentChar);
//            }
//        }
//        return sb.toString();
//    }
    private static String emptyToNull(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Collection Utils">
    /////////// Collection Utils ///////////////////////////////////
    private static <T> List<T> nullToEmpty(List<T> list) {
        if (list == null)
            return Collections.emptyList();
        return list;
    }

    private static <K, V> Map<K, V> nullToEmpty(Map<K, V> map) {
        if (map == null)
            return Collections.emptyMap();
        return map;
    }

    private static <C extends Collection<T>, T> C addAllIfNotContained(C c, Collection<T> c1) {
        for (T e : c1) {
            if (!c.contains(e))
                c.add(e);
        }
        return c;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Misc Utils">
    /////////// Misc Utils ///////////////////////////////////
    private static boolean propertyDefined(String... props) {
        for (String prop : props) {
            if (System.getProperty(prop) != null)
                return true;
        }
        return false;
    }

    private static String propertyOrEnv(String propName, String envVar) {
        String val = System.getProperty(propName);
        if (val == null)
            val = emptyToNull(System.getenv(envVar));
        return val;
    }
//    private static void setLibraryPath(String path) {
//        try {
//            System.setProperty("java.library.path", path);
//
//            final java.lang.reflect.Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
//            fieldSysPath.setAccessible(true);
//            fieldSysPath.set(null, null);
//        } catch (ReflectiveOperationException e) {
//            throw new AssertionError(e);
//        }
//    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Logging">
    /////////// Logging ///////////////////////////////////
    /**
     * Chooses and returns the capsules log level.
     */
    protected int chooseLogLevel() {
        String level = System.getProperty(PROP_LOG_LEVEL);
        if (level == null)
            level = getAttribute(ATTR_LOG_LEVEL);
        int lvl = getLogLevel(level);
        if (lvl < 0)
            throw new IllegalArgumentException("Unrecognized log level: " + level);
        return lvl;
    }

    private static int getLogLevel(String level) {
        if (level == null || level.isEmpty())
            level = "QUIET";
        switch (level.toUpperCase()) {
            case "NONE":
                return LOG_NONE;
            case "QUIET":
                return LOG_QUIET;
            case "VERBOSE":
                return LOG_VERBOSE;
            case "DEBUG":
                return LOG_DEBUG;
            default:
                return -1;
        }
    }

    /**
     * Tests id the given log level is currently being logged
     *
     * @param level
     */
    protected final boolean isLogging(int level) {
        return level <= logLevel;
    }

    private void println(String str) {
        log(LOG_QUIET, str);
    }

    private void log(int level, String str) {
        if (isLogging(level))
            System.err.println(LOG_PREFIX + str);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Pipe Streams (workaround for inheritIO bug)">
    /////////// Pipe Streams (workaround for inheritIO bug) ///////////////////////////////////
    private static boolean isInheritIoBug() {
        return isWindows() && compareVersions(System.getProperty(PROP_JAVA_VERSION), "1.8.0") < 0;
    }

    private void pipeIoStreams() {
        new Thread(this, "pipe-out").start();
        new Thread(this, "pipe-err").start();
        new Thread(this, "pipe-in").start();
    }

    /**
     * @deprecated marked deprecated to exclude from javadoc
     */
    @Override
    public final void run() {
        if (isInheritIoBug()) {
            switch (Thread.currentThread().getName()) {
                case "pipe-out":
                    pipe(child.getInputStream(), System.out);
                    return;
                case "pipe-err":
                    pipe(child.getErrorStream(), System.err);
                    return;
                case "pipe-in":
                    pipe(System.in, child.getOutputStream());
                    return;
                default: // shutdown hook
            }
        }
        if (child != null)
            child.destroy();
    }

    private void pipe(InputStream in, OutputStream out) {
        try (OutputStream out1 = out) {
            int read;
            byte[] buf = new byte[1024];
            while (-1 != (read = in.read(buf))) {
                out.write(buf, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            if (isLogging(LOG_VERBOSE))
                e.printStackTrace(System.err);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="POSIX">
    /////////// POSIX ///////////////////////////////////
    private static int getPid(Process p) {
        try {
            java.lang.reflect.Field pidField = p.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            return pidField.getInt(p);
        } catch (Exception e) {
            return -1;
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Object Methods">
    /////////// Object Methods ///////////////////////////////////
    /**
     * Throws a {@link CloneNotSupportedException}
     */
    @Override
    protected final Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public final int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(this.jarFile);
        hash = 47 * hash + Objects.hashCode(this.mode);
        return hash;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Capsule other = (Capsule) obj;
        if (!Objects.equals(this.jarFile, other.jarFile))
            return false;
        if (!Objects.equals(this.mode, other.mode))
            return false;
        return true;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append('[');
        sb.append(jarFile);
        if (appId != null) {
            sb.append(", ").append(appId);
            sb.append(getAttribute(ATTR_APP_CLASS) != null ? getAttribute(ATTR_APP_CLASS) : getAttribute(ATTR_APP_ARTIFACT));
        } else
            sb.append(", ").append("empty");
        if (mode != null)
            sb.append(", ").append("mode: ").append(mode);
        sb.append(']');
        return sb.toString();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Capsule Loading and Launching">
    /////////// Capsule Loading and Launching ///////////////////////////////////
    // visible for testing
    static Capsule newCapsule(Path jarFile, Path cacheDir) {
        return (Capsule) newCapsule(jarFile, cacheDir, Capsule.class.getClassLoader());
    }

    private static Object newCapsule(Path jarFile, Path cacheDir, ClassLoader cl) {
        try {
            final String mainClassName = getMainClass(jarFile);
            if (mainClassName != null) {
                final Class<?> clazz = cl.loadClass(mainClassName);
                if (isCapsuleClass(clazz)) {
                    final Constructor<?> ctor = clazz.getDeclaredConstructor(Path.class, Path.class);
                    ctor.setAccessible(true);
                    return ctor.newInstance(jarFile, cacheDir);
                }
            }
            throw new RuntimeException(jarFile + " does not appear to be a valid capsule.");
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(jarFile + " does not appear to be a valid capsule.", e);
        } catch (InvocationTargetException e) {
            throw rethrow(e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not instantiate capsule.", e);
        }
    }

    private static ProcessBuilder launchCapsule(Path path, Path cacheDir, List<String> cmdLine, List<String> args) {
        try {
            final ClassLoader cl = (ClassLoader) createClassLoader(path);
            final Object capsule = newCapsule(path, cacheDir, cl);
            final Method launch = getMethod(capsule.getClass(), "prepareForLaunch", List.class, List.class);
            if (launch != null)
                return (ProcessBuilder) launch.invoke(capsule, cmdLine, args);
            throw new RuntimeException(path + " does not appear to be a valid capsule.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        } catch (InvocationTargetException e) {
            throw rethrow(e.getTargetException());
        }
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        if (t instanceof Error)
            throw (Error) t;
        throw new RuntimeException(t);
    }

    private static boolean isCapsuleClass(Class<?> clazz) {
        if (clazz == null)
            return false;
        return Capsule.class.getName().equals(clazz.getName()) || isCapsuleClass(clazz.getSuperclass());
    }

    private static Object createClassLoader(Path path) throws IOException {
        return new JarClassLoader(path, true); // new URLClassLoader(new URL[]{path.toUri().toURL()}); // 
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            final Method method = clazz.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return clazz.getSuperclass() != null ? getMethod(clazz.getSuperclass(), name, paramTypes) : null;
        }
    }
    //</editor-fold>
}
