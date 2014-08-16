/*
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.common;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author pron
 */
public class JarClassLoader extends FlexibleClassLoader {
    private final Manifest mf;
    private final byte[] buffer;
    private final Path jarFile;

    public JarClassLoader(byte[] jar, ClassLoader parent, boolean childFirst) throws IOException {
        super(parent, childFirst);
        this.buffer = jar;
        this.jarFile = null;
        this.mf = getManifest(newInputStream());
    }

    public JarClassLoader(byte[] jar, boolean childFirst) throws IOException {
        super(childFirst);
        this.buffer = jar;
        this.jarFile = null;
        this.mf = getManifest(newInputStream());
    }

    public JarClassLoader(Path jarFile, ClassLoader parent, boolean childFirst) throws IOException {
        super(parent, childFirst);
        this.buffer = null;
        this.jarFile = jarFile;
        this.mf = getManifest(newInputStream());
    }

    public JarClassLoader(Path jarFile, boolean childFirst) throws IOException {
        super(childFirst);
        this.buffer = null;
        this.jarFile = jarFile;
        this.mf = getManifest(newInputStream());
    }

    public Manifest getManifest() {
        return mf;
    }

    @Override
    protected URL findResource1(String name) {
        try {
            if (!hasResource(name))
                return null;
            else
                return new URL("jar:" + jarFile.toUri() + "!/" + name);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Enumeration<URL> findResources1(String name) {
        final URL url = findResource1(name);
        return (Enumeration<URL>)Collections.enumeration(url != null ? Collections.singleton(url) : Collections.emptySet());
    }

    private boolean hasResource(String path) {
        try (InputStream is = findResourceAsStream(path)) {
            return is != null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected InputStream findResourceAsStream(String path) {
        try {
            final ZipInputStream jis = new ZipInputStream(newInputStream());
            for (ZipEntry entry; (entry = jis.getNextEntry()) != null;) {
                if (path.equalsIgnoreCase(entry.getName())) {
                    if (entry.isDirectory())
                        throw new FileNotFoundException(path + " is a directory");

                    return jis;
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected byte[] readResource(String path) {
        try (ZipInputStream jis = new ZipInputStream(newInputStream())) {
            for (ZipEntry entry; (entry = jis.getNextEntry()) != null;) {
                if (path.equalsIgnoreCase(entry.getName())) {
                    if (entry.isDirectory())
                        throw new FileNotFoundException(path + " is a directory");
                    final byte[] buf;
                    final long size = entry.getSize();
                    if (size < 0) {
                        final ByteArrayOutputStream bas = new ByteArrayOutputStream();
                        copy(jis, bas);
                        bas.close();
                        buf = bas.toByteArray();
                    } else {
                        buf = new byte[(int) size];
                        int n = 0;
                        while (n != -1)
                            n = jis.read(buf, n, buf.length - n);
                    }
                    return buf;
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static long copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[0x1000];  // 4K
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    private InputStream newInputStream() {
        try {
            final InputStream is = buffer != null ? new ByteArrayInputStream(buffer) : Files.newInputStream(jarFile);
            return skipToZipStart(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Manifest getManifest(InputStream is) throws IOException {
        try (JarInputStream jis = new JarInputStream(is)) {
            return jis.getManifest();
        }
    }

    private static int[] ZIP_HEADER = new int[]{'\n', 'P', 'K', 0x03, 0x04};

    private static InputStream skipToZipStart(InputStream is) throws IOException {
        if (!is.markSupported())
            is = new BufferedInputStream(is);
        int state = 1;
        for (;;) {
            if (state == 1)
                is.mark(ZIP_HEADER.length);
            final int b = is.read();
            if (b < 0)
                throw new IllegalArgumentException("Not a JAR/ZIP file");
            if (b == ZIP_HEADER[state]) {
                state++;
                if (state == ZIP_HEADER.length)
                    break;
            } else
                state = 0;
        }
        is.reset();
        return is;
    }
}
