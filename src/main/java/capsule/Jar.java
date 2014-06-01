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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Jar {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final Manifest manifest;
    private final JarFile jar;
    private final JarInputStream jis;
    private JarOutputStream jos;

    public Jar() {
        this.jar = null;
        this.jis = null;
        this.manifest = new Manifest();
    }

    public Jar(InputStream jar) throws IOException {
        this.jar = null;
        this.jis = jar instanceof JarInputStream ? (JarInputStream) jar : new JarInputStream(jar);
        this.manifest = new Manifest(jis.getManifest());
    }

    public Jar(JarFile jar) throws IOException {
        this.jar = jar;
        this.jis = null;
        this.manifest = new Manifest(jar.getManifest());
    }

    public Jar(Path jar) throws IOException {
        this(new JarFile(jar.toFile()));
    }

    public Jar(File jar) throws IOException {
        this(new JarFile(jar));
    }

    public Jar(String jar) throws IOException {
        this(new JarFile(jar));
    }

    public Manifest getManifest() {
        return manifest;
    }

    public Jar setAttribute(String name, String value) {
        getManifest().getMainAttributes().putValue(name, value);
        return this;
    }

    public Jar setAttribute(String section, String name, String value) {
        getManifest().getAttributes(section).putValue(name, value);
        return this;
    }

    private void beginWriting() throws IOException {
        if (jos != null)
            return;
        if (jar != null)
            jos = updateJar(jar, manifest, baos);
        else if (jis != null)
            jos = updateJar(jis, manifest, baos);
        else
            jos = new JarOutputStream(baos, manifest);
    }

    public Jar addEntry(Path path, InputStream is) throws IOException {
        return addEntry(path.toString(), is);
    }

    public Jar addEntry(Path path, Path file) throws IOException {
        return addEntry(path, Files.newInputStream(file));
    }

    public Jar addEntry(Path path, String file) throws IOException {
        return addEntry(path, Paths.get(file));
    }

    public Jar addEntry(String path, InputStream is) throws IOException {
        beginWriting();
        addEntry(jos, path, is);
        return this;
    }

    public Jar addEntry(String path, File file) throws IOException {
        return addEntry(path, new FileInputStream(file));
    }

    public Jar addEntry(String path, String file) throws IOException {
        return addEntry(path, new FileInputStream(file));
    }

    public <T extends OutputStream> T write(T os) throws IOException {
        beginWriting();
        jos.close();

        os.write(baos.toByteArray());
        os.close();
        return os;
    }

    public File write(File file) throws IOException {
        write(new FileOutputStream(file));
        return file;
    }

    public Path write(Path path) throws IOException {
        write(Files.newOutputStream(path));
        return path;
    }

    public void write(String file) throws IOException {
        write(Paths.get(file));
    }

    private static JarOutputStream updateJar(JarInputStream jar, Manifest manifest, OutputStream os) throws IOException {
        final JarOutputStream jarOut = new JarOutputStream(os, manifest);
        JarEntry entry;
        while ((entry = jar.getNextJarEntry()) != null) {
            if (entry.getName().equals(entry.toString())) {
                if (entry.getName().equals("META-INF/MANIFEST.MF"))
                    continue;
                jarOut.putNextEntry(entry);
                copy0(jar, jarOut);
                jar.closeEntry();
                jarOut.closeEntry();
            }
        }
        return jarOut;
    }

    private static JarOutputStream updateJar(JarFile jar, Manifest manifest, OutputStream os) throws IOException {
        final JarOutputStream jarOut = new JarOutputStream(os, manifest);
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.getName().equals("META-INF/MANIFEST.MF"))
                continue;
            jarOut.putNextEntry(entry);
            copy(jar.getInputStream(entry), jarOut);
            jarOut.closeEntry();
        }
        return jarOut;
    }

    private static void addEntry(JarOutputStream jarOut, String path, InputStream is) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        copy(is, jarOut);
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        try {
            copy0(is, os);
        } finally {
            is.close();
        }
    }

    private static void copy0(InputStream is, OutputStream os) throws IOException {
        final byte[] buffer = new byte[1024];
        for (int bytesRead; (bytesRead = is.read(buffer)) != -1;)
            os.write(buffer, 0, bytesRead);
    }

    public static InputStream toInputStream(String str, Charset charset) {
        return new ByteArrayInputStream(str.getBytes(charset));
    }
}
