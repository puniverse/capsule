/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import capsule.test.Pair;
import co.paralleluniverse.common.JarClassLoader;
import co.paralleluniverse.common.PathClassLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This capsule uses a class loader that is compatible with JimFS
 */
public class TestCapsule extends Capsule {
    static final boolean USE_JAR_CLASSLOADER = true;
    private static Map<Pair<String, String>, List<Path>> DEPS;

    public TestCapsule(Path jarFile) {
        super(jarFile);
    }

    public TestCapsule(Capsule pred) {
        super(pred);
    }

    @Override
    ClassLoader newClassLoader(ClassLoader parent, List<Path> ps) {
        if (ps.size() != 1)
            throw new AssertionError("Paths: " + ps);
        try {
            return USE_JAR_CLASSLOADER
                    ? new JarClassLoader(ps.get(0), parent, false)
                    : new PathClassLoader(ps.toArray(new Path[0]), parent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void reset() {
        DEPS = null;
    }

    public static void mock(String coords, String type, List<Path> paths) {
        if (DEPS == null)
            DEPS = new HashMap<>();
        DEPS.put(new Pair(coords, type), paths);
    }

    @Override
    protected List<Path> resolve0(Object x) {
        if (x instanceof Pair)
            return DEPS.get((Pair) x);
        return super.resolve0(x); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected Object lookup0(Object o, String type, Map.Entry<String, ?> attrContext, Object context) {
        if (DEPS == null)
            return super.lookup0(o, type, attrContext, context);
        if (o instanceof String) {
            String x = (String) o;
            if (x.contains(":"))
                o = new Pair(x, type.isEmpty() ? "jar" : type);
        }
        return super.lookup0(o, type, attrContext, context); //To change body of generated methods, choose Tools | Templates.
    }
}
