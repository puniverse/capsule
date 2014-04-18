
import co.paralleluniverse.capsule.dependency.DependencyManager;
import co.paralleluniverse.capsule.dependency.PomReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * <ul>
 * <li>{@code Min-Java-Version}</li>
 * <li>{@code App-Class} - the only mandatory attribute</li>
 * <li>{@code App-Version}</li>
 * <li>{@code App-Class-Path} default: the capsule jar root and every jar file found in the capsule jar's root.</li>
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
public final class Capsule {
    private static final String VERSION = "0.1.0-SNAPSHOT";
    private static final String RESET_PROPERTY = "capsule.reset";
    private static final String VERSION_PROPERTY = "capsule.version";
    private static final String LOG_PROPERTY = "capsule.log";
    private static final String TREE_PROPERTY = "capsule.tree";
    private static final String CACHE_DIR_ENV = "CAPSULE_CACHE_DIR";
    private static final String CACHE_NAME_ENV = "CAPSULE_CACHE_NAME";
    private static final String CACHE_DEFAULT_NAME = "capsule";

    private static final String ATTR_MIN_JAVA_VERSION = "Min-Java-Version";
    private static final String ATTR_APP_NAME = "App-Name";
    private static final String ATTR_APP_VERSION = "App-Version";
    private static final String ATTR_APP_CLASS = "App-Class";
    private static final String ATTR_JVM_ARGS = "JVM-Args";
    private static final String ATTR_SYSTEM_PROPERTIES = "System-Properties";
    private static final String ATTR_APP_CLASS_PATH = "App-Class-Path";
    private static final String ATTR_BOOT_CLASS_PATH = "Boot-Class-Path";
    private static final String ATTR_BOOT_CLASS_PATH_A = "Boot-Class-Path-A";
    private static final String ATTR_BOOT_CLASS_PATH_P = "Boot-Class-Path-P";
    private static final String ATTR_LIBRARY_PATH_A = "Library-Path-A";
    private static final String ATTR_LIBRARY_PATH_P = "Library-Path-P";
    private static final String ATTR_JAVA_AGENTS = "Java-Agents";
    private static final String ATTR_REPOSITORIES = "Repositories";
    private static final String ATTR_DEPENDENCIES = "Dependencies";

    private static final String POM_FILE = "pom.xml";

    private static final boolean verbose = "verbose".equals(System.getProperty(LOG_PROPERTY, "quiet"));

    private static final Path cacheDir = getCacheDir();

    private final JarFile jar;
    private final Manifest manifest;
    private final String appId;
    private final Path appCache;
    private final Object dependencyManager;
    private final Object pom;
    private final List<String> repositories;
    private final List<String> dependencies;

