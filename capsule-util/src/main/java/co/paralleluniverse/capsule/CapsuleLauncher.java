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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

/**
 * Provides methods for loading, inspecting, and launching capsules.
 *
 * @author pron
 */
public final class CapsuleLauncher {
    private static final String CAPSULE_CLASS_NAME = "Capsule";
    private static final String OPT_JMX_REMOTE = "com.sun.management.jmxremote";
    private static final String ATTR_MAIN_CLASS = "Main-Class";
    private static final String PROP_USER_HOME = "user.home";
    private static final String PROP_OS_NAME = "os.name";
    private static final String CACHE_DEFAULT_NAME = "capsule";

    /**
     * Creates a new capsule
     *
     * @param jarFile  the capsule JAR
     * @param cacheDir the directory to use as cache
     * @return a capsule object which can then be passed to {@link #getAppId(Object) getAppId} and
     *         {@link #prepareForLaunch(Object, List, List) prepareForLaunch}.
     */
    public static Object newCapsule(Path jarFile, Path cacheDir) {
        return newCapsule(jarFile, cacheDir, null);
    }

    public static Object newCapsule(Path jarFile, Path cacheDir, Map<String, Path> javaHomes) {
        try {
            final Manifest mf;
            try (final JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile))) {
                mf = jis.getManifest();
            }

            final ClassLoader cl = new JarClassLoader(jarFile, true);
            final Class<?> clazz = loadCapsuleClass(mf, cl);
            if (clazz == null)
                throw new RuntimeException(jarFile + " does not appear to be a valid capsule.");

            if (javaHomes != null)
                setJavaHomes(clazz, javaHomes);

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

    /**
     * Creates a {@link ProcessBuilder} ready to use for launching the capsule.
     *
     * @param capsule the capsule object returned from {@link #newCapsule(Path, Path) newCapsule}.
     * @param jvmArgs JVM arguments to use for launching the capsule
     * @param args    command line arguments to pass to the capsule.
     * @return a {@link ProcessBuilder} for launching the capsule process
     */
    public static ProcessBuilder prepareForLaunch(Object capsule, List<String> jvmArgs, List<String> args) {
        final Method launch = getCapsuleMethod(capsule, "prepareForLaunch", List.class, String[].class);
        return (ProcessBuilder) invoke(capsule, launch, jvmArgs, (String[]) args.toArray(new String[args.size()]));
    }

    /**
     * Returns a capsule's ID.
     *
     * @param capsule the capsule object returned from {@link #newCapsule(Path, Path) newCapsule}.
     * @return the capsule's ID.
     */
    public static String getAppId(Object capsule) {
        final Method appId = getCapsuleMethod(capsule, "appId", String[].class);
        return (String) invoke(capsule, appId, (Object) null);
    }

    /**
     * Returns a capsule's version.
     *
     * @param capsule the capsule object returned from {@link #newCapsule(Path, Path) newCapsule}.
     * @return the capsule's version.
     */
    public static String getVersion(Object capsule) {
        final Field version = getCapsuleField(capsule, "VERSION");
        return (String) get(capsule, version);
    }

    /**
     * Returns all known Java installations
     *
     * @return a map from the version strings to their respective paths of the Java installations.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Path> getJavaHomes() {
        try {
            final Class<?> clazz = CapsuleLauncher.class.getClassLoader().loadClass(CAPSULE_CLASS_NAME);
            final Method m = clazz.getDeclaredMethod("getJavaHomes");
            m.setAccessible(true);
            return (Map<String, Path>) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void setJavaHomes(Class<?> capsuleClass, Map<String, Path> javaHomes) {
        final Field homes = getField(capsuleClass, "JAVA_HOMES");
        if (homes == null)
            return;
        set(null, homes, javaHomes);
    }

    /**
     * Returns the given capsule's supported modes.
     * @param capsule
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getModes(Object capsule) {
        final Method getModes = getMethod(capsule.getClass(), "getModes");
        return (Set<String>)(getModes != null ? invoke(capsule, getModes) : Collections.emptySet());
    }

    private static Method getCapsuleMethod(Object capsule, String name, Class<?>... paramTypes) {
        final Method m = getMethod(capsule.getClass(), name, paramTypes);
        if (m == null)
            throw new RuntimeException("Could not call " + name + " on " + capsule + ". It does not appear to be a valid capsule.");
        return m;
    }

    private static Field getCapsuleField(Object capsule, String name) {
        final Field f = getField(capsule.getClass(), name);
        if (f == null)
            throw new RuntimeException("Could not find " + name + " on " + capsule + ". It does not appear to be a valid capsule.");
        return f;
    }

    private static Method getMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            final Method method = clazz.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return clazz.getSuperclass() != null ? getMethod(clazz.getSuperclass(), name, paramTypes) : null;
        }
    }

    private static Field getField(Class<?> clazz, String name) {
        try {
            final Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return clazz.getSuperclass() != null ? getField(clazz.getSuperclass(), name) : null;
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

    private static Object get(Object obj, Field field) {
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        }
    }

    private static void set(Object obj, Field field, Object value) {
        try {
            field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        }
    }

    /**
     * Adds an option to the JVM arguments to enable JMX connection
     *
     * @param jvmArgs the JVM args
     * @return a new list of JVM args
     */
    public static List<String> enableJMX(List<String> jvmArgs) {
        final String arg = "-D" + OPT_JMX_REMOTE;
        if (jvmArgs.contains(arg))
            return jvmArgs;
        final List<String> cmdLine2 = new ArrayList<>(jvmArgs);
        cmdLine2.add(arg);
        return cmdLine2;
    }

    /**
     * Returns the path of the default capsule cache directory
     */
    public static Path getDefaultCacheDir() {
        final Path cache;
        cache = getCacheHome().resolve((isWindows() ? "" : ".") + CACHE_DEFAULT_NAME);
        return cache;
    }

    private static Path getCacheHome() {
        final Path userHome = Paths.get(System.getProperty(PROP_USER_HOME));
        if (!isWindows())
            return userHome;

        Path localData;
        final String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            localData = Paths.get(localAppData);
            if (!Files.isDirectory(localData))
                throw new RuntimeException("%LOCALAPPDATA% set to nonexistent directory " + localData);
        } else {
            localData = userHome.resolve(Paths.get("AppData", "Local"));
            if (!Files.isDirectory(localData))
                localData = userHome.resolve(Paths.get("Local Settings", "Application Data"));
            if (!Files.isDirectory(localData))
                throw new RuntimeException("%LOCALAPPDATA% is undefined, and neither "
                        + userHome.resolve(Paths.get("AppData", "Local")) + " nor "
                        + userHome.resolve(Paths.get("Local Settings", "Application Data")) + " have been found");
        }
        return localData;
    }

    private static boolean isWindows() {
        return System.getProperty(PROP_OS_NAME).toLowerCase().startsWith("windows");
    }

    private CapsuleLauncher() {
    }
}
