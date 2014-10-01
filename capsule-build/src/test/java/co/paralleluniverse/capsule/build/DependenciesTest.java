/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.build;

import org.junit.Test;
import static org.junit.Assert.*;

import static co.paralleluniverse.capsule.build.Dependencies.*;
import java.util.Arrays;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author circlespainter
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
        assertTrue(
            "com.esotericsoftware.kryo:kryo:[2.23.0)(capsule:*,org.ow2.asm:1.0.0)".equals(
                toCapsuleDependencyString(depWithExclusions)
            ) ||
                "com.esotericsoftware.kryo:kryo:[2.23.0)(org.ow2.asm:1.0.0,capsule:*)".equals(
                    toCapsuleDependencyString(depWithExclusions)
                )
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

    @SafeVarargs
    private static <T> Set<T> set(T... xs) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(xs)));
    }
}
