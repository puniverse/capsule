/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package capsule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarUtil {
    public static JarOutputStream updateJarFile(JarFile jar, Manifest manifest, OutputStream os) throws IOException {
        final JarOutputStream jarOut = new JarOutputStream(os, manifest);
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if(entry.getName().equals("META-INF/MANIFEST.MF"))
                continue;
            jarOut.putNextEntry(entry);
            copy(jar.getInputStream(entry), jarOut);
            jarOut.closeEntry();
        }
        return jarOut;
    }

    public static void addEntry(JarOutputStream jarOut, Path path, InputStream is) throws IOException {
        addEntry(jarOut, path.toString(), is);
    }

    public static void addEntry(JarOutputStream jarOut, String path, InputStream is) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        copy(is, jarOut);
    }

    public static InputStream toInputStream(String str, Charset charset) {
        return new ByteArrayInputStream(str.getBytes(charset));
    }
    
    private static void copy(InputStream is, OutputStream os) throws IOException {
        try {
            final byte[] buffer = new byte[1024];
            for (int bytesRead; (bytesRead = is.read(buffer)) != -1;)
                os.write(buffer, 0, bytesRead);
        } finally {
            is.close();
        }
    }
}
