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
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 *
 * @author pron
 */
public final class CapsuleLauncher {
    private static final String CAPSULE_CLASS_NAME = "Capsule";
    private static final String CUSTOM_CAPSULE_CLASS_NAME = "CustomCapsule";
    private static final String OPT_JMX_REMOTE = "com.sun.management.jmxremote";
    private static final String ATTR_MAIN_CLASS = "Main-Class";

    public static Object getCapsule(Path path, Path cacheDir) {
        try {
            final Manifest mf;
            try (JarFile jar = new JarFile(path.toFile())) {
                mf = jar.getManifest();
            }
            final ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()});
            final Class clazz = loadCapsuleClass(mf, cl);
            if (clazz == null)
                throw new RuntimeException(path + " does not appear to be a valid capsule.");

            return getCapsuleConstructor(clazz, Path.class, Path.class).newInstance(path, cacheDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not create capsule instance.", e);
        }
    }

    public static Object getCapsule(byte[] buf, Path cacheDir) {
        try {
            final JarClassLoader cl = new JarClassLoader(buf);
            final Class clazz = loadCapsuleClass(cl.getManifest(), cl);
            if (clazz == null)
                throw new RuntimeException("The given buffer does not appear to be a valid capsule.");

            return getCapsuleConstructor(clazz, byte[].class, Path.class).newInstance((Object) buf, cacheDir);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not create capsule instance.", e);
        }
    }

    public static ProcessBuilder prepareForLaunch(Object capsule, List<String> cmdLine, String[] args) {
        final Method launch = getCapsuleMethod(capsule, "prepareForLaunch", List.class, String[].class);
        return (ProcessBuilder) invoke(capsule, launch, cmdLine, args);
    }

    public static String getAppId(Object capsule) {
        final Method appId = getCapsuleMethod(capsule, "appId", String[].class);
        return (String) invoke(capsule, appId, (Object) null);
    }

    private static Class loadCapsuleClass(Manifest mf, ClassLoader cl) {
        final String mainClass = mf.getMainAttributes() != null ? mf.getMainAttributes().getValue(ATTR_MAIN_CLASS) : null;
        if (mainClass == null)
            return null;

        try {
            Class clazz;
            if (CAPSULE_CLASS_NAME.equals(mainClass)) {
                try {
                    clazz = cl.loadClass(CUSTOM_CAPSULE_CLASS_NAME);
                } catch (ClassNotFoundException e) {
                    clazz = cl.loadClass(CAPSULE_CLASS_NAME);
                }
            } else {
                clazz = cl.loadClass(mainClass);
                if (!CAPSULE_CLASS_NAME.equals(clazz.getSuperclass().getName()))
                    clazz = null;
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Constructor<?> getCapsuleConstructor(Class<?> capsuleClass, Class<?>... paramTypes) throws NoSuchMethodException {
        final Constructor<?> ctor = capsuleClass.getDeclaredConstructor(paramTypes);
        ctor.setAccessible(true);
        return ctor;
    }

    private static Method getCapsuleMethod(Object capsule, String name, Class<?>... paramTypes) {
        final Method m = getMethod(capsule.getClass(), name, paramTypes);
        if (m == null)
            throw new RuntimeException("Could not call " + name + " on " + capsule + ". It does not appear to be a valid capsule.");
        return m;
    }

    private static Method getMethod(Class clazz, String name, Class<?>... paramTypes) {
        try {
            final Method method = clazz.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return clazz.getSuperclass() != null ? getMethod(clazz.getSuperclass(), name, paramTypes) : null;
        }
    }

    private static Object invoke(Object obj, Method method, Object... params) {
        try {
            return method.invoke(obj, params);
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        } catch (InvocationTargetException e) {
            final Throwable t = e.getTargetException();
            if (t instanceof RuntimeException)
                throw (RuntimeException) t;
            if (t instanceof Error)
                throw (Error) t;
            throw new RuntimeException(t);
        }
    }

    public static List<String> enableJMX(List<String> cmdLine) {
        final String arg = "-D" + OPT_JMX_REMOTE;
        if (cmdLine.contains(arg))
            return cmdLine;
        final List<String> cmdLine2 = new ArrayList<>(cmdLine);
        cmdLine2.add(arg);
        return cmdLine2;
    }

    private CapsuleLauncher() {
    }
}