    /**
     * Launches the application
     */
    @SuppressWarnings({"BroadCatchBlock", "CallToPrintStackTrace"})
    public static void main(String[] args) {
        try {
            if (System.getProperty(VERSION_PROPERTY) != null) {
                System.err.println("CAPSULE: Version " + VERSION);
                return;
            }

            final Capsule capsule = new Capsule(getJarFile());
            System.err.println("CAPSULE: Launching app " + capsule.appId);
            capsule.ensureExtracted();
            final ProcessBuilder pb = capsule.buildProcess(args);
            pb.inheritIO();

            System.exit(pb.start().waitFor());
        } catch (Throwable t) {
            System.err.println("CAPSULE EXCEPTION: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    private Capsule(JarFile jar) throws IOException {
        this.jar = jar;
        try {
            this.manifest = jar.getManifest();
        } catch (IOException e) {
            throw new RuntimeException("Jar file " + jar.getName() + " does not have a manifest");
        }
        this.appId = getAppId();

        this.pom = (!hasDependenciesAttribute() && hasPom()) ? createPomReader() : null;
        this.repositories = getRepositories();
        this.dependencyManager = (hasDependenciesAttribute() || pom != null) ? createDependencyManager() : null;
        if (dependencyManager != null) {
            this.dependencies = getDependencies();

            if (System.getProperty(TREE_PROPERTY) != null) {
                printDependencyTree(dependencies);
                System.exit(0);
            }
        } else {
            this.dependencies = null;
        }

        this.appCache = getAppCacheDir();
    }

    ProcessBuilder buildProcess(String[] args) {
        verifyRequiredJavaVersion();

        final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        final List<String> cmdLine = runtimeBean.getInputArguments();

        final List<String> appArgs = new ArrayList<String>();
        getModeAndArgs(args, appArgs);

//        final String classPath = runtimeBean.getClassPath();
//        final String bootClassPath = runtimeBean.getBootClassPath();
//        final String libraryPath = runtimeBean.getLibraryPath();
        List<String> command = new ArrayList<String>();

        command.add(getJavaProcessName());

        command.addAll(buildJVMArgs(cmdLine));
        command.addAll(compileSystemProperties(buildSystemProperties(cmdLine)));

        addOption(command, "-Xbootclasspath:", compileClassPath(buildBootClassPath(cmdLine)));
        addOption(command, "-Xbootclasspath/p:", compileClassPath(buildClassPath(ATTR_BOOT_CLASS_PATH_P)));
        addOption(command, "-Xbootclasspath/a:", compileClassPath(buildClassPath(ATTR_BOOT_CLASS_PATH_A)));

        command.add("-classpath");
        command.add(compileClassPath(buildClassPath()));

        for (String jagent : nullToEmpty(buildJavaAgents()))
            command.add("-javaagent:" + jagent);

        command.add(getMainClass());
        command.addAll(appArgs);

        if (verbose)
            System.err.println("CAPSULE: " + join(command, " "));

        return new ProcessBuilder(command);
    }

    private void verifyRequiredJavaVersion() {
        final String minVersion = getAttributes().getValue(ATTR_MIN_JAVA_VERSION);
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

        final List<String> localClassPath = new ArrayList<String>();
        localClassPath.addAll(nullToEmpty(getListAttribute(ATTR_APP_CLASS_PATH)));
        localClassPath.addAll(nullToEmpty(getDefaultClassPath()));
        
        classPath.addAll(toAbsoluteClassPath(appCache, localClassPath));
        if (dependencyManager != null)
            classPath.addAll(resolveDependencies());
        classPath.add(jar.getName());
        
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
        return toAbsoluteClassPath(appCache, getListAttribute(ATTR_BOOT_CLASS_PATH));
    }

    private List<String> buildClassPath(String attr) {
        return toAbsoluteClassPath(appCache, getListAttribute(attr));
    }

    private Map<String, String> buildSystemProperties(List<String> cmdLine) {
        final Map<String, String> systemProerties = new HashMap<String, String>();

        // attribute
        for (String p : nullToEmpty(getListAttribute(ATTR_SYSTEM_PROPERTIES)))
            addSystemProperty(p, systemProerties);

        // library path
        final List<String> libraryPath = new ArrayList<String>();
        libraryPath.addAll(nullToEmpty(getListAttribute(ATTR_LIBRARY_PATH_P)));
        libraryPath.addAll(Arrays.asList(ManagementFactory.getRuntimeMXBean().getLibraryPath().split(System.getProperty("path.separator"))));
        libraryPath.addAll(nullToEmpty(getListAttribute(ATTR_LIBRARY_PATH_A)));
        libraryPath.add(toAbsoluteClassPath(appCache, ""));
        systemProerties.put("java.library.path", compileClassPath(libraryPath));

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

    private static String getModeAndArgs(String[] args, List<String> argsList) {
        String mode = null;
        for (String a : args) {
            if (a.startsWith("-capsule:mode:")) {
                if (mode != null)
                    throw new IllegalArgumentException("The -capsule:mode: argument is given more than once");
                mode = a.substring("-capsule:mode:".length());
            }
            argsList.add(a);
        }
        return mode;
    }

    private List<String> buildJavaAgents() {
        final List<String> agents0 = getListAttribute(ATTR_JAVA_AGENTS);

        if (agents0 == null)
            return null;
        final List<String> agents = new ArrayList<String>(agents0.size());
        for (String agent : agents0) {
            final String agentJar = getBefore(agent, '=');
            final String agentOptions = getAfter(agent, '=');
            final String agentPath = getPath(appCache, dependencyManager, agentJar);
            agents.add(agentPath + (agentOptions != null ? "=" + agentOptions : ""));
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
        String appName = getAttributes().getValue(ATTR_APP_NAME);
        if (appName == null) {
            if (pom != null)
                return getPomAppName();
            appName = getMainClass();
        }

        final String version = getAttributes().getValue(ATTR_APP_VERSION);
        return appName + (version != null ? "_" + version : "");
    }

    private String getMainClass() {
        final String appClass = getAttributes().getValue(ATTR_APP_CLASS);
        if (appClass == null)
            throw new RuntimeException("Manifest of jar file " + jar.getName() + " does not contain an App-Class attribute");
        return appClass;
    }

    void ensureExtracted() {
        final boolean reset = Boolean.parseBoolean(System.getProperty(RESET_PROPERTY, "false"));
        if (reset || !isUpToDate(jar, appCache)) {
            try {
                if (verbose)
                    System.err.println("CAPSULE: Extracting " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath());
                deleteCache(appCache);
                Files.createDirectory(appCache);
                extractJar(jar, appCache);
                Files.createFile(appCache.resolve(".extracted"));
            } catch (IOException e) {
                throw new RuntimeException("Exception while extracting jar " + jar.getName() + " to app cache directory " + appCache.toAbsolutePath(), e);
            }
        }
    }

    private List<String> getDefaultClassPath() {
        try {
            final List<String> cp = new ArrayList<String>();
            cp.add("");
            Files.walkFileTree(appCache, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().endsWith(".jar"))
                        cp.add(file.getFileName().toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return dir.equals(appCache) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE; // add only jars in root dir
                }
            });
            return cp;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getPath(Path appCache, Object dependencyManager, List<String> ps) {
        if (ps == null)
            return null;
        final List<String> aps = new ArrayList<String>(ps.size());
        for (String p : ps)
            aps.add(getPath(appCache, dependencyManager, p));
        return aps;
    }

    private static String getPath(Path appCache, Object dependencyManager, String p) {
        return isDependency(p) ? getDependencyPath(dependencyManager, p) : toAbsoluteClassPath(appCache, p);
    }

    private static List<String> toAbsoluteClassPath(Path appCache, List<String> ps) {
        if (ps == null)
            return null;
        final List<String> aps = new ArrayList<String>(ps.size());
        for (String p : ps)
            aps.add(toAbsoluteClassPath(appCache, p));
        return aps;
    }

    private static String toAbsoluteClassPath(Path appCache, String p) {
        return appCache.resolve(sanitize(p)).toAbsolutePath().toString();
    }

    private static void deleteCache(Path appCacheDir) {
        try {
            Files.walkFileTree(appCacheDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    if (e == null) {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    } else
                        throw e;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Attributes getAttributes() {
        Attributes atts = manifest.getMainAttributes();
//        if (atts == null)
//            throw new RuntimeException("Manifest of jar file " + jar.getName() + " does not contain a Capsule section");
        return atts;
    }

    private List<String> getListAttribute(String attr) {
        final String vals = getAttributes().getValue(attr);
        if (vals == null)
            return null;
        return Arrays.asList(vals.split("\\s+"));
    }

    private static <T> Collection<T> nullToEmpty(Collection<T> coll) {
        return coll != null ? coll : Collections.EMPTY_LIST;
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
        final Path cacheDir;
        final String cacheDirEnv = System.getenv(CACHE_DIR_ENV);
        if (cacheDirEnv != null)
            cacheDir = Paths.get(cacheDirEnv);
        else {
            final String userHome = System.getProperty("user.home");

            final String cacheNameEnv = System.getenv(CACHE_NAME_ENV);
            final String cacheName = cacheNameEnv != null ? cacheNameEnv : CACHE_DEFAULT_NAME;
            if (isWindows())
                cacheDir = Paths.get("AppData", "Local", cacheName);
            else
                cacheDir = Paths.get(userHome, "." + cacheName);
        }
        try {
            if (!Files.exists(cacheDir))
                Files.createDirectory(cacheDir);
            return cacheDir;
        } catch (IOException e) {
            throw new RuntimeException("Error opening cache directory " + cacheDir.toAbsolutePath(), e);
        }
    }

    private static boolean isUpToDate(JarFile jar, Path appCache) {
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
            if (file.getName().equals("about.html"))
                continue;
            if (file.getName().endsWith(".class"))
                continue;
            if (file.getName().startsWith("co/paralleluniverse/capsule/")
//                    || file.getName().startsWith("org/eclipse/aether/")
//                    || file.getName().startsWith("org/apache/maven/")
//                    || file.getName().startsWith("org/apache/http/")
//                    || file.getName().startsWith("org/apache/commons/codec/")
//                    || file.getName().startsWith("licenses/")
                    )
                continue;

            final String dir = getDirectory(file.getName());
            if (dir != null && dir.startsWith("META-INF"))
                continue;

            if (dir != null)
                Files.createDirectories(targetDir.resolve(dir));

            final Path target = targetDir.resolve(file.getName());
            try (InputStream is = jar.getInputStream(file)) {
                Files.copy(is, target);
            }
        }
    }

    private static String getDirectory(String filename) {
        final int index = filename.lastIndexOf('/');
        if (index < 0)
            return null;
        return filename.substring(0, index);
    }

    private static String getJavaProcessName() {
        final String javaHome = System.getProperty("java.home");
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

    private static int compareVersionStrings(String a, String b) {
        String[] as = a.split("\\.");
        String[] bs = b.split("\\.");
        if (as.length != 3)
            throw new IllegalArgumentException("Version " + a + " is illegal. Must be of the form x.y.z[_u]");
        if (bs.length != 3)
            throw new IllegalArgumentException("Version " + b + " is illegal. Must be of the form x.y.z[_u]");
        int ax = Integer.parseInt(as[0]);
        int bx = Integer.parseInt(bs[0]);
        int ay = Integer.parseInt(as[1]);
        int by = Integer.parseInt(bs[1]);
        String[] azu = as[2].split("_");
        String[] bzu = bs[2].split("_");
        int az = Integer.parseInt(azu[0]);
        int bz = Integer.parseInt(bzu[0]);
        int au = azu.length > 1 ? Integer.parseInt(azu[1]) : 0;
        int bu = bzu.length > 1 ? Integer.parseInt(bzu[1]) : 0;

        if (ax != bx)
            return ax - bx;
        if (ay != by)
            return ay - by;
        if (az != bz)
            return az - bz;
        if (au != bu)
            return au - bu;
        return 0;
    }

    private boolean hasPom() {
        return jar.getEntry(POM_FILE) != null;
    }

    private boolean hasDependenciesAttribute() {
        return getAttributes().containsKey(new Attributes.Name(ATTR_DEPENDENCIES));
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

            final boolean reset = Boolean.parseBoolean(System.getProperty(RESET_PROPERTY, "false"));
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

        return !repos.isEmpty() ? repos : null;
    }

    private List<String> getDependencies() {
        List<String> deps = getListAttribute(ATTR_DEPENDENCIES);
        if (deps == null && pom != null)
            deps = getPomDependencies();

        return deps;
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
}
