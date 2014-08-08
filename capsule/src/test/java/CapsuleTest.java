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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import static org.truth0.Truth.*;
import static org.mockito.Mockito.*;
//import static org.hamcrest.CoreMatchers.*;
//import static org.hamcrest.collection.IsIn.*;

public class CapsuleTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private FileSystem fs;
    private Path cache;

    @Before
    public void setup() {
        fs = Jimfs.newFileSystem();
        cache = fs.getPath("/cache");
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

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals(getProperty(pb, "capsule.app"), "com.acme.Foo");
        assertEquals(getProperty(pb, "capsule.dir"), appCache.toString());
//        assertTrue(pb.command().contains("-Dcapsule.app=com.acme.Foo"));
//        assertTrue(pb.command().contains("-Dcapsule.dir=" + appCache));

        assertEquals(getMainAndArgs(pb), list("com.acme.Foo", "hi", "there"));

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

        ASSERT.that(getClassPath(pb)).has().item(fs.getPath("capsule.jar"));
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

        ASSERT.that(getClassPath(pb)).has().item(fs.getPath("capsule.jar"));
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
    public void testCapsuleInClass() throws Exception {
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

        ASSERT.that(getClassPath(pb)).has().noneOf(fs.getPath("capsule.jar"));
        ASSERT.that(getClassPath(pb)).has().allOf(
                appCache,
                appCache.resolve("foo.jar"),
                appCache.resolve("lib").resolve("a.jar"),
                appCache.resolve("lib").resolve("b.jar"));

//        assertThat(fs.getPath("capsule.jar"), not(isIn(getClassPath(pb))));
//        assertThat(appCache, isIn(getClassPath(pb)));
//        assertThat(appCache.resolve("foo.jar"), isIn(getClassPath(pb)));
//        assertThat(appCache.resolve("lib").resolve("a.jar"), isIn(getClassPath(pb)));
//        assertThat(appCache.resolve("lib").resolve("b.jar"), isIn(getClassPath(pb)));
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

        assertTrue(pb.command().contains("-Dfoo=x"));
        assertTrue(pb.command().contains("-Dbar"));
        assertTrue(pb.command().contains("-Dzzz"));
        assertTrue(pb.command().contains("-Dbaz=33"));
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

        assertEquals(pb.command(), list(appCache.resolve(Capsule.isWindows() ? "scr.bat" : "scr.sh").toString(), "hi", "there"));
    }

    @Test
    public void testEmptyCapsule() throws Exception {
        Jar jar = newCapsuleJar();

        Jar app = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry(Capsule.class)
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

        assertTrue(pb != null);

        String appId = capsule.appId(args);
        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals(getProperty(pb, "capsule.app"), "com.acme.Foo");
        assertEquals(getProperty(pb, "capsule.dir"), appCache.toString());

        assertEquals(getMainAndArgs(pb), list("com.acme.Foo", "hi", "there"));

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

        ASSERT.that(getClassPath(pb)).has().item(fooPath);
        ASSERT.that(getClassPath(pb)).has().item(appCache);
        ASSERT.that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        ASSERT.that(getClassPath(pb)).has().noneOf(appCache.resolve("lib").resolve("a.jar"));
    }

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    private Capsule newCapsule(Jar jar, DependencyManager dependencyManager) {
        try {
            Constructor<Capsule> ctor = Capsule.class.getDeclaredConstructor(Path.class, byte[].class, Path.class, Object.class);
            ctor.setAccessible(true);
            return ctor.newInstance(Paths.get("capsule.jar"), jar.toByteArray(), cache, dependencyManager);
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

    private List<Path> getClassPath(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int i = cmd.indexOf("-classpath");
        if (i < 0)
            return null;
        final String cp = cmd.get(i + 1);
        // return Arrays.asList(cp.split(":"));
        return paths(cp);
    }

    private List<Path> paths(String cp) {
        final List<Path> res = new ArrayList<>();
        for (String p : cp.split(":"))
            res.add(fs.getPath(p));
        return res;
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
    //</editor-fold>
}
