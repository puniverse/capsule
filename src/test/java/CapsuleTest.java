/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.DependencyManager;
import capsule.Jar;
import com.google.jimfs.Jimfs;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

public class CapsuleTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private FileSystem fs;
    private Path cache;

    @Before
    public void setup() {
        fs = Jimfs.newFileSystem();
        cache = fs.getPath("/cache");
    }

    private Capsule newCapsule(Jar jar, DependencyManager dependencyManager, String... args) {
        try {
            ByteArrayOutputStream baos = jar.write(new ByteArrayOutputStream());
            baos.close();

            return new Capsule(Paths.get("test"), args, cache, baos.toByteArray(), dependencyManager);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private Jar newCapsuleJar() {
        return new Jar()
                .setAttribute("Manifest-Version", "1.0")
                .setAttribute("Main-Class", "Capsule");
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
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsule(jar, null, args);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        Path appCahce = cache.resolve("apps").resolve("com.acme.Foo");

        assertTrue(pb.command().contains("-Dcapsule.app=com.acme.Foo"));
        assertTrue(pb.command().contains("-Dcapsule.dir=" + appCahce));

        assertTrue(Files.isDirectory(cache));
        assertTrue(Files.isDirectory(cache.resolve("apps")));
        assertTrue(Files.isDirectory(appCahce));
        assertTrue(Files.isRegularFile(appCahce.resolve(".extracted")));
        assertTrue(Files.isRegularFile(appCahce.resolve("foo.jar")));
    }

    @Test
    public void testSystemProperties() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .setAttribute("System-Properties", "bar baz=33 foo=y")
                .addEntry("foo.jar", Jar.toInputStream("", UTF8));

        String[] args = strings("hi", "there");
        List<String> cmdLine = list("-Dfoo=x", "-Dzzz");

        Capsule capsule = newCapsule(jar, null, args);
        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);
        assertTrue(pb.command().contains("-Dfoo=x"));
        assertTrue(pb.command().contains("-Dbar"));
        assertTrue(pb.command().contains("-Dzzz"));
        assertTrue(pb.command().contains("-Dbaz=33"));
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
}
