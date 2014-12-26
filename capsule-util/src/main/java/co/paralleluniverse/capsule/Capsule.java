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
import java.util.Map;
import java.util.Properties;
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
     * The properties used by the capsule.
     * These may be set to change the capsule's behavior.
     */
    Properties getProperties();
    
    /**
     * Returns a capsule's ID..
     */
    String getAppId();

    /**
     * The application's name
     */
    String getAppName();

    /**
     * The app's version or {@code null} if unversioned.
     */
    String getAppVersion();

    /**
     * Returns the capsule's supported modes.
     */
    Set<String> getModes();

    /**
     * Tests whether the given attribute is found in the manifest.
     */
    boolean hasAttribute(String attr);

    /**
     * Returns the value of the given manifest attribute with consideration to the capsule's mode.
     * If the attribute is not defined, its default value will be returned.
     */
    String getAttribute(String attr);

    /**
     * Returns the value of the given attribute (with consideration to the capsule's mode) as a list.
     */
    List<String> getListAttribute(String attr);

    /**
     * Returns the value of the given attribute (with consideration to the capsule's mode) as a map.
     *
     * @param attr         the attribute
     * @param defaultValue a default value to use for keys without a value, or {@code null} if such an event should throw an exception
     */
    Map<String, String> getMapAttribute(String attr, String defaultValue);

    /**
     * Creates a {@link ProcessBuilder} ready to use for launching the capsule.
     *
     * @param jvmArgs JVM arguments to use for launching the capsule
     * @param args    command line arguments to pass to the capsule.
     * @return a {@link ProcessBuilder} for launching the capsule process
     */
    ProcessBuilder prepareForLaunch(List<String> jvmArgs, List<String> args);
}
