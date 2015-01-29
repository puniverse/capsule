/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
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
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import static org.truth0.Truth.*;
import static org.mockito.Mockito.*;

public class CapsuleTest {
    /*
     * As a general rule, we prefer system tests, and only create unit tests for particular methods that,
     * while tested for integration, whose arguments don't get enough coverage in the system tests (like parsing methods and the like).
     *
     * All the tests in this test suite use an in-memory file system, and don't write to the disk at all.
     */
    private final FileSystem fs = Jimfs.newFileSystem();
    private final Path cache = fs.getPath("/cache");
    private final Path tmp = fs.getPath("/tmp");
    private static final ClassLoader MY_CLASSLOADER = Capsule.class.getClassLoader();

    private Properties props;

    @Before
    public void setUp() throws Exception {
        accessible(Capsule.class.getDeclaredField("CACHE_DIR")).set(null, cache);

        props = new Properties(System.getProperties());
        accessible(Capsule.class.getDeclaredField("PROPERTIES")).set(null, props);
        props.setProperty("capsule.no_dep_manager", "true");
    }

    @After
    public void tearDown() throws Exception {
        fs.close();
    }

    //<editor-fold desc="System Tests">
    /////////// System Tests ///////////////////////////////////
    @Test
    public void testSimpleExtract() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("a.class", emptyInputStream())
                .addEntry("b.txt", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.class", emptyInputStream())
                .addEntry("q/w/x.txt", emptyInputStream())
                .addEntry("d\\f\\y.txt", emptyInputStream()) // test with Windows path
                .addEntry("META-INF/x.txt", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        // dumpFileSystem(fs);
        assertEquals(args, getAppArgs(pb));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals("com.acme.Foo", getProperty(pb, "capsule.app"));
        assertEquals("com.acme.Foo", getEnv(pb, "CAPSULE_APP"));
        assertEquals(appCache, path(getProperty(pb, "capsule.dir")));
        assertEquals(absolutePath("capsule.jar"), path(getProperty(pb, "capsule.jar")));
        assertEquals(appCache, path(getEnv(pb, "CAPSULE_DIR")));
        assertEquals(absolutePath("capsule.jar"), path(getEnv(pb, "CAPSULE_JAR")));

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

        assertTrue(Files.isDirectory(appCache.resolve("q").resolve("w")));
        assertTrue(Files.isDirectory(appCache.resolve("d").resolve("f")));
        assertTrue(Files.isRegularFile(appCache.resolve("q").resolve("w").resolve("x.txt")));
        assertTrue(Files.isRegularFile(appCache.resolve("d").resolve("f").resolve("y.txt")));

        ASSERT.that(getClassPath(pb)).has().item(absolutePath("capsule.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache);
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        ASSERT.that(getClassPath(pb)).has().noneOf(appCache.resolve("lib").resolve("a.jar"));
    }

    @Test
    public void testNoExtract() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals(args, getAppArgs(pb));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");
        assertTrue(!Files.isDirectory(appCache));
    }

    @Test
    public void testJDKClassPath() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                //.setAttribute("Extract-Capsule", "false")
                .setAttribute("JDK-Required", "true")
                .setListAttribute("App-Class-Path", list("$JAVA_HOME/lib/tools.jar", "lib/*"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);
        Path javaHome = path(capsule.getJavaHome().toString()); // different FS

        assertEquals(args, getAppArgs(pb));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(javaHome.toString()).contains("jdk");
        ASSERT.that(javaHome.toString()).doesNotContain("jre");
        ASSERT.that(getClassPath(pb)).has().allOf(
                javaHome.resolve("lib/tools.jar"),
                appCache,
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));
    }

    @Test
    public void testLogLevel() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .setAttribute("Capsule-Log-Level", "verbose");

        Capsule capsule = newCapsule(jar, null);
        assertTrue(capsule.isLogging(2));
        assertTrue(!capsule.isLogging(3));

        props.setProperty("capsule.log", "none");
        capsule = newCapsule(jar, null);
        assertTrue(capsule.isLogging(0));
        assertTrue(!capsule.isLogging(1));

        props.setProperty("capsule.log", "quiet");
        capsule = newCapsule(jar, null);
        assertTrue(capsule.isLogging(1));
        assertTrue(!capsule.isLogging(2));

        props.setProperty("capsule.log", "");
        capsule = newCapsule(jar, null);
        assertTrue(capsule.isLogging(1));
        assertTrue(!capsule.isLogging(2));

        props.setProperty("capsule.log", "verbose");
        capsule = newCapsule(jar, null);
        assertTrue(capsule.isLogging(2));
        assertTrue(!capsule.isLogging(3));

        props.setProperty("capsule.log", "debug");
        capsule = newCapsule(jar, null);
        assertTrue(capsule.isLogging(3));
    }

