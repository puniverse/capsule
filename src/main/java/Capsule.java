/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import capsule.PomReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.text.ParseException;
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
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Capsule implements Runnable {
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
    private static final String VERSION = "0.4.0";

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
    private static final String ENV_CACHE_DIR = "CAPSULE_CACHE_DIR";
    private static final String ENV_CACHE_NAME = "CAPSULE_CACHE_NAME";

    private static final String PROP_CAPSULE_JAR = "capsule.jar";
    private static final String PROP_CAPSULE_DIR = "capsule.dir";
    private static final String PROP_CAPSULE_APP = "capsule.app";

    private static final String ATTR_APP_NAME = "Application-Name";
    private static final String ATTR_APP_VERSION = "Application-Version";
    private static final String ATTR_APP_CLASS = "Application-Class";
    private static final String ATTR_APP_ARTIFACT = "Application";
    private static final String ATTR_UNIX_SCRIPT = "Unix-Script";
    private static final String ATTR_WINDOWS_SCRIPT = "Windows-Script";
    private static final String ATTR_EXTRACT = "Extract-Capsule";
    private static final String ATTR_MIN_JAVA_VERSION = "Min-Java-Version";
    private static final String ATTR_JAVA_VERSION = "Java-Version";
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

    private static final String VAR_CAPSULE_DIR = "CAPSULE_DIR";
    private static final String VAR_CAPSULE_JAR = "CAPSULE_JAR";
    private static final String VAR_JAVA_HOME = "JAVA_HOME";

    private static final String CACHE_DEFAULT_NAME = "capsule";
    private static final String DEPS_CACHE_NAME = "deps";
    private static final String APP_CACHE_NAME = "apps";
    private static final String POM_FILE = "pom.xml";
    private static final String FILE_SEPARATOR = System.getProperty(PROP_FILE_SEPARATOR);
    private static final String PATH_SEPARATOR = System.getProperty(PROP_PATH_SEPARATOR);
    private static final Path LOCAL_MAVEN = Paths.get(System.getProperty(PROP_USER_HOME), ".m2", "repository");

    private static final boolean debug = "debug".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final boolean verbose = debug || "verbose".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final Path cacheDir = getCacheDir();

    private final JarFile jar;       // never null
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
    public static void main(String[] args) {
        try {
            final Capsule capsule = newCapsule(getJarFile(), args);

            if (anyPropertyDefined(PROP_VERSION, PROP_PRINT_JRES, PROP_TREE, PROP_RESOLVE)) {
                if (anyPropertyDefined(PROP_VERSION))
                    capsule.printVersion();

                if (anyPropertyDefined(PROP_PRINT_JRES))
                    capsule.printJVMs();

                if (anyPropertyDefined(PROP_TREE))
                    capsule.printDependencyTree(args);

                if (anyPropertyDefined(PROP_RESOLVE))
                    capsule.resolve(args);
                return;
            }

            capsule.launch(args);
        } catch (Throwable t) {
            System.err.println("CAPSULE EXCEPTION: " + t.getMessage()
                    + (!verbose ? " (for stack trace, run with -D" + PROP_LOG + "=verbose)" : ""));
            if (verbose)
                t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Capsule newCapsule(JarFile jar, String[] args) {
        try {
            final Class<?> clazz = Class.forName("CustomCapsule");
            try {
                Constructor<?> ctor = clazz.getConstructor(JarFile.class, String[].class);
                ctor.setAccessible(true);
                return (Capsule) ctor.newInstance(jar, args);
            } catch (Exception e) {
                throw new RuntimeException("Could not launch custom capsule.", e);
            }
        } catch (ClassNotFoundException e) {
            return new Capsule(jar, args);
        }
    }

    private static boolean isNonInteractiveProcess() {
        return System.console() == null || System.console().reader() == null;
    }

    protected Capsule(JarFile jar, String[] args) {
        this.jar = jar;
        try {
            this.manifest = jar.getManifest();
            if (manifest == null)
                throw new RuntimeException("Jar file " + jar.getName() + " does not have a manifest");
        } catch (IOException e) {
            throw new RuntimeException("Could not read Jar file " + jar.getName() + " manifest");
        }

        this.javaHome = getJavaHome();
        this.mode = System.getProperty(PROP_MODE);
        this.pom = (!hasAttribute(ATTR_DEPENDENCIES) && hasPom()) ? createPomReader() : null;
        this.dependencyManager = needsDependencyManager() ? createDependencyManager(getRepositories()) : null;
        this.appId = getAppId(args);
        this.appCache = needsAppCache() ? getAppCacheDir() : null;
        this.cacheUpToDate = appCache != null ? isUpToDate() : false;
    }

    private boolean needsDependencyManager() {
        return hasAttribute(ATTR_APP_ARTIFACT)
                || isEmptyCapsule()
                || pom != null
                || getDependencies() != null
                || getNativeDependencies() != null;
    }

    private void launch(String[] args) throws IOException, InterruptedException {
        if (launchCapsule(args))
            return;

        verbose("Launching app " + appId);

        ensureExtractedIfNecessary();

        final ProcessBuilder pb = buildProcess(args);

        if (appCache != null && !cacheUpToDate)
            markCache();

        if (!isInheritIoBug())
            pb.inheritIO();
        this.child = pb.start();

        if (isNonInteractiveProcess() && !isInheritIoBug()) {
            System.exit(0);
        } else {
            Runtime.getRuntime().addShutdownHook(new Thread(this));
            if (isInheritIoBug())
                pipeIoStreams();
            // registerSignals();
            System.exit(child.waitFor());
        }
    }

    private void printVersion() {
        System.out.println("CAPSULE: Application " + appId);
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
        System.out.println("CAPSULE: selected " + javaHome);
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
    public void run() {
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
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            PrintStream p = new PrintStream(out);
            String line;
            while ((line = r.readLine()) != null) {
                p.println(line);
            }
        } catch (IOException e) {
            if (verbose)
                e.printStackTrace(System.err);
        }
    }

    private boolean isEmptyCapsule() {
        return !hasAttribute(ATTR_APP_ARTIFACT) && !hasAttribute(ATTR_APP_CLASS) && getScript() == null;
    }

    private void printDependencyTree(String[] args) {
        System.out.println("Dependencies for " + appId);
        if (dependencyManager == null)
            System.out.println("No dependencies declared.");
        else if (hasAttribute(ATTR_APP_ARTIFACT) || isEmptyCapsule()) {
            final String appArtifact = isEmptyCapsule() ? getCommandLineArtifact(args) : getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact == null)
                throw new IllegalStateException("capsule " + jar.getName() + " has nothing to run");
            printDependencyTree(appArtifact);
        } else
            printDependencyTree(getDependencies(), "jar");

        final List<String> nativeDeps = getNativeDependencies();
        if (nativeDeps != null) {
            System.out.println("\nNative Dependencies:");
            printDependencyTree(nativeDeps, getNativeLibExtension());
        }
    }

    private boolean launchCapsule(String[] args) {
        if (getScript() == null) {
            String appArtifact = getAppArtifact(args);
            if (appArtifact != null) {
                try {
                    final List<Path> jars = resolveAppArtifact(appArtifact);
                    if (jars == null)
                        return false;
                    if (isCapsule(jars.get(0))) {
                        verbose("Running capsule " + jars.get(0));
                        runCapsule(jars.get(0), isEmptyCapsule() ? Arrays.copyOfRange(args, 1, args.length) : buildArgs(args).toArray(new String[0]));
                        return true;
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
        return false;
    }

    private String getAppArtifact(String[] args) {
        String appArtifact = null;
        if (isEmptyCapsule()) {
            appArtifact = getCommandLineArtifact(args);
            if (appArtifact == null)
                throw new IllegalStateException("capsule " + jar.getName() + " has nothing to run");
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

    private ProcessBuilder buildProcess(String[] args) {
        final ProcessBuilder pb = new ProcessBuilder();
        if (!buildScriptProcess(pb))
            buildJavaProcess(pb);

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

    private boolean buildJavaProcess(ProcessBuilder pb) {
        final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        final List<String> cmdLine = runtimeBean.getInputArguments();

//        final String classPath = runtimeBean.getClassPath();
//        final String bootClassPath = runtimeBean.getBootClassPath();
//        final String libraryPath = runtimeBean.getLibraryPath();
        if (javaHome != null)
            pb.environment().put("JAVA_HOME", javaHome);

        final List<String> command = pb.command();

        command.add(getJavaProcessName(javaHome));

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
                classPath.add(Paths.get(jar.getName()));
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
                Path p = Paths.get(expand(sanitize(sp)));

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
        final Map<String, String> systemProerties = new HashMap<String, String>();

        // attribute
        for (String p : nullToEmpty(getListAttribute(ATTR_SYSTEM_PROPERTIES))) {
            if (!p.trim().isEmpty())
                addSystemProperty(p, systemProerties);
        }

        // library path
        if (appCache != null) {
            final List<Path> libraryPath = buildNativeLibraryPath();
            libraryPath.add(appCache);
            systemProerties.put(PROP_JAVA_LIBRARY_PATH, compileClassPath(libraryPath));
        } else if (hasAttribute(ATTR_LIBRARY_PATH_P) || hasAttribute(ATTR_LIBRARY_PATH_A))
            throw new IllegalStateException("Cannot use the " + ATTR_LIBRARY_PATH_P + " or the " + ATTR_LIBRARY_PATH_A
                    + " attributes when the " + ATTR_EXTRACT + " attribute is set to false");

        if (hasAttribute(ATTR_SECURITY_POLICY) || hasAttribute(ATTR_SECURITY_POLICY_A)) {
            systemProerties.put(PROP_JAVA_SECURITY_MANAGER, "");
            if (hasAttribute(ATTR_SECURITY_POLICY_A))
                systemProerties.put(PROP_JAVA_SECURITY_POLICY, toJarUrl(getAttribute(ATTR_SECURITY_POLICY_A)));
            if (hasAttribute(ATTR_SECURITY_POLICY))
                systemProerties.put(PROP_JAVA_SECURITY_POLICY, "=" + toJarUrl(getAttribute(ATTR_SECURITY_POLICY)));
        }
        if (hasAttribute(ATTR_SECURITY_MANAGER))
            systemProerties.put(PROP_JAVA_SECURITY_MANAGER, getAttribute(ATTR_SECURITY_MANAGER));

        // Capsule properties
        if (appCache != null)
            systemProerties.put(PROP_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        systemProerties.put(PROP_CAPSULE_JAR, getJarPath());
        systemProerties.put(PROP_CAPSULE_APP, appId);

        // command line
        for (String option : cmdLine) {
            if (option.startsWith("-D"))
                addSystemProperty0(option.substring(2), systemProerties);
        }

        return systemProerties;
    }

    private List<Path> buildNativeLibraryPath() {
        final List<Path> libraryPath = new ArrayList<Path>();
        resolveNativeDependencies();
        libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_P))));
        libraryPath.addAll(toPath(Arrays.asList(ManagementFactory.getRuntimeMXBean().getLibraryPath().split(PATH_SEPARATOR))));
        libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_A))));
        libraryPath.add(appCache);
        return libraryPath;
    }

    private String getNativeLibExtension() {
        if (isLinux())
            return "so";
        if (isWindows())
            return "dll";
        if (isMac())
            return "dylib";
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
        if (isLinux())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_LINUX);
        if (isWindows())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_WIN);
        if (isMac())
            return getListAttribute(ATTR_NATIVE_DEPENDENCIES_MAC);
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
        return Paths.get(jar.getName()).getParent().toAbsolutePath().toString();
    }

    private static List<String> compileSystemProperties(Map<String, String> ps) {
        final List<String> command = new ArrayList<String>();
        for (Map.Entry<String, String> entry : ps.entrySet())
            command.add("-D" + entry.getKey() + (entry.getValue() != null && !entry.getValue().isEmpty() ? "=" + entry.getValue() : ""));
        return command;
    }

    private void addSystemProperty(String p, Map<String, String> ps) {
        try {
            String name = getBefore(p, '=');
            String value = getAfter(p, '=');
            ps.put(name, value != null ? expand(value) : "");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal system property definition: " + p);
        }
    }

    private static void addSystemProperty0(String p, Map<String, String> ps) {
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
        final List<String> agents0 = getListAttribute(ATTR_JAVA_AGENTS);

        if (agents0 == null)
            return null;
        final List<String> agents = new ArrayList<String>(agents0.size());
        for (String agent : agents0) {
            final String agentJar = getBefore(agent, '=');
            final String agentOptions = getAfter(agent, '=');
            try {
                final Path agentPath = getPath(agentJar);
                agents.add(agentPath + (agentOptions != null ? "=" + agentOptions : ""));
            } catch (IllegalStateException e) {
                if (appCache == null)
                    throw new RuntimeException("Cannot run the embedded Java agent " + agentJar + " when the " + ATTR_EXTRACT + " attribute is set to false");
                throw e;
            }
        }
        return agents;
    }

    private static JarFile getJarFile() {
        final URL url = Capsule.class.getClassLoader().getResource(Capsule.class.getName().replace('.', '/') + ".class");
        if (!"jar".equals(url.getProtocol()))
            throw new IllegalStateException("The Capsule class must be in a JAR file, but was loaded from: " + url);
        final String path = url.getPath();
        if (path == null || !path.startsWith("file:"))
            throw new IllegalStateException("The Capsule class must be in a local JAR file, but was loaded from: " + url);

        try {
            final URI jarUri = new URI(path.substring(0, path.indexOf('!')));
            try {
                final JarFile jar = new JarFile(new File(jarUri));
                return jar;
            } catch (IOException e) {
                throw new RuntimeException("Jar file containing the Capsule could not be opened: " + jarUri, e);
            }
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private String getAppId(String[] args) {
        String appName = System.getProperty(PROP_APP_ID);
        if (appName == null)
            appName = getAttribute(ATTR_APP_NAME);
        if (appName == null) {
            appName = getApplicationArtifactId(getAppArtifact(args));
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
            throw new RuntimeException("Capsule jar " + jar.getName() + " must either have the " + ATTR_APP_NAME + " manifest attribute, "
                    + "the " + ATTR_APP_CLASS + " attribute, or contain a " + POM_FILE + " file.");
        }

        final String version = hasAttribute(ATTR_APP_VERSION) ? getAttribute(ATTR_APP_VERSION) : getAttribute(ATTR_IMPLEMENTATION_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    private String getMainClass(List<Path> classPath) {
        try {
            String mainClass = getAttribute(ATTR_APP_CLASS);
            if (mainClass == null && hasAttribute(ATTR_APP_ARTIFACT))
                mainClass = getMainClass(new JarFile(classPath.get(0).toAbsolutePath().toString()));
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
        for (File f : appCache.toFile().listFiles()) {
            if (f.isFile() && f.getName().endsWith(".jar"))
                cp.add(f.toPath());
        }

        return cp;
    }

    private Path getPath(String p) {
        if (isDependency(p))
            return getDependencyPath(dependencyManager, p);
        else {
            if (appCache == null)
                throw new IllegalStateException("Capsule not extracted. Cannot obtain path " + p);
            return toAbsolutePath(appCache, p);
        }
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

    private static List<Path> toPath(List<String> ps) {
        if (ps == null)
            return null;
        final List<Path> aps = new ArrayList<Path>(ps.size());
        for (String p : ps)
            aps.add(Paths.get(p));
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

    private List<String> split(String str, String separator) {
        if (str == null)
            return null;
        return Arrays.asList(str.split(separator));
    }

    private Path getAppCacheDir() {
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
            final String userHome = System.getProperty(PROP_USER_HOME);

            final String cacheNameEnv = System.getenv(ENV_CACHE_NAME);
            final String cacheName = cacheNameEnv != null ? cacheNameEnv : CACHE_DEFAULT_NAME;
            if (isWindows()) {
                final String appData = System.getenv("LOCALAPPDATA"); // Files.isDirectory(Paths.get(userHome, "AppData")) ? "AppData" : "Application Data";
                if (appData != null)
                    cache = Paths.get(appData, cacheName);
                else {
                    Path localData = Paths.get(userHome, "AppData", "Local");
                    if (!Files.isDirectory(localData))
                        localData = Paths.get(userHome, "Local Settings", "Application Data");
                    if (!Files.isDirectory(localData))
                        throw new RuntimeException("%LOCALAPPDATA% is undefined, and neither "
                                + Paths.get(userHome, "AppData", "Local") + " nor "
                                + Paths.get(userHome, "Local Settings", "Application Data") + " have been found");
                    cache = localData.resolve(cacheName);
                }
            } else
                cache = Paths.get(userHome, "." + cacheName);
        }
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
            debug("Creating cache for " + jar.getName() + " in " + appCache.toAbsolutePath());
            delete(appCache);
            Files.createDirectory(appCache);
        } catch (IOException e) {
            throw new RuntimeException("Exception while extracting jar " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath(), e);
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

            Path jarFile = Paths.get(jar.getName());
            FileTime jarTime = Files.getLastModifiedTime(jarFile);

            return extractedTime.compareTo(jarTime) >= 0;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void extractCapsule() {
        try {
            verbose("Extracting " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath());
            extractJar(jar, appCache);

        } catch (IOException e) {
            throw new RuntimeException("Exception while extracting jar " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath(), e);
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
        try {
            if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                final JarFile jar = new JarFile(path.toFile());
                return isCapsule(jar);
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isCapsule(JarFile jar) {
        return "Capsule".equals(getMainClass(jar));
//        for (Enumeration entries = jar.entries(); entries.hasMoreElements();) {
//            final JarEntry file = (JarEntry) entries.nextElement();
//            if (file.getName().equals("Capsule.class"))
//                return true;
//        }
//        return false;
    }

    private static String getMainClass(JarFile jar) {
        try {
            final Manifest manifest = jar.getManifest();
            if (manifest != null)
                return manifest.getMainAttributes().getValue("Main-Class");
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void extractJar(JarFile jar, Path targetDir) throws IOException {
        for (Enumeration entries = jar.entries(); entries.hasMoreElements();) {
            final JarEntry file = (JarEntry) entries.nextElement();

            if (file.isDirectory())
                continue;

            if (file.getName().equals(Capsule.class.getName().replace('.', '/') + ".class")
                    || (file.getName().startsWith(Capsule.class.getName().replace('.', '/') + "$") && file.getName().endsWith(".class")))
                continue;
            if (file.getName().endsWith(".class"))
                continue;
            if (file.getName().startsWith("capsule/"))
                continue;

            final String dir = getDirectory(file.getName());
            if (dir != null && dir.startsWith("META-INF"))
                continue;

            if (dir != null)
                Files.createDirectories(targetDir.resolve(dir));

            final Path targetFile = targetDir.resolve(file.getName());
            try (InputStream is = jar.getInputStream(file)) {
                Files.copy(is, targetFile);
            }
        }
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
                        + getAttribute(ATTR_MIN_JAVA_VERSION) + " / " + getAttribute(ATTR_JAVA_VERSION)
                        + "(JDK required: " + jdk + ")"
                        + ". You can override the used Java version with the -D" + PROP_CAPSULE_JAVA_HOME + " flag.");
            }
            jhome = javaHomePath.toAbsolutePath().toString();
        }
        return jhome;
    }

    private boolean isMatchingJavaVersion(String javaVersion) {
        try {
            if (hasAttribute(ATTR_MIN_JAVA_VERSION) && compareVersions(javaVersion, getAttribute(ATTR_MIN_JAVA_VERSION)) < 0)
                return false;
            if (hasAttribute(ATTR_JAVA_VERSION) && compareVersions(majorJavaVersion(javaVersion), getAttribute(ATTR_JAVA_VERSION)) > 0)
                return false;
            return true;
        } catch (IllegalArgumentException ex) {
            verbose("Error parsing Java version " + javaVersion);
            return false;
        }
    }

    private String getJavaProcessName(String javaHome) {
        if (javaHome == null)
            javaHome = System.getProperty(PROP_JAVA_HOME);

        final String javaProcessName = javaHome + FILE_SEPARATOR + "bin" + FILE_SEPARATOR + "java" + (isWindows() ? ".exe" : "");
        return javaProcessName;
    }

    protected static boolean isWindows() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("windows");
    }

    protected static boolean isMac() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("mac");
    }

    protected static boolean isLinux() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().contains("nux");
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

    private boolean hasPom() {
        return jar.getEntry(POM_FILE) != null;
    }

    private Object createPomReader() {
        try {
            return new PomReader(jar.getInputStream(jar.getEntry(POM_FILE)));
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jar.getName()
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
                localRepo = !local.isEmpty() ? Paths.get(local) : LOCAL_MAVEN;
            debug("Local repo: " + localRepo);

            final boolean offline = "".equals(System.getProperty(PROP_OFFLINE)) || Boolean.parseBoolean(System.getProperty(PROP_OFFLINE));
            debug("Offline: " + offline);

            final DependencyManager dm = new DependencyManager(localRepo.toAbsolutePath(), repositories, reset, offline);

            return dm;
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jar.getName()
                    + " specifies dependencies, while the necessary dependency management classes are not found in the jar");
        }
    }

    private List<String> getRepositories() {
        List<String> repos = new ArrayList<String>();

        List<String> attrRepos = split(System.getenv(ENV_CAPSULE_REPOS), ":");
        if (attrRepos == null)
            getListAttribute(ATTR_REPOSITORIES);

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

    private static void delete(Path dir) throws IOException {
        // we don't use Files.walkFileTree because we'd like to avoid creating more classes (Capsule$1.class etc.)
        delete(dir.toFile());
    }

    private static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

    private Path findJavaHome(boolean jdk) {
        final Map<String, Path> homes = getJavaHomes(jdk);
        if (homes == null)
            return null;
        Path best = null;
        String bestVersion = null;
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            final String v = e.getKey();
            if (isMatchingJavaVersion(v)) {
                if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                    bestVersion = v;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    private static Map<String, Path> getJavaHomes(boolean jdk) {
        Path dir = Paths.get(System.getProperty(PROP_JAVA_HOME)).getParent();
        while (dir != null) {
            Map<String, Path> homes = getJavaHomes(dir, jdk);
            if (homes != null)
                return homes;
            dir = dir.getParent();
        }
        return null;
    }

    private static Map<String, Path> getJavaHomes(Path dir, boolean jdk) {
        File d = dir.toFile();
        if (!d.isDirectory())
            return null;
        Map<String, Path> dirs = new HashMap<String, Path>();
        for (File f : d.listFiles()) {
            if (f.isDirectory()) {
                String dirName = f.toPath().getFileName().toString();
                String ver = isJavaDir(dirName);
                if (ver != null && (!jdk || isJDK(dirName))) {
                    File home = searchJavaHomeInDir(f);
                    if (home != null)
                        dirs.put(javaVersion(ver), home.toPath());
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
            return javaVersion(fileName);
        } else
            return null;
    }

    private static boolean isJDK(String filename) {
        return filename.toLowerCase().contains("jdk");
    }

    private static String javaVersion(String ver) {
        try {
            String[] comps = ver.split("\\.");
            if (Integer.parseInt(comps[0]) > 1) {
                if (comps.length == 1)
                    return "1." + ver + ".0";
                else
                    return "1." + ver;
            }
            return ver;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static File searchJavaHomeInDir(File dir) {
        if (!dir.isDirectory())
            return null;
        for (File f : dir.listFiles()) {
            if (isJavaHome(f))
                return f;
            File home = searchJavaHomeInDir(f);
            if (home != null)
                return home;
        }
        return null;
    }

    private static boolean isJavaHome(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.isDirectory() && f.toPath().getFileName().toString().equals("bin")) {
                    for (File f0 : f.listFiles()) {
                        if (f0.isFile()) {
                            String fname = f0.toPath().getFileName().toString().toLowerCase();
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

    private static String majorJavaVersion(String v) {
        final String[] vs = v.split("\\.");
        if (vs.length == 1)
            return "1." + v;
        return vs[0] + "." + vs[1];
    }

    static int compareVersions(String a, String b) {
        return compareVersions(parseJavaVersion(a), parseJavaVersion(b));
    }

    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 5; i++) {
            if (a[i] != b[i])
                return a[i] - b[i];
        }
        return 0;
    }

    private static final Pattern PAT_JAVA_VERSION = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(_(?<update>\\d+))?(-(?<pre>.+))?");

    static int[] parseJavaVersion(String v) {
        final Matcher m = PAT_JAVA_VERSION.matcher(v);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse version: " + v);
        final int[] ver = new int[5];
        ver[0] = toInt(m.group("major"));
        ver[1] = Integer.parseInt(m.group("minor"));
        ver[2] = toInt(m.group("patch"));
        ver[3] = toInt(m.group("update"));
        final String pre = m.group("pre");
        if (pre == null)
            ver[4] = 0;
        else {
            if (pre.startsWith("rc"))
                ver[4] = -1;
            if (pre.startsWith("beta"))
                ver[4] = -2;
            if (pre.startsWith("ea"))
                ver[4] = -3;
        }
        return ver;
    }

    private static int toInt(String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        if (list == null)
            return Collections.emptyList();
        return list;
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

    private static void runCapsule(Path path, String[] args) {
        try {
            final ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()}, null);
            final Class cls = cl.loadClass("Capsule");
            final Method main = cls.getMethod("main", String[].class);
            main.invoke(cls, (Object) args);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(path + " does not appear to be a valid capsule.", e);
        } catch (MalformedURLException | IllegalAccessException e) {
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
}
