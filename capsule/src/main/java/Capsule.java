/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
import capsule.DependencyManagerImpl;
import capsule.DependencyManager;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static java.util.Collections.*;

/**
 * An application capsule.
 * <p>
 * This API is to be used by custom capsules to programmatically (rather than declaratively) configure the capsule and possibly provide custom behavior.
 * <p>
 * All non-final protected methods may be overridden by custom capsules. These methods will usually be called once, but they must be idempotent,
 * i.e. if called numerous times they must always return the same value, and produce the same effect as if called once.
 * <br>
 * Overridden methods need not be thread-safe, and are guaranteed to be called by a single thread at a time.
 * <br>
 * Overridable (non-final) methods <b>must never</b> be called directly by custom capsule code, except by their overrides.
 * <p>
 * Final methods implement various utility or accessors, which may be freely used by custom capsules.
 *
 * @author pron
 */
public class Capsule implements Runnable {
    protected static final String VERSION = "0.10.0";
    /*
     * This class follows some STRICT RULES:
     *
     * 1. IT MUST COMPILE TO A SINGLE CLASS FILE (so it must not contain nested or inner classes).
     * 2. IT MUST ONLY REFERENCE CLASSES IN THE JDK AND THOSE IN THE capsule PACKAGE.
     * 3. CAPSULES WITH NO DECLARED DEPENDENCIES MUST LAUNCH WITHOUT REQUIRING ANY CLASSES BUT THIS AND THE JDK.
     * 4. ALL METHODS MUST BE PURE OR, AT LEAST, IDEMPOTENT (with the exception of the launch method, and, of course, the constructor).
     *
     * Rules #1 and #3 ensure that fat capsules will work with only Capsule.class included in the JAR. Rule #2 helps enforcing rules #1 and #3.
     * Rule #4 ensures methods can be called in any order (after construction completes), and makes maintenance and evolution of Capsule simpler.
     *
     * This class contains several strange hacks to compy with rule #1.
     *
     * Also, the code is not meant to be the most efficient, but methods should be as independent and stateless as possible.
     * Other than those few methods called in the constructor, all others are can be called in any order, and don't rely on any state.
     *
     * We do a lot of data transformations that could benefited from Java 8's lambdas+streams, but we want Capsule to support Java 7.
     */

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
    private static final String PROP_CAPSULE_JAVA_CMD = "capsule.java.cmd";
    private static final String PROP_MODE = "capsule.mode";
    private static final String PROP_USE_LOCAL_REPO = "capsule.local";
    private static final String PROP_JVM_ARGS = "capsule.jvm.args";
    private static final String PROP_NO_DEP_MANAGER = "capsule.no_dep_manager";

    private static final String PROP_JAVA_VERSION = "java.version";
    private static final String PROP_JAVA_HOME = "java.home";
    private static final String PROP_OS_NAME = "os.name";
    private static final String PROP_USER_HOME = "user.home";
    private static final String PROP_JAVA_LIBRARY_PATH = "java.library.path";
    private static final String PROP_FILE_SEPARATOR = "file.separator";
    private static final String PROP_PATH_SEPARATOR = "path.separator";
    private static final String PROP_JAVA_SECURITY_POLICY = "java.security.policy";
    private static final String PROP_JAVA_SECURITY_MANAGER = "java.security.manager";
    private static final String PROP_TMP_DIR = "java.io.tmpdir";

    private static final String ENV_CACHE_DIR = "CAPSULE_CACHE_DIR";
    private static final String ENV_CACHE_NAME = "CAPSULE_CACHE_NAME";
    private static final String ENV_CAPSULE_REPOS = "CAPSULE_REPOS";
    private static final String ENV_CAPSULE_LOCAL_REPO = "CAPSULE_LOCAL_REPO";

    private static final String ATTR_MANIFEST_VERSION = "Manifest-Version";
    private static final String ATTR_CLASS_PATH = "Class-Path";
    private static final String ATTR_IMPLEMENTATION_VERSION = "Implementation-Version";

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
    private static final String ATTR_LOG_LEVEL = "Capsule-Log-Level";

