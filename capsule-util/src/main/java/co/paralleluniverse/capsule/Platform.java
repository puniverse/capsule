/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.util.Objects;


/**
 *
 * @author pron
 */
public final class Platform {
    private static final String PROP_OS_NAME = "os.name";

    private static final String OS_WINDOWS = "windows";
    private static final String OS_MACOS = "macos";
    private static final String OS_LINUX = "linux";
    private static final String OS_SOLARIS = "solaris";
    private static final String OS_BSD = "bsd";
    private static final String OS_AIX = "aix";
    private static final String OS_HP_UX = "hp-ux";
    private static final String OS_UNIX = "unix";
    private static final String OS_POSIX = "posix";
    private static final String OS_VMS = "vms";
    
    private static final Platform MY_PLATFORM = new Platform(System.getProperty(PROP_OS_NAME));

    public static Platform myPlatform() {
        return MY_PLATFORM;
    }
    
    private final String os;
    private final String platform;
    
    public Platform(String os) {
        this.os = os.toLowerCase();
        this.platform = getOS(os);
    }

    @Override
    public String toString() {
        return "Platform{" + "os: " + os + ", platform: " + platform + '}';
    }
    

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(this.os);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Platform other = (Platform) obj;
        if (!Objects.equals(this.os, other.os))
            return false;
        return true;
    }
    
    /**
     * Tests whether the current OS is Windows.
     */
    @SuppressWarnings("StringEquality")
    public boolean isWindows() {
        return platform == OS_WINDOWS;
    }

    /**
     * Tests whether the current OS is MacOS.
     */
    @SuppressWarnings("StringEquality")
    public boolean isMac() {
        return platform == OS_MACOS;
    }

    /**
     * Tests whether the current OS is UNIX/Linux.
     */
    @SuppressWarnings("StringEquality")
    public boolean isUnix() {
        return platform == OS_LINUX || platform == OS_SOLARIS || platform == OS_BSD
                || platform == OS_AIX || platform == OS_HP_UX;
    }

    private static String getOS(String os) {
        if (os.startsWith("windows"))
            return OS_WINDOWS;
        if (os.startsWith("mac"))
            return OS_MACOS;
        if (os.contains("linux"))
            return OS_LINUX;
        if (os.contains("solaris") || os.contains("sunos") || os.contains("illumos"))
            return OS_SOLARIS;
        if (os.contains("bsd"))
            return OS_BSD;
        if (os.contains("aix"))
            return OS_AIX;
        if (os.contains("hp-ux"))
            return OS_HP_UX;
        if (os.contains("vms"))
            return OS_VMS;

        return null;
    }

    /**
     * The suffix of a native library on this OS.
     */
    public String getNativeLibExtension() {
        if (isWindows())
            return "dll";
        if (isMac())
            return "dylib";
        if (isUnix())
            return "so";
        throw new RuntimeException("Unsupported operating system: " + System.getProperty(PROP_OS_NAME));
    }
}
