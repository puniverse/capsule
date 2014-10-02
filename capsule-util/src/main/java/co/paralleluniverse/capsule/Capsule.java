/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.util.List;
import java.util.Set;

/**
 * A capsule.
 */
public interface Capsule {

    /**
     * Returns the capsule's version.
     */
    String getVersion();

    /**
     * Creates a {@link ProcessBuilder} ready to use for launching the capsule.
     *
     * @param jvmArgs JVM arguments to use for launching the capsule
     * @param args    command line arguments to pass to the capsule.
     * @return a {@link ProcessBuilder} for launching the capsule process
     */
    public ProcessBuilder prepareForLaunch(List<String> jvmArgs, List<String> args);

    /**
     * Returns a capsule's ID..
     */
    public String getAppId();

    /**
     * Returns the capsule's supported modes.
     */
    Set<String> getModes();
}
