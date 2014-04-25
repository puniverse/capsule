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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class Capsule implements Runnable {
    /*
     * This class contains several strange hacks to avoid creating more classes. 
     * We'd like this file to compile to a single .class file.
     *
     * Also, the code here is not meant to be the most efficient, but methods should be as independent and stateless as possible.
     * Other than those few, methods called in the constructor, all others are can be called in any order, and don't rely on any state.
     */
    private static final String VERSION = "0.2.0";

    private static final String PROP_RESET = "capsule.reset";
    private static final String PROP_VERSION = "capsule.version";
    private static final String PROP_LOG = "capsule.log";
    private static final String PROP_TREE = "capsule.tree";
    private static final String PROP_APP_ID = "capsule.app.id";
    private static final String PROP_PRINT_JRES = "capsule.jvms";
    private static final String PROP_JAVA_HOME = "capsule.java.home";
    private static final String PROP_MODE = "capsule.mode";
    private static final String PROP_EXTRACT = "capsule.extract";
    private static final String PROP_USE_LOCAL_REPO = "capsule.local";

    private static final String ENV_CACHE_DIR = "CAPSULE_CACHE_DIR";
    private static final String ENV_CACHE_NAME = "CAPSULE_CACHE_NAME";

    private static final String PROP_CAPSULE_JAR = "capsule.jar";
    private static final String PROP_CAPSULE_DIR = "capsule.dir";

    private static final String ATTR_APP_NAME = "Application-Name";
    private static final String ATTR_APP_VERSION = "Application-Version";
    private static final String ATTR_APP_CLASS = "Application-Class";
    private static final String ATTR_APP_ARTIFACT = "Application";
    private static final String ATTR_UNIX_SCRIPT = "Unix-Script";
    private static final String ATTR_WINDOWS_SCRIPT = "Windows-Script";
    private static final String ATTR_EXTRACT = "Extract-Capsule";
    private static final String ATTR_MIN_JAVA_VERSION = "Min-Java-Version";
    private static final String ATTR_JAVA_VERSION = "Java-Version";
    private static final String ATTR_JVM_ARGS = "JVM-Args";
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

    private static final String VAR_CAPSULE_DIR = "CAPSULE_DIR";
    private static final String VAR_CAPSULE_JAR = "CAPSULE_JAR";

    private static final String CACHE_DEFAULT_NAME = "capsule";
    private static final String DEPS_CACHE_NAME = "deps";
    private static final String APP_CACHE_NAME = "apps";
    private static final String POM_FILE = "pom.xml";
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");
    private static final String PATH_SEPARATOR = System.getProperty("path.separator");
    private static final Path LOCAL_MAVEN = Paths.get(System.getProperty("user.home"), ".m2", "repository");

    private static final boolean verbose = "verbose".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final Path cacheDir = getCacheDir();

    private final JarFile jar;
    private final Manifest manifest;
    private final String appId;  // null iff noRun
    private final Path appCache; // non-null iff capsule is extracted
    private final String mode;
    private final Object pom; // non-null iff jar has pom AND manifest doesn't have ATTR_DEPENDENCIES 
    private final Object dependencyManager; // non-null iff pom exists OR manifest has ATTR_DEPENDENCIES 
    private Process child;

    /**
     * Launches the application
     *
     * @param args the program's command-line arguments
     */
    @SuppressWarnings({"BroadCatchBlock", "CallToPrintStackTrace"})
    public static void main(String[] args) {
        try {
            if (System.getProperty(PROP_VERSION) != null) {
                System.out.println("CAPSULE: Version " + VERSION);
                return;
            }

            if (System.getProperty(PROP_PRINT_JRES) != null) {
                final Map<String, Path> jres = getJavaHomes();
                if (jres == null)
                    System.out.println("CAPSULE: No detected Java installations");
                else {
                    System.out.println("CAPSULE: Detected Java installations:");
                    for (Map.Entry<String, Path> j : jres.entrySet())
                        System.out.println(j.getKey() + (j.getKey().length() < 8 ? "\t\t" : "\t") + j.getValue());
                }
                return;
            }

            final Capsule capsule = new Capsule(getJarFile());

            if (System.getProperty(PROP_TREE) != null) {
                capsule.printDependencyTree(args);
                return;
            }

            if (verbose && capsule.appId != null)
                System.err.println("CAPSULE: Launching app " + capsule.appId);
            capsule.launch(args);
        } catch (Throwable t) {
            System.err.println("CAPSULE EXCEPTION: " + t.getMessage()
                    + (!verbose ? " (for stack trace, run with -D" + PROP_LOG + "=verbose)" : ""));
            if (verbose)
                t.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean isNonInteractiveProcess() {
        return System.console() == null || System.console().reader() == null;
    }

    private Capsule(JarFile jar) throws IOException {
        this.jar = jar;
        try {
            this.manifest = jar.getManifest();
            if (manifest == null)
                throw new RuntimeException("Jar file " + jar.getName() + " does not have a manifest");
        } catch (IOException e) {
            throw new RuntimeException("Could not read Jar file " + jar.getName() + " manifest");
        }

        this.mode = System.getProperty(PROP_MODE);
        this.pom = (!hasAttribute(ATTR_DEPENDENCIES) && hasPom()) ? createPomReader() : null;
        this.appId = getAppId();
        this.dependencyManager = (hasAttribute(ATTR_DEPENDENCIES) || hasAttribute(ATTR_APP_ARTIFACT) || pom != null)
                ? createDependencyManager(getRepositories()) : null;
        this.appCache = shouldExtract() ? getAppCacheDir() : null;

        if (appCache != null)
            ensureExtracted();
    }

    private void launch(String[] args) throws IOException, InterruptedException {
        if (launchCapsule(args))
            return;

        final ProcessBuilder pb = buildProcess(args);
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

    private static boolean isInheritIoBug() {
        return isWindows() && compareVersionStrings(System.getProperty("java.version"), "1.8.0") < 0;
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
                e.printStackTrace();
        }
    }

    private boolean noRun() {
        return !hasAttribute(ATTR_APP_ARTIFACT) && !hasAttribute(ATTR_APP_CLASS) && getScript() == null;
    }

    private void printDependencyTree(String[] args) {
        if (dependencyManager == null)
            System.out.println("No dependencies declared.");
        else if (hasAttribute(ATTR_APP_ARTIFACT))
            printDependencyTree(getAttribute(ATTR_APP_ARTIFACT));
        else if (noRun()) {
            String appArtifact = getCommandLineArtifact(args);
            if (appArtifact == null)
                throw new IllegalStateException("capsule has nothing to run");
            printDependencyTree(getAttribute(appArtifact));
        } else
            printDependencyTree(getDependencies());
    }

    private boolean launchCapsule(String[] args) {
        if (getScript() == null) {
            String appArtifact = null;
            if (noRun()) {
                appArtifact = getCommandLineArtifact(args);
                if (appArtifact == null)
                    throw new IllegalStateException("capsule has nothing to run");
            }
            if (appArtifact == null)
                appArtifact = getAttribute(ATTR_APP_ARTIFACT);
            if (appArtifact != null) {
                final List<Path> jars = resolveAppArtifact(appArtifact);
                if (isCapsule(jars.get(0))) {
                    if (verbose)
                        System.err.println("Running capsule " + jars.get(0));
                    runCapsule(jars.get(0), args);
                    return true;
                } else if (noRun())
                    throw new IllegalArgumentException("Artifact " + appArtifact + " is not a capsule.");
            }
        }
        return false;
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
        command.addAll(Arrays.asList(args));

        buildEnvironmentVariables(pb.environment());

        if (verbose)
            System.err.println("CAPSULE: " + join(command, " "));

        return pb;
    }

    private boolean buildJavaProcess(ProcessBuilder pb) {
        final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        final List<String> cmdLine = runtimeBean.getInputArguments();

//        final String classPath = runtimeBean.getClassPath();
//        final String bootClassPath = runtimeBean.getBootClassPath();
//        final String libraryPath = runtimeBean.getLibraryPath();
        final String javaHome = getJavaHome();
        if (javaHome != null)
            pb.environment().put("JAVA_HOME", javaHome);

        final List<String> command = pb.command();

        command.add(getJavaProcessName(javaHome));

        command.addAll(buildJVMArgs(cmdLine));
        command.addAll(compileSystemProperties(buildSystemProperties(cmdLine)));

        addOption(command, "-Xbootclasspath:", compileClassPath(buildBootClassPath(cmdLine)));
        if (appCache != null) {
            addOption(command, "-Xbootclasspath/p:", compileClassPath(buildClassPath(ATTR_BOOT_CLASS_PATH_P)));
            addOption(command, "-Xbootclasspath/a:", compileClassPath(buildClassPath(ATTR_BOOT_CLASS_PATH_A)));
        } else if (hasAttribute(ATTR_BOOT_CLASS_PATH_P) || hasAttribute(ATTR_BOOT_CLASS_PATH_A))
            throw new IllegalStateException("Cannot use the " + ATTR_BOOT_CLASS_PATH_P + " or the " + ATTR_BOOT_CLASS_PATH_A
                    + " attributes when the " + ATTR_EXTRACT + " attribute is set to false");

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
                    Set<PosixFilePermission> newPerms = new HashSet<PosixFilePermission>(perms);
                    newPerms.add(PosixFilePermission.OWNER_EXECUTE);
                    Files.setPosixFilePermissions(file, newPerms);
                }
            } catch (UnsupportedOperationException e) {
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void verifyRequiredJavaVersion() {
        final String minVersion = getAttribute(ATTR_MIN_JAVA_VERSION);
        if (minVersion == null)
            return;
        final String javaVersion = System.getProperty("java.version");
        if (compareVersionStrings(minVersion, javaVersion) > 0)
            throw new IllegalStateException("Minimum required version to run this app is " + minVersion
                    + " while this Java version is " + javaVersion);
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

        // the capsule jar
        final String isCapsuleInClassPath = getAttribute(ATTR_CAPSULE_IN_CLASS_PATH);
        if (isCapsuleInClassPath == null || Boolean.parseBoolean(isCapsuleInClassPath))
            classPath.add(Paths.get(jar.getName()));
        else if (appCache == null)
            throw new IllegalStateException("Cannot set the " + ATTR_CAPSULE_IN_CLASS_PATH + " attribute to false when the "
                    + ATTR_EXTRACT + " attribute is also set to false");

        if (appCache == null && hasAttribute(ATTR_APP_CLASS_PATH))
            throw new IllegalStateException("Cannot use the " + ATTR_APP_CLASS_PATH + " attribute when the "
                    + ATTR_EXTRACT + " attribute is set to false");
        if (appCache != null) {
            classPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_APP_CLASS_PATH))));
            classPath.addAll(nullToEmpty(getDefaultCacheClassPath()));
        }

        if (dependencyManager != null) {
            classPath.addAll(nullToEmpty(resolveAppArtifact(getAttribute(ATTR_APP_ARTIFACT))));
            classPath.addAll(resolveDependencies(getDependencies()));
        }

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
        return toAbsolutePath(appCache, getListAttribute(ATTR_BOOT_CLASS_PATH));
    }

    private List<Path> buildClassPath(String attr) {
        return toAbsolutePath(appCache, getListAttribute(attr));
    }

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

        str = str.replaceAll("\\$" + VAR_CAPSULE_JAR, getJarPath());
        str = str.replace('/', FILE_SEPARATOR.charAt(0));
        return str;
    }

    private Map<String, String> buildSystemProperties(List<String> cmdLine) {
        final Map<String, String> systemProerties = new HashMap<String, String>();

        // attribute
        for (String p : nullToEmpty(getListAttribute(ATTR_SYSTEM_PROPERTIES)))
            addSystemProperty(p, systemProerties);

        // library path
        if (appCache != null) {
            final List<Path> libraryPath = new ArrayList<Path>();
            libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_P))));
            libraryPath.addAll(toPath(Arrays.asList(ManagementFactory.getRuntimeMXBean().getLibraryPath().split(PATH_SEPARATOR))));
            libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_A))));
            libraryPath.add(appCache);
            systemProerties.put("java.library.path", compileClassPath(libraryPath));
        } else if (hasAttribute(ATTR_LIBRARY_PATH_P) || hasAttribute(ATTR_LIBRARY_PATH_A))
            throw new IllegalStateException("Cannot use the " + ATTR_LIBRARY_PATH_P + " or the " + ATTR_LIBRARY_PATH_A
                    + " attributes when the " + ATTR_EXTRACT + " attribute is set to false");

        if (hasAttribute(ATTR_SECURITY_POLICY) || hasAttribute(ATTR_SECURITY_POLICY_A)) {
            systemProerties.put("java.security.manager", "");
            if (hasAttribute(ATTR_SECURITY_POLICY_A))
                systemProerties.put("java.security.policy", toJarUrl(getAttribute(ATTR_SECURITY_POLICY_A)));
            if (hasAttribute(ATTR_SECURITY_POLICY))
                systemProerties.put("java.security.policy", "=" + toJarUrl(getAttribute(ATTR_SECURITY_POLICY)));
        }
        if (hasAttribute(ATTR_SECURITY_MANAGER))
            systemProerties.put("java.security.manager", getAttribute(ATTR_SECURITY_MANAGER));

        // Capsule properties
        if (appCache != null)
            systemProerties.put(PROP_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        systemProerties.put(PROP_CAPSULE_JAR, getJarPath());

        // command line
        for (String option : cmdLine) {
            if (option.startsWith("-D"))
                addSystemProperty0(option.substring(2), systemProerties);
        }

        return systemProerties;
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

    private List<String> buildJVMArgs(List<String> cmdLine) {
        final Map<String, String> jvmArgs = new LinkedHashMap<String, String>();

        for (String a : nullToEmpty(getListAttribute(ATTR_JVM_ARGS))) {
            if (!a.startsWith("-Xbootclasspath:") && !a.startsWith("-javaagent:"))
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

    private String getAppId() {
        String appName = System.getProperty(PROP_APP_ID);
        if (appName == null)
            appName = getAttribute(ATTR_APP_NAME);
        if (appName == null) {
            appName = getApplicationArtifactId(getAttribute(ATTR_APP_ARTIFACT));
            if (appName != null)
                return appName;
        }
        if (appName == null) {
            if (pom != null)
                return getPomAppName();
            appName = getAttribute(ATTR_APP_CLASS);
        }
        if (appName == null) {
            if (noRun())
                return null;
            throw new RuntimeException("Capsule jar " + jar.getName() + " must either have the " + ATTR_APP_NAME + " manifest attribute, "
                    + "the " + ATTR_APP_CLASS + " attribute, or contain a " + POM_FILE + " file.");
        }

        final String version = getAttribute(ATTR_APP_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    private String getMainClass(List<Path> classPath) {
        try {
            String mainClass = getAttribute(ATTR_APP_CLASS);
            if (mainClass == null && hasAttribute(ATTR_APP_ARTIFACT))
                mainClass = getMainClass(new JarFile(classPath.get(0).toAbsolutePath().toString()));
            return mainClass;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean shouldExtract() {
        if (System.getProperty(PROP_EXTRACT) != null)
            return Boolean.parseBoolean(System.getProperty(PROP_EXTRACT, "true"));

        final String extract = getAttribute(ATTR_EXTRACT);
        return extract == null || Boolean.parseBoolean(extract);
    }

    private void ensureExtracted() {
        final boolean reset = Boolean.parseBoolean(System.getProperty(PROP_RESET, "false"));
        if (reset || !isUpToDate()) {
            try {
                if (verbose)
                    System.err.println("CAPSULE: Extracting " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath());
                delete(appCache);
                Files.createDirectory(appCache);
                extractJar(jar, appCache);
                Files.createFile(appCache.resolve(".extracted"));
            } catch (IOException e) {
                throw new RuntimeException("Exception while extracting jar " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath(), e);
            }
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
                throw new IllegalStateException("Capsule not extracted");
            return toAbsolutePath(appCache, p);
        }
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
        final String vals = getAttribute(attr);
        if (vals == null)
            return null;
        return Arrays.asList(vals.split("\\s+"));
    }

    private Path getAppCacheDir() {
        Path appDir = cacheDir.resolve(appId);
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
            final String userHome = System.getProperty("user.home");

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

    private boolean isUpToDate() {
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
        String javaHome = System.getProperty(PROP_JAVA_HOME);
        if (javaHome == null && getAttribute(ATTR_JAVA_VERSION) != null) {
            final Path javaHomePath = findJavaHome(getAttribute(ATTR_JAVA_VERSION));
            if (javaHomePath == null) {
                throw new RuntimeException("Could not find Java installation for requested version " + getAttribute(ATTR_JAVA_VERSION)
                        + ". You can override the used Java version with the -D" + PROP_JAVA_HOME + " flag.");
            }
            javaHome = javaHomePath.toAbsolutePath().toString();
        }
        if (javaHome == null)
            javaHome = System.getProperty("java.home");
        return javaHome;
    }

    private String getJavaProcessName(String javaHome) {
        if (Objects.equals(javaHome, System.getProperty("java.home")))
            verifyRequiredJavaVersion();

        final String javaProcessName = javaHome + FILE_SEPARATOR + "bin" + FILE_SEPARATOR + "java" + (isWindows() ? ".exe" : "");
        return javaProcessName;
    }

    private static boolean isWindows() {
        final String osName = System.getProperty("os.name");
        final boolean isWindows = osName.startsWith("Windows");
        return isWindows;
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
        if (s.lastIndexOf(separator) != i)
            throw new IllegalArgumentException("Illegal value: " + s);
        return s.substring(0, i);
    }

    private static String getAfter(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return null;
        if (s.lastIndexOf(separator) != i)
            throw new IllegalArgumentException("Illegal value: " + s);
        return s.substring(i + 1);
    }

    private boolean hasPom() {
        return jar.getEntry(POM_FILE) != null;
    }

    private Object createPomReader() throws IOException {
        try {
            return new PomReader(jar.getInputStream(jar.getEntry(POM_FILE)));
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jar.getName()
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

    private Object createDependencyManager(List<String> repositories) throws IOException {
        try {
            final Path depsCache = cacheDir.resolve(DEPS_CACHE_NAME);

            final boolean reset = Boolean.parseBoolean(System.getProperty(PROP_RESET, "false"));

            final String local = System.getProperty(PROP_USE_LOCAL_REPO);
            Path localRepo = depsCache;
            if (local != null)
                localRepo = !local.isEmpty() ? Paths.get(local) : LOCAL_MAVEN;

            final DependencyManager dm
                    = new DependencyManager(appId,
                            localRepo.toAbsolutePath(),
                            repositories,
                            reset, verbose);

            return dm;
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jar.getName()
                    + " contains a " + ATTR_DEPENDENCIES + " attribute or a " + ATTR_APP_ARTIFACT
                    + " attribute, while the necessary dependency management classes are not found in the jar");
        }
    }

    private List<String> getRepositories() {
        List<String> repos = new ArrayList<String>();

        List<String> attrRepos = getListAttribute(ATTR_REPOSITORIES);
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

    private List<String> getDependencies() {
        List<String> deps = getListAttribute(ATTR_DEPENDENCIES);
        if (deps == null && pom != null)
            deps = getPomDependencies();

        return Collections.unmodifiableList(deps);
    }

    private void printDependencyTree(String root) {
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(root);
    }

    private void printDependencyTree(List<String> dependencies) {
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(dependencies);
    }

    private List<Path> resolveDependencies(List<String> dependencies) {
        if (dependencies == null)
            return null;

        return ((DependencyManager) dependencyManager).resolveDependencies(dependencies);
    }

    private List<Path> resolveAppArtifact(String coords) {
        if (coords == null)
            return null;
        final DependencyManager dm = (DependencyManager) dependencyManager;
        return dm.resolveRoot(coords);
    }

    private static Path getDependencyPath(Object dependencyManager, String p) {
        if (dependencyManager == null)
            throw new RuntimeException("No Dependencies attribute in jar, therefore cannot resolve dependency " + p);
        final DependencyManager dm = (DependencyManager) dependencyManager;
        List<Path> depsJars = dm.resolveDependency(p);

        if (depsJars == null || depsJars.isEmpty())
            throw new RuntimeException("Dependency " + p + " was not found.");
        return depsJars.iterator().next().toAbsolutePath();
    }

    private static void delete(Path dir) {
        try {
            // we don't use Files.walkFileTree because we'd like to avoid creating more classes (Capsule$1.class etc.)
            delete(dir.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void delete(File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

    private static Path findJavaHome(String version) {
        // if my Java version matches requested, don't look for Java installations
        final int[] ver = parseJavaVersion(version);
        final int[] myVer = parseJavaVersion(System.getProperty("java.version"));
        if (ver[0] == myVer[0] && ver[1] == myVer[1])
            return Paths.get(System.getProperty("java.home"));

        final Map<String, Path> homes = getJavaHomes();
        if (homes == null)
            return null;
        Path best = null;
        int[] bestVersion = null;
        for (Map.Entry<String, Path> e : homes.entrySet()) {
            int[] v = parseJavaVersion(e.getKey());
            if (v[0] == ver[0] && v[1] == ver[1]) {
                if (bestVersion == null || compareVersions(v, bestVersion) > 0) {
                    bestVersion = v;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    private static Map<String, Path> getJavaHomes() {
        Path dir = Paths.get(System.getProperty("java.home")).getParent();
        while (dir != null) {
            Map<String, Path> homes = getJavaHomes(dir);
            if (homes != null)
                return homes;
            dir = dir.getParent();
        }
        return null;
    }

    private static Map<String, Path> getJavaHomes(Path dir) {
        File d = dir.toFile();
        if (!d.isDirectory())
            return null;
        Map<String, Path> dirs = new HashMap<String, Path>();
        for (File f : d.listFiles()) {
            if (f.isDirectory()) {
                String ver = isJavaDir(f.toPath().getFileName().toString());
                if (ver != null) {
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

    private static int compareVersionStrings(String a, String b) {
        return compareVersions(parseJavaVersion(a), parseJavaVersion(b));
    }

    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < 4; i++) {
            if (a[i] != b[i])
                return a[i] - b[i];
        }
        return 0;
    }

    private static int[] parseJavaVersion(String v) {
        final int[] ver = new int[4];
        String[] vs = v.split("\\.");
        if (vs.length < 2)
            throw new IllegalArgumentException("Version " + v + " is illegal. Must be of the form x.y.z[_u]");
        ver[0] = Integer.parseInt(vs[0]);
        ver[1] = Integer.parseInt(vs[1]);
        if (vs.length > 2) {
            String[] vzu = vs[2].split("_");
            ver[2] = Integer.parseInt(vzu[0]);
            ver[3] = vzu.length > 1 ? Integer.parseInt(vzu[1]) : 0;
        }
        return ver;
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
            final ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()});
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
}
