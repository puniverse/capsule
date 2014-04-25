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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    private static final String PROP_OFFLINE = "capsule.offline";
    private static final String PROP_CONNECT_TIMEOUT = "capsule.connect.timeout";
    private static final String PROP_REQUEST_TIMEOUT = "capsule.request.timeout";

    private final String appId;
    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repos;

    public DependencyManager(String appId, Path localRepoPath, List<String> repos, boolean forceRefresh, boolean verbose) {
        this.appId = appId;
        this.system = newRepositorySystem();

        this.session = newRepositorySession(system, localRepoPath, forceRefresh, verbose);

        final RepositoryPolicy policy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_WARN);
        this.repos = new ArrayList<RemoteRepository>();

        if (repos == null)
            this.repos.add(newCentralRepository(policy));
        else {
            for (String repo : repos) {
                if ("central".equals(repo))
                    this.repos.add(newCentralRepository(policy));
                else
                    this.repos.add(new RemoteRepository.Builder(null, null, repo).setPolicy(policy).build());
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

    private static RepositorySystemSession newRepositorySession(RepositorySystem system, Path localRepoPath, boolean forceRefresh, boolean verbose) {
        final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        final LocalRepository localRepo = new LocalRepository(localRepoPath.toString());

        session.setConfigProperty(ConfigurationProperties.CONNECT_TIMEOUT, System.getProperty(PROP_CONNECT_TIMEOUT));
        session.setConfigProperty(ConfigurationProperties.REQUEST_TIMEOUT, System.getProperty(PROP_REQUEST_TIMEOUT));
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);

        session.setOffline("".equals(System.getProperty(PROP_OFFLINE)) || Boolean.parseBoolean(System.getProperty(PROP_OFFLINE)));
        session.setUpdatePolicy(forceRefresh ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_NEVER);

        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        final PrintStream out = new PrintStream(System.err) {
            @Override
            public void println(String x) {
                super.println("CAPSULE: " + x);
            }
        };
        session.setTransferListener(new ConsoleTransferListener(out));
        session.setRepositoryListener(new ConsoleRepositoryListener(verbose, out));

        return session;
    }

    public void printDependencyTree(List<String> coords) {
        printDependencyTree(collect().setDependencies(toDependencies(coords)));
    }

    public void printDependencyTree(String coords) {
        printDependencyTree(collect().setRoot(toDependency(getLatestVersion(coords))));
    }

    private void printDependencyTree(CollectRequest collectRequest) {
        try {
            CollectResult collectResult = system.collectDependencies(session, collectRequest);
            collectResult.getRoot().accept(new ConsoleDependencyGraphDumper(appId, System.out));
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Path> resolveDependencies(List<String> coords) {
        return resolve(new CollectRequest().setRepositories(repos).setDependencies(toDependencies(coords)));
    }

    public List<Path> resolveDependency(String coords) {
        return resolveDependencies(Collections.singletonList(coords));
    }

    public List<Path> resolveRoot(String coords) {
        return resolve(collect().setRoot(toDependency(getLatestVersion(coords))));
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

    private Artifact getLatestVersion(String coords) {
        try {
            final Artifact artifact = coordsToArtifact(coords);
            final VersionRangeRequest versionRangeRequest = new VersionRangeRequest()
                    .setRepositories(repos)
                    .setArtifact(artifact);
            final VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
            final Version highestVersion = versionRangeResult.getHighestVersion();
            return artifact.setVersion(highestVersion.toString());
        } catch (VersionRangeResolutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static RemoteRepository newCentralRepository(RepositoryPolicy policy) {
        return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").setPolicy(policy).build();
    }

    private static Dependency toDependency(String coords) {
        return new Dependency(coordsToArtifact(coords), JavaScopes.RUNTIME, false, getExclusions(coords));
    }

    private static Dependency toDependency(Artifact artifact) {
        return new Dependency(artifact, JavaScopes.RUNTIME, false, null);
    }

    private static String toString(Dependency dep) {
        final Artifact artifact = dep.getArtifact();
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private static List<String> toString(List<Dependency> deps) {
        final List<String> strs = new ArrayList<String>(deps.size());
        for (Dependency dep : deps)
            strs.add(toString(dep));
        return strs;
    }

    private static List<Dependency> toDependencies(List<String> coords) {
        final List<Dependency> deps = new ArrayList<Dependency>(coords.size());
        for (String c : coords)
            deps.add(toDependency(c));
        return deps;
    }

    private static Artifact coordsToArtifact(final String coordsString0) {
        int parenIndex = coordsString0.indexOf('(');
        final String coordsString;
        if (parenIndex >= 0)
            coordsString = coordsString0.substring(0, parenIndex);
        else
            coordsString = coordsString0;

        String[] coords = coordsString.split(":");
        if (coords.length > 4 || coords.length < 3)
            throw new IllegalArgumentException("Illegal dependency coordinates: " + coordsString0);
        final String groupId = coords[0];
        final String artifactId = coords[1];
        final String version = coords[2];
        final String classifier = coords.length > 3 ? coords[3] : null;
        return new DefaultArtifact(groupId, artifactId, classifier, "jar", version);
    }

    private static Collection<Exclusion> getExclusions(String coordsString) {
        final List<String> exclusionPatterns = getExclusionPatterns(coordsString);
        if (exclusionPatterns == null)
            return null;
        final List<Exclusion> exclusions = new ArrayList<Exclusion>();
        for (String ex : exclusionPatterns) {
            String[] coords = ex.split(":");
            if (coords.length != 2)
                throw new IllegalArgumentException("Illegal dependency coordinates: " + coordsString + " (in exclusion " + ex + ")");
            exclusions.add(new Exclusion(coords[0], coords[1], "*", "*"));
        }
        return exclusions;
    }

    private static List<String> getExclusionPatterns(String coordsString) {
        final int leftParenIndex = coordsString.indexOf('(');
        if (leftParenIndex < 0)
            return null;
        if (coordsString.lastIndexOf('(') != leftParenIndex)
            throw new IllegalArgumentException("Illegal dependency coordinates: " + coordsString);

        int rightParenIndex = coordsString.indexOf(')');
        if (rightParenIndex < 0)
            throw new IllegalArgumentException("Illegal dependency coordinates: " + coordsString);
        if (coordsString.lastIndexOf(')') != rightParenIndex)
            throw new IllegalArgumentException("Illegal dependency coordinates: " + coordsString);

        final List<String> exclusions = Arrays.asList(coordsString.substring(leftParenIndex + 1, rightParenIndex).split(","));
        return exclusions;
    }
}
