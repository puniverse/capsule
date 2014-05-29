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
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 *
 * @author pron
 */
public class Jar {
    private final ByteArrayOutputStream baos;
    private final Manifest manifest;
    private final JarFile jar;
    private JarOutputStream jos;

    public Jar(JarFile jar, Manifest manifest) {
        try {
            this.baos = new ByteArrayOutputStream();
            this.jar = jar;
            this.manifest = manifest != null ? manifest : new Manifest(jar.getManifest());
            if (this.manifest == null)
                throw new NullPointerException();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Jar(Path jar, Manifest manifest) throws IOException {
        this(new JarFile(jar.toFile()), manifest);
    }

    public Jar(String jar, Manifest manifest) throws IOException {
        this(new JarFile(jar), manifest);
    }

    public Jar() {
        this((JarFile) null, new Manifest());
    }

    public Jar(Manifest manifest) {
        this((JarFile) null, manifest);
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

    private void beginWriting() throws IOException {
        if (jos != null)
            return;
        if (jar != null)
            jos = JarUtil.updateJarFile(jar, manifest, baos);
        else
            jos = new JarOutputStream(baos, manifest);
    }

    public Jar addEntry(Path path, InputStream is) throws IOException {
        beginWriting();
        return addEntry(path.toString(), is);
    }

    public Jar addEntry(String path, InputStream is) throws IOException {
        beginWriting();
        JarUtil.addEntry(jos, path, is);
        return this;
    }

    public void write(OutputStream os) throws IOException {
        beginWriting();
        jos.close();

        os.write(baos.toByteArray());
        os.close();
    }

    public void write(File file) throws IOException {
        write(new FileOutputStream(file));
    }

    public void write(Path path) throws IOException {
        write(Files.newOutputStream(path));
    }

    public void write(String file) throws IOException {
        write(Paths.get(file));
    }
}
