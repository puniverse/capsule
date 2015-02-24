/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package co.paralleluniverse.common;

import static co.paralleluniverse.common.Exceptions.rethrow;
import com.sun.nio.zipfs.ZipFileSystem;
import com.sun.nio.zipfs.ZipFileSystemProvider;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.Map;

/**
 * Bypasses the check in ZipFileSystemProvider.newFileSystem that verifies that the given path is in the default FileSystem.
 * This is a JDK bug, fixed in JDK 8. See https://bugs.openjdk.java.net/browse/JDK-8004789
 */
public final class ZipFS {
    private static final ZipFileSystemProvider ZIP_FILE_SYSTEM_PROVIDER = getZipFileSystemProvider();
    private static final Constructor<ZipFileSystem> ZIP_FILE_SYSTEM_CONSTRUCTOR;

    static {
        try {
            Constructor<ZipFileSystem> c = ZipFileSystem.class.getDeclaredConstructor(ZipFileSystemProvider.class, Path.class, Map.class);
            c.setAccessible(true);
            ZIP_FILE_SYSTEM_CONSTRUCTOR = c;
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static ZipFileSystemProvider getZipFileSystemProvider() {
        for (FileSystemProvider fsr : FileSystemProvider.installedProviders()) {
            if (fsr instanceof ZipFileSystemProvider)
                return (ZipFileSystemProvider) fsr;
        }
        throw new AssertionError("Zip file system not installed!");
    }

    public static FileSystem newZipFileSystem(Path path) throws IOException {
        // return FileSystems.newFileSystem(path, null);
        if (path.getFileSystem() instanceof ZipFileSystem)
            throw new IllegalArgumentException("Can't create a ZIP file system nested in a ZIP file system. (" + path + " is nested in " + path.getFileSystem() + ")");
        try {
            return (ZipFileSystem) ZIP_FILE_SYSTEM_CONSTRUCTOR.newInstance(ZIP_FILE_SYSTEM_PROVIDER, path, Collections.emptyMap());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } catch(Exception e) {
            throw rethrow(e);
        }
    }

    private ZipFS() {
    }
}
