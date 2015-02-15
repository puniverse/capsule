/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.test;

import co.paralleluniverse.capsule.Jar;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Properties;

/**
 *
 * @author pron
 */
public final class CapsuleTestUtils {
    private static final Class<?> capsuleClass;

    static {
        try {
            capsuleClass = Class.forName("Capsule");
            accessible(capsuleClass.getDeclaredField("PROFILE")).set(null, 10); // disable profiling even when log=debug
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static Object newCapsule(Jar jar, Path path) {
        try {
            jar.write(path);
            final String mainClass = jar.getAttribute("Main-Class");
            final Class<?> clazz = Class.forName(mainClass);

            Constructor<?> ctor = accessible(clazz.getDeclaredConstructor(Path.class));
            return ctor.newInstance(path);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void setCacheDir(Path cache) {
        try {
            accessible(capsuleClass.getDeclaredField("CACHE_DIR")).set(null, cache);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static void setProperties(Properties props) {
        try {
            accessible(capsuleClass.getDeclaredField("PROPERTIES")).set(null, props);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static void resetOutputStreams() {
        setSTDOUT(System.out);
        setSTDERR(System.err);
    }
    
    public static void setSTDOUT(PrintStream ps) {
        setStream("STDOUT", ps);
    }

    public static void setSTDERR(PrintStream ps) {
        setStream("STDERR", ps);
    }

    public static void setStream(String stream, PrintStream ps) {
        try {
            accessible(capsuleClass.getDeclaredField(stream)).set(null, ps);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static <T extends AccessibleObject> T accessible(T x) {
        if (!x.isAccessible())
            x.setAccessible(true);

        if (x instanceof Field) {
            Field field = (Field) x;
            if ((field.getModifiers() & Modifier.FINAL) != 0) {
                try {
                    Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }

        return x;
    }
    
    public static boolean isCI() {
        return (isEnvTrue("CI") || isEnvTrue("CONTINUOUS_INTEGRATION") || isEnvTrue("TRAVIS"));
    }

    private static boolean isEnvTrue(String envVar) {
        final String ev = System.getenv(envVar);
        if (ev == null)
            return false;
        try {
            return Boolean.parseBoolean(ev);
        } catch (Exception e) {
            return false;
        }
    }

    public static final PrintStream DEVNULL = new PrintStream(new OutputStream() {
        @Override
        public void write(int b) {
        }

        // OPTIONAL (overriding the above method is enough)
        @Override
        public void write(byte[] b, int off, int len) {
        }

        @Override
        public void write(byte[] b) throws IOException {
        }
    });

    public static class StringPrintStream extends PrintStream {
        private final ByteArrayOutputStream baos;

        public StringPrintStream() {
            super(new ByteArrayOutputStream());
            this.baos = (ByteArrayOutputStream) out;
        }

        @Override
        public String toString() {
            close();
            return baos.toString();
        }

        public BufferedReader toReader() {
            return new BufferedReader(new StringReader(toString()));
        }
    }

    private CapsuleTestUtils() {
    }
}
