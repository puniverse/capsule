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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 *
 * @author pron
 */
public final class CapsuleLauncher {
    private static final String CAPSULE_CLASS_NAME = "Capsule";
    private static final String OPT_JMX_REMOTE = "com.sun.management.jmxremote";
    private static final String ATTR_MAIN_CLASS = "Main-Class";

    public static Object newCapsule(Path jarFile, Path cacheDir) {
        try {
            final Manifest mf;
            try (final JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile))) {
                mf = jis.getManifest();
            }

            final ClassLoader cl = new JarClassLoader(jarFile, true);
            final Class clazz = loadCapsuleClass(mf, cl);
            if (clazz == null)
                throw new RuntimeException(jarFile + " does not appear to be a valid capsule.");

            final Constructor<?> ctor = clazz.getDeclaredConstructor(Path.class, Path.class);
            ctor.setAccessible(true);
            return ctor.newInstance(jarFile, cacheDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Could not create capsule instance.", e);
        }
    }

    private static Class loadCapsuleClass(Manifest mf, ClassLoader cl) {
        final String mainClass = mf.getMainAttributes() != null ? mf.getMainAttributes().getValue(ATTR_MAIN_CLASS) : null;
        if (mainClass == null)
            return null;

        try {
            Class clazz = cl.loadClass(mainClass);
            if (!isCapsuleClass(clazz))
                clazz = null;
            return clazz;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean isCapsuleClass(Class<?> clazz) {
        if (clazz == null)
            return false;
        if (CAPSULE_CLASS_NAME.equals(clazz.getName()))
            return true;
        return isCapsuleClass(clazz.getSuperclass());
    }

    public static ProcessBuilder prepareForLaunch(Object capsule, List<String> cmdLine, String[] args) {
        final Method launch = getCapsuleMethod(capsule, "prepareForLaunch", List.class, String[].class);
        return (ProcessBuilder) invoke(capsule, launch, cmdLine, args);
    }

    public static String getAppId(Object capsule) {
        final Method appId = getCapsuleMethod(capsule, "appId", String[].class);
        return (String) invoke(capsule, appId, (Object) null);
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