    @Test
    public void testCapsuleJavaHome() throws Exception {
        props.setProperty("capsule.java.home", "/my/java/home");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("/my/java/home/bin/java" + (Capsule.isWindows() ? ".exe" : ""), pb.command().get(0));
    }

    @Test
    public void testCapsuleJavaCmd() throws Exception {
        props.setProperty("capsule.java.cmd", "/my/java/home/gogo");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("/my/java/home/gogo", pb.command().get(0));
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
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("pom.xml", toInputStream(pom));

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        capsule.prepareForLaunch(cmdLine, args);

        String appId = capsule.getAppId();
        assertEquals("com.acme_foo_1.0", appId);
    }

    @Test
    public void testClassPath() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("App-Class-Path", list("lib/a.jar", "lib/b.jar", "lib2/*.jar"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream())
                .addEntry("lib2/c.jar", emptyInputStream())
                .addEntry("lib2/d.jar", emptyInputStream())
                .addEntry("lib2/e.txt", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib2").resolve("c.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib2").resolve("d.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib2").resolve("e.txt")));

        ASSERT.that(getClassPath(pb)).has().item(absolutePath("capsule.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache);
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("a.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("b.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib2").resolve("c.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib2").resolve("d.jar"));
        ASSERT.that(getClassPath(pb)).has().noneOf(appCache.resolve("lib2").resolve("e.txt"));
    }

    @Test
    public void testNatives1() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Library-Path-A", list("lib/a.so"))
                .setListAttribute("Library-Path-P", list("lib/b.so"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.so", emptyInputStream())
                .addEntry("lib/b.so", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("lib/d.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        int len = paths(getProperty(pb, "java.library.path")).size();
        ASSERT.that(paths(getProperty(pb, "java.library.path")).get(0)).isEqualTo(appCache.resolve("lib").resolve("b.so"));
        ASSERT.that(paths(getProperty(pb, "java.library.path")).get(len - 2)).isEqualTo(appCache.resolve("lib").resolve("a.so"));
        ASSERT.that(paths(getProperty(pb, "java.library.path")).get(len - 1)).isEqualTo(appCache);
    }

    @Test
    public void testNatives2() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Library-Path-A", list("lib/a.so"))
                .setListAttribute("Library-Path-P", list("lib/b.so"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.so", emptyInputStream())
                .addEntry("lib/b.so", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("lib/d.jar", emptyInputStream());

        props.setProperty("java.library.path", "/foo/bar");
        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(paths(getProperty(pb, "java.library.path"))).isEqualTo(list(
                appCache.resolve("lib").resolve("b.so"),
                path("/foo", "bar"),
                appCache.resolve("lib").resolve("a.so"),
                appCache));
    }

    @Test
    public void testNativesWithDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Linux", "Native-Dependencies", list("com.acme:baz-linux:3.4,libbaz.so"))
                .setListAttribute("Windows", "Native-Dependencies", list("com.acme:baz-win:3.4,libbaz.dll"))
                .setListAttribute("MacOS", "Native-Dependencies", list("com.acme:baz-macos:3.4,libbaz.dylib"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.so", emptyInputStream())
                .addEntry("lib/b.so", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("lib/d.jar", emptyInputStream());

        DependencyManager dm = mock(DependencyManager.class);
        Files.createDirectories(cache.resolve("deps").resolve("com.acme").resolve("baz"));
        Path bazLinuxPath = cache.resolve("deps").resolve("com.acme").resolve("baz").resolve("baz-linux-3.4.so");
        Files.createFile(bazLinuxPath);
        when(dm.resolveDependencies(list("com.acme:baz-linux:3.4"), "so")).thenReturn(list(bazLinuxPath));
        Path bazWindowsPath = cache.resolve("deps").resolve("com.acme").resolve("baz").resolve("baz-win-3.4.dll");
        Files.createFile(bazWindowsPath);
        when(dm.resolveDependencies(list("com.acme:baz-win:3.4"), "dll")).thenReturn(list(bazWindowsPath));
        Path bazMacPath = cache.resolve("deps").resolve("com.acme").resolve("baz").resolve("baz-macos-3.4.dylib");
        Files.createFile(bazMacPath);
        when(dm.resolveDependencies(list("com.acme:baz-macos:3.4"), "dylib")).thenReturn(list(bazMacPath));

        props.setProperty("java.library.path", "/foo/bar");
        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(paths(getProperty(pb, "java.library.path"))).has().item(appCache);

        if (Capsule.isUnix())
            assertTrue(Files.isRegularFile(appCache.resolve("libbaz.so")));
        else if (Capsule.isWindows())
            assertTrue(Files.isRegularFile(appCache.resolve("libbaz.dll")));
        else if (Capsule.isMac())
            assertTrue(Files.isRegularFile(appCache.resolve("libbaz.dylib")));
    }

    @Test
    public void testBootClassPath1() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path-A", list("lib/a.jar"))
                .setListAttribute("Boot-Class-Path-P", list("lib/b.jar"))
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "lib/d.jar"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("lib/d.jar", emptyInputStream());

        List<String> args = list("hi", "there");
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
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("lib/d.jar", emptyInputStream());

        List<String> args = list("hi", "there");
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
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream());

        DependencyManager dm = mock(DependencyManager.class);
        Path barPath = cache.resolve("deps").resolve("com.acme").resolve("bar").resolve("bar-1.2.jar");
        when(dm.resolveDependency("com.acme:bar:1.2", "jar")).thenReturn(list(barPath));
        when(dm.resolveDependencies(list("com.acme:bar:1.2"), "jar")).thenReturn(list(barPath));
        Path bazPath = cache.resolve("deps").resolve("com.acme").resolve("baz").resolve("baz-3.4.jar");
        when(dm.resolveDependency("com.acme:baz:3.4", "jar")).thenReturn(list(bazPath));
        when(dm.resolveDependencies(list("com.acme:baz:3.4"), "jar")).thenReturn(list(bazPath));

        List<String> args = list("hi", "there");
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
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("bar-1.2.jar", emptyInputStream());

        List<String> args = list("hi", "there");
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
                .addEntry("foo.jar", emptyInputStream());

        DependencyManager dm = mock(DependencyManager.class);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        capsule.prepareForLaunch(cmdLine, args);

        verify(dm).resolveDependencies(deps, "jar");
    }

    @Test
    public void testPomDependencies1() throws Exception {
        List<String> deps = list("com.acme:bar:1.2", "com.acme:baz:3.4:jdk8(org.asd:qqq,com.gogo:bad)");

        Model pom = newPom();
        pom.setGroupId("com.acme");
        pom.setArtifactId("foo");
        pom.setVersion("1.0");
        for (String dep : deps)
            pom.addDependency(coordsToDependency(dep));

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("pom.xml", toInputStream(pom));

        DependencyManager dm = mock(DependencyManager.class);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        capsule.prepareForLaunch(cmdLine, args);

        verify(dm).resolveDependencies(deps, "jar");
    }

    public void whenDepManagerThenDontResolveEmbeddedDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "com.acme:bar:1.2"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("bar-1.2.jar", emptyInputStream());

        DependencyManager dm = mock(DependencyManager.class);
        Path barPath = cache.resolve("deps").resolve("com.acme").resolve("bar").resolve("bar-1.2.jar");
        when(dm.resolveDependency("com.acme:bar:1.2", "jar")).thenReturn(list(barPath));
        when(dm.resolveDependencies(list("com.acme:bar:1.2"), "jar")).thenReturn(list(barPath));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        ProcessBuilder pb = newCapsule(jar, dm).prepareForLaunch(cmdLine, args);

        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().noneOf(appCache.resolve("bar-1.2.jar"));
        ASSERT.that(paths(getOption(pb, "-Xbootclasspath"))).has().item(barPath);
    }

    @Test
    public void testCapsuleInClassPath() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("App-Class-Path", list("lib/a.jar", "lib/b.jar"))
                .setAttribute("Capsule-In-Class-Path", "false")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));

        ASSERT.that(getClassPath(pb)).has().noneOf(absolutePath("capsule.jar"));
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
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("33", getProperty(pb, "baz"));
    }

    @Test
    public void testPlatformSepcific() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Linux", "System-Properties", "bar baz=33 foo=y os=lin")
                .setAttribute("MacOS", "System-Properties", "bar baz=33 foo=y os=mac")
                .setAttribute("Windows", "System-Properties", "bar baz=33 foo=y os=win")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("33", getProperty(pb, "baz"));

        if (Capsule.isWindows())
            assertEquals("win", getProperty(pb, "os"));
        else if (Capsule.isUnix())
            assertEquals("lin", getProperty(pb, "os"));
        if (Capsule.isMac())
            assertEquals("mac", getProperty(pb, "os"));
    }

    @Test
    public void testJVMArgs() throws Exception {
        props.setProperty("capsule.jvm.args", "-Xfoo500 -Xbar:120");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10 -Xfoo400")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Xms15");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertTrue(getJvmArgs(pb).contains("-Xmx100"));
        assertTrue(getJvmArgs(pb).contains("-Xms15"));
        assertTrue(!getJvmArgs(pb).contains("-Xms10"));
        assertTrue(getJvmArgs(pb).contains("-Xfoo500"));
        assertTrue(getJvmArgs(pb).contains("-Xbar:120"));
    }

    @Test
    public void testAgents() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Java-Agents", "ja1.jar ja2.jar=a=1,b=2 com.acme:bar=x=hi")
                .setAttribute("Native-Agents", "na1=c=3,d=4 na2")
                .addEntry("foo.jar", emptyInputStream());

        DependencyManager dm = mock(DependencyManager.class);
        Path barPath = cache.resolve("deps").resolve("com.acme").resolve("bar").resolve("bar-1.2.jar");
        when(dm.resolveDependency("com.acme:bar", "jar")).thenReturn(list(barPath));

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        ASSERT.that(getJvmArgs(pb)).has().allOf("-javaagent:" + appCache.resolve("ja1.jar"));
        ASSERT.that(getJvmArgs(pb)).has().item("-javaagent:" + appCache.resolve("ja2.jar") + "=a=1,b=2");
        ASSERT.that(getJvmArgs(pb)).has().item("-javaagent:" + barPath + "=x=hi");
        ASSERT.that(getJvmArgs(pb)).has().item("-agentpath:" + appCache.resolve("na1." + Capsule.getNativeLibExtension()) + "=c=3,d=4");
        ASSERT.that(getJvmArgs(pb)).has().item("-agentpath:" + appCache.resolve("na2." + Capsule.getNativeLibExtension()));
    }

    @Test
    public void testMode() throws Exception {
        props.setProperty("capsule.mode", "ModeX");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("ModeX", "System-Properties", "bar baz=55 foo=w")
                .setAttribute("ModeX", "Description", "This is a secret mode")
                .setAttribute("ModeX-Linux", "System-Properties", "bar baz=55 foo=w os=lin")
                .setAttribute("ModeX-MacOS", "System-Properties", "bar baz=55 foo=w os=mac")
                .setAttribute("ModeX-Windows", "System-Properties", "bar baz=55 foo=w os=win")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("55", getProperty(pb, "baz"));

        assertEquals(new HashSet<String>(list("ModeX")), capsule.getModes());
        assertEquals("This is a secret mode", capsule.getModeDescription("ModeX"));

        if (Capsule.isWindows())
            assertEquals("win", getProperty(pb, "os"));
        else if (Capsule.isUnix())
            assertEquals("lin", getProperty(pb, "os"));
        if (Capsule.isMac())
            assertEquals("mac", getProperty(pb, "os"));
    }

    @Test(expected = Exception.class)
    public void testMode2() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("ModeX", "Application-Class", "com.acme.Bar")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        newCapsule(jar, null).prepareForLaunch(cmdLine, args);
    }

    @Test
    public void testApplicationArtifact() throws Exception {
        Jar bar = new Jar()
                .setAttribute("Main-Class", "com.acme.Bar")
                .addEntry("com/acme/Bar.class", emptyInputStream());

        Path barPath = cache.resolve("deps").resolve("com.acme").resolve("bar").resolve("bar-1.2.jar");
        Files.createDirectories(barPath.getParent());
        bar.write(barPath);

        DependencyManager dm = mock(DependencyManager.class);
        when(dm.resolveDependency("com.acme:bar:1.2", "jar")).thenReturn(list(barPath));
        when(dm.getLatestVersion("com.acme:bar:1.2", "jar")).thenReturn("com.acme:bar:1.2");

        Jar jar = newCapsuleJar()
                .setAttribute("Application", "com.acme:bar:1.2");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        ASSERT.that(getClassPath(pb)).has().item(barPath);
        assertEquals("com.acme.Bar", getMainClass(pb));
    }

    @Test
    public void testEmbeddedArtifact() throws Exception {
        Jar bar = new Jar()
                .setAttribute("Main-Class", "com.acme.Bar")
                .setAttribute("Class-Path", "lib/liba.jar lib/libb.jar")
                .addEntry("com/acme/Bar.class", emptyInputStream());

        Jar jar = newCapsuleJar()
                .setAttribute("Application", "bar.jar")
                .setAttribute("Application-Name", "AcmeFoo")
                .setAttribute("Application-Version", "1.0")
                .addEntry("bar.jar", bar.toByteArray())
                .addEntry("lib/liba.jar", emptyInputStream())
                .addEntry("lib/libb.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("AcmeFoo_1.0");
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("bar.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("liba.jar"));
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("libb.jar"));
        assertEquals("com.acme.Bar", getMainClass(pb));
    }

    @Test
    public void testScript() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Dependencies", "com.acme:bar:1.2")
                .setAttribute("Linux", "Application-Script", "scr.sh")
                .setAttribute("MacOS", "Application-Script", "scr.sh")
                .setAttribute("Windows", "Application-Script", "scr.bat")
                .addEntry("scr.sh", emptyInputStream())
                .addEntry("scr.bat", emptyInputStream())
                .addEntry("foo.jar", emptyInputStream());

        DependencyManager dm = mock(DependencyManager.class);
        Path barPath = cache.resolve("deps").resolve("com.acme").resolve("bar").resolve("bar-1.2.jar");
        when(dm.resolveDependencies(list("com.acme:bar:1.2"), "jar")).thenReturn(list(barPath));

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, dm);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");
        assertEquals(list(appCache.resolve(Capsule.isWindows() ? "scr.bat" : "scr.sh").toString(), "hi", "there"),
                pb.command());

        assertEquals(getEnv(pb, "CLASSPATH"), absolutePath("capsule.jar") + PS + appCache + PS + appCache.resolve("foo.jar") + PS + barPath);
    }

