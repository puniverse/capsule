/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author pron
 */
public final class CapsuleLauncher {
    private static final String CAPSULE_CLASS_NAME = "Capsule";
    private static final String CUSTOM_CAPSULE_CLASS_NAME = "CustomCapsule";

    public static ProcessBuilder launchCapsule(Path path, List<String> cmdLine, String[] args) {
        try {
            final ClassLoader cl = new URLClassLoader(new URL[]{path.toUri().toURL()}, null);
            Class clazz;
            try {
                clazz = cl.loadClass(CUSTOM_CAPSULE_CLASS_NAME);
            } catch (ClassNotFoundException e) {
                clazz = cl.loadClass(CAPSULE_CLASS_NAME);
            }
            final Object capsule;
            try {
                Constructor<?> ctor = clazz.getConstructor(Path.class);
                ctor.setAccessible(true);
                capsule = ctor.newInstance(path);
            } catch (Exception e) {
                throw new RuntimeException("Could not launch custom capsule.", e);
            }
            final Method launch = clazz.getMethod("prepareForLaunch", List.class, String[].class);
            launch.setAccessible(true);
            return (ProcessBuilder) launch.invoke(capsule, cmdLine, args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(path + " does not appear to be a valid capsule.", e);
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

    private CapsuleLauncher() {
    }
}
