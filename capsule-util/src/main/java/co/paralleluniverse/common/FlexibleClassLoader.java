/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 *
 * @author pron
 */
public abstract class FlexibleClassLoader extends ClassLoader {
    private final boolean childFirst;
    private final ThreadLocal<Boolean> inGetResourceAsStream = new ThreadLocal<Boolean>();

    public FlexibleClassLoader(ClassLoader parent, boolean childFirst) {
        super(parent);
        this.childFirst = childFirst;
    }

    public FlexibleClassLoader(boolean childFirst) {
        this.childFirst = childFirst;
    }

    @Override
    public URL getResource(String name) {
        if (!childFirst)
            return super.getResource(name);
        URL url = findResource(name);
        if (url == null)
            url = super.getResource(name);
        return url;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        inGetResourceAsStream.set(Boolean.TRUE);
        try {
            InputStream is = null;
            if (!childFirst)
                is = super.getResourceAsStream(name);
            if (is == null)
                is = findResourceAsStream(name);
            if (is == null && childFirst)
                is = super.getResourceAsStream(name);
            return is;
        } finally {
            inGetResourceAsStream.remove();
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!childFirst)
            return super.loadClass(name, resolve);
        Class c;
        try {
            c = findClass(name);
            if (resolve)
                resolveClass(c);
            return c;
        } catch (ClassNotFoundException e) {
        }
        return super.loadClass(name, resolve);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration[] tmp = new Enumeration[2];
        tmp[childFirst ? 1 : 0] = super.getResources(name);
        tmp[childFirst ? 0 : 1] = findResources1(name);
        return new sun.misc.CompoundEnumeration<URL>(tmp);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = findLoadedClass(name);
        if (clazz == null) {
            final byte[] buf = readResource(name.replace('.', '/') + ".class");
            if (buf == null)
                throw new ClassNotFoundException(name);
            clazz = defineClass(name, buf, 0, buf.length);
        }
        return clazz;
    }

    @Override
    protected URL findResource(String name) {
        if (inGetResourceAsStream.get() == Boolean.TRUE)
            return null;
        return findResource1(name);
    }

    protected abstract URL findResource1(String name);

    protected abstract Enumeration<URL> findResources1(String name);

    protected abstract InputStream findResourceAsStream(String name);

    protected abstract byte[] readResource(String name);
}
