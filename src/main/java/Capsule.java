/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0 as published by the Eclipse Foundation.
 */

import co.paralleluniverse.capsule.dependency.DependencyManager;
import co.paralleluniverse.capsule.dependency.PomReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * <ul>
 * <li>{@code Application-Class} - the only mandatory attribute</li>
 * <li>{@code Application-Name}</li>
 * <li>{@code Application-Version}</li>
 * <li>{@code Unix-Script}</li>
 * <li>{@code Windows-Script}</li>
 * <li>{@code Extract-Capsule}</li>
 * <li>{@code Min-Java-Version}</li>
 * <li>{@code Java-Version}</li>
 * <li>{@code App-Class-Path} default: the capsule jar root and every jar file found in the capsule jar's root.</li>
 * <li>{@code Environment-Variables}</li>
 * <li>{@code System-Properties}</li>
 * <li>{@code JVM-Args}</li>
 * <li>{@code Boot-Class-Path}</li>
 * <li>{@code Boot-Class-Path-P}</li>
 * <li>{@code Boot-Class-Path-A}</li>
 * <li>{@code Library-Path-P}</li>
 * <li>{@code Library-Path-A}</li>
 * <li>{@code Java-Agents}</li>
 * <li>{@code Repositories}</li>
 * <li>{@code Dependencies}</li>
 * </ul>
 *
 * @author pron
 */
public final class Capsule extends Thread {
    private static final String VERSION = "0.1.0-SNAPSHOT";

    private static final String PROP_RESET = "capsule.reset";
    private static final String PROP_VERSION = "capsule.version";
    private static final String PROP_LOG = "capsule.log";
    private static final String PROP_TREE = "capsule.tree";
    private static final String PROP_PRINT_JRES = "capsule.javas";
    private static final String PROP_JAVA_HOME = "capsule.java.home";
    private static final String PROP_MODE = "capsule.mode";
    private static final String PROP_EXTRACT = "capsule.extract";

    private static final String ENV_CACHE_DIR = "CAPSULE_CACHE_DIR";
    private static final String ENV_CACHE_NAME = "CAPSULE_CACHE_NAME";
    private static final String CACHE_DEFAULT_NAME = "capsule";

    private static final String PROP_CAPSULE_JAR_DIR = "capsule.jar.dir";
    private static final String PROP_CAPSULE_DIR = "capsule.dir";

    private static final String ATTR_APP_NAME = "Application-Name";
    private static final String ATTR_APP_VERSION = "Application-Version";
    private static final String ATTR_APP_CLASS = "Application-Class";
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
    private static final String ATTR_JAVA_AGENTS = "Java-Agents";
    private static final String ATTR_REPOSITORIES = "Repositories";
    private static final String ATTR_DEPENDENCIES = "Dependencies";

    private static final String POM_FILE = "pom.xml";

    private static final boolean verbose = "verbose".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final Path cacheDir = getCacheDir();

