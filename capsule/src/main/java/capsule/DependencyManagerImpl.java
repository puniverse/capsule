/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.unmodifiableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.crypto.DefaultSettingsDecrypter;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.ConservativeAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.eclipse.aether.version.Version;

/**
 * Uses Aether as the Maven dependency manager.
 */
public class DependencyManagerImpl implements DependencyManager {
    /*
     * see http://git.eclipse.org/c/aether/aether-ant.git/tree/src/main/java/org/eclipse/aether/internal/ant/AntRepoSys.java
     */
    private static final String PROP_USER_HOME = "user.home";
    private static final String PROP_OFFLINE = "capsule.offline";
    private static final String PROP_CONNECT_TIMEOUT = "capsule.connect.timeout";
    private static final String PROP_REQUEST_TIMEOUT = "capsule.request.timeout";
    private static final String PROP_OS_NAME = "os.name";
    private static final String ENV_MAVEN_HOME = "M2_HOME";
    private static final String ENV_CONNECT_TIMEOUT = "CAPSULE_CONNECT_TIMEOUT";
    private static final String ENV_REQUEST_TIMEOUT = "CAPSULE_REQUEST_TIMEOUT";
    private static final String LATEST_VERSION = "[0,)";
    private static final String SETTINGS_XML = "settings.xml";

    private static final Path MAVEN_HOME = getMavenHome();
    private static final Path DEFAULT_LOCAL_MAVEN = Paths.get(System.getProperty(PROP_USER_HOME), ".m2");

    private final Map<String, String> WELL_KNOWN_REPOS = stringMap(
            "central", "https://repo1.maven.org/maven2/",
            "central-http", "http://repo1.maven.org/maven2/",
            "jcenter", "https://jcenter.bintray.com/",
            "jcenter-http", "http://jcenter.bintray.com/",
            "local", "file:" + DEFAULT_LOCAL_MAVEN.resolve("repository")
    );

    private static final int LOG_NONE = 0;
    private static final int LOG_QUIET = 1;
    private static final int LOG_VERBOSE = 2;
    private static final int LOG_DEBUG = 3;
    private static final String LOG_PREFIX = "CAPSULE: ";

    private final boolean forceRefresh;
    private final boolean offline;
    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repos;
    private final int logLevel;
    private final Settings settings;

    public DependencyManagerImpl(Path localRepoPath, List<String> repos, boolean forceRefresh, Boolean offline, boolean allowSnapshots, int logLevel) {
        this.system = newRepositorySystem();
        this.settings = getSettings();
        this.forceRefresh = forceRefresh;
        this.offline = offline != null ? offline : (settings != null ? settings.isOffline() : false);
        this.logLevel = logLevel;

        final LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());
        this.session = newRepositorySession(system, localRepo);

        if (repos == null)
            repos = Arrays.asList("central");

        final RepositoryPolicy releasePolicy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        final RepositoryPolicy snapshotPolicy = allowSnapshots ? releasePolicy : new RepositoryPolicy(false, null, null);
        this.repos = new ArrayList<RemoteRepository>();
        for (String repo : repos)
            this.repos.add(newRemoteRepository(repo, WELL_KNOWN_REPOS.containsKey(repo) ? WELL_KNOWN_REPOS.get(repo) : repo, releasePolicy, snapshotPolicy));

