package co.paralleluniverse.capsule.build.test;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import static capsule.Lang.*;
import static co.paralleluniverse.capsule.build.Dependencies.*;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * Created by circlespainter on 01/10/14.
 */
public class DependenciesTest {
    @Test
    public void testDeps() {
        assertEquals("com.esotericsoftware.kryo:kryo:LATEST", toCapsuleDependencyString(depWithoutExclusions));
        assertEquals (
            "com.esotericsoftware.kryo:kryo:[2.23.0)(org.ow2.asm:1.0.0)",
            toCapsuleDependencyString(depWithExclusion)
        );
        assertEquals (
            "com.esotericsoftware.kryo:kryo:2.23.0(org.ow2.asm:*)",
            toCapsuleDependencyString(depWithExclusionWildcardExplicit)
        );
        assertEquals (
            "com.esotericsoftware.kryo:kryo:RELEASE(org.ow2.asm:*)",
            toCapsuleDependencyString(depWithExclusionWildcardImplicit)
        );
        assertEquals (
            "com.esotericsoftware.kryo:kryo:[2.23.0)(org.ow2.asm:1.0.0,capsule:*)",
            toCapsuleDependencyString(depWithExclusions)
        );
    }

    @Test
    public void testRepos() {
        assertEquals("central", toCapsuleRepositoryString(repoDefault));
        assertEquals("http://clojars.org/repo", toCapsuleRepositoryString(repoUrl));
    }

    private static final Dependency depWithoutExclusions =
        new Dependency (
            new DefaultArtifact("com.esotericsoftware.kryo", "kryo", null, null, "LATEST"),
            JavaScopes.RUNTIME
        );

    private static final Dependency depWithExclusion =
        new Dependency (
            new DefaultArtifact("com.esotericsoftware.kryo", "kryo", null, null, "[2.23.0)"),
            JavaScopes.RUNTIME,
            false,
            set(new Exclusion("org.ow2.asm", "1.0.0", null, null))
        );

    private static final Dependency depWithExclusions =
        new Dependency (
            new DefaultArtifact("com.esotericsoftware.kryo", "kryo", null, null, "[2.23.0)"),
            JavaScopes.RUNTIME,
            false,
            set (
                new Exclusion("org.ow2.asm", "1.0.0", null, null),
                new Exclusion("capsule", null, null, null)
            )
        );

    private static final Dependency depWithExclusionWildcardImplicit =
        new Dependency (
            new DefaultArtifact("com.esotericsoftware.kryo", "kryo", null, null, "RELEASE"),
            JavaScopes.RUNTIME,
            false,
            set(new Exclusion("org.ow2.asm", null, null, null))
        );

    private static final Dependency depWithExclusionWildcardExplicit =
        new Dependency (
            new DefaultArtifact("com.esotericsoftware.kryo", "kryo", null, null, "2.23.0"),
            JavaScopes.RUNTIME,
            false,
            set(new Exclusion("org.ow2.asm", "*", null, null))
        );

    private static final RemoteRepository repoDefault =
        new RemoteRepository.Builder("central", null, null).build();

    private static final RemoteRepository repoUrl =
        new RemoteRepository.Builder(null, null, "http://clojars.org/repo").build();
}