    private final JarFile jar;
    private final Manifest manifest;
    private final String appId;
    private final Path appCache; // non-null iff capsule is extracted
    private final String mode;
    private final Object pom; // non-null iff jar has pom AND manifest doesn't have ATTR_DEPENDENCIES 
    private final Object dependencyManager; // non-null iff pom exists OR manifest has ATTR_DEPENDENCIES 
    private final List<String> repositories;
    private final List<String> dependencies;
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
                System.exit(0);
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
                System.exit(0);
            }

            final Capsule capsule = new Capsule(getJarFile());

            if (System.getProperty(PROP_TREE) != null) {
                capsule.printDependencyTree();
                System.exit(0);
            }

            if (verbose)
                System.err.println("CAPSULE: Launching app " + capsule.appId);
            capsule.launch(args);
        } catch (Throwable t) {
            System.err.println("CAPSULE EXCEPTION: " + t.getMessage() + " (for stack trace, run with -D" + PROP_LOG + "=verbose)");
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
        getMainClass(); // verify existence of ATTR_APP_CLASS

        this.appId = getAppId();
        this.pom = (!hasAttribute(ATTR_DEPENDENCIES) && hasPom()) ? createPomReader() : null;
        this.repositories = getRepositories();
        this.dependencyManager = (hasAttribute(ATTR_DEPENDENCIES) || pom != null) ? createDependencyManager() : null;
        this.dependencies = dependencyManager != null ? getDependencies() : null;
        this.appCache = shouldExtract() ? getAppCacheDir() : null;

        if (appCache != null)
            ensureExtracted();
    }

    private void launch(String[] args) throws IOException, InterruptedException {
        final ProcessBuilder pb = buildProcess(args);
        pb.inheritIO();

        this.child = pb.start();
        if (isNonInteractiveProcess())
            System.exit(0);
        else {
            Runtime.getRuntime().addShutdownHook(this);
            // registerSignals();
            System.exit(child.waitFor());
        }
    }

    @Override
    public void run() {
        if (child != null)
            child.destroy();
    }

    private void printDependencyTree() {
        if (dependencyManager == null)
            System.out.println("No dependencies declared.");
        else
            printDependencyTree(dependencies);
    }

    private ProcessBuilder buildProcess(String[] args) {
        final ProcessBuilder pb = new ProcessBuilder();
        if (!buildScripProcess(pb))
            buildJavaProcess(pb);

        final List<String> command = pb.command();
        command.addAll(Arrays.asList(args));

        buildEnvironmentVariables(pb.environment());

        if (verbose)
            System.err.println("CAPSULE: " + join(command, " "));

        return pb;
    }

    private void buildJavaProcess(ProcessBuilder pb) {
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

        command.add("-classpath");
        command.add(compileClassPath(buildClassPath()));

        for (String jagent : nullToEmpty(buildJavaAgents()))
            command.add("-javaagent:" + jagent);

        command.add(getMainClass());
    }

    private boolean buildScripProcess(ProcessBuilder pb) {
        final String script = getAttribute(isWindows() ? ATTR_WINDOWS_SCRIPT : ATTR_UNIX_SCRIPT);
        if (script == null)
            return false;

        if (appCache == null)
            throw new IllegalStateException("Cannot run the startup script " + script + " when the "
                    + ATTR_EXTRACT + " attribute is set to false");

        final String processPath = toAbsolutePath(appCache, script);
        pb.command().add(processPath);
        return true;
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

    private static String compileClassPath(List<String> cp) {
        return join(cp, System.getProperty("path.separator"));
    }

    private List<String> buildClassPath() {
        final List<String> classPath = new ArrayList<String>();

        if (appCache == null && hasAttribute(ATTR_APP_CLASS_PATH))
            throw new IllegalStateException("Cannot use the " + ATTR_APP_CLASS_PATH + " attribute when the "
                    + ATTR_EXTRACT + " attribute is set to false");
        if (appCache != null) {
            final List<String> localClassPath = new ArrayList<String>();
            localClassPath.addAll(nullToEmpty(getListAttribute(ATTR_APP_CLASS_PATH)));
            localClassPath.addAll(nullToEmpty(getDefaultClassPath()));
            classPath.addAll(toAbsolutePath(appCache, localClassPath));
        }

        if (dependencyManager != null)
            classPath.addAll(resolveDependencies());

        // the capsule jar
        final String isCapsuleInClassPath = getAttribute(ATTR_CAPSULE_IN_CLASS_PATH);
        if (isCapsuleInClassPath == null || Boolean.parseBoolean(isCapsuleInClassPath))
            classPath.add(jar.getName());
        else if (appCache == null)
            throw new IllegalStateException("Cannot set the " + ATTR_CAPSULE_IN_CLASS_PATH + " attribute to false when the "
                    + ATTR_EXTRACT + " attribute is also set to false");

        return classPath;
    }

    private List<String> buildBootClassPath(List<String> cmdLine) {
        String option = null;
        for (String o : cmdLine) {
            if (o.startsWith("-Xbootclasspath:"))
                option = o.substring("-Xbootclasspath:".length());
        }
        if (option != null)
            return Arrays.asList(option.split(System.getProperty("path.separator")));
        return toAbsolutePath(appCache, getListAttribute(ATTR_BOOT_CLASS_PATH));
    }

    private List<String> buildClassPath(String attr) {
        return toAbsolutePath(appCache, getListAttribute(attr));
    }

    private void buildEnvironmentVariables(Map<String, String> env) {
        final List<String> jarEnv = getListAttribute(ATTR_ENV);
        if (jarEnv != null) {
            for (String e : jarEnv) {
                String[] kv = e.split("=");
                if (kv.length < 1 || kv.length > 2)
                    throw new IllegalArgumentException("Malformed env variable definition: " + e);
                if (!env.containsKey(kv[0])) { // don't override inherited environment
                    if (kv.length == 1)
                        env.put(kv[0], "");
                    else
                        env.put(kv[0], processEnvValue(kv[1]));
                }
            }
        }
    }

    private String processEnvValue(String value) {
        value = value.replaceAll("\\$CAPSULE_DIR", appCache.toAbsolutePath().toString());
        value = value.replace('/', System.getProperty("file.separator").charAt(0));
        return value;
    }

    private Map<String, String> buildSystemProperties(List<String> cmdLine) {
        final Map<String, String> systemProerties = new HashMap<String, String>();

        // attribute
        for (String p : nullToEmpty(getListAttribute(ATTR_SYSTEM_PROPERTIES)))
            addSystemProperty(p, systemProerties);

        // library path
        if (appCache != null) {
            final List<String> libraryPath = new ArrayList<String>();
            libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_P))));
            libraryPath.addAll(Arrays.asList(ManagementFactory.getRuntimeMXBean().getLibraryPath().split(System.getProperty("path.separator"))));
            libraryPath.addAll(nullToEmpty(toAbsolutePath(appCache, getListAttribute(ATTR_LIBRARY_PATH_A))));
            libraryPath.add(toAbsolutePath(appCache, ""));
            systemProerties.put("java.library.path", compileClassPath(libraryPath));
        } else if (hasAttribute(ATTR_LIBRARY_PATH_P) || hasAttribute(ATTR_LIBRARY_PATH_A))
            throw new IllegalStateException("Cannot use the " + ATTR_LIBRARY_PATH_P + " or the " + ATTR_LIBRARY_PATH_A
                    + " attributes when the " + ATTR_EXTRACT + " attribute is set to false");

        // Capsule properties
        if (appCache != null)
            systemProerties.put(PROP_CAPSULE_DIR, appCache.toAbsolutePath().toString());
        systemProerties.put(PROP_CAPSULE_JAR_DIR, Paths.get(jar.getName()).getParent().toAbsolutePath().toString());

        // command line
        for (String option : cmdLine) {
            if (option.startsWith("-D"))
                addSystemProperty(option.substring(2), systemProerties);
        }

        return systemProerties;
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

    private List<String> buildJVMArgs(List<String> cmdLine) {
        final Map<String, String> jvmArgs = new LinkedHashMap<String, String>();

        for (String a : nullToEmpty(getListAttribute(ATTR_JVM_ARGS))) {
            if (!a.startsWith("-Xbootclasspath:") && !a.startsWith("-javaagent:"))
                addJvmArg(a, jvmArgs);
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
                final String agentPath = getPath(agentJar);
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
        final String jarPath = path.substring("file:".length(), path.indexOf('!'));
        final JarFile jar;
        try {
            jar = new JarFile(jarPath);
            return jar;
        } catch (IOException e) {
            throw new RuntimeException("Jar file containing the Capsule could not be opened: " + jarPath, e);
        }
    }

    private String getAppId() {
        String appName = getAttribute(ATTR_APP_NAME);
        if (appName == null) {
            if (pom != null)
                return getPomAppName();
            appName = getMainClass();
        }
        if (appName == null)
            throw new RuntimeException("Capsule jar " + jar.getName() + " must either have the " + ATTR_APP_NAME + " manifest attribute, "
                    + "the " + ATTR_APP_CLASS + " attribute, or contain a " + POM_FILE + " file.");

        final String version = getAttribute(ATTR_APP_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    private String getMainClass() {
        return getAttribute(ATTR_APP_CLASS);
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

    private List<String> getDefaultClassPath() {
        final List<String> cp = new ArrayList<String>();
        cp.add("");
        // we don't use Files.walkFileTree because we'd like to avoid creating more classes (Capsule$1.class etc.)
        for (File f : appCache.toFile().listFiles()) {
            if (f.isFile() && f.getName().endsWith(".jar"))
                cp.add(f.toPath().getFileName().toString());
        }

        return cp;
    }

    private String getPath(String p) {
        if (isDependency(p))
            return getDependencyPath(dependencyManager, p);
        else {
            if (appCache == null)
                throw new IllegalStateException("Capsule not extracted");
            return toAbsolutePath(appCache, p);
        }
    }

    private static List<String> toAbsolutePath(Path root, List<String> ps) {
        if (ps == null)
            return null;
        final List<String> aps = new ArrayList<String>(ps.size());
        for (String p : ps)
            aps.add(toAbsolutePath(root, p));
        return aps;
    }

    private static String toAbsolutePath(Path root, String p) {
        return root.resolve(sanitize(p)).toAbsolutePath().toString();
    }

    private String getAttribute(String attr) {
        // if possible, we could take into account the mode as the manifest's section name
        Attributes atts = manifest.getMainAttributes();
        return atts.getValue(attr);
    }

    private boolean hasAttribute(String attr) {
        Attributes atts = manifest.getMainAttributes();
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
            if (isWindows())
                cache = Paths.get("AppData", "Local", cacheName);
            else
                cache = Paths.get(userHome, "." + cacheName);
        }
        try {
            if (!Files.exists(cache))
                Files.createDirectory(cache);
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

    private static void extractJar(JarFile jar, Path targetDir) throws IOException {
        for (Enumeration entries = jar.entries(); entries.hasMoreElements();) {
            JarEntry file = (JarEntry) entries.nextElement();

            if (file.isDirectory())
                continue;

            if (file.getName().equals(Capsule.class.getName().replace('.', '/') + ".class")
                    || (file.getName().startsWith(Capsule.class.getName().replace('.', '/') + "$") && file.getName().endsWith(".class")))
                continue;
//            if (file.getName().equals("about.html"))
//                continue;
            if (file.getName().endsWith(".class"))
                continue;
            if (file.getName().startsWith("co/paralleluniverse/capsule/"))
                //                    || file.getName().startsWith("org/eclipse/aether/")
                //                    || file.getName().startsWith("org/apache/maven/")
                //                    || file.getName().startsWith("org/apache/http/")
                //                    || file.getName().startsWith("org/apache/commons/codec/")
                //                    || file.getName().startsWith("licenses/"))
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

        final String fileSeparateor = System.getProperty("file.separator");

        final String javaProcessName = javaHome + fileSeparateor + "bin" + fileSeparateor + "java" + (isWindows() ? ".exe" : "");
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

    private Object createDependencyManager() throws IOException {
        try {
            Path depsCache = getCacheDir().resolve("deps");
            Files.createDirectories(depsCache);

            final boolean reset = Boolean.parseBoolean(System.getProperty(PROP_RESET, "false"));
            final DependencyManager dm
                    = new DependencyManager(appId,
                            depsCache.toAbsolutePath().toString(),
                            repositories,
                            reset, verbose);

            return dm;
        } catch (NoClassDefFoundError e) {
            throw new RuntimeException("Jar " + jar.getName()
                    + " contains a Dependencies attributes, while the necessary dependency management classes are not found in the jar");
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

    private void printDependencyTree(List<String> dependencies) {
        final DependencyManager dm = (DependencyManager) dependencyManager;
        dm.printDependencyTree(dependencies);
    }

    private List<String> resolveDependencies() {
        if (dependencies == null)
            return null;

        final DependencyManager dm = (DependencyManager) dependencyManager;
        final List<Path> depsJars = dm.resolveDependencies(dependencies);

        List<String> depsPaths = new ArrayList<String>(depsJars.size());
        for (Path p : depsJars)
            depsPaths.add(p.toAbsolutePath().toString());

        return depsPaths;
    }

    private static String getDependencyPath(Object dependencyManager, String p) {
        if (dependencyManager == null)
            throw new RuntimeException("No Dependencies attribute in jar, therefore cannot resolve dependency " + p);
        final DependencyManager dm = (DependencyManager) dependencyManager;
        List<Path> depsJars = dm.resolveDependency(p);

        if (depsJars == null || depsJars.isEmpty())
            throw new RuntimeException("Dependency " + p + " was not found.");
        return depsJars.iterator().next().toAbsolutePath().toString();
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
}
