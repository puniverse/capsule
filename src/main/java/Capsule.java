/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.AetherDependencyManager;
import capsule.DependencyManager;
import capsule.PomReader;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Capsule implements Runnable, FileVisitor<Path> {
    /*
     * This class contains several strange hacks to avoid creating more classes. 
     * We'd like this file to compile to a single .class file.
     *
     * Also, the code here is not meant to be the most efficient, but methods should be as independent and stateless as possible.
     * Other than those few, methods called in the constructor, all others are can be called in any order, and don't rely on any state.
     *
     * We do a lot of data transformations that would have really benefitted from Java 8's lambdas and streams, 
     * but we want Capsule to support Java 7.
     */
    private static final String VERSION = "0.6.0";

    private static final String PROP_RESET = "capsule.reset";
    private static final String PROP_VERSION = "capsule.version";
    private static final String PROP_LOG = "capsule.log";
    private static final String PROP_TREE = "capsule.tree";
    private static final String PROP_APP_ID = "capsule.app.id";
    private static final String PROP_PRINT_JRES = "capsule.jvms";
    private static final String PROP_CAPSULE_JAVA_HOME = "capsule.java.home";
    private static final String PROP_MODE = "capsule.mode";
    private static final String PROP_USE_LOCAL_REPO = "capsule.local";
    private static final String PROP_OFFLINE = "capsule.offline";
    private static final String PROP_RESOLVE = "capsule.resolve";

    private static final String PROP_JAVA_VERSION = "java.version";
    private static final String PROP_JAVA_HOME = "java.home";
    private static final String PROP_OS_NAME = "os.name";
    private static final String PROP_USER_HOME = "user.home";
    private static final String PROP_JAVA_LIBRARY_PATH = "java.library.path";
    private static final String PROP_FILE_SEPARATOR = "file.separator";
    private static final String PROP_PATH_SEPARATOR = "path.separator";
    private static final String PROP_JAVA_SECURITY_POLICY = "java.security.policy";
    private static final String PROP_JAVA_SECURITY_MANAGER = "java.security.manager";

    private static final String ENV_CAPSULE_REPOS = "CAPSULE_REPOS";

    private static final String ATTR_APP_NAME = "Application-Name";
    private static final String ATTR_APP_VERSION = "Application-Version";
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
    private static final String ATTR_DEPENDENCIES = "Dependencies";
    private static final String ATTR_NATIVE_DEPENDENCIES_LINUX = "Native-Dependencies-Linux";
    private static final String ATTR_NATIVE_DEPENDENCIES_WIN = "Native-Dependencies-Win";
    private static final String ATTR_NATIVE_DEPENDENCIES_MAC = "Native-Dependencies-Mac";
    private static final String ATTR_IMPLEMENTATION_VERSION = "Implementation-Version";

    // outgoing
    private static final String VAR_CAPSULE_DIR = "CAPSULE_DIR";
    private static final String VAR_CAPSULE_JAR = "CAPSULE_JAR";
    private static final String VAR_JAVA_HOME = "JAVA_HOME";

    private static final String ENV_CACHE_DIR = "CAPSULE_CACHE_DIR";
    private static final String ENV_CACHE_NAME = "CAPSULE_CACHE_NAME";

    private static final String PROP_CAPSULE_JAR = "capsule.jar";
    private static final String PROP_CAPSULE_DIR = "capsule.dir";
    private static final String PROP_CAPSULE_APP = "capsule.app";

    // misc
    private static final String CACHE_DEFAULT_NAME = "capsule";
    private static final String DEPS_CACHE_NAME = "deps";
    private static final String APP_CACHE_NAME = "apps";
    private static final String POM_FILE = "pom.xml";
    private static final String DOT = "\\.";
    private static final String CUSTOM_CAPSULE_CLASS_NAME = "CustomCapsule";
    private static final String FILE_SEPARATOR = System.getProperty(PROP_FILE_SEPARATOR);
    private static final String PATH_SEPARATOR = System.getProperty(PROP_PATH_SEPARATOR);
    private static final Path DEFAULT_LOCAL_MAVEN = Paths.get(System.getProperty(PROP_USER_HOME), ".m2", "repository");

    //////////////////////////////////////////////////////////////////////////////////////////////////////////
    private static final boolean debug = "debug".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final boolean verbose = debug || "verbose".equals(System.getProperty(PROP_LOG, "quiet"));
    private final Path cacheDir;
    private final JarFile jar;       // null only in tests
    private final byte[] jarBuffer;  // non-null only in tests
    private final Path jarFile;      // never null
    private final Manifest manifest; // never null
    private final String javaHome;
    private final String appId;      // null iff isEmptyCapsule()
    private final Path appCache;     // non-null iff capsule is extracted
    private final boolean cacheUpToDate;
    private final String mode;
    private final Object pom;               // non-null iff jar has pom AND manifest doesn't have ATTR_DEPENDENCIES 
    private final Object dependencyManager; // non-null iff needsDependencyManager is true
    private Process child;

    /**
     * Launches the application
     *
     * @param args the program's command-line arguments
     */
    @SuppressWarnings({"BroadCatchBlock", "CallToPrintStackTrace"})
    public static final void main(String[] args) {
        try {
            final Capsule capsule = newCapsule(getJarFile());

            if (anyPropertyDefined(PROP_VERSION, PROP_PRINT_JRES, PROP_TREE, PROP_RESOLVE)) {
                if (anyPropertyDefined(PROP_VERSION))
                    capsule.printVersion(args);

                if (anyPropertyDefined(PROP_PRINT_JRES))
                    capsule.printJVMs();

                if (anyPropertyDefined(PROP_TREE))
                    capsule.printDependencyTree(args);

                if (anyPropertyDefined(PROP_RESOLVE))
                    capsule.resolve(args);
                return;
            }

            final Process p = capsule.launch(ManagementFactory.getRuntimeMXBean().getInputArguments(), args);
            if (p != null)
                System.exit(p.waitFor());
        } catch (Throwable t) {
            System.err.println("CAPSULE EXCEPTION: " + t.getMessage()
                    + (!verbose ? " (for stack trace, run with -D" + PROP_LOG + "=verbose)" : ""));
            if (verbose)
                t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Capsule newCapsule(Path jarFile) {
        try {
            final Class<?> clazz = Class.forName(CUSTOM_CAPSULE_CLASS_NAME);
            try {
                Constructor<?> ctor = clazz.getConstructor(Path.class);
                ctor.setAccessible(true);
                return (Capsule) ctor.newInstance(jarFile);
            } catch (Exception e) {
                throw new RuntimeException("Could not launch custom capsule.", e);
            }
        } catch (ClassNotFoundException e) {
            return new Capsule(jarFile);
        }
    }

    protected Capsule(Path jarFile) {
        this.jarFile = jarFile;
        try {
            this.jar = new JarFile(jarFile.toFile()); // only use of old File API
            this.jarBuffer = null;
            this.manifest = jar.getManifest();
            if (manifest == null)
                throw new RuntimeException("JAR file " + jarFile + " does not have a manifest");
        } catch (IOException e) {
            throw new RuntimeException("Could not read JAR file " + jarFile + " manifest");
        }

        this.cacheDir = initCacheDir(getCacheDir());
        this.javaHome = getJavaHome();
        this.mode = System.getProperty(PROP_MODE);
        this.pom = (!hasAttribute(ATTR_DEPENDENCIES) && hasPom()) ? createPomReader() : null;
        this.dependencyManager = needsDependencyManager() ? createDependencyManager(getRepositories()) : null;
        this.appId = getAppId();
        this.appCache = needsAppCache() ? getAppCacheDir() : null;
        this.cacheUpToDate = appCache != null ? isUpToDate() : false;
    }

    @Deprecated // Used only in tests
    Capsule(Path jarFile, String[] args, Path cacheDir, byte[] jarBuffer, Object dependencyManager) {
        this.jarFile = jarFile;
        this.jar = null;
        this.jarBuffer = jarBuffer;

        this.manifest = getJarInputStream().getManifest();
        if (manifest == null)
            throw new RuntimeException("JAR file " + jarFile + " does not have a manifest");

        this.cacheDir = initCacheDir(cacheDir);
        this.javaHome = getJavaHome();
        this.mode = System.getProperty(PROP_MODE);
        this.pom = (!hasAttribute(ATTR_DEPENDENCIES) && hasPom()) ? createPomReader() : null;
        this.dependencyManager = dependencyManager;
        this.appId = getAppId(args);
        this.appCache = needsAppCache() ? getAppCacheDir() : null;
        this.cacheUpToDate = appCache != null ? isUpToDate() : false;
    }

    protected final boolean isTest() { // so far only getEntry and extractJar run different code for tests
        return jar == null;
    }

    private JarInputStream getJarInputStream() {
        try {
            return new JarInputStream(new ByteArrayInputStream(jarBuffer));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private boolean needsDependencyManager() {
        return hasAttribute(ATTR_APP_ARTIFACT)
                || isEmptyCapsule()
                || pom != null
                || getDependencies() != null
                || getNativeDependencies() != null;
    }

    private Process launch(List<String> cmdLine, String[] args) throws IOException, InterruptedException {
        ProcessBuilder pb = launchCapsuleArtifact(cmdLine, args);
        if (pb == null)
            pb = prepareForLaunch(cmdLine, args);

        Runtime.getRuntime().addShutdownHook(new Thread(this));

        if (!isInheritIoBug())
            pb.inheritIO();
        this.child = pb.start();
        if (isInheritIoBug())
            pipeIoStreams();
        return child;
    }

    // visible for testing
    final ProcessBuilder prepareForLaunch(List<String> cmdLine, String[] args) {
        ensureExtractedIfNecessary();
        final ProcessBuilder pb = buildProcess(cmdLine, args);
        if (appCache != null && !cacheUpToDate)
            markCache();
        verbose("Launching app " + appId(args));
        return pb;
    }

    private void printVersion(String[] args) {
        System.out.println("CAPSULE: Application " + appId(args));
        System.out.println("CAPSULE: Capsule Version " + VERSION);
    }

    private void printJVMs() {
        final Map<String, Path> jres = getJavaHomes(false);
        if (jres == null)
            println("No detected Java installations");
        else {
            System.out.println("CAPSULE: Detected Java installations:");
            for (Map.Entry<String, Path> j : jres.entrySet())
                System.out.println(j.getKey() + (j.getKey().length() < 8 ? "\t\t" : "\t") + j.getValue());
        }
        System.out.println("CAPSULE: selected " + (javaHome != null ? javaHome : (System.getProperty(PROP_JAVA_HOME) + " (current)")));
    }

    private void resolve(String[] args) throws IOException, InterruptedException {
        ensureExtractedIfNecessary();
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH));
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH_P));
        getPath(getListAttribute(ATTR_BOOT_CLASS_PATH_A));
        resolveAppArtifact(getAppArtifact(args));
        resolveDependencies(getDependencies(), "jar");
        resolveNativeDependencies();
    }

    private void ensureExtractedIfNecessary() {
        if (appCache != null && !cacheUpToDate) {
            resetAppCache();
            if (shouldExtract())
                extractCapsule();
        }
    }

    private static boolean isInheritIoBug() {
        return isWindows() && compareVersions(System.getProperty(PROP_JAVA_VERSION), "1.8.0") < 0;
    }

    private void pipeIoStreams() {
        new Thread(this, "pipe-out").start();
        new Thread(this, "pipe-err").start();
        new Thread(this, "pipe-in").start();
    }

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

    private static void pipe(InputStream in, OutputStream out) {
        try {
            int read;
            byte[] buf = new byte[1024];
            while (-1 != (read = in.read(buf))) {
                out.write(buf, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            if (verbose)
                e.printStackTrace(System.err);
        } finally {
            try {
                out.close();
            } catch (IOException e2) {
                if (verbose)
                    e2.printStackTrace(System.err);
            }
        }
    }

    private boolean isEmptyCapsule() {
        return !hasAttribute(ATTR_APP_ARTIFACT) && !hasAttribute(ATTR_APP_CLASS) && getScript() == null;
    }

    private void printDependencyTree(String[] args) {
        System.out.println("Dependencies for " + appId(args));
        if (dependencyManager == null)
            System.out.println("No dependencies declared.");
        else if (hasAttribute(ATTR_APP_ARTIFACT) || isEmptyCapsule()) {
            final String appArtifact = isEmptyCapsule() ? getCommandLineArtifact(args) : getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact == null)
                throw new IllegalStateException("capsule " + jarFile + " has nothing to run");
            printDependencyTree(appArtifact);
        } else
            printDependencyTree(getDependencies(), "jar");

        final List<String> nativeDeps = getNativeDependencies();
        if (nativeDeps != null) {
            System.out.println("\nNative Dependencies:");
            printDependencyTree(nativeDeps, getNativeLibExtension());
        }
    }

    private ProcessBuilder launchCapsuleArtifact(List<String> cmdLine, String[] args) {
        if (getScript() == null) {
            String appArtifact = getAppArtifact(args);
            if (appArtifact != null) {
                try {
                    final List<Path> jars = resolveAppArtifact(appArtifact);
                    if (jars == null)
                        return null;
                    if (isCapsule(jars.get(0))) {
                        verbose("Running capsule " + jars.get(0));
                        return launchCapsule(jars.get(0),
                                cmdLine,
                                isEmptyCapsule() ? Arrays.copyOfRange(args, 1, args.length) : buildArgs(args).toArray(new String[0]));
                    } else if (isEmptyCapsule())
                        throw new IllegalArgumentException("Artifact " + appArtifact + " is not a capsule.");
                } catch (RuntimeException e) {
                    if (isEmptyCapsule())
                        throw new RuntimeException("Usage: java -jar capsule.jar CAPSULE_ARTIFACT_COORDINATES", e);
                    else
                        throw e;
                }
            }
        }
        return null;
    }

    private String getAppArtifact(String[] args) {
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

    private String getCommandLineArtifact(String[] args) {
        if (args.length > 0)
            return args[0];
        return null;
    }

    private ProcessBuilder buildProcess(List<String> cmdLine, String[] args) {
        final ProcessBuilder pb = new ProcessBuilder();
        if (!buildScriptProcess(pb))
            buildJavaProcess(pb, cmdLine);

        final List<String> command = pb.command();
        command.addAll(buildArgs(args));

        buildEnvironmentVariables(pb.environment());

        verbose(join(command, " "));

        return pb;
    }

    /**
     * Returns a list of command line arguments to pass to the application.
     *
     * @param args The command line arguments passed to the capsule at launch
     */
    protected List<String> buildArgs(String[] args) {
        final List<String> args0 = new ArrayList<String>();
        args0.addAll(nullToEmpty(expand(getListAttribute(ATTR_ARGS))));
        args0.addAll(Arrays.asList(args));
        return args0;
    }

    private boolean buildJavaProcess(ProcessBuilder pb, List<String> cmdLine) {
        if (javaHome != null)
            pb.environment().put("JAVA_HOME", javaHome);

        final List<String> command = pb.command();

        command.add(getJavaProcessName());

        command.addAll(buildJVMArgs(cmdLine));
        command.addAll(compileSystemProperties(buildSystemProperties(cmdLine)));

        addOption(command, "-Xbootclasspath:", compileClassPath(buildBootClassPath(cmdLine)));
        addOption(command, "-Xbootclasspath/p:", compileClassPath(buildClassPath(ATTR_BOOT_CLASS_PATH_P)));
        addOption(command, "-Xbootclasspath/a:", compileClassPath(buildClassPath(ATTR_BOOT_CLASS_PATH_A)));

        final List<Path> classPath = buildClassPath();
        command.add("-classpath");
        command.add(compileClassPath(classPath));

        for (String jagent : nullToEmpty(buildJavaAgents()))
            command.add("-javaagent:" + jagent);

        command.add(getMainClass(classPath));
        return true;
    }

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

        final Path scriptPath = appCache.resolve(sanitize(script)).toAbsolutePath();
        ensureExecutable(scriptPath);
        pb.command().add(scriptPath.toString());
        return true;
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

    private static void addOption(List<String> cmdLine, String prefix, String value) {
        if (value == null)
            return;
        cmdLine.add(prefix + value);
    }

    private static String compileClassPath(List<Path> cp) {
        return join(cp, PATH_SEPARATOR);
    }

    private List<Path> buildClassPath() {
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
            assert dependencyManager != null;
            classPath.addAll(nullToEmpty(resolveAppArtifact(getAttribute(ATTR_APP_ARTIFACT))));
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
            classPath.addAll(nullToEmpty(getDefaultCacheClassPath()));

        classPath.addAll(nullToEmpty(resolveDependencies(getDependencies(), "jar")));

        return classPath;
    }

    private List<Path> buildBootClassPath(List<String> cmdLine) {
        String option = null;
        for (String o : cmdLine) {
            if (o.startsWith("-Xbootclasspath:"))
                option = o.substring("-Xbootclasspath:".length());
        }
        if (option != null)
            return toPath(Arrays.asList(option.split(PATH_SEPARATOR)));
        return getPath(getListAttribute(ATTR_BOOT_CLASS_PATH));
    }

    private List<Path> buildClassPath(String attr) {
        return getPath(getListAttribute(attr));
    }

    /**
     * Returns a map of environment variables (property-value pairs).
     *
     * @param env the current environment
     */
    private void buildEnvironmentVariables(Map<String, String> env) {
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
    }

    /**
     * Returns a map of system properties (property-value pairs).
     *
     * @param cmdLine the list of JVM arguments passed to the capsule at launch
     */
    protected Map<String, String> buildSystemProperties(List<String> cmdLine) {
        final Map<String, String> systemProperties = new HashMap<String, String>();

        // attribute
        for (Map.Entry<String, String> pv : nullToEmpty(getMapAttribute(ATTR_SYSTEM_PROPERTIES, "")).entrySet())
            systemProperties.put(pv.getKey(), expand(pv.getValue()));

        // library path
        if (appCache != null) {
            final List<Path> libraryPath = buildNativeLibraryPath();
            libraryPath.add(appCache);
            systemProperties.put(PROP_JAVA_LIBRARY_PATH, compileClassPath(libraryPath));
        } else if (hasAttribute(ATTR_LIBRARY_PATH_P) || hasAttribute(ATTR_LIBRARY_PATH_A))
            throw new IllegalStateException("Cannot use the " + ATTR_LIBRARY_PATH_P + " or the " + ATTR_LIBRARY_PATH_A
                    + " attributes when the " + ATTR_EXTRACT + " attribute is set to false");

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
        systemProperties.put(PROP_CAPSULE_JAR, getJarPath());
        systemProperties.put(PROP_CAPSULE_APP, appId);

        // command line
        for (String option : cmdLine) {
            if (option.startsWith("-D"))
                addSystemProperty(option.substring(2), systemProperties);
        }

        return systemProperties;
    }

    private List<Path> buildNativeLibraryPath() {
        final List<Path> libraryPath = new ArrayList<Path>();
        resolveNativeDependencies();
        libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_P))));
        libraryPath.addAll(toPath(Arrays.asList(System.getProperty(PROP_JAVA_LIBRARY_PATH).split(PATH_SEPARATOR))));
        libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_A))));
        libraryPath.add(appCache);
        return libraryPath;
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

    private List<String> getNativeDependencies() {
        return stripNativeDependencies(getNativeDependenciesAndRename());
    }

    /**
     * Returns a list of dependencies, each in the format {@code groupId:artifactId:version[:classifier][,renameTo]}
     * (classifier and renameTo are optional)
     */
    protected List<String> getNativeDependenciesAndRename() {
        if (isWindows())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_WIN);
        if (isMac())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_MAC);
        if (isUnix())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_LINUX);
        return null;
    }

    protected final List<String> stripNativeDependencies(List<String> nativeDepsAndRename) {
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
        final List<String> depsAndRename = getNativeDependenciesAndRename();
        if (depsAndRename == null)
            return false;
        for (String depAndRename : depsAndRename) {
            if (depAndRename.contains(","))
                return true;
        }
        return false;
    }

    private void resolveNativeDependencies() {
        if (appCache == null)
            throw new IllegalStateException("Cannot set " + ATTR_EXTRACT + " to false if there are native dependencies.");

        final List<String> depsAndRename = getNativeDependenciesAndRename();
        if (depsAndRename == null || depsAndRename.isEmpty())
            return;
        final List<String> deps = new ArrayList<String>(depsAndRename.size());
        final List<String> renames = new ArrayList<String>(depsAndRename.size());
        for (String depAndRename : depsAndRename) {
            String[] dna = depAndRename.split(",");
            deps.add(dna[0]);
            renames.add(dna.length > 1 ? dna[1] : null);
        }
        verbose("Resolving native libs " + deps);
        final List<Path> resolved = resolveDependencies(deps, getNativeLibExtension());
        if (resolved.size() != deps.size())
            throw new RuntimeException("One of the native artifacts " + deps + " reolved to more than a single file");

        assert appCache != null;
        if (!cacheUpToDate) {
            if (debug)
                System.err.println("Copying native libs to " + appCache);
            try {
                for (int i = 0; i < deps.size(); i++) {
                    final Path lib = resolved.get(i);
                    final String rename = sanitize(renames.get(i));
                    Files.copy(lib, appCache.resolve(rename != null ? rename : lib.getFileName().toString()));
                }
            } catch (IOException e) {
                throw new RuntimeException("Exception while copying native libs");
            }
        }
    }

    private String getJarPath() {
        return jarFile.toAbsolutePath().getParent().toString();
    }

    private static List<String> compileSystemProperties(Map<String, String> ps) {
        final List<String> command = new ArrayList<String>();
        for (Map.Entry<String, String> entry : ps.entrySet())
            command.add("-D" + entry.getKey() + (entry.getValue() != null && !entry.getValue().isEmpty() ? "=" + entry.getValue() : ""));
        return command;
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

    /**
     * Returns a list of JVM arguments.
     *
     * @param cmdLine the list of JVM arguments passed to the capsule at launch
     */
    protected List<String> buildJVMArgs(List<String> cmdLine) {
        final Map<String, String> jvmArgs = new LinkedHashMap<String, String>();

        for (String a : nullToEmpty(getListAttribute(ATTR_JVM_ARGS))) {
            a = a.trim();
            if (!a.isEmpty() && !a.startsWith("-Xbootclasspath:") && !a.startsWith("-javaagent:"))
                addJvmArg(expand(a), jvmArgs);
        }

        for (String option : cmdLine) {
            if (!option.startsWith("-D") && !option.startsWith("-Xbootclasspath:"))
                addJvmArg(option, jvmArgs);
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
        if (a.startsWith("-Xloggc:"))
            return "-Xloggc";
        if (a.startsWith("-Xss"))
            return "-Xss";
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

    private static Path getJarFile() {
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

    private String getAppId() {
        if (isEmptyCapsule())
            return null;
        String appName = System.getProperty(PROP_APP_ID);
        if (appName == null)
            appName = getAttribute(ATTR_APP_NAME);
        if (appName == null) {
            appName = getAppArtifact(null);
            if (appName != null)
                return getAppArtifactLatestVersion(appName);
        }
        if (appName == null) {
            if (pom != null)
                return getPomAppName();
            appName = getAttribute(ATTR_APP_CLASS);
        }
        if (appName == null) {
            if (isEmptyCapsule())
                return null;
            throw new RuntimeException("Capsule jar " + jarFile + " must either have the " + ATTR_APP_NAME + " manifest attribute, "
                    + "the " + ATTR_APP_CLASS + " attribute, or contain a " + POM_FILE + " file.");
        }

        final String version = hasAttribute(ATTR_APP_VERSION) ? getAttribute(ATTR_APP_VERSION) : getAttribute(ATTR_IMPLEMENTATION_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    private String appId(String[] args) {
        if (appId != null)
            return appId;
        assert isEmptyCapsule();
        String appName = getAppArtifact(args);
        if (appName == null)
            throw new RuntimeException("No application to run");
        return getAppArtifactLatestVersion(appName);
    }

    private String getMainClass(List<Path> classPath) {
        try {
            String mainClass = getAttribute(ATTR_APP_CLASS);
            if (mainClass == null && hasAttribute(ATTR_APP_ARTIFACT))
                mainClass = getMainClass(classPath.get(0).toAbsolutePath());
            if (mainClass == null)
                throw new RuntimeException("Jar " + classPath.get(0).toAbsolutePath() + " does not have a main class defined in the manifest.");
            return mainClass;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Path> getDefaultCacheClassPath() {
        final List<Path> cp = new ArrayList<Path>();
        cp.add(appCache);
        // we don't use Files.walkFileTree because we'd like to avoid creating more classes (Capsule$1.class etc.)
        for (Path f : listDir(appCache)) {
            if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".jar")) {

                cp.add(f.toAbsolutePath());
            }
        }

        return cp;
    }

    private Path getPath(String p) {
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

    private Path path(String p, String... more) {
        return cacheDir.getFileSystem().getPath(p, more);
    }

    private List<Path> getPath(List<String> ps) {
        if (ps == null)
            return null;
        final List<Path> res = new ArrayList<Path>(ps.size());
        for (String p : ps)
            res.add(getPath(p));
        return res;
    }

    private String toJarUrl(String relPath) {
        return "jar:file:" + getJarPath() + "!/" + relPath;
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

    private String getAttribute(String attr) {
        String value = null;
        Attributes atts;
        if (mode != null) {
            atts = manifest.getAttributes(mode);
            if (atts == null)
                throw new IllegalArgumentException("Mode " + mode + " not defined in manifest");
            value = atts.getValue(attr);
        }
        if (value == null) {
            atts = manifest.getMainAttributes();
            value = atts.getValue(attr);
        }
        return value;
    }

    private boolean hasAttribute(String attr) {
        final Attributes.Name key = new Attributes.Name(attr);
        Attributes atts;
        if (mode != null) {
            atts = manifest.getAttributes(mode);
            if (atts != null && atts.containsKey(key))
                return true;
        }
        atts = manifest.getMainAttributes();
        return atts.containsKey(new Attributes.Name(attr));
    }

    private List<String> getListAttribute(String attr) {
        return split(getAttribute(attr), "\\s+");
    }

    private Map<String, String> getMapAttribute(String attr, String defaultValue) {
        return mapSplit(getAttribute(attr), '=', "\\s+", defaultValue);
    }

    private static List<String> split(String str, String separator) {
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

    private static Map<String, String> mapSplit(String map, char kvSeparator, String separator, String defaultValue) {
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

    private boolean needsAppCache() {
        if (isEmptyCapsule())
            return false;
        if (hasRenamedNativeDependencies())
            return true;
        if (hasAttribute(ATTR_APP_ARTIFACT))
            return false;
        return shouldExtract();
    }

    private boolean shouldExtract() {
        final String extract = getAttribute(ATTR_EXTRACT);
        return extract == null || Boolean.parseBoolean(extract);
    }

    private void resetAppCache() {
        try {
            debug("Creating cache for " + jarFile + " in " + appCache.toAbsolutePath());
            delete(appCache);
            Files.createDirectory(appCache);
        } catch (IOException e) {
            throw new RuntimeException("Exception while extracting jar " + jarFile + " to app cache directory " + appCache.toAbsolutePath(), e);
        }
    }

    private boolean isUpToDate() {
        if (Boolean.parseBoolean(System.getProperty(PROP_RESET, "false")))
            return false;
        try {
            Path extractedFile = appCache.resolve(".extracted");
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
            verbose("Extracting " + jarFile + " to app cache directory " + appCache.toAbsolutePath());
            if (!isTest())
                extractJar(jar, appCache);
            else
                extractJar(getJarInputStream(), appCache);
        } catch (IOException e) {
            throw new RuntimeException("Exception while extracting jar " + jarFile + " to app cache directory " + appCache.toAbsolutePath(), e);
        }
    }

    private void markCache() {
        try {
            Files.createFile(appCache.resolve(".extracted"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isCapsule(Path path) {
        if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
            try (final JarInputStream jar = new JarInputStream(Files.newInputStream(path))) {
                return isCapsule(jar);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private static boolean isCapsule(JarInputStream jar) {
        return "Capsule".equals(getMainClass(jar.getManifest()));
//        for (Enumeration entries = jar.entries(); entries.hasMoreElements();) {
//            final JarEntry file = (JarEntry) entries.nextElement();
//            if (file.getName().equals("Capsule.class"))
//                return true;
//        }
//        return false;
    }

    private static String getMainClass(Path jar) throws IOException {
        try (final JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
            return getMainClass(jis.getManifest());
        }
    }

    private static String getMainClass(Manifest manifest) {
        if (manifest != null)
            return manifest.getMainAttributes().getValue("Main-Class");
        return null;
    }

    private static void extractJar(JarFile jar, Path targetDir) throws IOException {
        for (Enumeration entries = jar.entries(); entries.hasMoreElements();) {
            final JarEntry entry = (JarEntry) entries.nextElement();
            if (entry.isDirectory() || !shouldExtractFile(entry.getName()))
                continue;

            try (InputStream is = jar.getInputStream(entry)) {
                writeFile(targetDir, entry.getName(), is);
            }
        }
    }

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
        final String dir = getDirectory(fileName);
        if (dir != null && dir.startsWith("META-INF"))
            return false;
        return true;
    }

    private static void writeFile(Path targetDir, String fileName, InputStream is) throws IOException {
        final String dir = getDirectory(fileName);
        if (dir != null)
            Files.createDirectories(targetDir.resolve(dir));

        final Path targetFile = targetDir.resolve(fileName);
        Files.copy(is, targetFile);
    }

    private static String getDirectory(String filename) {
        final int index = filename.lastIndexOf('/');
        if (index < 0)
            return null;
        return filename.substring(0, index);
    }

    private String getJavaHome() {
        String jhome = System.getProperty(PROP_CAPSULE_JAVA_HOME);
        if (jhome == null && !isMatchingJavaVersion(System.getProperty(PROP_JAVA_VERSION))) {
            final boolean jdk = hasAttribute(ATTR_JDK_REQUIRED) && Boolean.parseBoolean(getAttribute(ATTR_JDK_REQUIRED));
            final Path javaHomePath = findJavaHome(jdk);
            if (javaHomePath == null) {
                throw new RuntimeException("Could not find Java installation for requested version "
                        + '[' + "Min. Java version: " + getAttribute(ATTR_MIN_JAVA_VERSION)
                        + " JavaVersion: " + getAttribute(ATTR_JAVA_VERSION)
                        + " Min. update version: " + getAttribute(ATTR_MIN_UPDATE_VERSION) + ']'
                        + " (JDK required: " + jdk + ")"
                        + ". You can override the used Java version with the -D" + PROP_CAPSULE_JAVA_HOME + " flag.");
            }
            jhome = javaHomePath.toAbsolutePath().toString();
        }
        return jhome;
    }

    private boolean isMatchingJavaVersion(String javaVersion) {
        try {
            if (hasAttribute(ATTR_MIN_JAVA_VERSION) && compareVersions(javaVersion, getAttribute(ATTR_MIN_JAVA_VERSION)) < 0) {
                debug("Java version " + javaVersion + " fails to match due to " + ATTR_MIN_JAVA_VERSION + ": " + getAttribute(ATTR_MIN_JAVA_VERSION));
                return false;
            }
            if (hasAttribute(ATTR_JAVA_VERSION) && compareVersions(javaVersion, shortJavaVersion(getAttribute(ATTR_JAVA_VERSION)), 3) > 0) {
                debug("Java version " + javaVersion + " fails to match due to " + ATTR_JAVA_VERSION + ": " + getAttribute(ATTR_JAVA_VERSION));
                return false;
            }
            if (getMinUpdateFor(javaVersion) > parseJavaVersion(javaVersion)[3]) {
                debug("Java version " + javaVersion + " fails to match due to " + ATTR_MIN_UPDATE_VERSION + ": " + getAttribute(ATTR_MIN_UPDATE_VERSION) + " (" + getMinUpdateFor(javaVersion) + ")");
                return false;
            }
            debug("Java version " + javaVersion + " matches");
            return true;
        } catch (IllegalArgumentException ex) {
            verbose("Error parsing Java version " + javaVersion);
            return false;
        }
    }

    private String getJavaProcessName() {
        final String javaHome1 = javaHome != null ? javaHome : System.getProperty(PROP_JAVA_HOME);
        verbose("Using JVM: " + javaHome1 + (javaHome == null ? " (current)" : ""));
        return getJavaProcessName(javaHome1);
    }

    private static String getJavaProcessName(String javaHome) {
        return javaHome + FILE_SEPARATOR + "bin" + FILE_SEPARATOR + "java" + (isWindows() ? ".exe" : "");
    }

    protected static final boolean isWindows() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("windows");
    }

    protected static final boolean isMac() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("mac");
    }

    protected static final boolean isUnix() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().contains("nux")
                || System.getProperty(PROP_OS_NAME).toLowerCase().contains("solaris")
                || System.getProperty(PROP_OS_NAME).toLowerCase().contains("aix");
    }

    private static boolean isDependency(String lib) {
        return lib.contains(":");
    }

    private static String sanitize(String path) {
        if (path.startsWith("/") || path.startsWith("../") || path.contains("/../"))
            throw new IllegalArgumentException("Path " + path + " is not local");
        return path;
    }

    private static String join(Collection<?> coll, String separator) {
        if (coll == null)
            return null;
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

    private boolean hasEntry(String name) {
        try {
            return getEntry(name) != null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private InputStream getEntry(String name) throws IOException {
        if (!isTest()) {
            final JarEntry entry = jar.getJarEntry(name);
            if (entry == null)
                return null;
            return jar.getInputStream(entry);
        }

        // test
        final JarInputStream jis = getJarInputStream();
        for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
            if (name.equals(entry.getName()))
                return jis;
        }
        return null;
    }

    private boolean hasPom() {
        return hasEntry(POM_FILE);
    }

    private Object createPomReader() {
        try {
            return new PomReader(getEntry(POM_FILE));
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jarFile
                    + " contains a pom.xml file, while the necessary dependency management classes are not found in the jar");
        } catch (IOException e) {
            throw new RuntimeException("Failed reading pom", e);
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

    private Object createDependencyManager(List<String> repositories) {
        try {
            final Path depsCache = cacheDir.resolve(DEPS_CACHE_NAME);

            final boolean reset = Boolean.parseBoolean(System.getProperty(PROP_RESET, "false"));

            final String local = expandCommandLinePath(System.getProperty(PROP_USE_LOCAL_REPO));
            Path localRepo = depsCache;
            if (local != null)
                localRepo = !local.isEmpty() ? Paths.get(local) : DEFAULT_LOCAL_MAVEN;
            debug("Local repo: " + localRepo);

            final boolean offline = "".equals(System.getProperty(PROP_OFFLINE)) || Boolean.parseBoolean(System.getProperty(PROP_OFFLINE));
            debug("Offline: " + offline);

            final DependencyManager dm = new AetherDependencyManager(localRepo.toAbsolutePath(), repositories, reset, offline);

            return dm;
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jarFile
                    + " specifies dependencies, while the necessary dependency management classes are not found in the jar");
        }
    }

    private List<String> getRepositories() {
        List<String> repos = new ArrayList<String>();

        List<String> attrRepos = split(System.getenv(ENV_CAPSULE_REPOS), ":");
        if (attrRepos == null)
            attrRepos = getListAttribute(ATTR_REPOSITORIES);

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

    /**
     * Returns a list of dependencies, each in the format {@code groupId:artifactId:version[:classifier]} (classifier is optional)
     */
    protected List<String> getDependencies() {
        List<String> deps = getListAttribute(ATTR_DEPENDENCIES);
        if (deps == null && pom != null)
            deps = getPomDependencies();

        return deps != null ? Collections.unmodifiableList(deps) : null;
    }

    private void printDependencyTree(String root) {
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(root);
    }

    private void printDependencyTree(List<String> dependencies, String type) {
        if (dependencies == null)
            return;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(dependencies, type);
    }

    private List<Path> resolveDependencies(List<String> dependencies, String type) {
        if (dependencies == null)
            return null;

        return ((DependencyManager) dependencyManager).resolveDependencies(dependencies, type);
    }

    private String getAppArtifactLatestVersion(String coords) {
        if (coords == null)
            return null;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        return dm.getLatestVersion(coords);
    }

    private List<Path> resolveAppArtifact(String coords) {
        if (coords == null)
            return null;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        return dm.resolveRoot(coords);
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

    private void delete(Path dir) throws IOException {
        // we don't use Files.walkFileTree because we'd like to avoid creating more classes (Capsule$1.class etc.)
        for (Path f : listDir(dir, FILE_VISITOR_MODE_POSTORDER))
            Files.delete(f);
    }

    private Path findJavaHome(boolean jdk) {
        final Map<String, Path> homes = getJavaHomes(jdk);
        if (homes == null)
            return null;
        Path best = null;
        String bestVersion = null;
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            final String v = e.getKey();
            debug("Trying JVM: " + e.getValue() + " (version " + e.getKey() + ")");
            if (isMatchingJavaVersion(v)) {
                debug("JVM " + e.getValue() + " (version " + e.getKey() + ") matches");
                if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                    debug("JVM " + e.getValue() + " (version " + e.getKey() + ") is best so far");
                    bestVersion = v;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    private Map<String, Path> getJavaHomes(boolean jdk) {
        Path dir = Paths.get(System.getProperty(PROP_JAVA_HOME)).getParent();
        while (dir != null) {
            Map<String, Path> homes = getJavaHomes(dir, jdk);
            if (homes != null)
                return homes;
            dir = dir.getParent();
        }
        return null;
    }

    private Map<String, Path> getJavaHomes(Path dir, boolean jdk) {
        if (!Files.isDirectory(dir))
            return null;
        Map<String, Path> dirs = new HashMap<String, Path>();
        for (Path f : listDir(dir)) {
            if (Files.isDirectory(f)) {
                String dirName = f.getFileName().toString();
                String ver = isJavaDir(dirName);
                if (ver != null && (!jdk || isJDK(dirName))) {
                    final Path home = searchJavaHomeInDir(f);
                    if (home != null) {
                        if (parseJavaVersion(ver)[3] == 0)
                            ver = getActualJavaVersion(home.toString());
                        dirs.put(ver, home);
                    }
                }
            }
        }
        return !dirs.isEmpty() ? dirs : null;
    }

    private static String isJavaDir(String fileName) {
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

    private static boolean isJDK(String filename) {
        return filename.toLowerCase().contains("jdk");
    }

    private Path searchJavaHomeInDir(Path dir) {
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

    private boolean isJavaHome(Path dir) {
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

    private static final Pattern PAT_JAVA_VERSION_LINE = Pattern.compile(".*?\"(.+?)\"");

    private static String getActualJavaVersion(String javaHome) {
        try {
            final ProcessBuilder pb = new ProcessBuilder(getJavaProcessName(javaHome), "-version");
            if (javaHome != null)
                pb.environment().put("JAVA_HOME", javaHome);
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

    // visible for testing
    static String shortJavaVersion(String v) {
        try {
            final String[] vs = v.split(DOT);
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

    private static <T> Collection<T> nullToEmpty(Collection<T> coll) {
        if (coll == null)
            return Collections.emptyList();
        return coll;
    }

    private List<String> expand(List<String> strs) {
        if (strs == null)
            return null;
        final List<String> res = new ArrayList<String>(strs.size());
        for (String s : strs)
            res.add(expand(s));
        return res;
    }

    private String expand(String str) {
        if (appCache != null)
            str = str.replaceAll("\\$" + VAR_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        else if (str.contains("$" + VAR_CAPSULE_DIR))
            throw new IllegalStateException("The $" + VAR_CAPSULE_DIR + " variable cannot be expanded when the "
                    + ATTR_EXTRACT + " attribute is set to false");

        str = expandCommandLinePath(str);
        str = str.replaceAll("\\$" + VAR_CAPSULE_JAR, getJarPath());
        str = str.replaceAll("\\$" + VAR_JAVA_HOME, javaHome != null ? javaHome : System.getProperty(PROP_JAVA_HOME));
        str = str.replace('/', FILE_SEPARATOR.charAt(0));
        return str;
    }

    private static String expandCommandLinePath(String str) {
        if (str == null)
            return null;
//        if (isWindows())
//            return str;
//        else
        return str.startsWith("~/") ? str.replace("~", System.getProperty(PROP_USER_HOME)) : str;
    }

    private static String getApplicationArtifactId(String coords) {
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

    private int getMinUpdateFor(String version) {
        final Map<String, String> m = getMapAttribute(ATTR_MIN_UPDATE_VERSION, null);
        if (m == null)
            return 0;
        final int[] ver = parseJavaVersion(version);
        for (Map.Entry<String, String> entry : m.entrySet()) {
            if (equals(ver, toInt(shortJavaVersion(entry.getKey()).split(DOT)), 3))
                return Integer.parseInt(entry.getValue());
        }
        return 0;
    }

    private static boolean anyPropertyDefined(String... props) {
        for (String prop : props) {
            if (System.getProperty(prop) != null)
                return true;
        }
        return false;
    }

    private static void println(String str) {
        System.err.println("CAPSULE: " + str);
    }

    private static void verbose(String str) {
        if (verbose)
            System.err.println("CAPSULE: " + str);
    }

    private static void debug(String str) {
        if (debug)
            System.err.println("CAPSULE: " + str);
    }

    /////////////////// file visitor
    /*
     * This is written in a funny way because we don't want to create any classes, even anonymous ones, so that this file will compile
     * to a single class file.
     */
    private final Object fileVisitorLock = new Object();
    private int fileVisitorMode;
    private Path fileVisitorStart;
    private List<Path> fileVisitorResult;
    private static final int FILE_VISITOR_MODE_NO_RECURSE = 0;
    private static final int FILE_VISITOR_MODE_PREORDER = 1;
    private static final int FILE_VISITOR_MODE_POSTORDER = 2;

    protected final List<Path> listDir(Path dir) {
        return listDir(dir, FILE_VISITOR_MODE_NO_RECURSE);
    }

    protected final List<Path> listDir(Path dir, int fileVisitorMode) {
        synchronized (fileVisitorLock) {
            this.fileVisitorMode = fileVisitorMode;
            List<Path> res = new ArrayList<>();
            this.fileVisitorStart = dir;
            this.fileVisitorResult = res;
            try {
                Files.walkFileTree(dir, this);
                return res;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                this.fileVisitorStart = null;
                this.fileVisitorResult = null;
                this.fileVisitorMode = -1;
            }
        }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (fileVisitorStart.equals(dir))
            return FileVisitResult.CONTINUE;

        if (fileVisitorMode == FILE_VISITOR_MODE_PREORDER)
            fileVisitorResult.add(dir);
        else if (fileVisitorMode == FILE_VISITOR_MODE_NO_RECURSE) {
            fileVisitorResult.add(dir);
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (exc != null)
            throw exc;
        if (fileVisitorMode == FILE_VISITOR_MODE_POSTORDER)
            fileVisitorResult.add(dir);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        fileVisitorResult.add(file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        throw exc;
    }

    /////////////
    private static ProcessBuilder launchCapsule(Path path, List<String> cmdLine, String[] args) {
        try {
            final ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()}, null);
            Class clazz;
            try {
                clazz = cl.loadClass(CUSTOM_CAPSULE_CLASS_NAME);
            } catch (ClassNotFoundException e) {
                clazz = cl.loadClass(Capsule.class.getName());
            }
            final Object capsule;
            try {
                Constructor<?> ctor = clazz.getConstructor(Path.class);
                ctor.setAccessible(true);
                capsule = ctor.newInstance(path);
            } catch (Exception e) {
                throw new RuntimeException("Could not launch custom capsule.", e);
            }
            final Method launch = clazz.getMethod("prepareForLaunch", List.class, String[].class);
            launch.setAccessible(true);
            return (ProcessBuilder) launch.invoke(capsule, cmdLine, args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(path + " does not appear to be a valid capsule.", e);
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        } catch (InvocationTargetException e) {
            final Throwable t = e.getTargetException();
            if (t instanceof RuntimeException)
                throw (RuntimeException) t;
            if (t instanceof Error)
                throw (Error) t;
            throw new RuntimeException(t);
        }
    }
}
