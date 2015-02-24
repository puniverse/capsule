/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.common;

import java.lang.reflect.InvocationTargetException;

/**
 *
 * @author pron
 */
public class Exceptions {
    public static RuntimeException rethrow(Throwable t) {
        while (t instanceof InvocationTargetException)
            t = ((InvocationTargetException) t).getTargetException();
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        if (t instanceof Error)
            throw (Error) t;
        throw new RuntimeException(t);
    }
}
