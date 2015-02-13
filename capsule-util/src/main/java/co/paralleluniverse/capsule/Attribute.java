/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.util.Map;

/**
 *
 * @author pron
 */
public class Attribute<T> {
    public static <T> Attribute<T> named(String name) {
        return new Attribute<>(name);
    }
    
    public static <T> Attribute<T> of(Map.Entry<String, T> attr) {
        return new Attribute<>(attr.getKey());
    }

    private final String name;

    private Attribute(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    Map.Entry<String, T> toEntry() {
        return new Map.Entry<String, T>() {
            @Override
            public String getKey() {
                return name;
            }

            @Override
            public T getValue() {
                return null;
            }

            @Override
            public T setValue(T value) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