        log(LOG_VERBOSE, "Dependency manager initialized with repositories: " + this.repos);
    }

    private static RepositorySystem newRepositorySystem() {
        /*
         * We're using DefaultServiceLocator rather than Guice/Sisu because it's more lightweight.
         * This method pulls together the necessary Aether components and plugins.
         */
        final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable ex) {
                throw new RuntimeException("Service creation failed for type " + type.getName() + " with impl " + impl, ex);
            }
        });

        locator.addService(org.eclipse.aether.spi.connector.RepositoryConnectorFactory.class, org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory.class);
        locator.addService(org.eclipse.aether.spi.connector.transport.TransporterFactory.class, org.eclipse.aether.transport.http.HttpTransporterFactory.class);
        locator.addService(org.eclipse.aether.spi.connector.transport.TransporterFactory.class, org.eclipse.aether.transport.file.FileTransporterFactory.class);

        // Takari (support concurrent downloads)
        locator.setService(org.eclipse.aether.impl.SyncContextFactory.class, LockingSyncContextFactory.class);
        locator.setService(org.eclipse.aether.spi.io.FileProcessor.class, LockingFileProcessor.class);

        return locator.getService(RepositorySystem.class);
    }

    private static Settings getSettings() {
        final DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setUserSettingsFile(DEFAULT_LOCAL_MAVEN.resolve(SETTINGS_XML).toFile());
        request.setGlobalSettingsFile(MAVEN_HOME != null ? MAVEN_HOME.resolve("conf").resolve(SETTINGS_XML).toFile() : null);
        request.setSystemProperties(getSystemProperties());

        try {
            final SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
            final Settings settings = settingsBuilder.build(request).getEffectiveSettings();
            final SettingsDecrypter settingsDecrypter = new DefaultSettingsDecrypter();
            final SettingsDecryptionResult result = settingsDecrypter.decrypt(new DefaultSettingsDecryptionRequest(settings));
            settings.setServers(result.getServers());
            settings.setProxies(result.getProxies());

            System.out.println("SETTINGS: " + settings);
            return settings;
        } catch (SettingsBuildingException e) {
            throw new RuntimeException(e);
        }
    }

    private RepositorySystemSession newRepositorySession(RepositorySystem system, LocalRepository localRepo) {
        final DefaultRepositorySystemSession s = MavenRepositorySystemUtils.newSession();

        s.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, propertyOrEnv(PROP_CONNECT_TIMEOUT, ENV_CONNECT_TIMEOUT));
        s.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, propertyOrEnv(PROP_REQUEST_TIMEOUT, ENV_REQUEST_TIMEOUT));
        s.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);

        s.setOffline(offline);
        s.setUpdatePolicy(forceRefresh ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_NEVER);

        s.setLocalRepositoryManager(system.newLocalRepositoryManager(s, localRepo));

        s.setProxySelector(getProxySelector());
        s.setMirrorSelector(getMirrorSelector());
        s.setAuthenticationSelector(getAuthSelector());

        if (logLevel > LOG_NONE) {
            final PrintStream out = prefixStream(System.err, LOG_PREFIX);
            s.setTransferListener(new ConsoleTransferListener(isLogging(LOG_VERBOSE), out));
            s.setRepositoryListener(new ConsoleRepositoryListener(isLogging(LOG_VERBOSE), out));
        }

        return s;
    }

    private static RemoteRepository newRemoteRepository(String name, String url, RepositoryPolicy releasePolicy, RepositoryPolicy snapshotPolicy) {
        if (url.startsWith("file:")) {
            releasePolicy = new RepositoryPolicy(releasePolicy.isEnabled(), releasePolicy.getUpdatePolicy(), RepositoryPolicy.CHECKSUM_POLICY_IGNORE);
            snapshotPolicy = releasePolicy;
        }
        return new RemoteRepository.Builder(name, "default", url).setReleasePolicy(releasePolicy).setSnapshotPolicy(snapshotPolicy).build();
    }

    @Override
    public void printDependencyTree(List<String> coords, String type, PrintStream out) {
        printDependencyTree(collect().setDependencies(toDependencies(coords, type)), out);
    }

    @Override
    public void printDependencyTree(String coords, String type, PrintStream out) {
        printDependencyTree(collect().setRoot(toDependency(coords, type)), out);
    }

    private void printDependencyTree(CollectRequest collectRequest, PrintStream out) {
        try {
            CollectResult collectResult = system.collectDependencies(session, collectRequest);
            collectResult.getRoot().accept(new ConsoleDependencyGraphDumper(out));
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Path> resolveDependencies(List<String> coords, String type) {
        return resolve(collect().setDependencies(toDependencies(coords, type)));
    }

    @Override
    public List<Path> resolveDependency(String coords, String type) {
        return resolve(collect().setRoot(toDependency(coords, type))); // resolveDependencies(Collections.singletonList(coords), type);
    }

    private List<Path> resolve(CollectRequest collectRequest) {
        try {
            final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
            final List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();

            final List<Path> jars = new ArrayList<Path>();
            for (ArtifactResult artifactResult : artifactResults)
                jars.add(artifactResult.getArtifact().getFile().toPath().toAbsolutePath());
            return jars;
        } catch (DependencyResolutionException e) {
            throw new RuntimeException("Error resolving dependencies.", e);
        }
    }

    private CollectRequest collect() {
        return new CollectRequest().setRepositories(repos);
    }

    @Override
    public String getLatestVersion(String coords, String type) {
        return artifactToCoords(getLatestVersion0(coords, type));
    }

    private Artifact getLatestVersion0(String coords, String type) {
        try {
            final Artifact artifact = coordsToArtifact(coords, type);
            final String version;
            if (isVersionRange(artifact.getVersion())) {
                final VersionRangeRequest request = new VersionRangeRequest().setRepositories(repos).setArtifact(artifact);
                final VersionRangeResult result = system.resolveVersionRange(session, request);
                final Version highestVersion = result.getHighestVersion();
                version = highestVersion != null ? highestVersion.toString() : null;
            } else {
                final VersionRequest request = new VersionRequest().setRepositories(repos).setArtifact(artifact);
                final VersionResult result = system.resolveVersion(session, request);
                version = result.getVersion();
            }
            if (version == null)
                throw new RuntimeException("Could not find any version of artifact " + coords + " (looking for: " + artifact + ")");
            return artifact.setVersion(version);
        } catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isVersionRange(String version) {
        return version.startsWith("(") || version.startsWith("[");
    }

    // visible for testing
    static Dependency toDependency(String coords, String type) {
        return new Dependency(coordsToArtifact(coords, type), JavaScopes.RUNTIME, false, getExclusions(coords));
    }

    private static Dependency toDependency(Artifact artifact) {
        return new Dependency(artifact, JavaScopes.RUNTIME, false, null);
    }

    private static List<Dependency> toDependencies(List<String> coords, String type) {
        final List<Dependency> deps = new ArrayList<Dependency>(coords.size());
        for (String c : coords)
            deps.add(toDependency(c, type));
        return deps;
    }

    private static String artifactToCoords(Artifact artifact) {
        if (artifact == null)
            return null;
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion()
                + ((artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) ? (":" + artifact.getClassifier()) : "");
    }

    private static final Pattern PAT_DEPENDENCY = Pattern.compile("(?<groupId>[^:\\(]+):(?<artifactId>[^:\\(]+)(:(?<version>\\(?[^:\\(]*))?(:(?<classifier>[^:\\(]+))?(\\((?<exclusions>[^\\(\\)]*)\\))?");

    static Artifact coordsToArtifact(final String depString, String type) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        final String groupId = m.group("groupId");
        final String artifactId = m.group("artifactId");
        String version = m.group("version");
        if (version == null || version.isEmpty())
            version = LATEST_VERSION;
        final String classifier = m.group("classifier");
        return new DefaultArtifact(groupId, artifactId, classifier, type, version);
    }

    static Collection<Exclusion> getExclusions(String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        if (m.group("exclusions") == null || m.group("exclusions").isEmpty())
            return null;

        final List<String> exclusionPatterns = Arrays.asList(m.group("exclusions").split(","));
        final List<Exclusion> exclusions = new ArrayList<Exclusion>();
        for (String ex : exclusionPatterns) {
            String[] coords = ex.trim().split(":");
            if (coords.length != 2)
                throw new IllegalArgumentException("Illegal exclusion dependency coordinates: " + depString + " (in exclusion " + ex + ")");
            exclusions.add(new Exclusion(coords[0], coords[1], "*", "*"));
        }
        return exclusions;
    }

    private static PrintStream prefixStream(PrintStream out, final String prefix) {
        return new PrintStream(out) {
            @Override
            public void println(String x) {
                super.println(prefix + x);
            }
        };
    }

    private static String propertyOrEnv(String propName, String envVar) {
        String val = System.getProperty(propName);
        if (val == null)
            val = emptyToNull(System.getenv(envVar));
        return val;
    }

    private static Path getMavenHome() {
        String mhome = emptyToNull(System.getenv(ENV_MAVEN_HOME));
        if (mhome == null)
            mhome = System.getProperty("maven.home");
        return mhome != null ? Paths.get(mhome) : null;
    }

    public static File findUserSettings() {
        return new File(new File(System.getProperty(PROP_USER_HOME), ".m2"), SETTINGS_XML);
    }

    private ProxySelector getProxySelector() {
        final DefaultProxySelector selector = new DefaultProxySelector();

        for (org.apache.maven.settings.Proxy proxy : settings.getProxies()) {
            AuthenticationBuilder auth = new AuthenticationBuilder();
            auth.addUsername(proxy.getUsername()).addPassword(proxy.getPassword());
            selector.add(new org.eclipse.aether.repository.Proxy(proxy.getProtocol(), proxy.getHost(),
                    proxy.getPort(), auth.build()),
                    proxy.getNonProxyHosts());
        }

        return selector;
    }

    private MirrorSelector getMirrorSelector() {
        final DefaultMirrorSelector selector = new DefaultMirrorSelector();

        for (org.apache.maven.settings.Mirror mirror : settings.getMirrors())
            selector.add(String.valueOf(mirror.getId()), mirror.getUrl(), mirror.getLayout(), false, mirror.getMirrorOf(), mirror.getMirrorOfLayouts());

        return selector;
    }

    private AuthenticationSelector getAuthSelector() {
        final DefaultAuthenticationSelector selector = new DefaultAuthenticationSelector();

        for (Server server : settings.getServers()) {
            AuthenticationBuilder auth = new AuthenticationBuilder();
            auth.addUsername(server.getUsername()).addPassword(server.getPassword());
            auth.addPrivateKey(server.getPrivateKey(), server.getPassphrase());
            selector.add(server.getId(), auth.build());
        }

        return new ConservativeAuthenticationSelector(selector);
    }

    private static Properties getSystemProperties() {
        Properties props = new Properties();
        getEnvProperties(props);
        props.putAll(System.getProperties());
        return props;
    }

    private static Properties getEnvProperties(Properties props) {
        if (props == null)
            props = new Properties();

        boolean envCaseInsensitive = isWindows();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if (envCaseInsensitive)
                key = key.toUpperCase(Locale.ENGLISH);
            key = "env." + key;
            props.put(key, entry.getValue());
        }
        return props;
    }

    private static String emptyToNull(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isWindows() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("windows");
    }

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

    private static Map<String, String> stringMap(String... ss) {
        final Map<String, String> m = new HashMap<>();
        for (int i = 0; i < ss.length / 2; i++)
            m.put(ss[i * 2], ss[i * 2 + 1]);
        return Collections.unmodifiableMap(m);
    }

    // necessary if we want to forgo Guice/Sisu injection and use DefaultServiceLocator instead
    private static final io.takari.filemanager.FileManager takariFileManager = new io.takari.filemanager.internal.DefaultFileManager();

    public static class LockingFileProcessor extends io.takari.aether.concurrency.LockingFileProcessor {
        public LockingFileProcessor() {
            super(takariFileManager);
        }
    }

    public static class LockingSyncContextFactory extends io.takari.aether.concurrency.LockingSyncContextFactory {
        public LockingSyncContextFactory() {
            super(takariFileManager);
        }
    }
}
