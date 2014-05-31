/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.eclipse.aether.version.Version;

public class DependencyManager {
    private static final String PROP_LOG = "capsule.log";
    private static final String PROP_CONNECT_TIMEOUT = "capsule.connect.timeout";
    private static final String PROP_REQUEST_TIMEOUT = "capsule.request.timeout";
    private static final boolean debug = "debug".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final boolean verbose = debug || "verbose".equals(System.getProperty(PROP_LOG, "quiet"));
    private static final String MAVEN_CENTRAL_URL = "http://central.maven.org/maven2/";

    private final boolean forceRefresh;
    private final boolean offline;
    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repos;

    public DependencyManager(Path localRepoPath, List<String> repos, boolean forceRefresh, boolean offline) {
        this.system = newRepositorySystem();
        this.forceRefresh = forceRefresh;
        this.offline = offline;

        this.session = newRepositorySession(system, localRepoPath);

        final RepositoryPolicy policy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        this.repos = new ArrayList<RemoteRepository>();

        if (repos == null)
            this.repos.add(newRemoteRepository("central", MAVEN_CENTRAL_URL, policy));
        else {
            for (String repo : repos) {
                if ("central".equals(repo))
                    this.repos.add(newRemoteRepository("central", MAVEN_CENTRAL_URL, policy));
                else
                    this.repos.add(newRemoteRepository(null, repo, policy));
            }
        }
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        // locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        return locator.getService(RepositorySystem.class);
    }

    private RepositorySystemSession newRepositorySession(RepositorySystem system, Path localRepoPath) {
        final DefaultRepositorySystemSession s = MavenRepositorySystemUtils.newSession();
        final LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());

        s.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, System.getProperty(PROP_CONNECT_TIMEOUT));
        s.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, System.getProperty(PROP_REQUEST_TIMEOUT));
        s.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);

        s.setOffline(offline);
        s.setUpdatePolicy(forceRefresh ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_NEVER);

        s.setLocalRepositoryManager(system.newLocalRepositoryManager(s, localRepo));

        final PrintStream out = new PrintStream(System.err) {
            @Override
            public void println(String x) {
                super.println("CAPSULE: " + x);
            }
        };
        s.setTransferListener(new ConsoleTransferListener(out));
        s.setRepositoryListener(new ConsoleRepositoryListener(verbose, out));

        return s;
    }

    public void printDependencyTree(List<String> coords, String type) {
        printDependencyTree(collect().setDependencies(toDependencies(coords, type)));
    }

    public void printDependencyTree(String coords) {
        printDependencyTree(collect().setRoot(toDependency(getLatestVersion0(coords))));
    }

    private void printDependencyTree(CollectRequest collectRequest) {
        try {
            CollectResult collectResult = system.collectDependencies(session, collectRequest);
            collectResult.getRoot().accept(new ConsoleDependencyGraphDumper(System.out));
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Path> resolveDependencies(List<String> coords, String type) {
        return resolve(new CollectRequest().setRepositories(repos).setDependencies(toDependencies(coords, type)));
    }

    public List<Path> resolveDependency(String coords, String type) {
        return resolveDependencies(Collections.singletonList(coords), type);
    }

    public List<Path> resolveRoot(String coords) {
        return resolve(collect().setRoot(toDependency(getLatestVersion0(coords))));
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

    public String getLatestVersion(String coords) {
        return artifactToCoords(getLatestVersion0(coords));
    }

    private Artifact getLatestVersion0(String coords) {
        try {
            final Artifact artifact = coordsToArtifact(coords, "jar");
            final VersionRangeRequest versionRangeRequest = new VersionRangeRequest().setRepositories(repos).setArtifact(artifact);
            final VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
            final Version highestVersion = versionRangeResult.getHighestVersion();
            if (highestVersion == null)
                throw new RuntimeException("Could not find any version of artifact " + coords + " (looking for: " + artifact + ")");
            return artifact.setVersion(highestVersion.toString());
        } catch (VersionRangeResolutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static RemoteRepository newRemoteRepository(String name, String url, RepositoryPolicy policy) {
        return new RemoteRepository.Builder(name, "default", url).setPolicy(policy).build();
    }

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
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private static final Pattern PAT_DEPENDENCY = Pattern.compile("(?<groupId>[^:\\(\\)]+):(?<artifactId>[^:\\(\\)]+)(:(?<version>[^:\\(\\)]*))?(:(?<classifier>[^:\\(\\)]+))?(\\((?<exclusions>[^\\(\\)]*)\\))?");

    static Artifact coordsToArtifact(final String depString, String type) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        final String groupId = m.group("groupId");
        final String artifactId = m.group("artifactId");
        String version = m.group("version");
        if (version == null || version.isEmpty())
            version = "[0,)";
        final String classifier = m.group("classifier");
        return new DefaultArtifact(groupId, artifactId, classifier, type, version);
    }

    static Collection<Exclusion> getExclusions(String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        if(m.group("exclusions") == null || m.group("exclusions").isEmpty())
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
}
