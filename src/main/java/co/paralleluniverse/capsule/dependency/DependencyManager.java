/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0 as published by the Eclipse Foundation.
 */
package co.paralleluniverse.capsule.dependency;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
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
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;

public class DependencyManager {
    private final String appId;
    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repos;

    public DependencyManager(String appId, String localRepoPath, List<String> repos, boolean forceRefresh, boolean verbose) {
        this.appId = appId;
        this.system = newRepositorySystem();
        this.session = newRepositorySession(system, localRepoPath, verbose);

        final RepositoryPolicy policy = getRepoConfig(forceRefresh);
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

    private static RepositorySystemSession newRepositorySession(RepositorySystem system, String localRepoPath, boolean verbose) {
        final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        final LocalRepository localRepo = new LocalRepository(localRepoPath);

        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        final PrintStream out = new PrintStream(System.err) {
            @Override
            public void println(String x) {
                super.println("CAPSULE: " + x); //To change body of generated methods, choose Tools | Templates.
            }
        };
        session.setTransferListener(new ConsoleTransferListener(out));
        session.setRepositoryListener(new ConsoleRepositoryListener(verbose, out));

        return session;
    }

    public void printDependencyTree(List<String> coords) {
        try {
            final CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRepositories(repos);
            collectRequest.setDependencies(toDependencies(coords));
            CollectResult collectResult = system.collectDependencies(session, collectRequest);
            collectResult.getRoot().accept(new ConsoleDependencyGraphDumper(appId, System.out));
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Path> resolveDependencies(List<String> coords) {
        try {
            final CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRepositories(repos);
            collectRequest.setDependencies(toDependencies(coords));

            final DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
            final List<ArtifactResult> artifactResults = system.resolveDependencies(session, dependencyRequest).getArtifactResults();

            List<Path> jars = new ArrayList<Path>();
            for (ArtifactResult artifactResult : artifactResults)
                jars.add(artifactResult.getArtifact().getFile().toPath());
            return jars;
        } catch (DependencyResolutionException e) {
            throw new RuntimeException("Error while resolving dependency " + coords, e);
        }
    }

    public List<Path> resolveDependency(String coords) {
        return resolveDependencies(Collections.singletonList(coords));
    }

    private static RepositoryPolicy getRepoConfig(boolean forceRefresh) {
        return new RepositoryPolicy(true,
                forceRefresh ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_NEVER,
                RepositoryPolicy.CHECKSUM_POLICY_WARN);
    }

    private static RemoteRepository newCentralRepository(RepositoryPolicy policy) {
        return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").setPolicy(policy).build();
    }

    private static Dependency toDependency(String coords) {
        return new Dependency(coordsToArtifact(coords), JavaScopes.RUNTIME, false, getExclusions(coords));
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
