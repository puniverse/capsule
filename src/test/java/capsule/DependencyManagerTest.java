/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.junit.Test;
import static org.junit.Assert.*;

public class DependencyManagerTest {

    @Test
    public void testParseDependency() {
        Dependency dep;

        dep = dep("com.acme:foo:1.3:jdk8");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("1.3", dep.getArtifact().getVersion());
        assertEquals("jdk8", dep.getArtifact().getClassifier());
        assertEquals(0, dep.getExclusions().size());

        dep = dep("com.acme:foo:1.3");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("1.3", dep.getArtifact().getVersion());
        assertEquals("", dep.getArtifact().getClassifier());
        assertEquals(0, dep.getExclusions().size());

        dep = dep("com.acme:foo");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("[0,)", dep.getArtifact().getVersion());
        assertEquals("", dep.getArtifact().getClassifier());
        assertEquals(0, dep.getExclusions().size());

        dep = dep("com.acme:foo::jdk8");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("[0,)", dep.getArtifact().getVersion());
        assertEquals("jdk8", dep.getArtifact().getClassifier());
        assertEquals(0, dep.getExclusions().size());

        dep = dep("com.acme:foo:1.3:jdk8(org.apache:log4j,javax.jms:jms-api)");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("1.3", dep.getArtifact().getVersion());
        assertEquals("jdk8", dep.getArtifact().getClassifier());
        assertEquals(2, dep.getExclusions().size());
        assertEquals("org.apache", exc(dep, 0).getGroupId());
        assertEquals("log4j", exc(dep, 0).getArtifactId());
        assertEquals("javax.jms", exc(dep, 1).getGroupId());
        assertEquals("jms-api", exc(dep, 1).getArtifactId());

        dep = dep("com.acme:foo:1.3(org.apache:log4j,javax.jms:jms-api)");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("1.3", dep.getArtifact().getVersion());
        assertEquals("", dep.getArtifact().getClassifier());
        assertEquals(2, dep.getExclusions().size());
        assertEquals("org.apache", exc(dep, 0).getGroupId());
        assertEquals("log4j", exc(dep, 0).getArtifactId());
        assertEquals("javax.jms", exc(dep, 1).getGroupId());
        assertEquals("jms-api", exc(dep, 1).getArtifactId());
        
        dep = dep("com.acme:foo(org.apache:log4j,javax.jms:jms-api)");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("[0,)", dep.getArtifact().getVersion());
        assertEquals("", dep.getArtifact().getClassifier());
        assertEquals(2, dep.getExclusions().size());
        assertEquals("org.apache", exc(dep, 0).getGroupId());
        assertEquals("log4j", exc(dep, 0).getArtifactId());
        assertEquals("javax.jms", exc(dep, 1).getGroupId());
        assertEquals("jms-api", exc(dep, 1).getArtifactId());

        dep = dep("com.acme:foo::jdk8(org.apache:log4j,javax.jms:jms-api)");
        assertEquals("com.acme", dep.getArtifact().getGroupId());
        assertEquals("foo", dep.getArtifact().getArtifactId());
        assertEquals("[0,)", dep.getArtifact().getVersion());
        assertEquals("jdk8", dep.getArtifact().getClassifier());
        assertEquals(2, dep.getExclusions().size());
        assertEquals("org.apache", exc(dep, 0).getGroupId());
        assertEquals("log4j", exc(dep, 0).getArtifactId());
        assertEquals("javax.jms", exc(dep, 1).getGroupId());
        assertEquals("jms-api", exc(dep, 1).getArtifactId());
    }

    private static Dependency dep(String desc) {
        return DependencyManager.toDependency(desc, "jar");
    }

    private static Exclusion exc(Dependency dep, int i) {
        return dep.getExclusions().toArray(new Exclusion[0])[i];
    }
}
