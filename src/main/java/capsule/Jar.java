/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Jar {
    private final ByteArrayOutputStream baos;
    private final Manifest manifest;
    private final JarFile jar;
    private final JarInputStream jis;
    private JarOutputStream jos;

    public Jar(Manifest manifest) {
        this.baos = new ByteArrayOutputStream();
        this.jar = null;
        this.jis = null;
        this.manifest = manifest;
        if (this.manifest == null)
            throw new NullPointerException();
    }

    public Jar(InputStream jar, Manifest manifest) throws IOException {
        this.baos = new ByteArrayOutputStream();
        this.jar = null;
        this.jis = jar instanceof JarInputStream ? (JarInputStream) jar : new JarInputStream(jar);
        this.manifest = manifest != null ? manifest : new Manifest(jis.getManifest());
        if (this.manifest == null)
            throw new NullPointerException();
    }

    public Jar(JarFile jar, Manifest manifest) throws IOException {
        this.baos = new ByteArrayOutputStream();
        this.jar = jar;
        this.jis = null;
        this.manifest = manifest != null ? manifest : new Manifest(jar.getManifest());
        if (this.manifest == null)
            throw new NullPointerException();
    }

    public Jar() {
        this(new Manifest());
    }

    public Jar(InputStream jar) throws IOException {
        this(jar, null);
    }

    public Jar(Path jar, Manifest manifest) throws IOException {
        this(new JarFile(jar.toFile()), manifest);
    }

    public Jar(String jar, Manifest manifest) throws IOException {
        this(new JarFile(jar), manifest);
    }

    public Jar(JarFile jar) throws IOException {
        this(jar, null);
    }

    public Jar(Path jar) throws IOException {
        this(jar, null);
    }

    public Jar(String jar) throws IOException {
        this(jar, null);
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
            jos = JarUtil.updateJar(jar, manifest, baos);
        else if (jis != null)
            jos = JarUtil.updateJar(jis, manifest, baos);
        else
            jos = new JarOutputStream(baos, manifest);
    }

    public Jar addEntry(Path path, InputStream is) throws IOException {
        return addEntry(path.toString(), is);
    }

    public Jar addEntry(String path, InputStream is) throws IOException {
        beginWriting();
        JarUtil.addEntry(jos, path, is);
        return this;
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
}
