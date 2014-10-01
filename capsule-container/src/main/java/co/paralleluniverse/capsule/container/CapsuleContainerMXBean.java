/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.container;

import java.util.Set;

/**
 *
 * @author pron
 */
public interface CapsuleContainerMXBean {
    Set<String> getProcesses();
    void killProcess(String id);
}