    @Test
    public void testReallyExecutableCapsule() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .setReallyExecutable(true)
                .addEntry("a.class", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz", "-Xms15");

        Path capsuleJar = absolutePath("capsule.jar");
        jar.write(capsuleJar);

        Capsule.newCapsule(MY_CLASSLOADER, capsuleJar).prepareForLaunch(cmdLine, args);
    }

    @Test
    public void testCustomCapsule() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .addClass(MyCapsule.class);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz", "-Xms15");

        Path capsuleJar = absolutePath("capsule.jar");
        jar.write(capsuleJar);
        Capsule capsule = Capsule.newCapsule(MY_CLASSLOADER, capsuleJar);

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("44", getProperty(pb, "baz"));

        assertTrue(getJvmArgs(pb).contains("-Xmx3000"));
        assertTrue(!getJvmArgs(pb).contains("-Xmx100"));
        assertTrue(getJvmArgs(pb).contains("-Xms15"));
        assertTrue(!getJvmArgs(pb).contains("-Xms10"));
    }

    @Test
    public void testWrapperCapsule() throws Exception {
        Jar wrapper = newCapsuleJar()
                .setAttribute("Caplets", "MyCapsule")
                .setAttribute("System-Properties", "p1=555")
                .addClass(Capsule.class)
                .addClass(MyCapsule.class);

        Jar app = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "p1=111")
                .setListAttribute("App-Class-Path", list("lib/a.jar"))
                .addClass(Capsule.class)
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("a.class", emptyInputStream())
                .addEntry("b.txt", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.class", emptyInputStream())
                .addEntry("META-INF/x.txt", emptyInputStream());

        Path fooPath = cache.resolve("deps").resolve("com.acme").resolve("foo").resolve("foo-1.0.jar");
        Files.createDirectories(fooPath.getParent());
        app.write(fooPath);

        DependencyManager dm = mock(DependencyManager.class);
        when(dm.resolveDependency("com.acme:foo", "jar")).thenReturn(list(fooPath));
        when(dm.getLatestVersion("com.acme:foo", "jar")).thenReturn("com.acme.foo:1.0");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(wrapper, dm).setTarget("com.acme:foo");
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        // dumpFileSystem(fs);
        assertTrue(pb != null);

        String appId = capsule.getAppId();
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

        assertEquals("111", getProperty(pb, "p1"));
    }

    @Test
    public void testWrapperCapsuleNonCapsuleApp() throws Exception {
        Jar wrapper = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("System-Properties", "p1=555")
                .addClass(Capsule.class)
                .addClass(MyCapsule.class);

        Jar app = new Jar()
                .setAttribute("Main-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "p1=111")
                .setAttribute("Class-Path", "lib/a.jar lib/b.jar")
                .addEntry("a.class", emptyInputStream())
                .addEntry("b.txt", emptyInputStream())
                .addEntry("META-INF/x.txt", emptyInputStream());

        Path fooPath = path("foo-1.0.jar");
        app.write(fooPath);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(wrapper, null).setTarget(fooPath);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        // dumpFileSystem(fs);
        assertTrue(pb != null);

        String appId = capsule.getAppId();
        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals(list("com.acme.Foo", "hi", "there"), getMainAndArgs(pb));

        //assertTrue(!Files.exists(appCache));
        assertTrue(!Files.exists(appCache.resolve("b.txt")));
        assertTrue(!Files.exists(appCache.resolve("a.class")));

        ASSERT.that(getClassPath(pb)).has().allOf(
                fooPath.toAbsolutePath(),
                path("lib").resolve("a.jar").toAbsolutePath(),
                path("lib").resolve("b.jar").toAbsolutePath());
        ASSERT.that(getClassPath(pb)).has().noneOf(
                absolutePath("capsule.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));

        assertEquals("555", getProperty(pb, "p1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrapperCapsuleNoMain() throws Exception {
        Jar wrapper = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("System-Properties", "p1=555")
                .addClass(Capsule.class)
                .addClass(MyCapsule.class);

        Jar app = new Jar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "p1=111")
                .setListAttribute("App-Class-Path", list("lib/a.jar"))
                .addClass(Capsule.class)
                .addEntry("foo.jar", emptyInputStream());

        Path fooPath = path("foo-1.0.jar");
        app.write(fooPath);

        newCapsule(wrapper, null).setTarget(fooPath);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Unit Tests">
    /////////// Unit Tests ///////////////////////////////////
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
        assertEquals("1.7.0", Capsule.isJavaDir("java-7-openjdk-amd64"));
    }

    @Test
    public void testDelete() throws Exception {
        Files.createDirectories(path("a", "b", "c"));
        Files.createDirectories(path("a", "b1"));
        Files.createDirectories(path("a", "b", "c1"));
        Files.createFile(path("a", "x"));
        Files.createFile(path("a", "b", "x"));
        Files.createFile(path("a", "b1", "x"));
        Files.createFile(path("a", "b", "c", "x"));
        Files.createFile(path("a", "b", "c1", "x"));

        assertTrue(Files.exists(path("a")));
        assertTrue(Files.isDirectory(path("a")));

        //Files.delete(path("a"));
        Capsule.delete(path("a"));

        assertTrue(!Files.exists(path("a")));
    }

    @Test
    public void testListDir() throws Exception {
        Files.createDirectories(path("a", "b", "c"));
        Files.createDirectories(path("a", "b1"));
        Files.createDirectories(path("a", "b", "c1"));
        Files.createFile(path("a", "x"));
        Files.createFile(path("a", "b", "x"));
        Files.createFile(path("a", "b1", "x"));
        Files.createFile(path("a", "b", "c", "x"));
        Files.createFile(path("a", "b", "c1", "x"));

        assertEquals(list(path("a", "b"), path("a", "b1"), path("a", "x")),
                Capsule.listDir(path("a"), null, false));
        assertEquals(list(path("a", "x")),
                Capsule.listDir(path("a"), null, true));
        assertEquals(set(path("a", "x"), path("a", "b", "x"), path("a", "b1", "x"), path("a", "b", "c", "x"), path("a", "b", "c1", "x")),
                set(Capsule.listDir(path("a"), "**", true)));
        assertEquals(list(path("a", "b1", "x")),
                Capsule.listDir(path("a"), "b?/*", false));
    }

    @Test
    public void testGlob() throws Exception {
        FileSystem fs1 = FileSystems.getDefault();
        PathMatcher pathMatcher = fs1.getPathMatcher("glob:java{.exe,}");
        assertTrue(pathMatcher.matches(fs1.getPath("java")));
        assertTrue(pathMatcher.matches(fs1.getPath("java.exe")));
        assertTrue(!pathMatcher.matches(fs1.getPath(".java.exe")));
        assertTrue(!pathMatcher.matches(fs1.getPath("java.exe1")));
        assertTrue(!pathMatcher.matches(fs1.getPath("java.")));
    }

    @Test
    public void testExpandVars1() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo");

        Capsule capsule = newCapsule(jar, null);
        capsule.prepareForLaunch(null, null);

        String cj = absolutePath("capsule.jar").toString();
        String cd = cache.resolve("apps").resolve("com.acme.Foo").toString();
        String cid = capsule.getAppId();

        assertEquals(cj + "abc" + cj + "def" + cj, capsule.expand("$CAPSULE_JAR" + "abc" + "$CAPSULE_JAR" + "def" + "$CAPSULE_JAR"));
        assertEquals(cid + "abc" + cid + "def" + cid, capsule.expand("$CAPSULE_APP" + "abc" + "$CAPSULE_APP" + "def" + "$CAPSULE_APP"));
        assertEquals(cd + "abc" + cd + "def" + cd, capsule.expand("$CAPSULE_DIR" + "abc" + "$CAPSULE_DIR" + "def" + "$CAPSULE_DIR"));
        assertEquals(cd + "abc" + cid + "def" + cj, capsule.expand("$CAPSULE_DIR" + "abc" + "$CAPSULE_APP" + "def" + "$CAPSULE_JAR"));
    }

    @Test
    public void testExpandVars2() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false");

        Capsule capsule = newCapsule(jar, null);
        capsule.prepareForLaunch(null, null);

        String cj = absolutePath("capsule.jar").toString();
        String cd = cache.resolve("apps").resolve("com.acme.Foo").toString();
        String cid = capsule.getAppId();

        assertEquals(cj + "xxx" + cj + "xxx" + cj, capsule.expand("$CAPSULE_JAR" + "xxx" + "$CAPSULE_JAR" + "xxx" + "$CAPSULE_JAR"));
        assertEquals(cid + "xxx" + cid + "xxx" + cid, capsule.expand("$CAPSULE_APP" + "xxx" + "$CAPSULE_APP" + "xxx" + "$CAPSULE_APP"));
        try {
            capsule.expand("$CAPSULE_DIR" + "xxx" + "$CAPSULE_DIR" + "xxx" + "$CAPSULE_DIR");
            fail();
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testExpandArgs() throws Exception {
        assertEquals(list("x", "y", "z"), Capsule.expandArgs(list("x", "y", "z"), CapsuleTest.<String>list()));
        assertEquals(list("a", "b", "c"), Capsule.expandArgs(CapsuleTest.<String>list(), list("a", "b", "c")));
        assertEquals(list("x", "y", "z", "a", "b", "c"), Capsule.expandArgs(list("x", "y", "z"), list("a", "b", "c")));
        assertEquals(list("x", "a", "b", "c", "z"), Capsule.expandArgs(list("x", "$*", "z"), list("a", "b", "c")));
        assertEquals(list("b", "a", "c"), Capsule.expandArgs(list("$2", "$1", "$3"), list("a", "b", "c")));
    }

    @Test
    public void splitTest() throws Exception {
        assertEquals(list("ab", "cd", "ef", "g", "hij", "kl"), Capsule.split("ab,cd  ef g, hij kl  ", "[,\\s]\\s*"));

        assertEquals(stringMap("ab", "1",
                "cd", "xx",
                "ef", "32",
                "g", "xx",
                "hij", "",
                "kl", ""), Capsule.split("ab=1,cd  ef=32 g, hij= kl=  ", '=', "[,\\s]\\s*", "xx"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testPathingJar() throws Exception {
        Files.createDirectories(tmp);
        List<Path> cp = list(path("/a.jar"), path("/b.jar"), path("/c.jar"));
        Path pathingJar = Capsule.createPathingJar(tmp, cp);
        try {
            List<Path> cp2;
            try (JarInputStream jis = new JarInputStream(Files.newInputStream(pathingJar))) {
                cp2 = toPath(Arrays.asList(jis.getManifest().getMainAttributes().getValue("Class-Path").split(" ")));
            }
            assertEquals(cp, toAbsolutePath(cp2));
            for (Path p : cp2)
                assertTrue(!p.isAbsolute());
        } finally {
            Files.delete(pathingJar);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    // may be called once per test (always writes jar into /capsule.jar)
    private Capsule newCapsule(Jar jar, DependencyManager dependencyManager) {
        try {
            Path capsuleJar = path("capsule.jar");
            jar.write(capsuleJar);
            final String mainClass = jar.getAttribute("Main-Class");
            final Class<?> clazz = Class.forName(mainClass);
            accessible(Capsule.class.getDeclaredField("DEPENDENCY_MANAGER")).set(null, dependencyManager);
            accessible(Capsule.class.getDeclaredField("PROFILE")).set(null, 10); // disable profiling even when log=debug

            Constructor<?> ctor = accessible(clazz.getDeclaredConstructor(Path.class));
            return (Capsule) ctor.newInstance(capsuleJar);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private Jar newCapsuleJar() {
        return new Jar()
                .setAttribute("Manifest-Version", "1.0")
                .setAttribute("Main-Class", "TestCapsule");
    }

    private Capsule setTarget(Capsule capsule, String artifact) {
        try {
            accessible(Capsule.class.getDeclaredMethod("setTarget", String.class)).invoke(capsule, artifact);
            return capsule;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Capsule setTarget(Capsule capsule, Path jar) {
        try {
            accessible(Capsule.class.getDeclaredMethod("setTarget", Path.class)).invoke(capsule, jar);
            return capsule;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Model newPom() {
        return new Model();
    }

    private Path path(String first, String... more) {
        return fs.getPath(first, more);
    }

    private Path absolutePath(String first, String... more) {
        return fs.getPath(first, more).toAbsolutePath();
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

    private InputStream emptyInputStream() {
        return Jar.toInputStream("", UTF_8);
    }

    private List<Path> paths(String cp) {
        final List<Path> res = new ArrayList<>();
        for (String p : cp.split(":"))
            res.add(path(p));
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

    private String getEnv(ProcessBuilder pb, String envVar) {
        return pb.environment().get(envVar);
    }

    private String getOption(ProcessBuilder pb, String opt) {
        return getOption(pb, opt, ':');
    }

    private String getOption(ProcessBuilder pb, String opt, char separator) {
        final List<String> jvmargs = getJvmArgs(pb);
        for (String a : jvmargs) {
            if (a.startsWith(opt)) {
                String res = getAfter(a, separator);
                return res != null ? res : "";
            }
        }
        return null;
    }

    private List<String> getJvmArgs(ProcessBuilder pb) {
        boolean classpath = false;
        int i = 0;
        for (String x : pb.command().subList(1, pb.command().size())) {
            if (x.equals("-jar") || (!x.startsWith("-") && !classpath))
                break;
            if (x.equals("-classpath") || x.equals("-cp"))
                classpath = true;
            else
                classpath = false;
            i++;
        }
        return pb.command().subList(1, i + 1);
    }

    private String getMainJar(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int start = getJvmArgs(pb).size() + 1;
        if (cmd.get(start).equals("-jar"))
            return cmd.get(start + 1);
        return null;
    }

    private String getMainClass(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int start = getJvmArgs(pb).size() + 1;
        if (cmd.get(start).equals("-jar"))
            return null;
        return cmd.get(start);
    }

    private List<String> getAppArgs(ProcessBuilder pb) {
        List<String> jvmArgs = getJvmArgs(pb);
        final List<String> cmd = pb.command();
        final int start = jvmArgs.size() + 1;
        return cmd.subList(start + (cmd.get(start).equals("-jar") ? 2 : 1), cmd.size());
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

    private List<Path> toPath(List<String> ps) {
        final List<Path> pss = new ArrayList<Path>(ps.size());
        for (String p : ps)
            pss.add(path(p));
        return pss;
    }

    private List<Path> toAbsolutePath(List<Path> ps) {
        final List<Path> pss = new ArrayList<Path>(ps.size());
        for (Path p : ps)
            pss.add(p.toAbsolutePath().normalize());
        return pss;
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

    private static <T extends Comparable<? super T>> List<T> sort(List<T> list) {
        final List<T> c = new ArrayList<>(list);
        Collections.<T>sort(c);
        return c;
    }

    private static <T> Set<T> set(Collection<T> list) {
        return new HashSet<>(list);
    }

    @SafeVarargs
    private static <T> List<T> list(T... xs) {
        return Arrays.asList(xs);
    }

    @SafeVarargs
    private static <T> Set<T> set(T... xs) {
        return set(Arrays.asList(xs));
    }

    private static String[] strings(String... xs) {
        return xs;
    }

    private static int[] ints(int... xs) {
        return xs;
    }

    private static Map<String, String> stringMap(String... ss) {
        final Map<String, String> m = new HashMap<>();
        for (int i = 0; i < ss.length / 2; i++)
            m.put(ss[i * 2], ss[i * 2 + 1]);
        return Collections.unmodifiableMap(m);
    }

    private static <T extends AccessibleObject> T accessible(T x) {
        if (!x.isAccessible())
            x.setAccessible(true);

        if (x instanceof Field) {
            Field field = (Field) x;
            if ((field.getModifiers() & Modifier.FINAL) != 0) {
                try {
                    Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }

        return x;
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

    private static final String PS = System.getProperty("path.separator");
    //</editor-fold>
}
