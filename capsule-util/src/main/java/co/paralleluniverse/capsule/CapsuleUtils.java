/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A utility class that can be used by code launched in a capsule to interact with the capsule.
 *
 * @author pron
 */
public final class CapsuleUtils {
    private static final String PROP_CAPSULE_JAR = "capsule.jar";
    private static final String PROP_CAPSULE_DIR = "capsule.dir";
    private static final String PROP_CAPSULE_APP = "capsule.app";

    /**
     * Tests if we're running in a capsule.
     *
     * @return {@code true} if this code has been launched in a capsule, or {@code false} otherwise.
     */
    public static boolean isRunningInCapsule() {
        return System.getProperty(PROP_CAPSULE_APP) != null;
    }

    /**
     * Returns this capsule's app ID.
     *
     * @throws IllegalStateException if this code has not been launched in a capsule.
     */
    public static String getAppId() {
        if (!isRunningInCapsule())
            throw new IllegalStateException("Not running in capsule");
        return System.getProperty(PROP_CAPSULE_APP);
    }

    /**
     * Returns the full path to the capsule's JAR.
     *
     * @throws IllegalStateException if this code has not been launched in a capsule.
     */
    public static Path getCapsuleJar() {
        if (!isRunningInCapsule())
            throw new IllegalStateException("Not running in capsule");
        return Paths.get(System.getProperty(PROP_CAPSULE_JAR));
    }

    /**
     * Returns the full path to the capsule's app cache directory.
     * The app's cache directory is where this app's capsule has been extracted.
     *
     * @return the full path to the capsule's app cache directory or {@code null}, if the capsule has not been extracted.
     * @throws IllegalStateException if this code has not been launched in a capsule.
     */
    public static Path getCapsuleAppDir() {
        if (!isRunningInCapsule())
            throw new IllegalStateException("Not running in capsule");
        return Paths.get(System.getProperty(PROP_CAPSULE_DIR));
    }

    private CapsuleUtils() {
    }
}
