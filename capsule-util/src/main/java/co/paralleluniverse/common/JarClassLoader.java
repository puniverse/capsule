/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.common;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 *
 * @author pron
 */
public class JarClassLoader extends ClassLoader {
    private final byte[] jar;

    public JarClassLoader(byte[] jar, ClassLoader parent) {
        super(parent);
        this.jar = jar;
    }

    public JarClassLoader(byte[] jar) {
        this.jar = jar;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream is = super.getResourceAsStream(name);
        if (is == null)
            is = getEntryAsStream(name);
        return is;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        final byte[] buf = getEntry(name.replace('.', '/') + ".class");
        if (buf == null)
            throw new ClassNotFoundException(name);
        return defineClass(name, buf, 0, buf.length);
    }

    private InputStream getEntryAsStream(String path) {
        try {
            final JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jar));
            for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
                if (path.equalsIgnoreCase(entry.getName())) {
                    if (entry.isDirectory())
                        throw new FileNotFoundException(path + " is a directory");

                    return jis;
                }
            }
            return null;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private byte[] getEntry(String path) {
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jar))) {
            for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
                if (path.equalsIgnoreCase(entry.getName())) {
                    if (entry.isDirectory())
                        throw new FileNotFoundException(path + " is a directory");

                    byte[] buf = new byte[(int) entry.getSize()];
                    int n = 0;
                    while (n != -1)
                        n = jis.read(buf, n, buf.length - n);
                    return buf;
                }
            }
            return null;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
