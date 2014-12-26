/*
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.common;

import java.io.IOException;
import java.io.InputStream;
import static co.paralleluniverse.common.ZipInputStream.skipToZipStart;

/**
 *
 * @author pron
 */
public class JarInputStream extends java.util.jar.JarInputStream {

    public JarInputStream(InputStream in) throws IOException {
        super(skipToZipStart(in));
    }

    public JarInputStream(InputStream in, boolean verify) throws IOException {
        super(skipToZipStart(in), verify);
    }
}
