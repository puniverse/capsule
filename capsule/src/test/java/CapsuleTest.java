/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are licensed under the terms
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import co.paralleluniverse.capsule.Jar;
import co.paralleluniverse.capsule.test.CapsuleTestUtils;
import co.paralleluniverse.capsule.test.CapsuleTestUtils.StringPrintStream;
import static co.paralleluniverse.capsule.test.CapsuleTestUtils.*;

import co.paralleluniverse.common.ZipFS;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarInputStream;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.junit.Before;
import static com.google.common.truth.Truth.*;
import java.nio.file.Paths;
import org.joor.Reflect;
//import static org.mockito.Mockito.*;

public class CapsuleTest {
    /*
     * As a general rule, we prefer system tests, and only create unit tests for particular methods that,
     * while tested for integration, whose arguments don't get enough coverage in the system tests (like parsing methods and the like).
     *
     * All the tests in this test suite use an in-memory file system, and don't write to the disk at all.
     */
    private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    private final Path cache = fs.getPath("/cache");
    private final Path tmp = fs.getPath("/tmp");
    private static final ClassLoader MY_CLASSLOADER = Capsule.class.getClassLoader();

    private Properties props;

    @Before
    public void setUp() throws Exception {
        props = new Properties(System.getProperties());
        setProperties(props);
        setCacheDir(cache);
        resetOutputStreams();

        TestCapsule.reset();
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

        Capsule capsule = newCapsule(jar);
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

        // assert_().that(getClassPath(pb)).has().item(absolutePath("capsule.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        assert_().that(getClassPath(pb)).has().noneOf(appCache.resolve("lib").resolve("a.jar"));
    }

    @Test
    public void testNoExtract() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.txt", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals(args, getAppArgs(pb));

