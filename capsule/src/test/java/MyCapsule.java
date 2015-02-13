/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * A custom capsule example
 */
public class MyCapsule extends TestCapsule {
    public MyCapsule(Path jarFile) {
        super(jarFile);
    }

    public MyCapsule(Capsule pred) {
        super(pred);
    }

    @Override
    protected <T> T attribute(Map.Entry<String, T> attr) {
        if (attr == ATTR_SYSTEM_PROPERTIES) {
            final Map<String, String> props = super.attribute(ATTR_SYSTEM_PROPERTIES);
            props.put("foo", "z");
            props.put("baz", "44");
            return (T) props;
        }
        if (attr == ATTR_JVM_ARGS) {
            final List<String> args = super.attribute(ATTR_JVM_ARGS);
            for (ListIterator<String> it = args.listIterator(); it.hasNext();) {
                String arg = it.next();
                if (arg.startsWith("-Xmx"))
                    it.set("-Xmx3000");
                else if (arg.startsWith("-Xms"))
                    it.set("-Xms3");
            }
            return (T) args;
        }
        return super.attribute(attr); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected String[] buildAppId() {
        return super.buildAppId();
    }

    @Override
    protected List<String> buildArgs(List<String> args) {
        return super.buildArgs(args);
    }
}
