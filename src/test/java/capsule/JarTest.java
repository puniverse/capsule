/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.Test;
import static org.junit.Assert.*;

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

        printEntries(toInput(res));

        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
        assertEquals("5678", man2.getMainAttributes().getValue("Bar"));
    }

    @Test
    public void testUpdateJar() throws Exception {
        Path jarPath = Files.createTempFile("temp", ".jar");
        try {
            // create
            new Jar().setAttribute("Manifest-Version", "1.0") // necessary, otherwise the manifest won't be written to the jar
                    .setAttribute("Foo", "1234")
                    .setAttribute("Bar", "5678")
                    .addEntry(Paths.get("foo.txt"), Jar.toInputStream("I am foo!\n", UTF8))
                    .addEntry(Paths.get("dir", "bar.txt"), Jar.toInputStream("I am bar!\n", UTF8))
                    .write(jarPath);

            // update
            ByteArrayOutputStream res = new Jar(jarPath)
                    .setAttribute("Baz", "hi!")
                    .setAttribute("Bar", "8765")
                    .addEntry(Paths.get("dir", "baz.txt"), Jar.toInputStream("And I am baz!\n", UTF8))
                    .write(new ByteArrayOutputStream());

            // test
            printEntries(toInput(res));

            assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
            assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
            assertEquals("And I am baz!\n", getEntryAsString(toInput(res), Paths.get("dir", "baz.txt"), UTF8));
            Manifest man2 = toInput(res).getManifest();
            assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
            assertEquals("8765", man2.getMainAttributes().getValue("Bar"));
            assertEquals("hi!", man2.getMainAttributes().getValue("Baz"));
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
        printEntries(toInput(res));

        assertEquals("I am foo!\n", getEntryAsString(toInput(res), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(res), Paths.get("dir", "bar.txt"), UTF8));
        assertEquals("And I am baz!\n", getEntryAsString(toInput(res), Paths.get("dir", "baz.txt"), UTF8));
        Manifest man2 = toInput(res).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
        assertEquals("8765", man2.getMainAttributes().getValue("Bar"));
        assertEquals("hi!", man2.getMainAttributes().getValue("Baz"));
    }

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
}