        assert_().that(getClassPath(pb)).has().item(absolutePath("capsule.jar"));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");
        assertTrue(!Files.isDirectory(appCache));
    }

    @Test
    public void testJDKClassPath() throws Exception {
        assumeTrue(!isCI());

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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);
        Path javaHome = path(capsule.getJavaHome().toString()); // different FS

        assertEquals(args, getAppArgs(pb));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        final String h = javaHome.toString();
        assert_().that(!h.contains("jre") && (h.contains("jdk") || Files.exists(javaHome.resolve("include").resolve("jni.h"))));
        assert_().that(h).doesNotContain("jre");
        assert_().that(getClassPath(pb)).has().allOf(
                javaHome.resolve("lib/tools.jar"),
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));
    }

    @Test
    public void testLogLevel() throws Exception {
        setSTDERR(DEVNULL);

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .setAttribute("Capsule-Log-Level", "verbose");

        newCapsule(jar);
        assertTrue(Capsule.isLogging(2));
        assertTrue(!Capsule.isLogging(3));

        props.setProperty("capsule.log", "none");
        newCapsule(jar);
        assertTrue(Capsule.isLogging(0));
        assertTrue(!Capsule.isLogging(1));

        props.setProperty("capsule.log", "quiet");
        newCapsule(jar);
        assertTrue(Capsule.isLogging(1));
        assertTrue(!Capsule.isLogging(2));

        props.setProperty("capsule.log", "");
        newCapsule(jar);
        assertTrue(Capsule.isLogging(1));
        assertTrue(!Capsule.isLogging(2));

        props.setProperty("capsule.log", "verbose");
        newCapsule(jar);
        assertTrue(Capsule.isLogging(2));
        assertTrue(!Capsule.isLogging(3));

        props.setProperty("capsule.log", "debug");
        newCapsule(jar);
        assertTrue(Capsule.isLogging(3));
    }

    @Test
    public void testCapsuleJavaHome() throws Exception {
        props.setProperty("capsule.java.home", "/my/1.7.0.jdk/home");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("/my/1.7.0.jdk/home/bin/java" + (Capsule.isWindows() ? ".exe" : ""), pb.command().get(0));
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("/my/java/home/gogo", pb.command().get(0));
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib2").resolve("c.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib2").resolve("d.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib2").resolve("e.txt")));

        // assert_().that(getClassPath(pb)).has().item(absolutePath("capsule.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("a.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("b.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("lib2").resolve("c.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("lib2").resolve("d.jar"));
        assert_().that(getClassPath(pb)).has().noneOf(appCache.resolve("lib2").resolve("e.txt"));
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        int len = paths(getProperty(pb, "java.library.path")).size();
        assert_().that(paths(getProperty(pb, "java.library.path")).get(0)).isEqualTo(appCache.resolve("lib").resolve("b.so"));
        assert_().that(paths(getProperty(pb, "java.library.path")).get(len - 2)).isEqualTo(appCache.resolve("lib").resolve("a.so"));
        assert_().that(paths(getProperty(pb, "java.library.path")).get(len - 1)).isEqualTo(appCache);
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(paths(getProperty(pb, "java.library.path"))).isEqualTo(list(
                appCache.resolve("lib").resolve("b.so"),
                path("/foo", "bar"),
                appCache.resolve("lib").resolve("a.so"),
                appCache));
    }

    @Test
    public void testNativesWithDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Linux", "Native-Dependencies", list("com.acme:baz-linux:3.4=libbaz.so"))
                .setListAttribute("Windows", "Native-Dependencies", list("com.acme:baz-win:3.4=libbaz.dll"))
                .setListAttribute("MacOS", "Native-Dependencies", list("com.acme:baz-macos:3.4=libbaz.dylib"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.so", emptyInputStream())
                .addEntry("lib/b.so", emptyInputStream())
                .addEntry("lib/c.jar", emptyInputStream())
                .addEntry("lib/d.jar", emptyInputStream());

        Path bazLinuxPath = mockDep("com.acme:baz-linux:3.4", "so");
        Path bazWindowsPath = mockDep("com.acme:baz-win:3.4", "dll");
        Path bazMacPath = mockDep("com.acme:baz-macos:3.4", "dylib");

        Files.createDirectories(bazLinuxPath.getParent());
        Files.createFile(bazLinuxPath);
        Files.createFile(bazWindowsPath);
        Files.createFile(bazMacPath);

        props.setProperty("java.library.path", "/foo/bar");
        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(paths(getProperty(pb, "java.library.path"))).has().item(appCache);

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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("c.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("d.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath/a"))).isEqualTo(list(appCache.resolve("lib").resolve("a.jar")));
        assert_().that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(getOption(pb, "-Xbootclasspath")).isEqualTo("/foo/bar");
        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().noneOf(appCache.resolve("lib").resolve("c.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().noneOf(appCache.resolve("lib").resolve("d.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath/a"))).isEqualTo(list(appCache.resolve("lib").resolve("a.jar")));
        assert_().that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
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

        List<Path> barPath = mockDep("com.acme:bar:1.2", "jar", "com.acme:bar:1.2");
        List<Path> bazPath = mockDep("com.acme:baz:3.4", "jar", "com.acme:baz:3.4");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("c.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().allFrom(barPath);
        assert_().that(paths(getOption(pb, "-Xbootclasspath/a"))).has().allFrom(bazPath);
        assert_().that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("lib").resolve("c.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().item(appCache.resolve("bar-1.2.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath/p"))).isEqualTo(list(appCache.resolve("lib").resolve("b.jar")));
    }

    @Test
    public void testDependencies1() throws Exception {
        List<String> deps = list("com.acme:bar:1.2", "com.acme:baz:3.4:jdk8");
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Dependencies", deps)
                .setListAttribute("App-Class-Path", list("com.acme:wat:5.8", "com.acme:woo"))
                .addEntry("foo.jar", emptyInputStream());

        final List<Path> paths = new ArrayList<>();
        paths.add(mockDep("com.acme:bar:1.2", "jar"));
        paths.addAll(mockDep("com.acme:baz:3.4:jdk8", "jar", "com.google:guava:18.0"));
        paths.add(mockDep("com.acme:wat:5.8", "jar"));
        paths.addAll(mockDep("com.acme:woo", "jar", "org.apache:tomcat:8.0", "io.jetty:jetty:123.0"));

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assert_().that(getClassPath(pb)).has().allFrom(paths);
    }

    public void whenDepManagerThenDontResolveEmbeddedDeps() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setListAttribute("Boot-Class-Path", list("lib/c.jar", "com.acme:bar:1.2"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("bar-1.2.jar", emptyInputStream());

        Path barPath = mockDep("com.acme:bar:1.2", "jar");

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        ProcessBuilder pb = newCapsule(jar).prepareForLaunch(cmdLine, args);

        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().noneOf(appCache.resolve("bar-1.2.jar"));
        assert_().that(paths(getOption(pb, "-Xbootclasspath"))).has().item(barPath);
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));

        assert_().that(getClassPath(pb)).has().noneOf(absolutePath("capsule.jar"));
        assert_().that(getClassPath(pb)).has().allOf(
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("33", getProperty(pb, "baz"));
    }

    @Test
    public void testPlatformSepcific() throws Exception {
        props.setProperty("capsule.java.home", "/my/1.8.0.jdk/home");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Linux", "System-Properties", "bar baz=33 foo=y os=lin")
                .setAttribute("MacOS", "System-Properties", "bar baz=33 foo=y os=mac")
                .setAttribute("Windows", "System-Properties", "bar baz=33 foo=y os=win")
                .setAttribute("Java-8", "System-Properties", "jjj=8")
                .setAttribute("Java-7", "System-Properties", "jjj=7")
                .addEntry("foo.jar", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertEquals("x", getProperty(pb, "foo"));
        assertEquals("", getProperty(pb, "bar"));
        assertEquals("", getProperty(pb, "zzz"));
        assertEquals("33", getProperty(pb, "baz"));
        assertEquals("8", getProperty(pb, "jjj"));

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

        Capsule capsule = newCapsule(jar);
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

        Path barPath = mockDep("com.acme:bar", "jar", "com.acme:bar:1.2").get(0);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(getJvmArgs(pb)).has().allOf("-javaagent:" + appCache.resolve("ja1.jar"));
        assert_().that(getJvmArgs(pb)).has().item("-javaagent:" + appCache.resolve("ja2.jar") + "=a=1,b=2");
        assert_().that(getJvmArgs(pb)).has().item("-javaagent:" + barPath + "=x=hi");
        assert_().that(getJvmArgs(pb)).has().item("-agentpath:" + appCache.resolve("na1." + Capsule.getNativeLibExtension()) + "=c=3,d=4");
        assert_().that(getJvmArgs(pb)).has().item("-agentpath:" + appCache.resolve("na2." + Capsule.getNativeLibExtension()));
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

        Capsule capsule = newCapsule(jar);
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

        newCapsule(jar).prepareForLaunch(cmdLine, args);
    }

    @Test
    public void testApplicationArtifact() throws Exception {
        Jar bar = new Jar()
                .setAttribute("Main-Class", "com.acme.Bar")
                .addEntry("com/acme/Bar.class", emptyInputStream());

        Path barPath = mockDep("com.acme:bar:1.2", "jar");

        Files.createDirectories(barPath.getParent());
        bar.write(barPath);

        Jar jar = newCapsuleJar()
                .setAttribute("Application", "com.acme:bar:1.2");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assert_().that(getClassPath(pb)).has().item(barPath);
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

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("AcmeFoo_1.0");
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("bar.jar"));
//        assert_().that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("liba.jar"));
//        assert_().that(getClassPath(pb)).has().item(appCache.resolve("lib").resolve("libb.jar"));
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

        Path barPath = mockDep("com.acme:bar:1.2", "jar");

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");
        assertEquals(list(appCache.resolve(Capsule.isWindows() ? "scr.bat" : "scr.sh").toString(), "hi", "there"),
                pb.command());

        assert_().that(getEnv(pb, "CLASSPATH")).contains(appCache.resolve("foo.jar") + PS + barPath);
    }

    @Test
    public void testReallyExecutableCapsule() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Premain-Class", "MyCapsule")
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
    public void testSimpleCaplet1() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Premain-Class", "MyCapsule")
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
    public void testSimpleCaplet2() throws Exception {
        Jar jar = newCapsuleJar()
                .setListAttribute("Caplets", list("MyCapsule"))
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .setListAttribute("App-Class-Path", list("lib/*"))
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.jar", emptyInputStream())
                .addClass(MyCapsule.class);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz", "-Xms15");

        Path capsuleJar = absolutePath("capsule.jar");
        jar.write(capsuleJar);
        Capsule capsule = Capsule.newCapsule(MY_CLASSLOADER, capsuleJar);

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assert_().that(getProperty(pb, "foo")).isEqualTo("x");
        assert_().that(getProperty(pb, "bar")).isEqualTo("");
        assert_().that(getProperty(pb, "zzz")).isEqualTo("");
        assert_().that(getProperty(pb, "baz")).isEqualTo("44");

        assert_().that(getJvmArgs(pb)).has().item("-Xmx3000");
        assert_().that(getJvmArgs(pb)).has().noneOf("-Xmx100");
        assert_().that(getJvmArgs(pb)).has().item("-Xms15");
        assert_().that(getJvmArgs(pb)).has().noneOf("-Xms10");

        assert_().that(getClassPath(pb)).has().allOf(
                fs.getPath("/foo/bar"),
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));
    }

    @Test
    public void testEmbeddedCaplet() throws Exception {
        Jar bar = newCapsuleJar()
                .setListAttribute("Caplets", list("MyCapsule"))
                .addClass(MyCapsule.class);

        Jar jar = newCapsuleJar()
                .setListAttribute("Caplets", list("com.acme:mycapsule:0.9"))
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .setAttribute("JVM-Args", "-Xmx100 -Xms10")
                .addEntry("mycapsule-0.9.jar", bar.toByteArray());

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

        Path fooPath = mockDep("com.acme:foo", "jar", "com.acme:foo:1.0").get(0);

        Files.createDirectories(fooPath.getParent());
        app.write(fooPath);

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(wrapper).setTarget("com.acme:foo");
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assertTrue(capsule.hasCaplet("MyCapsule"));

        assertTrue(capsule.toString() != null); // exercise toString

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

        assert_().that(getClassPath(pb)).has().allOf(
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"));

        assertEquals("111", getProperty(pb, "p1"));
    }

    @Test
    public void testWrapperCapsuleNonCapsuleApp() throws Exception {
        Jar wrapper = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Premain-Class", "MyCapsule")
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

        Capsule capsule = newCapsule(wrapper).setTarget(fooPath.toString());
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        // dumpFileSystem(fs);
        assertTrue(pb != null);

        String appId = capsule.getAppId();
        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals(list("com.acme.Foo", "hi", "there"), getMainAndArgs(pb));

        //assertTrue(!Files.exists(appCache));
        assertTrue(!Files.exists(appCache.resolve("b.txt")));
        assertTrue(!Files.exists(appCache.resolve("a.class")));

        assert_().that(getClassPath(pb)).has().item(fooPath.toAbsolutePath());
//        assert_().that(getClassPath(pb)).has().allOf(
//                path("lib").resolve("a.jar").toAbsolutePath(),
//                path("lib").resolve("b.jar").toAbsolutePath());
        assert_().that(getClassPath(pb)).has().noneOf(
                absolutePath("capsule.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));

        assertEquals("555", getProperty(pb, "p1"));
    }

    @Test
    public void testCapsuleJvmArgsParsing() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Premain-Class", "MyCapsule")
                .setAttribute("Application-Class", "com.acme.Foo")
                .addClass(MyCapsule.class);
        Path capsuleJar = absolutePath("capsule.jar");
        jar.write(capsuleJar);
        Capsule capsule = Capsule.newCapsule(MY_CLASSLOADER, capsuleJar);
        List<String> args = list();
        List<String> cmdLine = list();

        Capsule.setProperty("capsule.jvm.args",
                "-Ddouble.quoted.arg=\"escape me\" "
                + "-Dsingle.quoted.arg='escape me'");

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        assert_().that(getProperty(pb, "double.quoted.arg")).isEqualTo("escape me");
        assert_().that(getProperty(pb, "single.quoted.arg")).isEqualTo("escape me");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrapperCapsuleNoMain() throws Exception {
        Jar wrapper = newCapsuleJar()
                .setAttribute("Main-Class", "MyCapsule")
                .setAttribute("Premain-Class", "MyCapsule")
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

        newCapsule(wrapper).setTarget(fooPath.toString());
    }

    @Test
    public void testProcessCommandLineOptions() throws Exception {
        List<String> args = new ArrayList<>(list("-java-home", "/foo/bar", "-reset", "-jvm-args=a b c", "-java-cmd", "gogo", "hi", "there"));
        List<String> jvmArgs = list("-Dcapsule.java.cmd=wow");

        processCmdLineOptions(args, jvmArgs);

        assertEquals("/foo/bar", props.getProperty("capsule.java.home"));
        assertEquals("true", props.getProperty("capsule.reset"));
        assertEquals("a b c", props.getProperty("capsule.jvm.args"));
        assertEquals(null, props.getProperty("capsule.java.cmd")); // overriden
        assertEquals(list("hi", "there"), args);
    }

    private static void processCmdLineOptions(List<String> args, List<String> jvmArgs) {
        Reflect.on(Capsule.class).call("processCmdLineOptions", args, jvmArgs);
    }

    @Test
    public void testTrampoline() throws Exception {
        props.setProperty("capsule.java.home", "/my/1.7.0.jdk/home");
        props.setProperty("capsule.trampoline", "true");

        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Extract-Capsule", "false")
                .addEntry("foo.jar", emptyInputStream());

        Class<?> capsuleClass = loadCapsule(jar);
        setProperties(capsuleClass, props);
        StringPrintStream out = setSTDOUT(capsuleClass, new StringPrintStream());

        int exit = main0(capsuleClass, "hi", "there!");

        assertEquals(0, exit);
        String res = out.toString();
        assert_().that(res).matches("[^\n]+\n\\z"); // a single line, teminated with a newline
        assert_().that(res).startsWith("\"" + "/my/1.7.0.jdk/home/bin/java" + (Capsule.isWindows() ? ".exe" : "") + "\"");
        assert_().that(res).endsWith("\"com.acme.Foo\" \"hi\" \"there!\"\n");
    }

    private static int main0(Class<?> clazz, String... args) {
        return Reflect.on(clazz).call("main0", (Object) args).get();
    }

    private static boolean runActions(Object capsule, List<String> args) {
        return Reflect.on(capsule.getClass()).call("runActions", capsule, args).get();
    }

    @Test
    public void testPrintHelp() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Application-Version", "12.34")
                .addEntry("foo.jar", emptyInputStream());

        props.setProperty("capsule.help", "");

        Class<?> capsuleClass = loadCapsule(jar);
        setProperties(capsuleClass, props);
        StringPrintStream out = setSTDERR(capsuleClass, new StringPrintStream());

        Object capsule = newCapsule(capsuleClass);
        boolean found = runActions(capsule, null);

        String res = out.toString();
        assert_().that(found).isTrue();
        assert_().that(res).contains("USAGE: ");
    }

    @Test
    public void testPrintCapsuleVersion() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Application-Version", "12.34")
                .addEntry("foo.jar", emptyInputStream());

        props.setProperty("capsule.version", "");

        Class<?> capsuleClass = loadCapsule(jar);
        setProperties(capsuleClass, props);
        StringPrintStream out = setSTDOUT(capsuleClass, new StringPrintStream());

        Object capsule = newCapsule(capsuleClass);
        boolean found = runActions(capsule, null);

        String res = out.toString();
        assert_().that(found).isTrue();
        assert_().that(res).contains("Application com.acme.Foo_12.34");
        assert_().that(res).contains("Version: 12.34");
    }

    @Test
    public void testPrintModes() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("Application-Version", "12.34")
                .setAttribute("ModeX", "System-Properties", "bar baz=55 foo=w")
                .setAttribute("ModeX", "Description", "This is a secret mode")
                .setAttribute("ModeY", "Description", "This is another secret mode")
                .setAttribute("ModeZ", "Foo", "xxx")
                .setAttribute("ModeX-Linux", "System-Properties", "bar baz=55 foo=w os=lin")
                .setAttribute("ModeX-MacOS", "System-Properties", "bar baz=55 foo=w os=mac")
                .setAttribute("ModeX-Windows", "System-Properties", "bar baz=55 foo=w os=win")
                .setAttribute("ModeY-Java-15", "Description", "This is a secret mode")
                .addEntry("foo.jar", emptyInputStream());

        props.setProperty("capsule.modes", "");

        Class<?> capsuleClass = loadCapsule(jar);
        setProperties(capsuleClass, props);
        StringPrintStream out = setSTDOUT(capsuleClass, new StringPrintStream());

        Object capsule = newCapsule(capsuleClass);
        boolean found = runActions(capsule, null);

        String res = out.toString();
        assert_().that(found).isTrue();
        assert_().that(res).contains("* ModeX: This is a secret mode");
        assert_().that(res).contains("* ModeY: This is another secret mode");
        assert_().that(res).contains("* ModeZ");
        assert_().that(res).doesNotContain("* ModeX-Linux");
        assert_().that(res).doesNotContain("* ModeY-Java-15");
    }

    @Test
    public void testMerge() throws Exception {
        Jar wrapper = newCapsuleJar()
                .setAttribute("Caplets", "MyCapsule")
                .setAttribute("System-Properties", "p1=555")
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

        Class<?> capsuleClass = loadCapsule(wrapper);
//        setProperties(capsuleClass, props);

        Path fooPath = mockDep(capsuleClass, "com.acme:foo", "jar", "com.acme:foo:1.0").get(0);
        Files.createDirectories(fooPath.getParent());
        app.write(fooPath);

        props.setProperty("capsule.merge", "out.jar");
//        props.setProperty("capsule.log", "verbose");

        int exit = main0(capsuleClass, "com.acme:foo");

        assertEquals(0, exit);
        assertTrue(Files.isRegularFile(path("out.jar")));
        Jar out = new Jar(path("out.jar"));

        assert_().that(out.getAttribute("Main-Class")).isEqualTo("TestCapsule");
        assert_().that(out.getAttribute("Premain-Class")).isEqualTo("TestCapsule");
        assert_().that(out.getListAttribute("Caplets")).isEqualTo(list("MyCapsule"));
        assert_().that(out.getMapAttribute("System-Properties", "")).isEqualTo(map("p1", "111"));

        FileSystem jar = ZipFS.newZipFileSystem(path("out.jar"));
        assertTrue(Files.isRegularFile(jar.getPath("Capsule.class")));
        assertTrue(Files.isRegularFile(jar.getPath("TestCapsule.class")));
        assertTrue(Files.isRegularFile(jar.getPath("MyCapsule.class")));
        assertTrue(Files.isRegularFile(jar.getPath("foo.jar")));
        assertTrue(Files.isRegularFile(jar.getPath("a.class")));
        assertTrue(Files.isRegularFile(jar.getPath("b.txt")));
        assertTrue(Files.isRegularFile(jar.getPath("lib/a.jar")));
        assertTrue(Files.isRegularFile(jar.getPath("lib/b.class")));
        assertTrue(Files.isRegularFile(jar.getPath("META-INF/x.txt")));
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Unit Tests">
    /////////// Unit Tests ///////////////////////////////////
    @Test
    public void testParseJavaVersion() {
        int[] ver;

        ver = Capsule.parseJavaVersion("8");
        assertArrayEquals(ver, ints(1, 8, 0, 0, 0));
        assertEquals("1.8.0", Capsule.toJavaVersionString(ver));

        ver = Capsule.parseJavaVersion("1.8.0");
        assertArrayEquals(ver, ints(1, 8, 0, 0, 0));
        assertEquals("1.8.0", Capsule.toJavaVersionString(ver));

        ver = Capsule.parseJavaVersion("1.8");
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
        assertEquals("1.7.0", Capsule.isJavaDir("java-1.7.0-openjdk-amd64"));
        assertEquals("1.7.0", Capsule.isJavaDir("java-1.7.0-openjdk-1.7.0.79.x86_64"));
        assertEquals("1.8.0", Capsule.isJavaDir("java-8-oracle"));
        assertEquals("1.8.0", Capsule.isJavaDir("jdk-8-oracle"));
        assertEquals("1.8.0", Capsule.isJavaDir("jre-8-oracle"));
        assertEquals("1.8.0", Capsule.isJavaDir("jdk-8-oracle-x64"));
        assertEquals("1.8.0", Capsule.isJavaDir("jdk-1.8.0"));
        assertEquals("1.8.0", Capsule.isJavaDir("jre-1.8.0"));
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
    public void testGlobToRegex() throws Exception {
        assertEquals(true, "abc/def".matches(Capsule.globToRegex("abc/def")));
        assertEquals(true, "abc/def".matches(Capsule.globToRegex("*/d*")));
        assertEquals(true, "abc/def".matches(Capsule.globToRegex("a*/d*")));
        assertEquals(true, "abc/def".matches(Capsule.globToRegex("*/*")));
        assertEquals(false, "abc/def".matches(Capsule.globToRegex("abc/d")));
        assertEquals(false, "abc/def".matches(Capsule.globToRegex("*")));
        assertEquals(false, "abc/def".matches(Capsule.globToRegex("d*")));
        assertEquals(false, "abc/def".matches(Capsule.globToRegex("abc?d*")));
    }

    @Test
    public void testParseCommandLineArguments() throws Exception {
        assertEquals(list("x", "y", "z"), Capsule.parseCommandLineArguments("x y z"));
        assertEquals(list("x", "y z"), Capsule.parseCommandLineArguments("x 'y z'"));
        assertEquals(list("x y", "z"), Capsule.parseCommandLineArguments("\"x y\" z"));
    }

    @Test
    public void testMove() throws Exception {
        assertEquals(Paths.get("/c/d"), Capsule.move(Paths.get("/a/b"), Paths.get("/a/b"), Paths.get("/c/d/")));
        assertEquals(Paths.get("/c/d/e"), Capsule.move(Paths.get("/a/b/e"), Paths.get("/a/b"), Paths.get("/c/d/")));
    }

    @Test
    public void testDependencyToLocalJar() throws Exception {
        Path jar = fs.getPath("foo.jar");
        String file;

        file = "lib/com.acme/foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "lib/com.acme-foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "lib/com.acme-foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "lib/com.acme-foo-3.1-big.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1:big", "mmm"));

        file = "lib/foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "com.acme/foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "com.acme-foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "com.acme-foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));

        file = "com.acme-foo-3.1-big.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1:big", "mmm"));

        file = "foo-3.1.mmm";
        writeJarWithFile(jar, file);
        assertEquals(file, dependencyToLocalJar(jar, "com.acme:foo:3.1", "mmm"));
    }

    private Path writeJarWithFile(Path path, String... entries) throws IOException {
        Jar jar = newCapsuleJar();
        for (String entry : entries)
            jar.addEntry(entry, emptyInputStream());
        jar.write(path);
        return path;
    }

    private static void clearCaches() {
        Reflect.on(Capsule.class).call("clearCaches");
    }

    private static String dependencyToLocalJar(Path jar, String dep, String type) {
        clearCaches();
        return Reflect.on(Capsule.class).call("dependencyToLocalJar0", jar, dep, type, null).get();
    }

    @Test
    public void testExpandVars1() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo");

        props.setProperty("a.b.c", "777");
        props.setProperty("my.prop", "888");

        Capsule capsule = newCapsule(jar);
        capsule.prepareForLaunch(null, null);

        String cj = absolutePath("capsule.jar").toString();
        String cd = cache.resolve("apps").resolve("com.acme.Foo").toString();
        String cid = capsule.getAppId();

        assertEquals(cj + "abc" + cj + "def " + cj, expand(capsule, "${CAPSULE_JAR}" + "abc" + "${CAPSULE_JAR}" + "def" + " $CAPSULE_JAR"));
        assertEquals(cid + " abc" + cid + "def" + cid, expand(capsule, "$CAPSULE_APP" + " abc" + "${CAPSULE_APP}" + "def" + "${CAPSULE_APP}"));
        assertEquals(cd + "abc " + cd + " def" + cd, expand(capsule, "${CAPSULE_DIR}" + "abc " + "$CAPSULE_DIR" + " def" + "${CAPSULE_DIR}"));
        assertEquals(cd + "abc" + cid + "def" + cj + "888777", expand(capsule, "${CAPSULE_DIR}" + "abc" + "${CAPSULE_APP}" + "def" + "${CAPSULE_JAR}${my.prop}${a.b.c}"));
        assertEquals("888", expand(capsule, "${my.prop}"));
        assertEquals(cj, expand(capsule, "$CAPSULE_JAR"));

        try {
            expand(capsule, "${foo.bar.baz}");
            fail();
        } catch (RuntimeException e) {
        }
    }

    private String expand(Capsule c, String s) {
        return Reflect.on(c).call("expand", s).get();
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
    public void testParseAttribute() {
        assertEquals("abcd 123", Capsule.parse("abcd 123", Capsule.T_STRING(), null));
        assertEquals(true, Capsule.parse("TRUE", Capsule.T_BOOL(), null));
        assertEquals(true, Capsule.parse("true", Capsule.T_BOOL(), null));
        assertEquals(false, Capsule.parse("FALSE", Capsule.T_BOOL(), null));
        assertEquals(false, Capsule.parse("false", Capsule.T_BOOL(), null));
        assertEquals(15L, (long) Capsule.parse("15", Capsule.T_LONG(), null));
        try {
            Capsule.parse("15abs", Capsule.T_LONG(), null);
            fail();
        } catch (RuntimeException e) {
        }
        assertEquals(1.2, Capsule.parse("1.2", Capsule.T_DOUBLE(), null), 0.0001);
        try {
            Capsule.parse("1.2a", Capsule.T_DOUBLE(), null);
            fail();
        } catch (RuntimeException e) {
        }

        assertEquals(list("abcd", "123"), Capsule.parse("abcd 123", Capsule.T_LIST(Capsule.T_STRING()), null));
        assertEquals(list("ab", "cd", "ef", "g", "hij", "kl"), Capsule.parse("ab cd  ef g hij kl  ", Capsule.T_LIST(Capsule.T_STRING()), null));
        assertEquals(list(true, false, true, false), Capsule.parse("TRUE false true FALSE", Capsule.T_LIST(Capsule.T_BOOL()), null));
        assertEquals(list(123L, 456L, 7L), Capsule.parse("123 456  7", Capsule.T_LIST(Capsule.T_LONG()), null));
        assertEquals(list(1.23, 3.45), Capsule.parse("1.23 3.45", Capsule.T_LIST(Capsule.T_DOUBLE()), null));

        assertEquals(map("ab", "1",
                "cd", "xx",
                "ef", "32",
                "g", "xx",
                "hij", "",
                "kl", ""), Capsule.parse("ab=1 cd  ef=32 g hij= kl=  ", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_STRING(), "xx"), null));
        try {
            Capsule.parse("ab=1 cd  ef=32 g hij= kl=  ", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_STRING(), null), null);
            fail();
        } catch (Exception e) {
        }

        assertEquals(map("ab", true, "cd", true, "ef", false, "g", true), Capsule.parse("ab=true cd  ef=false  g", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_BOOL(), true), null));
        try {
            Capsule.parse("ab=true cd  ef=false  g", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_BOOL(), null), null);
            fail();
        } catch (Exception e) {
        }

        assertEquals(map("ab", 12L, "cd", 17L, "ef", 54L, "g", 17L), Capsule.parse("ab=12 cd  ef=54  g", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_LONG(), 17), null));
        try {
            Capsule.parse("ab=12 cd  ef=54  g", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_LONG(), null), null);
            fail();
        } catch (Exception e) {
        }
        try {
            Capsule.parse("ab=12 cd=xy  ef=54  g=z", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_LONG(), 17), null);
            fail();
        } catch (Exception e) {
        }

        assertEquals(map("ab", 12.0, "cd", 100.0, "ef", 5.4, "g", 100.0), Capsule.parse("ab=12 cd  ef=5.4  g", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_DOUBLE(), 100), null));
        try {
            Capsule.parse("ab=12.1 cd  ef=5.4  g", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_DOUBLE(), null), null);
            fail();
        } catch (Exception e) {
        }
        try {
            Capsule.parse("ab=12 cd=xy ef=54  g=z", Capsule.T_MAP(Capsule.T_STRING(), Capsule.T_DOUBLE(), 17.0), null);
            fail();
        } catch (Exception e) {
        }

        assertEquals(map(12.3, 12L, 1.01, 17L, 2.05, 54L, 4.0, 17L), Capsule.parse("12.3=12 1.01  2.05=54  4.0", Capsule.T_MAP(Capsule.T_DOUBLE(), Capsule.T_LONG(), 17), null));

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

    @Test
    public void testParseJavaVersionLine() {
        assertEquals("1.8.0_161",   Capsule.parseJavaVersionLine("java version \"1.8.0_161\""));
        assertEquals("9.0.4",       Capsule.parseJavaVersionLine("java version \"9.0.4\""));
        assertEquals("10",          Capsule.parseJavaVersionLine("java version \"10\" 2018-03-20"));
        assertEquals("11",          Capsule.parseJavaVersionLine("java version \"11\" 2018-09-25"));
        assertEquals("10.0.2",      Capsule.parseJavaVersionLine("openjdk version \"10.0.2\" 2018-07-17"));
    }

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    // may be called once per test (always writes jar into /capsule.jar)
    private Capsule newCapsule(Jar jar) {
        return (Capsule) CapsuleTestUtils.newCapsule(jar, path("capsule.jar"));
    }

    private Class<?> loadCapsule(Jar jar) throws IOException {
        jar = makeRealCapsuleJar(jar);
        Class<?> clazz = CapsuleTestUtils.loadCapsule(jar, path("capsule.jar"));
        setProperties(clazz, props);
        setCacheDir(clazz, cache);
        return clazz;
    }

    private Object newCapsule(Class<?> capsuleClass) {
        return CapsuleTestUtils.newCapsule(capsuleClass, path("capsule.jar"));
    }

    private Jar newCapsuleJar() {
        return new Jar()
                .setAttribute("Manifest-Version", "1.0")
                .setAttribute("Main-Class", "TestCapsule")
                .setAttribute("Premain-Class", "TestCapsule")
                .setAttribute("Capsule-Agent", "true");
    }

    private Jar makeRealCapsuleJar(Jar jar) throws IOException {
        return jar.addClass(Capsule.class)
                .addClass(TestCapsule.class);
    }

    private Capsule setTarget(Capsule capsule, String artifact) {
        return Reflect.on(capsule).call("setTarget", artifact).get();
    }

    private Capsule setTarget(Capsule capsule, Path jar) {
        return Reflect.on(capsule).call("setTarget", jar).get();
    }

    private Path path(String first, String... more) {
        return fs.getPath(first, more);
    }

    private Path absolutePath(String first, String... more) {
        return fs.getPath(first, more).toAbsolutePath();
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

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object... ss) {
        final Map<K, V> m = new HashMap<>();
        for (int i = 0; i < ss.length / 2; i++)
            m.put((K) ss[i * 2], (V) ss[i * 2 + 1]);
        return Collections.unmodifiableMap(m);
    }

    private Path mockDep(String dep, String type) {
        return mockDep(dep, type, dep).get(0);
    }

    private List<Path> mockDep(String dep, String type, String... artifacts) {
        return mockDep(TestCapsule.class, dep, type, artifacts);
    }

    private List<Path> mockDep(Class<?> testCapsuleClass, String dep, String type, String... artifacts) {
        List<Path> as = new ArrayList<>(artifacts.length);
        for (String a : artifacts)
            as.add(artifact(a, type));

        try {
            testCapsuleClass.getMethod("mock", String.class, String.class, List.class).invoke(null, dep, type, as);
        } catch (ReflectiveOperationException e) {
            throw rethrow(e);
        }

        return as;
    }

    private Path artifact(String x, String type) {
        String[] coords = x.split(":");
        String group = coords[0];
        String artifact = coords[1];
        String artifactDir = artifact.split("-")[0]; // arbitrary
        String version = coords[2] + (coords.length > 3 ? "-" + coords[3] : "");
        return cache.resolve("deps").resolve(group).resolve(artifactDir).resolve(artifact + "-" + version + '.' + type);
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