    private static final Set<String> NON_MODAL_ATTRS = unmodifiableSet(new HashSet<String>(Arrays.asList(
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
    private static final String LOCK_FILE_NAME = ".lock";
    private static final String TIMESTAMP_FILE_NAME = ".extracted";
    private static final String FILE_SEPARATOR = System.getProperty(PROP_FILE_SEPARATOR);
    private static final char FILE_SEPARATOR_CHAR = FILE_SEPARATOR.charAt(0);
    private static final String PATH_SEPARATOR = System.getProperty(PROP_PATH_SEPARATOR);
    private static final Path WINDOWS_PROGRAM_FILES_1 = Paths.get("C:", "Program Files");
    private static final Path WINDOWS_PROGRAM_FILES_2 = Paths.get("C:", "Program Files (x86)");
    private static final int WINDOWS_MAX_CMD = 32500; // actually 32768 - http://blogs.msdn.com/b/oldnewthing/archive/2003/12/10/56028.aspx
    private static final Object DEFAULT = new Object();

    @SuppressWarnings("FieldMayBeFinal")
    private static Object DEPENDENCY_MANAGER = DEFAULT; // used only by tests

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

    /**
     * Returns the singleton Capsule instance.
     * The passed argument list must be mutable, as this method might consume some arguments and remove them from the list.
     *
     * @param args The command-line arguments passed to {@code main}, as a <i>mutable</i> list.
     */
    protected static Capsule myCapsule(List<String> args) {
        if (CAPSULE == null) {
            final Capsule capsule = newCapsule(findOwnJarFile(), getCacheDir());
            if (capsule.isEmptyCapsule() && !args.isEmpty())
                capsule.setTargetCapsule(args.remove(0));
            CAPSULE = capsule;
        }
        return CAPSULE;
    }

    /**
     * Launches the capsule.
     * A custom capsule may provide its own main method to add capsule actions, and then call this method
     * if no custom action is performed.
     */
    @SuppressWarnings({"BroadCatchBlock", "CallToPrintStackTrace", "StringEquality", "null"})
    public static final void main(String[] args0) {
        final List<String> args = new ArrayList<>(Arrays.asList(args0)); // list must be mutable b/c myCapsule() might mutate it
        try {
            final Capsule capsule = myCapsule(args);

            if (capsule.getClass().equals(Capsule.class) && capsule.wrapper && !capsule.isEmptyCapsule()) // not a custom capsule
                System.exit(runMain(capsule.jarFile, args));

            if (propertyDefined(PROP_VERSION, PROP_MODES, PROP_TREE, PROP_RESOLVE, PROP_PRINT_JRES)) {
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

            if (capsule.isEmptyCapsule())
                throw new RuntimeException("USAGE: java -jar " + capsule.jarFile + " PATH_OR_MAVEN_COORDINATES\n" + capsule.jarFile + " is an empty capsule.");

            System.exit(capsule.launch(args));
        } catch (Throwable t) {
            System.err.print("CAPSULE EXCEPTION: " + t.getMessage());
            if (hasContext() && (t.getMessage() == null || t.getMessage().length() < 50))
                System.err.print(" while processing " + reportContext());
            if (getLogLevel(System.getProperty(PROP_LOG_LEVEL)) >= LOG_VERBOSE) {
                System.err.println();
                t.printStackTrace(System.err);
            } else
                System.err.println(" (for stack trace, run with -D" + PROP_LOG_LEVEL + "=verbose)");
            System.exit(1);
        }
    }

    @SuppressWarnings("CallToPrintStackTrace")
    private static int runMain(Path jar, List<String> args) {
        try {
            new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)
                    .loadClass(getMainClass(jar))
                    .getMethod("main", String[].class).invoke(null, (Object) args.toArray(new String[0]));
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }
    //</editor-fold>

    private static Map<String, Path> JAVA_HOMES; // an optimization trick (can be injected by CapsuleLauncher)

    // fields marked /*final*/ are effectively final after finalizeCapsule
    private final Path cacheDir;     // never null
    private final Object dependencyManager;
    private final boolean wrapper;
    private /*final*/ Path jarFile;  // never null
    private final Manifest manifest; // never null
    private /*final*/ String appId;  // null iff empty capsule
    private /*final*/ String mode;
    private /*final*/ Object pom;    // non-null iff jar has pom AND manifest doesn't have ATTR_DEPENDENCIES 
    private final int logLevel;

    private Path appCache;           // non-null iff capsule is extracted
    private boolean cacheUpToDate;
    private FileLock appCacheLock;

    // very limited state
    private List<String> jvmArgs_;
    private List<String> args_;
    private Path pathingJar;
    private Process child;
    // Error reporting
    private static String contextType_;
    private static String contextKey_;
    private static String contextValue_;

    //<editor-fold defaultstate="collapsed" desc="Constructors">
    /////////// Constructors ///////////////////////////////////
    /*
     * The constructors and methods in this section may be reflectively called by CapsuleLauncher
     */
    /**
     * Constructs a capsule from the given JAR file.
     *
     * @param jarFile  the path to the JAR file
     * @param cacheDir the path to the (shared) Capsule cache directory
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    protected Capsule(Path jarFile, Path cacheDir) {
        Objects.requireNonNull(jarFile, "jarFile can't be null");
        Objects.requireNonNull(cacheDir, "cacheDir can't be null");

        this.cacheDir = initCacheDir(cacheDir);
        this.jarFile = jarFile;

        try (JarInputStream jis = openJarInputStream(jarFile)) {
            this.manifest = jis.getManifest();
            if (manifest == null)
                throw new RuntimeException("Capsule " + jarFile + " does not have a manifest");
            this.pom = !hasAttribute(ATTR_DEPENDENCIES) ? createPomReader(jis) : null;
        } catch (IOException e) {
            throw new RuntimeException("Could not read JAR file " + jarFile, e);
        }

        this.logLevel = chooseLogLevel();
        this.wrapper = isEmptyCapsule();
        this.dependencyManager = DEPENDENCY_MANAGER != DEFAULT ? DEPENDENCY_MANAGER : tryCreateDependencyManager();

        if (this.dependencyManager != null)
            setDependencyRepositories(getRepositories());

        if (!wrapper)
            finalizeCapsule();
    }

    private void setTargetCapsule(String capsuleArtifact) {
        clearContext();
        final Path jar = isDependency(capsuleArtifact) ? firstOrNull(resolveDependency(capsuleArtifact, "jar")) : Paths.get(capsuleArtifact);
        if (jar == null)
            throw new RuntimeException(capsuleArtifact + " not found.");
        setTargetCapsule(jar);
    }

    private void setTargetCapsule(Path jar) {
        if (!isEmptyCapsule())
            throw new IllegalStateException("Capsule " + jarFile + " isn't empty");
        if (jar.equals(jarFile)) // catch simple loops
            throw new RuntimeException("Capsule wrapping loop detected with capsule " + jarFile);

        log(LOG_VERBOSE, "Wrapping capsule " + jar);
        this.jarFile = jar;

        try (JarInputStream jis = openJarInputStream(jarFile)) {
            boolean isCapsule = false;
            for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
                if (entry.getName().equals(Capsule.class.getName() + ".class"))
                    isCapsule = true;
                else if (entry.getName().equals(POM_FILE))
                    this.pom = !hasAttribute(ATTR_DEPENDENCIES) ? createPomReader0(jis) : null;
            }

            final Manifest man = jis.getManifest();
            if (man == null || !isCapsule)
                throw new RuntimeException(jarFile + " is not a capsule");

            merge(manifest, man);
        } catch (IOException e) {
            throw new RuntimeException("Could not read JAR file " + jarFile, e);
        }

        if (this.dependencyManager != null)
            setDependencyRepositories(getRepositories());
        finalizeCapsule();
    }

    private void finalizeCapsule() {
        validateManifest();
        this.appId = buildAppId();
        this.mode = chooseMode1();
    }

    private boolean isEmptyCapsule() {
        return !hasAttribute(ATTR_APP_ARTIFACT) && !hasAttribute(ATTR_APP_CLASS) && getScript() == null;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Properties">
    /////////// Properties ///////////////////////////////////
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
    private static Path findOwnJarFile() {
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
        if (getAppId() != null)
            System.out.println(LOG_PREFIX + "Application " + getAppId());
        System.out.println(LOG_PREFIX + "Capsule Version " + VERSION);
    }

    private void printModes(List<String> args) {
        System.out.println(LOG_PREFIX + "Application " + getAppId());
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
        System.out.println(LOG_PREFIX + "selected " + (javaHome != null ? javaHome : (systemProperty(PROP_JAVA_HOME) + " (current)")));
    }

    private void printDependencyTree(List<String> args) {
        System.out.println("Dependencies for " + getAppId());
        if (dependencyManager == null)
            System.out.println("No dependencies declared.");
        else if (hasAttribute(ATTR_APP_ARTIFACT)) {
            final String appArtifact = getAttribute(ATTR_APP_ARTIFACT);
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
        resolveDependency(getAttribute(ATTR_APP_ARTIFACT), "jar");
        resolveDependencies(getDependencies(), "jar");
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH));
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH_P));
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH_A));

        resolveNativeDependencies();
        log(LOG_QUIET, "Capsule resolved");
    }

    private int launch(List<String> args) throws IOException, InterruptedException {
        final ProcessBuilder pb;
        try {
            final List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            pb = prepareForLaunch(jvmArgs, args);
        } catch (Throwable t) {
            cleanup();
            throw t;
        }
        clearContext();

        log(LOG_VERBOSE, join(pb.command(), " "));

        if (isTrampoline()) {
            if (hasAttribute(ATTR_ENV))
                throw new RuntimeException("Capsule cannot trampoline because manifest defines the " + ATTR_ENV + " attribute.");
            pb.command().remove("-D" + PROP_TRAMPOLINE);
            System.out.println(join(pb.command(), " "));
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread(this));

            if (!isInheritIoBug())
                pb.inheritIO();

            this.child = pb.start();

            if (isInheritIoBug())
                pipeIoStreams();

            final int pid = getPid(child);
            if (pid > 0)
                System.setProperty(PROP_CAPSULE_APP_PID, Integer.toString(pid));

            child.waitFor();
        }

        return child != null ? child.exitValue() : 0;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Launch">
    /////////// Launch ///////////////////////////////////
    // directly used by CapsuleLauncher
    final ProcessBuilder prepareForLaunch(List<String> jvmArgs, List<String> args) {
        this.jvmArgs_ = nullToEmpty(jvmArgs); // hack
        this.args_ = nullToEmpty(jvmArgs);    // hack

        log(LOG_VERBOSE, "Launching app " + appId + (mode != null ? " in mode " + mode : ""));
        try {
            ProcessBuilder pb = null;
            try {
                ensureExtractedIfNecessary();

                pb = prelaunch(nullToEmpty(args));
                return pb;
            } finally {
                markCache(pb != null);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @deprecated marked deprecated to exclude from javadoc
     */
    @Override
    public final void run() {
        if (isInheritIoBug() && pipeIoStream())
            return;

        // shutdown hook
        cleanup();
    }

    /**
     * Called when the capsule exits after a successful or failed attempt to launch the application.
     * If you override this method, you must make sure to call {@code super.cleanup()} even in the event of an abnormal termination
     * (i.e. when an exception is thrown). This method must not throw any exceptions. All exceptions origination by {@code cleanup}
     * must be wither ignored completely or printed to STDERR.
     */
    @SuppressWarnings("CallToPrintStackTrace")
    protected void cleanup() {
        try {
            if (child != null)
                child.destroy();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            if (pathingJar != null)
                Files.delete(pathingJar);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private String chooseMode1() {
        String m = chooseMode();
        if (m != null && !hasMode(m))
            throw new IllegalArgumentException("Capsule " + jarFile + " does not have mode " + m);
        return m;
    }

    /**
     * Chooses this capsule's mode.
     * The mode is chosen during the preparations for launch (not at construction time).
     */
    protected String chooseMode() {
        return emptyToNull(systemProperty(PROP_MODE));
    }

    /**
     * Returns a configured {@link ProcessBuilder} that is later used to launch the capsule.
     * The ProcessBuilder's IO redirection is left in its default settings.
     * Custom capsules may override this method to display a message prior to launch, or to configure the process's IO streams.
     * For more elaborate manipulation of the Capsule's launched process, consider overriding {@link #buildProcess() buildProcess}.
     *
     * @param args the application command-line arguments
     * @return a configured {@code ProcessBuilder} (must never return {@code null})
     */
    protected ProcessBuilder prelaunch(List<String> args) {
        final ProcessBuilder pb = buildProcess();

        final List<String> command = pb.command();
        command.addAll(buildArgs(args));

        buildEnvironmentVariables(pb.environment());

        return pb;
    }

    /**
     * Constructs a {@link ProcessBuilder} that is later used to launch the capsule.
     * The returned process builder should contain the command <i>minus</i> the application arguments (which are later constructed by
     * {@link #buildArgs(List) buildArgs} and appended to the command).<br>
     * While environment variables may be set at this stage, the environment is later configured by
     * {@link #buildEnvironmentVariables(Map) buildEnvironmentVariables}.
     * <p>
     * This implementation tries to create a process running a startup script, and, if one has not been set, constructs a Java process.
     * <p>
     * This method should be overridden to add new types of processes the capsule can launch (like, say, Python scripts).
     * If all you want is to configure the returned {@link ProcessBuilder}, for example to set IO stream redirection,
     * you should override {@link #prelaunch(List) prelaunch}.
     *
     * @return a {@code ProcessBuilder}
     */
    protected ProcessBuilder buildProcess() {
        if (jvmArgs_ == null)
            throw new IllegalStateException("Capsule has not been prepared for launch!");

        final ProcessBuilder pb = new ProcessBuilder();
        if (!buildScriptProcess(pb))
            buildJavaProcess(pb, jvmArgs_, args_);
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

                if (var.isEmpty())
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

    private static boolean isTrampoline() {
        return propertyDefined(PROP_TRAMPOLINE);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="App ID">
    /////////// App ID ///////////////////////////////////
    /**
     * Computes and returns application's ID
     */
    protected String buildAppId() {
        String appName = systemProperty(PROP_APP_ID);
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
            throw new IllegalArgumentException("Capsule jar " + jarFile + " must either have the " + ATTR_APP_NAME + " manifest attribute, "
                    + "the " + ATTR_APP_CLASS + " attribute, or contain a " + POM_FILE + " file.");
        }

        final String version = hasAttribute(ATTR_APP_VERSION) ? getAttribute(ATTR_APP_VERSION) : getAttribute(ATTR_IMPLEMENTATION_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    /**
     * Returns the app's ID.
     */
    protected final String getAppId() {
        return appId;
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
            Files.createDirectories(cache.resolve(APP_CACHE_NAME));
            Files.createDirectories(cache.resolve(DEPS_CACHE_NAME));

            return cache;
        } catch (IOException e) {
            throw new RuntimeException("Error opening cache directory " + cache.toAbsolutePath(), e);
        }
    }

    private static Path getCacheHome() {
        final Path userHome = Paths.get(systemProperty(PROP_USER_HOME));
        if (!isWindows())
            return userHome;

        Path localData;
        final String localAppData = getenv("LOCALAPPDATA");
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
    private Path getAppCacheDir() throws IOException {
        assert appId != null;
        Path appDir = cacheDir.resolve(APP_CACHE_NAME).resolve(appId);
        try {
            if (!Files.exists(appDir))
                Files.createDirectory(appDir);
            return appDir;
        } catch (IOException e) {
            throw new IOException("Application cache directory " + appDir.toAbsolutePath() + " could not be created.");
        }
    }

    private void ensureAppCacheIfNecessary() throws IOException {
        if (appCache != null)
            return;

        this.appCache = needsAppCache() ? getAppCacheDir() : null;
        this.cacheUpToDate = appCache != null ? isAppCacheUpToDate1() : false;
    }

    private void ensureExtractedIfNecessary() throws IOException {
        ensureAppCacheIfNecessary();
        if (appCache != null) {
            if (!cacheUpToDate) {
                resetAppCache();
                if (shouldExtract())
                    extractCapsule();
            } else
                log(LOG_VERBOSE, "App cache " + appCache + " is up to date.");
        }
    }

    private boolean needsAppCache() {
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

    private void resetAppCache() throws IOException {
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
            throw new IOException("Exception while extracting jar " + jarFile + " to app cache directory " + appCache.toAbsolutePath(), e);
        }
    }

    private boolean isAppCacheUpToDate1() throws IOException {
        boolean res = isAppCacheUpToDate();
        if (!res) {
            lockAppCache();
            res = isAppCacheUpToDate();
            if (res)
                unlockAppCache();
        }
        return res;
    }

    /**
     * Tests if the app cache is up to date.
     *
     * The app cache directory is obtained by calling {@link #getAppCache() getAppCache}.
     * This creates a file in the app cache, whose timestamp is compared with the capsule's JAR timestamp.
     */
    protected boolean isAppCacheUpToDate() throws IOException {
        if (systemPropertyEmptyOrTrue(PROP_RESET))
            return false;

        Path extractedFile = appCache.resolve(TIMESTAMP_FILE_NAME);
        if (!Files.exists(extractedFile))
            return false;
        FileTime extractedTime = Files.getLastModifiedTime(extractedFile);
        FileTime jarTime = Files.getLastModifiedTime(jarFile);
        return extractedTime.compareTo(jarTime) >= 0;
    }

    /**
     * Extracts the capsule's contents into the app cache directory.
     * This method may be overridden to write additional files to the app cache.
     */
    protected void extractCapsule() throws IOException {
        try {
            log(LOG_VERBOSE, "Extracting " + jarFile + " to app cache directory " + appCache.toAbsolutePath());
            extractJar(openJarInputStream(jarFile), appCache);
        } catch (IOException e) {
            throw new IOException("Exception while extracting jar " + jarFile + " to app cache directory " + appCache.toAbsolutePath(), e);
        }
    }

    private void markCache(boolean success) throws IOException {
        if (appCache == null || cacheUpToDate)
            return;
        if (success)
            Files.createFile(appCache.resolve(TIMESTAMP_FILE_NAME));
        unlockAppCache();
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

    //<editor-fold defaultstate="collapsed" desc="Script Process">
    /////////// Script Process ///////////////////////////////////
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

        setJavaHomeEnv(pb, getJavaHome());

        final List<Path> classPath = buildClassPath();
        resolveNativeDependencies();
        pb.environment().put(VAR_CLASSPATH, compileClassPath(classPath));

        final Path scriptPath = sanitize(appCache, script).toAbsolutePath();
        ensureExecutable(scriptPath);
        pb.command().add(scriptPath.toString());
        return true;
    }

    private Path setJavaHomeEnv(ProcessBuilder pb, Path javaHome) {
        if (javaHome == null)
            return null;
        pb.environment().put(VAR_JAVA_HOME, javaHome.toString());
        return javaHome;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Java Process">
    /////////// Java Process ///////////////////////////////////
    private boolean buildJavaProcess(ProcessBuilder pb, List<String> cmdLine, List<String> args) {
        final List<String> command = pb.command();

        command.add(getJavaExecutable().toString());

        command.addAll(buildJVMArgs(cmdLine));
        command.addAll(compileSystemProperties(buildSystemProperties(cmdLine)));

        addOption(command, "-Xbootclasspath:", compileClassPath(buildBootClassPath(cmdLine)));
        addOption(command, "-Xbootclasspath/p:", compileClassPath(buildBootClassPathP()));
        addOption(command, "-Xbootclasspath/a:", compileClassPath(buildBootClassPathA()));

        for (String jagent : nullToEmpty(buildJavaAgents()))
            command.add("-javaagent:" + jagent);

        final List<Path> classPath = buildClassPath();
        final String mainClass = getMainClass(classPath);

        command.add("-classpath");
        command.add(compileClassPath(handleLongClasspath(classPath, mainClass.length(), command, args)));

        command.add(mainClass);
        return true;
    }

    private List<Path> handleLongClasspath(List<Path> cp, int extra, List<?>... args) {
        if (!isWindows())
            return cp; // why work hard if we know the problem only exists on Windows?

        long len = extra + getStringsLength(cp) + cp.size();
        for (List<?> list : args)
            len += getStringsLength(list) + list.size();

        if (len >= getMaxCommandLineLength()) {
            log(LOG_DEBUG, "Command line length: " + len);
            if (isTrampoline())
                throw new RuntimeException("Command line too long and trampoline requested.");
            this.pathingJar = createPathingJar(Paths.get(systemProperty(PROP_TMP_DIR)), cp);
            log(LOG_VERBOSE, "Writing classpath: " + cp + " to pathing JAR: " + pathingJar);
            return singletonList(pathingJar);
        } else
            return cp;
    }

    /**
     * Returns the path to the executable that will be used to launch Java.
     * The default implementation uses the {@code capsule.java.cmd} property or the {@code JAVACMD} environment variable,
     * and if not set, returns the value of {@code getJavaExecutable(getJavaHome())}.
     */
    protected Path getJavaExecutable() {
        String javaCmd = emptyToNull(systemProperty(PROP_CAPSULE_JAVA_CMD));
        if (javaCmd != null)
            return path(javaCmd);

        return getJavaExecutable(getJavaHome());
    }

    /**
     * Finds the path to the executable that will be used to launch Java within the given {@code javaHome}.
     */
    protected static final Path getJavaExecutable(Path javaHome) {
        final Path exec = getJavaExecutable0(javaHome);
        assert exec.startsWith(javaHome);
        return exec;
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

        // the capsule jar
        final String isCapsuleInClassPath = getAttribute(ATTR_CAPSULE_IN_CLASS_PATH);
        if (isCapsuleInClassPath == null || Boolean.parseBoolean(isCapsuleInClassPath))
            classPath.add(jarFile);
        else if (appCache == null)
            throw new IllegalStateException("Cannot set the " + ATTR_CAPSULE_IN_CLASS_PATH + " attribute to false when the "
                    + ATTR_EXTRACT + " attribute is also set to false");

        // the app artifact (mustn't be a capsule if we're here)
        if (hasAttribute(ATTR_APP_ARTIFACT)) {
            if (isGlob(getAttribute(ATTR_APP_ARTIFACT)))
                throw new IllegalArgumentException("Glob pattern not allowed in " + ATTR_APP_ARTIFACT + " attribute.");
            classPath.addAll(getPath(getAttribute(ATTR_APP_ARTIFACT)));
        }

        if (hasAttribute(ATTR_APP_CLASS_PATH)) {
            for (String sp : getListAttribute(ATTR_APP_CLASS_PATH)) {
                if (isDependency(sp))
                    throw new IllegalArgumentException("Dependency " + sp + " is not allowed in the " + ATTR_APP_CLASS_PATH + " attribute");
                addAllIfNotContained(classPath, getPath(sp));
            }
        }

        if (appCache != null)
            addAllIfNotContained(classPath, nullToEmpty(getDefaultCacheClassPath()));

        classPath.addAll(nullToEmpty(resolveDependencies(getDependencies(), "jar")));

        return classPath;
    }

    private List<Path> getDefaultCacheClassPath() {
        final List<Path> cp = new ArrayList<Path>(listDir(appCache, "*.jar", true));

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

        return (deps != null && !deps.isEmpty()) ? unmodifiableList(deps) : null;
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
        final List<Path> libraryPath = new ArrayList<Path>(toPath(Arrays.asList(systemProperty(PROP_JAVA_LIBRARY_PATH).split(PATH_SEPARATOR))));

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
        splitDepsAndRename(depsAndRename, deps, renames);

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
                    final String rename = renames.get(i);
                    Files.copy(lib, sanitize(appCache, rename != null ? rename : lib.getFileName().toString()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception while copying native libs", e);
            }
        }
    }

    private void splitDepsAndRename(List<String> depsAndRename, List<String> deps, List<String> renames) {
        for (String depAndRename : depsAndRename) {
            String[] dna = depAndRename.split(",");
            deps.add(dna[0]);
            if (renames != null)
                renames.add(dna.length > 1 ? dna[1] : null);
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

        for (String option : nullToEmpty(Capsule.split(systemProperty(PROP_JVM_ARGS), " ")))
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
            return a.substring(0, a.indexOf('='));
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
                final Path agentPath = first(getPath(agent.getKey()));
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
                mainClass = getMainClass(getAppArtifactJarFromClasspath(classPath));
            if (mainClass == null)
                throw new RuntimeException("Jar " + classPath.get(0).toAbsolutePath() + " does not have a main class defined in the manifest.");
            return mainClass;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getAppArtifactJarFromClasspath(List<Path> classPath) {
        return classPath.get(0).equals(jarFile) ? classPath.get(1) : classPath.get(0);
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
            this.javaHome_ = jhome != null ? jhome : Paths.get(systemProperty(PROP_JAVA_HOME));
            log(LOG_VERBOSE, "Using JVM: " + javaHome_);

        }
        return javaHome_;
    }

    /**
     * Chooses which Java installation to use for running the app.
     *
     * @return the path of the Java installation to use for launching the app, or {@code null} if the current JVM is to be used.
     */
    protected Path chooseJavaHome() {
        Path jhome = emptyToNull(systemProperty(PROP_CAPSULE_JAVA_HOME)) != null ? Paths.get(systemProperty(PROP_CAPSULE_JAVA_HOME)) : null;
        if (jhome == null && !isMatchingJavaVersion(systemProperty(PROP_JAVA_VERSION))) {
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
        try {
            final InputStream is = getEntry(zis, POM_FILE);
            if (is == null)
                return null;
            return createPomReader0(is);
        } catch (IOException e) {
            throw new IOException("Could not read " + POM_FILE, e);
        }
    }

    private Object createPomReader0(InputStream is) {
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
    private List<String> getRepositories() {
        final List<String> repos = new ArrayList<String>();

        final List<String> envRepos = Capsule.split(getenv(ENV_CAPSULE_REPOS), "[,\\s]\\s*");
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

        return !repos.isEmpty() ? unmodifiableList(repos) : null;
    }

    private Object tryCreateDependencyManager() {
        if (systemPropertyEmptyOrTrue(PROP_NO_DEP_MANAGER))
            return null;
        try {
            final boolean reset = systemPropertyEmptyOrTrue(PROP_RESET);
            final Path localRepo = getLocalRepo();
            return new DependencyManagerImpl(localRepo.toAbsolutePath(), reset, logLevel);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    private void verifyDependencyManager() {
        if (dependencyManager == null)
            throw new RuntimeException("Capsule " + jarFile + " uses dependencies, while the necessary dependency management classes are not found in the capsule JAR");
    }

    private void setDependencyRepositories(List<String> repositories) {
        verifyDependencyManager();
        final boolean allowSnapshots = hasAttribute(ATTR_ALLOW_SNAPSHOTS) && Boolean.parseBoolean(getAttribute(ATTR_ALLOW_SNAPSHOTS));
        ((DependencyManager) dependencyManager).setRepos(repositories, allowSnapshots);
    }

    private Path getLocalRepo() {
        Path localRepo = cacheDir.resolve(DEPS_CACHE_NAME);
        final String local = expandCommandLinePath(propertyOrEnv(PROP_USE_LOCAL_REPO, ENV_CAPSULE_LOCAL_REPO));
        if (local != null)
            localRepo = !local.isEmpty() ? Paths.get(local) : null;
        return localRepo;
    }

    private void printDependencyTree(String root, String type) {
        verifyDependencyManager();
        ((DependencyManager) dependencyManager).printDependencyTree(root, type, System.out);
    }

    private void printDependencyTree(List<String> dependencies, String type) {
        if (dependencies == null)
            return;
        verifyDependencyManager();
        ((DependencyManager) dependencyManager).printDependencyTree(dependencies, type, System.out);
    }

    private List<Path> resolveDependencies(List<String> dependencies, String type) {
        if (dependencies == null)
            return null;
        verifyDependencyManager();
        return ((DependencyManager) dependencyManager).resolveDependencies(dependencies, type);
    }

    private List<Path> resolveDependency(String coords, String type) {
        if (coords == null)
            return null;
        verifyDependencyManager();
        return ((DependencyManager) dependencyManager).resolveDependency(coords, type);
    }

    private String getAppArtifactSpecificVersion(String appArtifact) {
        return getArtifactLatestVersion(appArtifact, "jar");
    }

    private String getArtifactLatestVersion(String coords, String type) {
        if (coords == null)
            return null;
        verifyDependencyManager();
        return ((DependencyManager) dependencyManager).getLatestVersion(coords, type);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Attributes">
    /////////// Attributes ///////////////////////////////////
    /*
     * The methods in this section are the only ones accessing the manifest. Therefore other means of
     * setting attributes can be added by changing these methods alone.
     */
    private void validateManifest() {
        if (manifest.getMainAttributes().getValue(ATTR_CLASS_PATH) != null)
            throw new IllegalStateException("Capsule manifest contains a " + ATTR_CLASS_PATH + " attribute."
                    + " Use " + ATTR_APP_CLASS_PATH + " and/or " + ATTR_DEPENDENCIES + " instead.");
        validateNonModalAttributes();
    }

    private void validateNonModalAttributes() {
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
        return unmodifiableSet(manifest.getEntries().keySet());
    }

    /**
     * Returns the description of the given mode.
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
        setContext("attribute", attr, value);
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
        return manifest.getMainAttributes().containsKey(key);
    }

    /**
     * Returns the value of the given attribute (with consideration to the capsule's mode) as a list.
     * The items comprising attribute's value must be whitespace-separated.
     * <p>
     * Same as {@code parse(getAttribute(attr))}.
     *
     * @param attr the attribute
     */
    protected final List<String> getListAttribute(String attr) {
        return parse(getAttribute(attr));
    }

    /**
     * Returns the value of the given attribute (with consideration to the capsule's mode) as a map.
     * The key-value pairs comprising attribute's value must be whitespace-separated, with each pair written as <i>key</i>=<i>value</i>.
     * <p>
     * Same as {@code parse(getAttribute(attr), defaultValue)}.
     *
     * @param attr         the attribute
     * @param defaultValue a default value to use for keys without a value, or {@code null} if such an event should throw an exception
     */
    protected final Map<String, String> getMapAttribute(String attr, String defaultValue) {
        return parse(getAttribute(attr), defaultValue);
    }

    /**
     * Parses an attribute's value string into a list.
     * The items comprising attribute's value must be whitespace-separated.
     *
     * @param value the attribute's value
     */
    protected static final List<String> parse(String value) {
        return split(value, "\\s+");
    }

    /**
     * Parses an attribute's value string into a map.
     * The key-value pairs comprising string must be whitespace-separated, with each pair written as <i>key</i>=<i>value</i>.
     *
     * @param value        the attribute's value
     * @param defaultValue a default value to use for keys without a value, or {@code null} if such an event should throw an exception
     */
    protected static final Map<String, String> parse(String value, String defaultValue) {
        return split(value, '=', "\\s+", defaultValue);
    }

    /**
     * Combines collection elements into a string that can be used as the value of an attribute.
     */
    protected static final String toStringValue(Collection<String> list) {
        return join(list, " ");
    }

    /**
     * Combines map elements into a string that can be used as the value of an attribute.
     */
    protected static final String toStringValue(Map<String, String> map) {
        return join(map, '=', " ");
    }

    private void merge(Manifest man1, Manifest man2) {
        if (man2 == null)
            return;
        merge(man1.getMainAttributes(), man2.getMainAttributes());
        for (Map.Entry<String, Attributes> entry : man2.getEntries().entrySet()) {
            final Attributes attr = man1.getAttributes(entry.getKey());
            if (attr != null)
                merge(attr, entry.getValue());
            else
                man1.getEntries().put(entry.getKey(), entry.getValue());
        }
    }

    private void merge(Attributes a1, Attributes a2) {
        for (Map.Entry<Object, Object> entry : a2.entrySet())
            a1.put(entry.getKey(), merge(entry.getKey().toString(), (String) a1.get(entry.getKey()), (String) entry.getValue()));
    }

    /**
     * When using an empty capsule to launch another, this method will be called for each attribute in the wrapped (non-empty) manifest to merge
     * the wrapped capsule's attribute with the wrapper (empty) capsule's attribute.
     * When overriding this method, it may be useful to make use of {@link #parse(String) parse}, {@link #parse(String, String) parse map},
     * {@link #toStringValue(Collection) toStringValue}, and {@link #toStringValue(Map) map toStringValue}.
     * <p>
     * The default implementation simply returns {@code value1}, unless it is {@code null} in which case it will pick {@code value2};
     * in other words, it gives preference to the wrapper capsule's attributes.
     *
     * @param attribute the attribute's name
     * @param value1    the attribute's value in the wrapper (empty) capsule
     * @param value2    the attribute's value in the wrapped capsule
     * @return the value which will be used when launching the capsule
     */
    protected String merge(String attribute, String value1, String value2) {
        return (value1 != null) ? value1 : value2;
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
    /**
     * Returns the path or paths to the given file descriptor.
     * The given descriptor can be a dependency, a file name (relative to the app cache)
     * or a glob pattern (again, relative to the app cache). The returned list can contain more than one element
     * if a dependency is given and it resolves to more than a single artifact, or if a glob pattern is given,
     * which matches more than one file.
     */
    private List<Path> getPath(String p) {
        if (p == null)
            return null;
        if (isDependency(p) && dependencyManager != null) {
            final List<Path> res = resolveDependency(p, "jar");
            if (res == null || res.isEmpty())
                throw new RuntimeException("Dependency " + p + " was not found.");
            return res;
        }

        if (appCache == null)
            throw new IllegalStateException(
                    (isDependency(p) ? "Dependency manager not found. Cannot resolve" : "Capsule not extracted. Cannot obtain path") + " " + p);
        if (isDependency(p)) {
            Path f = appCache.resolve(dependencyToLocalJar(true, p));
            if (Files.isRegularFile(f))
                return singletonList(f);
            f = appCache.resolve(dependencyToLocalJar(false, p));
            if (Files.isRegularFile(f))
                return singletonList(f);
            throw new IllegalArgumentException("Dependency manager not found, and could not locate artifact " + p + " in capsule");
        } else if (isGlob(p)) {
            final List<Path> files = listDir(appCache, p, false);
            Collections.sort(files); // see comment in getDefaultCacheClassPath()
            return files;
        } else
            return singletonList(toAbsolutePath(appCache, p));
    }

    /**
     * Returns a list which is a concatenation of all lists returned by calling
     * {@link #getPath(String) getPath(String)} on each of the file descriptors in the given list.
     */
    private List<Path> getPath(List<String> ps) {
        if (ps == null)
            return null;
        final List<Path> res = new ArrayList<Path>(ps.size());
        for (String p : ps)
            res.addAll(getPath(p));
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
        return sanitize(root, p).toAbsolutePath();
    }

    private static Path sanitize(Path dir, String p) {
        final Path path = dir.resolve(p).normalize().toAbsolutePath();
        if (!path.startsWith(dir))
            throw new IllegalArgumentException("Path " + p + " is not local");
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

    private static long getMaxCommandLineLength() {
        if (isWindows())
            return WINDOWS_MAX_CMD;
        return Long.MAX_VALUE;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="JAR Utils">
    /////////// JAR Utils ///////////////////////////////////
    private static JarInputStream openJarInputStream(Path jar) throws IOException {
        return new JarInputStream(skipToZipStart(Files.newInputStream(jar)));
    }

    private static String getMainClass(Path jar) throws IOException {
        return getMainClass(getManifest(jar));
    }

    private static String getMainClass(Manifest manifest) {
        if (manifest == null)
            return null;
        return manifest.getMainAttributes().getValue(ATTR_MAIN_CLASS);
    }

    private static Manifest getManifest(Path jar) throws IOException {
        try (JarInputStream jis = openJarInputStream(jar)) {
            return jis.getManifest();
        }
    }

    private static final int[] ZIP_HEADER = new int[]{'\n', 'P', 'K', 0x03, 0x04};

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
                if (b == ZIP_HEADER[state])
                    state++;
            }
        }
        is.reset();
        return is;
    }

    /**
     * @deprecated marked deprecated to exclude from javadoc. Visible for testing
     */
    static Path createPathingJar(Path dir, List<Path> cp) {
        try {
            dir = dir.toAbsolutePath();
            final List<Path> paths = new ArrayList<>(cp.size());
            for (Path p : cp) // In order to use the Class-Path attribute, we must either relativize the paths, or specifiy them as file URLs
                paths.add(dir.relativize(p));

            final Path pathingJar = Files.createTempFile(dir, "capsule_pathing_jar", ".jar");
            final Manifest man = new Manifest();
            man.getMainAttributes().putValue(ATTR_MANIFEST_VERSION, "1.0");
            man.getMainAttributes().putValue(ATTR_CLASS_PATH, join(paths, " "));
            new JarOutputStream(Files.newOutputStream(pathingJar), man).close();

            return pathingJar;
        } catch (IOException e) {
            throw new RuntimeException("Pathing JAR creation failed", e);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="File Utils">
    /////////// File Utils ///////////////////////////////////
    private static void writeFile(Path targetDir, String fileName, InputStream is) throws IOException {
        fileName = toNativePath(fileName);
        final String dir = getDirectory(fileName);
        if (dir != null)
            Files.createDirectories(targetDir.resolve(dir));

        final Path targetFile = targetDir.resolve(fileName);
        Files.copy(is, targetFile);
    }

    private static String toNativePath(String filename) {
        final char ps = (!filename.contains("/") && filename.contains("\\")) ? '\\' : '/';
        return ps != FILE_SEPARATOR_CHAR ? filename.replace(ps, FILE_SEPARATOR_CHAR) : filename;
    }

    private static String getDirectory(String filename) {
        final int index = filename.lastIndexOf(FILE_SEPARATOR_CHAR);
        if (index < 0)
            return null;
        return filename.substring(0, index);
    }

    // visible for testing
    static void delete(Path file) throws IOException {
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

    /**
     * Returns the contents of a directory. <br>
     * Passing {@code null} as the glob pattern is the same as passing {@code "*"}
     *
     * @param dir     the directory
     * @param glob    the glob pattern to use to filter the entries, or {@code null} if all entries are to be returned
     * @param regular whether only regular files should be returned
     */
    protected static final List<Path> listDir(Path dir, String glob, boolean regular) {
        return listDir(dir, glob, false, regular, false, new ArrayList<Path>());
    }

    /**
     * Finds the first file matching the glob pattern in the given directory (or subdirectories, according to the glob).
     *
     * @param dir     the directory
     * @param glob    the glob pattern to use to filter the entries
     * @param regular whether only regular files should be returned
     * @return the path to the matching file or {@code null} if no matching file is found.
     */
    protected static final Path findFile(Path dir, String glob, boolean regular) {
        final List<Path> list = listDir(dir, glob, false, regular, true, new ArrayList<Path>());
        return !list.isEmpty() ? list.get(0) : null;
    }

    private static boolean isGlob(String s) {
        return s.contains("*") || s.contains("?") || s.contains("{") || s.contains("[");
    }

    private static List<Path> listDir(Path dir, String glob, boolean recursive, boolean regularFile, boolean firstMatch, List<Path> res) {
        return listDir(dir, splitGlob(glob), recursive, regularFile, firstMatch, res);
    }

    private static List<String> splitGlob(String glob) { // splits glob pattern by directory
        return glob != null ? Arrays.asList(glob.split(FILE_SEPARATOR_CHAR == '\\' ? "\\\\" : FILE_SEPARATOR)) : null;
    }

    private static List<Path> listDir(Path dir, List<String> globs, boolean recursive, boolean regularFile, boolean firstMatch, List<Path> res) {
        PathMatcher matcher = null;
        if (globs != null && !globs.isEmpty()) {
            if (globs.size() == 1 && "**".equals(globs.get(0)))
                recursive = true;
            else
                matcher = dir.getFileSystem().getPathMatcher("glob:" + globs.get(0));
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f : ds) {
                if (recursive && Files.isDirectory(f))
                    listDir(f, globs, recursive, regularFile, firstMatch, res);
                if (matcher == null) {
                    if (!regularFile || Files.isRegularFile(f))
                        res.add(f);
                } else {
                    if (matcher.matches(f.getFileName())) {
                        assert globs != null;
                        if (globs.size() == 1 && (!regularFile || Files.isRegularFile(f)))
                            res.add(f);
                        else if (Files.isDirectory(f))
                            listDir(f, globs.subList(1, globs.size()), recursive, regularFile, firstMatch, res);
                    }
                }
                if (firstMatch && !res.isEmpty())
                    break;
            }
            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        for (Path f : listDir(dir, null, false)) {
            if (Files.isDirectory(f)) {
                String dirName = f.getFileName().toString();
                String ver = isJavaDir(dirName);
                if (ver != null) {
                    Path home = searchJavaHomeInDir(f);
                    if (home != null) {
                        home = home.toAbsolutePath();
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
        for (Path f : listDir(dir, null, false)) {
            if (isJavaHome(f))
                return f;
            Path home = searchJavaHomeInDir(f);
            if (home != null)
                return home;
        }
        return null;
    }

    private static boolean isJavaHome(Path dir) {
        if (Files.isDirectory(dir))
            return findFile(dir, "bin" + FILE_SEPARATOR + "{java|java.exe}", true) != null;
        return false;
    }

    private static Path getJavaExecutable0(Path javaHome) {
        final String exec = (isWindows() && System.console() == null) ? "javaw" : "java";
        return javaHome.resolve("bin").resolve(exec + (isWindows() ? ".exe" : ""));
    }

    private static final Pattern PAT_JAVA_VERSION_LINE = Pattern.compile(".*?\"(.+?)\"");

    private static String getActualJavaVersion(Path javaHome) {
        try {
            final String versionLine = exec(1, getJavaExecutable0(javaHome).toString(), "-version").get(0);
            final Matcher m = PAT_JAVA_VERSION_LINE.matcher(versionLine);
            if (!m.matches())
                throw new IllegalArgumentException("Could not parse version line: " + versionLine);
            final String version = m.group(1);

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

    /**
     * Compares two dotted software versions, regarding only the first several version components.
     *
     * @param a first version
     * @param b second version
     * @param n the number of (most significant) components to consider
     * @return {@code 0} if {@code a == b}; {@code > 0} if {@code a > b}; {@code < 0} if {@code a < b};
     */
    protected static final int compareVersions(String a, String b, int n) {
        return compareVersions(parseJavaVersion(a), parseJavaVersion(b), n);
    }

    /**
     * Compares two dotted software versions.
     *
     * @param a first version
     * @param b second version
     * @return {@code 0} if {@code a == b}; {@code > 0} if {@code a > b}; {@code < 0} if {@code a < b};
     */
    protected static final int compareVersions(String a, String b) {
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
        final String jhome = toString(getJavaHome());
        if (jhome != null)
            str = str.replaceAll("\\$" + VAR_JAVA_HOME, jhome);
        str = str.replace('/', FILE_SEPARATOR_CHAR);
        return str;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="String Utils">
    /////////// String Utils ///////////////////////////////////
    private static String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    static List<String> split(String str, String separator) {
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

    static Map<String, String> split(String map, char kvSeparator, String separator, String defaultValue) {
        if (map == null)
            return null;
        Map<String, String> m = new HashMap<>();
        for (String entry : Capsule.split(map, separator)) {
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

    final static String join(Collection<?> coll, String separator) {
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

    final static String join(Map<String, String> map, char kvSeparator, String separator) {
        if (map == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet())
            sb.append(entry.getKey()).append(kvSeparator).append(entry.getValue()).append(separator);
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

    private static long getStringsLength(Collection<?> coll) {
        if (coll == null)
            return 0;
        long len = 0;
        for (Object o : coll)
            len += o.toString().length();
        return len;
    }

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
            return emptyList();
        return list;
    }

    private static <K, V> Map<K, V> nullToEmpty(Map<K, V> map) {
        if (map == null)
            return emptyMap();
        return map;
    }

    private static <T> T first(List<T> c) {
        if (c == null || c.isEmpty())
            throw new IllegalArgumentException("Not found");
        return c.get(0);
    }

    private static <T> T firstOrNull(List<T> c) {
        if (c == null || c.isEmpty())
            return null;
        return c.get(0);
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
        String val = systemProperty(propName);
        if (val == null)
            val = emptyToNull(getenv(envVar));
        return val;
    }

    /**
     * Same as {@link System#getProperty(java.lang.String) System.getProperty(propName)} but sets context for error reporting.
     */
    protected static final String systemProperty(String propName) {
        final String val = System.getProperty(propName);
        setContext("system property", propName, val);
        return val;
    }

    /**
     * Same as {@link System#getenv(java.lang.String) System.getenv(envName)} but sets context for error reporting.
     */
    private static String getenv(String envName) {
        final String val = System.getenv(envName);
        setContext("environment variable", envName, val);
        return val;
    }

    private static boolean systemPropertyEmptyOrTrue(String property) {
        final String value = System.getProperty(property);
        if (value == null)
            return false;
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    /**
     * Executes a command and returns its output as a list of lines.
     * The method will wait for the child process to terminate, and throw an exception if the command returns an exit value {@code != 0}.
     * <br>
     * Same as calling {@code exec(-1, cmd}}.
     *
     * @param cmd the command
     * @return the lines output by the command
     */
    protected static List<String> exec(String... cmd) throws IOException {
        return exec(-1, cmd);
    }

    /**
     * Executes a command and returns its output as a list of lines.
     * If the number of lines read is less than {@code numLines}, or if {@code numLines < 0}, then the method will wait for the child process
     * to terminate, and throw an exception if the command returns an exit value {@code != 0}.
     *
     * @param numLines the maximum number of lines to read, or {@code -1} for an unbounded number
     * @param cmd      the command
     * @return the lines output by the command
     */
    protected static List<String> exec(int numLines, String... cmd) throws IOException {
        return exec(numLines, new ProcessBuilder(Arrays.asList(cmd)));
    }

    /**
     * Executes a command and returns its output as a list of lines.
     * The method will wait for the child process to terminate, and throw an exception if the command returns an exit value {@code != 0}.
     * <br>
     * Same as calling {@code exec(-1, pb}}.
     *
     * @param pb the {@link ProcessBuilder} that will be used to launch the command
     * @return the lines output by the command
     */
    protected static List<String> exec(ProcessBuilder pb) throws IOException {
        return exec(-1, pb);
    }

    /**
     * Executes a command and returns its output as a list of lines.
     * If the number of lines read is less than {@code numLines}, or if {@code numLines < 0}, then the method will wait for the child process
     * to terminate, and throw an exception if the command returns an exit value {@code != 0}.
     *
     * @param numLines the maximum number of lines to read, or {@code -1} for an unbounded number
     * @param pb       the {@link ProcessBuilder} that will be used to launch the command
     * @return the lines output by the command
     */
    protected static List<String> exec(int numLines, ProcessBuilder pb) throws IOException {
        final List<String> lines = new ArrayList<>();
        final Process p = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream(), Charset.defaultCharset()))) {
            for (int i = 0; numLines < 0 || i < numLines; i++) {
                final String line = reader.readLine();
                if (line == null)
                    break;
                lines.add(line);
            }
        }
        try {
            if (numLines < 0 || lines.size() < numLines) {
                final int exitValue = p.waitFor();
                if (exitValue != 0)
                    throw new RuntimeException("Command '" + join(pb.command(), " ") + "' has returned " + exitValue);
            }
            return lines;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Logging">
    /////////// Logging ///////////////////////////////////
    /**
     * Chooses and returns the capsules log level.
     */
    protected int chooseLogLevel() {
        String level = System.getProperty(PROP_LOG_LEVEL);
        if (level == null && manifest != null)
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
     * Tests if the given log level is currently being logged
     */
    protected final boolean isLogging(int level) {
        return level <= logLevel;
    }

    /**
     * Prints a message to stderr if the given log-level is being logged.
     */
    protected final void log(int level, String str) {
        if (isLogging(level))
            System.err.println(LOG_PREFIX + str);
    }

    private void println(String str) {
        log(LOG_QUIET, str);
    }

    private static boolean hasContext() {
        return contextType_ != null;
    }

    private static void clearContext() {
        setContext(null, null, null);
    }

    private static void setContext(String type, String key, String value) {
        contextType_ = type;
        contextKey_ = key;
        contextValue_ = value;
    }

    private static String reportContext() {
        return contextType_ + " " + contextKey_ + ": " + contextValue_;
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

    private boolean pipeIoStream() {
        switch (Thread.currentThread().getName()) {
            case "pipe-out":
                pipe(child.getInputStream(), System.out);
                return true;
            case "pipe-err":
                pipe(child.getErrorStream(), System.err);
                return true;
            case "pipe-in":
                pipe(System.in, child.getOutputStream());
                return true;
            default:
                return false;
        }
    }

    private void pipe(InputStream in, OutputStream out) {
        try (OutputStream out1 = out) {
            final byte[] buf = new byte[1024];
            int read;
            while (-1 != (read = in.read(buf))) {
                out.write(buf, 0, read);
                out.flush();
            }
        } catch (Throwable e) {
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
    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    protected final Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
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
            sb.append(", ").append(getAttribute(ATTR_APP_CLASS) != null ? getAttribute(ATTR_APP_CLASS) : getAttribute(ATTR_APP_ARTIFACT));
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
        try {
            final String mainClassName = getMainClass(jarFile);
            if (mainClassName != null) {
                final Class<?> clazz = Capsule.class.getClassLoader().loadClass(mainClassName);
                if (isCapsuleClass(clazz)) {
                    final Constructor<?> ctor = clazz.getDeclaredConstructor(Path.class, Path.class);
                    ctor.setAccessible(true);
                    return (Capsule) ctor.newInstance(jarFile, cacheDir);
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

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof InvocationTargetException)
            t = ((InvocationTargetException) t).getTargetException();
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
    //</editor-fold>
}
