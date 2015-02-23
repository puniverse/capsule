/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package co.paralleluniverse.common;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 *
 * @author pron
 */
public class ZipInputStream extends java.util.zip.ZipInputStream {

    public ZipInputStream(InputStream in) throws IOException {
        super(skipToZipStart(in));
    }

    public ZipInputStream(InputStream in, Charset charset) throws IOException {
        super(skipToZipStart(in), charset);
    }

    private static final int[] ZIP_HEADER = new int[]{'P', 'K', 0x03, 0x04};

    static InputStream skipToZipStart(InputStream is) throws IOException {
        if (!is.markSupported())
            is = new BufferedInputStream(is);
        int state = 0;
        for (;;) {
            if (state == 0)
                is.mark(ZIP_HEADER.length);
            final int b = is.read();
            if (b < 0)
                throw new IllegalArgumentException("Not a JAR/ZIP file");
            if (state >= 0 && b == ZIP_HEADER[state]) {
                state++;
                if (state == ZIP_HEADER.length)
                    break;
            } else {
                state = -1;
                if (b == '\n' || b == 0) // start matching on \n and \0
                    state = 0;
            }
        }
        is.reset();
        return is;
    }
}
