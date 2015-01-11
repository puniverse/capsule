/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import co.paralleluniverse.common.JarClassLoader;
import co.paralleluniverse.common.PathClassLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 *
 * @author pron
 */
public class TestCapsule extends Capsule {

    public TestCapsule(Path jarFile, Path cacheDir) {
        super(jarFile, cacheDir);
    }

    public TestCapsule(Capsule pred) {
        super(pred);
    }

    @Override
    ClassLoader newClassLoader(ClassLoader parent, List<Path> ps) {
        if (ps.size() != 1)
            throw new AssertionError("Paths: " + ps);
        try {
            return CapsuleTest.USE_JAR_CLASSLOADER
                    ? new JarClassLoader(ps.get(0), parent, false)
                    : new PathClassLoader(ps.toArray(new Path[0]), parent);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
