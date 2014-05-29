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
public class JarUtilTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Test
    public void testCreateJar() throws Exception {
        Manifest man = new Manifest();
        Attributes attr = man.getMainAttributes();
        attr.putValue("Manifest-Version", "1.0"); // necessary, otherwise the manifest won't be written to the jar
        attr.putValue("Foo", "1234");
        attr.putValue("Bar", "5678");

        JarOutputStream jos = new JarOutputStream(new ByteArrayOutputStream(), man);
        JarUtil.addEntry(jos, Paths.get("foo.txt"), JarUtil.toInputStream("I am foo!\n", UTF8));
        JarUtil.addEntry(jos, Paths.get("dir", "bar.txt"), JarUtil.toInputStream("I am bar!\n", UTF8));
        jos.close();

        printEntries(toInput(jos));

        assertEquals("I am foo!\n", getEntryAsString(toInput(jos), Paths.get("foo.txt"), UTF8));
        assertEquals("I am bar!\n", getEntryAsString(toInput(jos), Paths.get("dir", "bar.txt"), UTF8));
        Manifest man2 = toInput(jos).getManifest();
        assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
        assertEquals("5678", man2.getMainAttributes().getValue("Bar"));
    }

    @Test
    public void testUpdateJar() throws Exception {
        Path jarPath = Files.createTempFile("temp", ".jar");
        try {
            // create
            Manifest man = new Manifest();
            Attributes attr = man.getMainAttributes();
            attr.putValue("Manifest-Version", "1.0"); // necessary, otherwise the manifest won't be written to the jar
            attr.putValue("Foo", "1234");
            attr.putValue("Bar", "5678");

            JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), man);
            JarUtil.addEntry(jos, Paths.get("foo.txt"), JarUtil.toInputStream("I am foo!\n", UTF8));
            JarUtil.addEntry(jos, Paths.get("dir", "bar.txt"), JarUtil.toInputStream("I am bar!\n", UTF8));
            jos.close();

            // update
            JarFile jar = new JarFile(jarPath.toFile());
            
            Manifest man1 = new Manifest(man);
            man1.getMainAttributes().putValue("Baz", "hi!");
            
            jos = JarUtil.updateJarFile(jar, man1, new ByteArrayOutputStream());
            JarUtil.addEntry(jos, Paths.get("dir", "baz.txt"), JarUtil.toInputStream("And I am baz!\n", UTF8));
            
            // test
            printEntries(toInput(jos));

            assertEquals("I am foo!\n", getEntryAsString(toInput(jos), Paths.get("foo.txt"), UTF8));
            assertEquals("I am bar!\n", getEntryAsString(toInput(jos), Paths.get("dir", "bar.txt"), UTF8));
            assertEquals("And I am baz!\n", getEntryAsString(toInput(jos), Paths.get("dir", "baz.txt"), UTF8));
            Manifest man2 = toInput(jos).getManifest();
            assertEquals("1234", man2.getMainAttributes().getValue("Foo"));
            assertEquals("5678", man2.getMainAttributes().getValue("Bar"));
            assertEquals("hi!", man2.getMainAttributes().getValue("Baz"));
        } finally {
            Files.delete(jarPath);
        }
    }

    private static JarInputStream toInput(JarOutputStream jos) {
        try {
            Field outField = FilterOutputStream.class.getDeclaredField("out");
            outField.setAccessible(true);

            jos.close();
            ByteArrayOutputStream baos = (ByteArrayOutputStream) outField.get(jos);
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
        while ((je = jar.getNextJarEntry()) != null) {
            System.out.println(je.getName());
        }
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
