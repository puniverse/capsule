/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author pron
 */
public final class StreamUtils {
    private static final AtomicInteger threadId = new AtomicInteger();

    public static Writer prefixWriter(Writer out, final String prefix) {
        return new FilterWriter(out) {
            private boolean first = true;

            @Override
            public void write(int c) throws IOException {
                if (first) {
                    super.write(prefix);
                    first = false;
                }
                super.write(c);
                if (c == '\n')
                    super.write(prefix);
            }

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                if (first) {
                    super.write(prefix);
                    first = false;
                }
                int start = 0, end = 0;
                for (char c : cbuf) {
                    end++;
                    if (c == '\n') {
                        super.write(cbuf, start, end - start);
                        start = end;
                        super.flush();
                        super.write(prefix);
                    }
                }
                if (end > start)
                    super.write(cbuf, start, end - start);
            }

            @Override
            public void write(String str, int off, int len) throws IOException {
                if (first) {
                    super.write(prefix);
                    first = false;
                }
                int start = 0, end = 0;
                while (end < str.length()) {
                    end++;
                    char c = str.charAt(end);
                    if (c == '\n') {
                        super.write(str, start, end - start);
                        start = end;
                        super.flush();
                        super.write(prefix);
                    }
                }
                if (end > start)
                    super.write(str, start, end - start);
            }
        };
    }

    public static void multiplex(PrintStream out, InputStream in, String prefix) {
        multiplex(new PrintWriter(out), in, prefix);
    }

    public static void multiplex(Writer out, InputStream in, String prefix) {
        multiplex(out, new InputStreamReader(in), prefix);
    }

    public static void multiplex(Writer out, Reader in, final String prefix) {
        startPiper("writer-piper-" + prefix, in, prefixWriter(out, prefix));
    }

    public static void startPiper(Reader in, Writer out) {
        startPiper("writer-piper-" + threadId.incrementAndGet(), in, out);
    }

    public static void startPiper(InputStream in, OutputStream out) {
        startPiper("stream-piper-" + threadId.incrementAndGet(), in, out);
    }

    private static void startPiper(String id, final Reader in, final Writer out) {
        new Thread(id) {
            @Override
            public void run() {
                try {
                    pipe(in, out);
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                }
            }
        }.start();
    }

    private static void startPiper(String id, final InputStream in, final OutputStream out) {
        new Thread(id) {
            @Override
            public void run() {
                try {
                    pipe(in, out);
                } catch (Throwable e) {
                    e.printStackTrace(System.err);
                }
            }
        }.start();
    }

    public static void pipe(Reader in, Writer out) throws IOException {
        try (Writer out1 = out) {
            int read;
            char[] buf = new char[1024];
            while (-1 != (read = in.read(buf))) {
                out.write(buf, 0, read);
                out.flush();
            }
        }
    }

    public static void pipe(InputStream in, OutputStream out) throws IOException {
        try (OutputStream out1 = out) {
            int read;
            byte[] buf = new byte[1024];
            while (-1 != (read = in.read(buf))) {
                out.write(buf, 0, read);
                out.flush();
            }
        }
    }

    private StreamUtils() {
    }
}
