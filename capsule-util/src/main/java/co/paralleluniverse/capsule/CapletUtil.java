/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

/**
 *
 * @author pron
 */
public final class CapletUtil {
    private CapletUtil() {
    }
    
    public static boolean isSubclass(Class<?> clazz, String superName) {
        for(Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            if(superName.equals(c.getName()))
                return true;
        }
        return false;
    }
}
