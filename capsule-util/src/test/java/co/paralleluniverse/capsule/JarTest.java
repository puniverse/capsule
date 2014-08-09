/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import co.paralleluniverse.common.JarClassLoader;
import com.google.common.jimfs.Jimfs;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author pron
 */
public class JarTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Test
    public void testCreateJar() throws Exception {
        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .setAttribute("Bar", "5678")
                .addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
        assertEquals("5678", man2.getMainAttributes().getValue("Bar"));
    }

    @Test
    public void testUpdateJar() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();
        Path jarPath = fs.getPath("test.jar");
        try {
            // create
            new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                    .setAttribute("Foo", "1234")
                    .setAttribute("Bar", "5678")
                    .setListAttribute("List", Arrays.asList("a", "b"))
                    .setMapAttribute("Map", new HashMap<String, String>() {
                        {
                            put("x", "1");
                            put("y", "2");
                        }
                    })
                    .addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                    .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                    .write(jarPath);

            // update
            Jar jar = new Jar(jarPath);
            ByteArrayOutputStream res = jar
                    .setAttribute("Baz", "hi!")
                    .setAttribute("Bar", "8765")
                    .setListAttribute("List", addLast(addFirst(jar.getListAttribute("List"), "0"), "c"))
                    .setMapAttribute("Map", put(put(jar.getMapAttribute("Map", null), "z", "3"), "x", "0"))
                    .addEntry(Paths.get("dir", "baz.txt"), Jar.toInputStream("And I am baz!\n", UTF8))
                    .write(new ByteArrayOutputStream());

            // test
            // printEntries(toInput(res));
            assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
            assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
            assertEquals("And I am baz!\n", getEntryAsString(toInput(res), Paths.get("dir", "baz.txt"), UTF8));
            Manifest man2 = toInput(res).getManifest();
            assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
            assertEquals("8765", man2.getMainAttributes().getValue("Bar"));
            assertEquals("hi!", man2.getMainAttributes().getValue("Baz"));
            assertEquals(Arrays.asList("0", "a", "b", "c"), new Jar(toInput(res)).getListAttribute("List"));
            assertEquals(new HashMap<String, String>() {
                {
                    put("x", "0");
                    put("y", "2");
                    put("z", "3");
                }
            }, new Jar(toInput(res)).getMapAttribute("Map", null));
        } finally {
            Files.delete(jarPath);
        }
    }

    @Test
    public void testUpdateJar2() throws Exception {
        // create
        ByteArrayOutputStream baos = new Jar()
                .setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .setAttribute("Bar", "5678")
                .addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                .write(new ByteArrayOutputStream());

        // update
        ByteArrayOutputStream res = new Jar(toInput(baos))
                .setAttribute("Baz", "hi!")
                .setAttribute("Bar", "8765")
                .addEntry(Paths.get("dir", "baz.txt"), Jar.toInputStream("And I am baz!\n", UTF8))
                .write(new ByteArrayOutputStream());

        // test
        // printEntries(toInput(res));
        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
        assertEquals("And I am baz!\n", getEntryAsString(toInput(res), Paths.get("dir", "baz.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
        assertEquals("8765", man2.getMainAttributes().getValue("Bar"));
        assertEquals("hi!", man2.getMainAttributes().getValue("Baz"));
    }

    @Test
    public void testAddDirectory1() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();
        Path myDir = fs.getPath("dir1", "dir2");

        Files.createDirectories(myDir.resolve("da"));
        Files.createDirectories(myDir.resolve("db"));
        Files.createFile(myDir.resolve("da").resolve("x"));
        Files.createFile(myDir.resolve("da").resolve("y"));
        Files.createFile(myDir.resolve("db").resolve("w"));
        Files.createFile(myDir.resolve("db").resolve("z"));

        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addEntries((Path) null, myDir)
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertTrue(getEntry(toInput(res), Paths.get("da", "x")) != null);
        assertTrue(getEntry(toInput(res), Paths.get("da", "y")) != null);
        assertTrue(getEntry(toInput(res), Paths.get("db", "w")) != null);
        assertTrue(getEntry(toInput(res), Paths.get("db", "z")) != null);
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    @Test
    public void testAddDirectory2() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();
        Path myDir = fs.getPath("dir1", "dir2");

        Files.createDirectories(myDir.resolve("da"));
        Files.createDirectories(myDir.resolve("db"));
        Files.createFile(myDir.resolve("da").resolve("x"));
        Files.createFile(myDir.resolve("da").resolve("y"));
        Files.createFile(myDir.resolve("db").resolve("w"));
        Files.createFile(myDir.resolve("db").resolve("z"));

        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addEntries(Paths.get("d1", "d2"), myDir)
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertTrue(getEntry(toInput(res), Paths.get("d1", "d2", "da", "x")) != null);
        assertTrue(getEntry(toInput(res), Paths.get("d1", "d2", "da", "y")) != null);
        assertTrue(getEntry(toInput(res), Paths.get("d1", "d2", "db", "w")) != null);
        assertTrue(getEntry(toInput(res), Paths.get("d1", "d2", "db", "z")) != null);
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    @Test
    public void testAddZip1() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();
        Path myZip = fs.getPath("zip1");

        new Jar().addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                .write(myZip);

        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addEntries((String) null, myZip)
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    @Test
    public void testAddZip2() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();
        Path myZip = fs.getPath("zip1");

        new Jar().addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                .write(myZip);

        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addEntries(Paths.get("d1", "d2"), myZip)
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("d1", "d2", "foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("d1", "d2", "dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    @Test
    public void testAddZip3() throws Exception {
        FileSystem fs = Jimfs.newFileSystem();

        ByteArrayOutputStream myZip = new Jar()
                .addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                .write(new ByteArrayOutputStream());

        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addEntries((Path) null, toInput(myZip))
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    @Test
    public void testAddZip4() throws Exception {
        ByteArrayOutputStream myZip = new Jar()
                .addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                .write(new ByteArrayOutputStream());

        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addEntries(Paths.get("d1", "d2"), toInput(myZip))
                .write(new ByteArrayOutputStream());

        // printEntries(toInput(res));
        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("d1", "d2", "foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("d1", "d2", "dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    @Test
    public void testAddPackage() throws Exception {
        final Class clazz = JarClassLoader.class;
        
        ByteArrayOutputStream res = new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                .setAttribute("Foo", "1234")
                .addPackageOf(clazz)
                .write(new ByteArrayOutputStream());

        printEntries(toInput(res));
        
        final Path pp = Paths.get(clazz.getPackage().getName().replace('.', '/'));
        
        assertTrue(getEntry(toInput(res), pp.resolve(clazz.getSimpleName() + ".class")) != null);
        assertTrue(getEntry(toInput(res), pp.resolve("ProcessUtil.class")) != null);

        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
    }

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    private static JarInputStream toInput(JarOutputStream jos) {
        try {
            Field outField = FilterOutputStream.class.getDeclaredField("out");
            outField.setAccessible(true);
            jos.close();
            return toInput((ByteArrayOutputStream) outField.get(jos));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static JarInputStream toInput(ByteArrayOutputStream baos) {
        try {
            baos.close();
            byte[] bytes = baos.toByteArray();

            return new JarInputStream(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static <T> List<T> addLast(List<T> list, T value) {
        list.add(value);
        return list;
    }

    private static <T> List<T> addFirst(List<T> list, T value) {
        list.add(0, value);
        return list;
    }

    private static <K, V> Map<K, V> put(Map<K, V> map, K key, V value) {
        map.put(key, value);
        return map;
    }

    private static String getEntryAsString(JarInputStream jar, Path entry, Charset charset) throws IOException {
        byte[] buffer = getEntry(jar, entry);
        return buffer != null ? new String(buffer, charset) : null;
    }

    private static String getEntryAsString(JarFile jar, Path entry, Charset charset) throws IOException {
        byte[] buffer = getEntry(jar, entry);
        return buffer != null ? new String(buffer, charset) : null;
    }

    private static byte[] getEntry(JarFile jar, Path entry) throws IOException {
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry je = entries.nextElement();
            if (je.getName().equals(entry.toString())) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                copy(jar.getInputStream(je), baos);
                baos.close();
                return baos.toByteArray();
            }
        }
        return null;
    }

    private static byte[] getEntry(JarInputStream jar, Path entry) throws IOException {
        JarEntry je;
        while ((je = jar.getNextJarEntry()) != null) {
            if (je.getName().equals(entry.toString())) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                copy(jar, baos);
                baos.close();
                return baos.toByteArray();
            }
        }
        return null;
    }

    private static void printEntries(JarInputStream jar) throws IOException {
        JarEntry je;
        while ((je = jar.getNextJarEntry()) != null)
            System.out.println(je.getName());
        System.out.println();
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        try {
            final byte[] buffer = new byte[1024];
            for (int bytesRead; (bytesRead = is.read(buffer)) != -1;)
                os.write(buffer, 0, bytesRead);
        } finally {
            is.close();
        }
    }
    //</editor-fold>
}
