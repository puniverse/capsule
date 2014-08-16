/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import co.paralleluniverse.capsule.Jar;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.truth0.Truth.*;
import static org.mockito.Mockito.*;

public class CapsuleTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    /*
     * All the tests in this test suite use an in-memory file system, and don't 
     * write to the disk at all.
     */
    private final FileSystem fs = Jimfs.newFileSystem();
    private final Path cache = fs.getPath("/cache");

    @After
    public void tearDown() throws Exception {
        fs.close();
    }
    
    @Test
    public void testParseJavaVersion() {
        int[] ver;

        ver = Capsule.parseJavaVersion("1.8.0");
        assertArrayEquals(ver, ints(1, 8, 0, 0, 0));
        assertEquals("1.8.0", Capsule.toJavaVersionString(ver));

        ver = Capsule.parseJavaVersion("1.8.0_30");
        assertArrayEquals(ver, ints(1, 8, 0, 30, 0));
        assertEquals("1.8.0_30", Capsule.toJavaVersionString(ver));

        ver = Capsule.parseJavaVersion("1.8.0-rc");
        assertArrayEquals(ver, ints(1, 8, 0, 0, -1));
        assertEquals("1.8.0-rc", Capsule.toJavaVersionString(ver));

        ver = Capsule.parseJavaVersion("1.8.0_30-ea");
        assertArrayEquals(ver, ints(1, 8, 0, 30, -3));
        assertEquals("1.8.0_30-ea", Capsule.toJavaVersionString(ver));
    }

    @Test
    public void testCompareVersions() {
        assertTrue(Capsule.compareVersions("1.8.0_30-ea", "1.8.0_30") < 0);
        assertTrue(Capsule.compareVersions("1.8.0_30-ea", "1.8.0_20") > 0);
        assertTrue(Capsule.compareVersions("1.8.0-ea", "1.8.0_20") < 0);
        assertTrue(Capsule.compareVersions("1.8.0-ea", "1.8.0") < 0);
        assertTrue(Capsule.compareVersions("1.8.0-ea", "1.7.0") > 0);
    }

    @Test
    public void testShortJavaVersion() {
        assertEquals("1.8.0", Capsule.shortJavaVersion("8"));
        assertEquals("1.8.0", Capsule.shortJavaVersion("1.8"));
        assertEquals("1.8.0", Capsule.shortJavaVersion("1.8.0"));
    }

    @Test
    public void isJavaDir() {
        assertEquals("1.7.0", Capsule.isJavaDir("jre7"));
        assertEquals("1.7.0_45", Capsule.isJavaDir("jdk1.7.0_45"));
        assertEquals("1.7.0_51", Capsule.isJavaDir("jdk1.7.0_51.jdk"));
        assertEquals("1.7.0", Capsule.isJavaDir("1.7.0.jdk"));
        assertEquals("1.8.0", Capsule.isJavaDir("jdk1.8.0.jdk"));
    }

    @Test
    public void testDelete() throws Exception {
        Files.createDirectories(fs.getPath("a", "b", "c"));
        Files.createDirectories(fs.getPath("a", "b1"));
        Files.createDirectories(fs.getPath("a", "b", "c1"));
        Files.createFile(fs.getPath("a", "x"));
        Files.createFile(fs.getPath("a", "b", "x"));
        Files.createFile(fs.getPath("a", "b1", "x"));
        Files.createFile(fs.getPath("a", "b", "c", "x"));
        Files.createFile(fs.getPath("a", "b", "c1", "x"));

        assertTrue(Files.exists(fs.getPath("a")));
        assertTrue(Files.isDirectory(fs.getPath("a")));

        //Files.delete(fs.getPath("a"));
        Capsule.delete(fs.getPath("a"));

        assertTrue(!Files.exists(fs.getPath("a")));
    }

    @Test
    public void testSimpleExtract() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("a.class", Jar.toInputStream("", UTF8))
                .addEntry("b.txt", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.class", Jar.toInputStream("", UTF8))
                .addEntry("META-INF/x.txt", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        // dumpFileSystem(fs);
        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals("com.acme.Foo", getProperty(pb, "capsule.app"));
        assertEquals(appCache.toString(), getProperty(pb, "capsule.dir"));
//        assertTrue(pb.command().contains("-Dcapsule.app=com.acme.Foo"));
//        assertTrue(pb.command().contains("-Dcapsule.dir=" + appCache));

        assertEquals(list("com.acme.Foo", "hi", "there"), getMainAndArgs(pb));

        assertTrue(Files.isDirectory(cache));
        assertTrue(Files.isDirectory(cache.resolve("apps")));
        assertTrue(Files.isDirectory(appCache));
        assertTrue(Files.isRegularFile(appCache.resolve(".extracted")));
        assertTrue(Files.isRegularFile(appCache.resolve("foo.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("b.txt")));
        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));
        assertTrue(!Files.isRegularFile(appCache.resolve("a.class")));
        assertTrue(!Files.isRegularFile(appCache.resolve("lib").resolve("b.class")));
        assertTrue(!Files.isDirectory(appCache.resolve("META-INF")));
        assertTrue(!Files.isRegularFile(appCache.resolve("META-INF").resolve("x.txt")));

        ASSERT.that(getClassPath(pb)).has().item(getPath("capsule.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache);
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        ASSERT.that(getClassPath(pb)).has().noneOf(appCache.resolve("lib").resolve("a.jar"));
    }

    @Test
    public void testNoExtract() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");
        assertTrue(!Files.isDirectory(appCache));
    }

    @Test
    public void whenNoNameAndPomTakeIdFromPom() throws Exception {
        Model pom = newPom();
        pom.setGroupId("com.acme");
        pom.setArtifactId("foo");
        pom.setVersion("1.0");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("pom.xml", toInputStream(pom));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        String appId = capsule.appId(null);
        assertEquals("com.acme_foo_1.0", appId);
    }

    @Test
    public void testClassPath() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("App-Class-Path", list("lib/a.jar", "lib/b.jar"))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));

        ASSERT.that(getClassPath(pb)).has().item(getPath("capsule.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache);
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("a.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("b.jar"));
    }

    @Test
    public void testBootClassPath1() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path-A", list("lib/a.jar"))
                .setListAttribute("Boot-Class-Path-P", list("lib/b.jar"))
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "lib/d.jar"))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/c.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/d.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("c.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("d.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/a"))).isEqualTo(list(appCache.resolve("lib").resolve("a.jar")));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
    }

    @Test
    public void testBootClassPath2() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path-A", list("lib/a.jar"))
                .setListAttribute("Boot-Class-Path-P", list("lib/b.jar"))
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "lib/d.jar"))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/c.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/d.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list("-Xbootclasspath:/foo/bar");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(getOption(pb, "-Xbootclasspath")).isEqualTo("/foo/bar");
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().noneOf(appCache.resolve("lib").resolve("c.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().noneOf(appCache.resolve("lib").resolve("d.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/a"))).isEqualTo(list(appCache.resolve("lib").resolve("a.jar")));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
    }

    @Test
    public void testBootClassPathWithDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path-A", list("com.acme:baz:3.4"))
                .setListAttribute("Boot-Class-Path-P", list("lib/b.jar"))
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "com.acme:bar:1.2"))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/c.jar", Jar.toInputStream("", UTF8));

        DependencyManager dm = mock(DependencyManager.class);
        Path barPath = cache.resolve("deps").resolve("com.acme").resolve("bar").resolve("bar-1.2.jar");
        when(dm.resolveDependency("com.acme:bar:1.2", "jar")).thenReturn(list(barPath));
        Path bazPath = cache.resolve("deps").resolve("com.acme").resolve("baz").resolve("bar-3.4.jar");
        when(dm.resolveDependency("com.acme:baz:3.4", "jar")).thenReturn(list(bazPath));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("c.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(barPath);
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/a"))).has().item(bazPath);
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
    }

    @Test
    public void testBootClassPathWithEmbeddedDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path-P", list("lib/b.jar"))
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "com.acme:bar:1.2"))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/c.jar", Jar.toInputStream("", UTF8))
                .addEntry("bar-1.2.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("c.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("bar-1.2.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
    }

    @Test
    public void testDependencies1() throws Exception {
        List<String> deps = list("com.acme:bar:1.2", "com.acme:baz:3.4:jdk8");
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Dependencies", deps)
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        DependencyManager dm = mock(DependencyManager.class);

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        verify(dm).resolveDependencies(deps, "jar");
    }

    @Test
    public void testPomDependencies1() throws Exception {
        List<String> deps = list("com.acme:bar:1.2", "com.acme:baz:3.4:jdk8");

        Model pom = newPom();
        pom.setGroupId("com.acme");
        pom.setArtifactId("foo");
        pom.setVersion("1.0");
        for (String dep : deps)
            pom.addDependency(coordsToDependency(dep));

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("pom.xml", toInputStream(pom));

        DependencyManager dm = mock(DependencyManager.class);

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        verify(dm).resolveDependencies(deps, "jar");
    }

    @Test(expected = RuntimeException.class)
    public void whenDepManagerThenDontResolveEmbeddedDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "com.acme:bar:1.2"))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("bar-1.2.jar", Jar.toInputStream("", UTF8));

        DependencyManager dm = mock(DependencyManager.class);

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);
    }

    @Test
    public void testCapsuleInClassPath() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("App-Class-Path", list("lib/a.jar", "lib/b.jar"))
                .setAttribute("Capsule-In-Class-Path", "false")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));

        ASSERT.that(getClassPath(pb)).has().noneOf(getPath("capsule.jar"));
        ASSERT.that(getClassPath(pb)).has().allOf(
                appCache,
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));
    }

    @Test
    public void testSystemProperties() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("33", getProperty(pb, "baz"));
    }

    @Test
    public void testJVMArgs() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list("-Xms15");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertTrue(pb.command().contains("-Xmx100"));
        assertTrue(pb.command().contains("-Xms15"));
        assertTrue(!pb.command().contains("-Xms10"));
    }

    @Test
    public void testMode() throws Exception {
        System.setProperty("capsule.mode", "ModeX");
        try {
            Jar jar = newCapsuleJar()
                    .setAttribute("Application-Class", "com.acme.Foo")
                    .setAttribute("System-Properties", "bar baz=33 foo=y")
                    .setAttribute("ModeX", "System-Properties", "bar baz=55 foo=w")
                    .addEntry("foo.jar", Jar.toInputStream("", UTF8));

            String[] args = strings("hi", "there");
            List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

            Capsule capsule = newCapsule(jar, null);
            ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

            assertEquals("x", getProperty(pb, "foo"));
            assertEquals("", getProperty(pb, "bar"));
            assertEquals("", getProperty(pb, "zzz"));
            assertEquals("55", getProperty(pb, "baz"));
        } finally {
            System.setProperty("capsule.mode", "");
        }
    }

    @Test(expected = Exception.class)
    public void testMode2() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("ModeX", "Application-Class", "com.acme.Bar")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        Capsule capsule = newCapsule(jar, null);
    }

    @Test
    public void testScript() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Unix-Script", "scr.sh")
                .setAttribute("Windows-Script", "scr.bat")
                .addEntry("scr.sh", Jar.toInputStream("", UTF8))
                .addEntry("scr.bat", Jar.toInputStream("", UTF8))
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals(list(appCache.resolve(Capsule.isWindows() ? "scr.bat" : "scr.sh").toString(), "hi", "there"),
                pb.command());
    }

    @Test
    public void testReallyExecutableCapsule() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .setReallyExecutable(true)
                .addEntry("a.class", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz", "-Xms15");

        final Path capsuleJar = getPath("capsule.jar");
        jar.write(capsuleJar);
        Capsule capsule = Capsule.newCapsule(capsuleJar, cache);

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);
    }

    @Test
    public void testCustomCapsule() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .addClass(MyCapsule.class);

        String[] args = strings("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz", "-Xms15");

        final Path capsuleJar = getPath("capsule.jar");
        jar.write(capsuleJar);
        Capsule capsule = Capsule.newCapsule(capsuleJar, cache);

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("44", getProperty(pb, "baz"));

        assertTrue(pb.command().contains("-Xmx3000"));
        assertTrue(!pb.command().contains("-Xmx100"));
        assertTrue(pb.command().contains("-Xms15"));
        assertTrue(!pb.command().contains("-Xms10"));
    }

    @Test
    public void testEmptyCapsule() throws Exception {
        Jar jar = newCapsuleJar();

        Jar app = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("App-Class-Path", list("lib/a.jar"))
                .addClass(Capsule.class)
                .addEntry("foo.jar", Jar.toInputStream("", UTF8))
                .addEntry("a.class", Jar.toInputStream("", UTF8))
                .addEntry("b.txt", Jar.toInputStream("", UTF8))
                .addEntry("lib/a.jar", Jar.toInputStream("", UTF8))
                .addEntry("lib/b.class", Jar.toInputStream("", UTF8))
                .addEntry("META-INF/x.txt", Jar.toInputStream("", UTF8));

        Path fooPath = cache.resolve("deps").resolve("com.acme").resolve("foo").resolve("foo-1.0.jar");
        Files.createDirectories(fooPath.getParent());
        app.write(fooPath);

        DependencyManager dm = mock(DependencyManager.class);
        when(dm.resolveRoot("com.acme:foo")).thenReturn(list(fooPath));
        when(dm.getLatestVersion("com.acme:foo")).thenReturn("com.acme.foo:1.0");

        String[] args = strings("com.acme:foo", "hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.launchCapsuleArtifact(cmdLine, args);

        // dumpFileSystem(fs);
        assertTrue(pb != null);

        String appId = capsule.appId(args);
        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals("com.acme.Foo", getProperty(pb, "capsule.app"));
        assertEquals(appCache.toString(), getProperty(pb, "capsule.dir"));

        assertEquals(list("com.acme.Foo", "hi", "there"), getMainAndArgs(pb));

        assertTrue(Files.isDirectory(cache));
        assertTrue(Files.isDirectory(cache.resolve("apps")));
        assertTrue(Files.isDirectory(appCache));
        assertTrue(Files.isRegularFile(appCache.resolve(".extracted")));
        assertTrue(Files.isRegularFile(appCache.resolve("foo.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("b.txt")));
        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));
        assertTrue(!Files.isRegularFile(appCache.resolve("a.class")));
        assertTrue(!Files.isRegularFile(appCache.resolve("lib").resolve("b.class")));
        assertTrue(!Files.isDirectory(appCache.resolve("META-INF")));
        assertTrue(!Files.isRegularFile(appCache.resolve("META-INF").resolve("x.txt")));

        ASSERT.that(getClassPath(pb)).has().allOf(
                fooPath,
                appCache,
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"));
    }

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    // may be called once per test (always writes jar into /capsule.jar)
    private Capsule newCapsule(Jar jar, DependencyManager dependencyManager) {
        try {
            final Path capsuleJar = getPath("capsule.jar");
            jar.write(capsuleJar);
            Constructor<Capsule> ctor = Capsule.class.getDeclaredConstructor(Path.class, Path.class, Object.class);
            ctor.setAccessible(true);
            return ctor.newInstance(capsuleJar, cache, dependencyManager);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Jar newCapsuleJar() {
        return new Jar()
                .setAttribute("Manifest-Version", "1.0")
                .setAttribute("Main-Class", "Capsule");
    }

    private Path getPath(String first, String... more) {
        return fs.getPath(first, more);
    }

    private Model newPom() {
        Model a;
        return new Model();
    }

    private InputStream toInputStream(Model model) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new MavenXpp3Writer().write(baos, model);
            baos.close();
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private List<Path> paths(String cp) {
        final List<Path> res = new ArrayList<>();
        for (String p : cp.split(":"))
            res.add(getPath(p));
        return res;
    }

    private List<Path> getClassPath(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int i = cmd.indexOf("-classpath");
        if (i < 0)
            return null;
        final String cp = cmd.get(i + 1);
        // return Arrays.asList(cp.split(":"));
        return paths(cp);
    }

    private String getProperty(ProcessBuilder pb, String prop) {
        return getOption(pb, "-D" + prop, '=');
    }

    private String getOption(ProcessBuilder pb, String opt) {
        return getOption(pb, opt, ':');
    }

    private String getOption(ProcessBuilder pb, String opt, char separator) {
        final List<String> cmd = pb.command();
        for (String a : cmd) {
            if (a.startsWith(opt)) {
                String res = getAfter(a, separator);
                return res != null ? res : "";
            }
        }
        return null;
    }

    private static String getBefore(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return s;
        return s.substring(0, i);
    }

    private static String getAfter(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return null;
        return s.substring(i + 1);
    }

    private static List<String> getMainAndArgs(ProcessBuilder pb) {
        List<String> cmd = pb.command();
        cmd = cmd.subList(1, cmd.size());

        boolean prevClassPath = false;
        int i = 0;
        for (String c : cmd) {
            if (c.startsWith("-") || prevClassPath)
                i++;
            else
                break;
            prevClassPath = c.equals("-classpath");
        }
        return cmd.subList(i, cmd.size());
    }

    private static <T> List<T> list(T... xs) {
        return Arrays.asList(xs);
    }

    private static String[] strings(String... xs) {
        return xs;
    }

    private static int[] ints(int... xs) {
        return xs;
    }

    private static final Pattern PAT_DEPENDENCY = Pattern.compile("(?<groupId>[^:\\(\\)]+):(?<artifactId>[^:\\(\\)]+)(:(?<version>[^:\\(\\)]*))?(:(?<classifier>[^:\\(\\)]+))?(\\((?<exclusions>[^\\(\\)]*)\\))?");

    static Dependency coordsToDependency(final String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        Dependency d = new Dependency();
        d.setGroupId(m.group("groupId"));
        d.setArtifactId(m.group("artifactId"));
        String version = m.group("version");
        if (version == null || version.isEmpty())
            version = "[0,)";
        d.setVersion(version);
        d.setClassifier(m.group("classifier"));
        d.setScope("runtime");
        for (Exclusion ex : getExclusions(depString))
            d.addExclusion(ex);
        return d;
    }

    static Collection<Exclusion> getExclusions(String depString) {
        final Matcher m = PAT_DEPENDENCY.matcher(depString);
        if (!m.matches())
            throw new IllegalArgumentException("Could not parse dependency: " + depString);

        if (m.group("exclusions") == null || m.group("exclusions").isEmpty())
            return Collections.emptyList();

        final List<String> exclusionPatterns = Arrays.asList(m.group("exclusions").split(","));
        final List<Exclusion> exclusions = new ArrayList<Exclusion>();
        for (String expat : exclusionPatterns) {
            String[] coords = expat.trim().split(":");
            if (coords.length != 2)
                throw new IllegalArgumentException("Illegal exclusion dependency coordinates: " + depString + " (in exclusion " + expat + ")");
            Exclusion ex = new Exclusion();
            ex.setGroupId(coords[0]);
            ex.setArtifactId(coords[1]);
            exclusions.add(ex);
        }
        return exclusions;
    }

    private static void dumpFileSystem(FileSystem fs) {
        try {
            Files.walkFileTree(fs.getRootDirectories().iterator().next(), new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    System.out.println("-- " + file);
                    return super.visitFile(file, attrs);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    //</editor-fold>
}
