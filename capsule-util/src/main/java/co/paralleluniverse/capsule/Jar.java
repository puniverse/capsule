/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.jar.Pack200;

/**
 * A JAR file that can be easily modified.
 */
public class Jar {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private final Manifest manifest;
    private final JarFile jar;
    private final JarInputStream jis;
    private JarOutputStream jos;
    private Pack200.Packer packer;
    private boolean reallyExecutable;

    /**
     * Creates a new, empty, JAR
     */
    public Jar() {
        this.jar = null;
        this.jis = null;
        this.manifest = new Manifest();
    }

    /**
     * Reads in the JAR from the given {@code InputStream}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(InputStream jar) throws IOException {
        this.jar = null;
        this.jis = jar instanceof JarInputStream ? (JarInputStream) jar : new JarInputStream(jar);
        this.manifest = new Manifest(jis.getManifest());
    }

    /**
     * Reads in the JAR from the given {@code JarFile}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(JarFile jar) throws IOException {
        this.jar = jar;
        this.jis = null;
        this.manifest = new Manifest(jar.getManifest());
    }

    /**
     * Reads in the JAR from the given {@code Path}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(Path jar) throws IOException {
        this(new JarFile(jar.toFile()));
    }

    /**
     * Reads in the JAR from the given {@code File}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(File jar) throws IOException {
        this(new JarFile(jar));
    }

    /**
     * Reads in the JAR from the given path.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(String jar) throws IOException {
        this(new JarFile(jar));
    }

    /**
     * Returns the manifest of this JAR. Modifications to the manifest will be reflected in the written JAR, provided they are done
     * before any entries are added with {@code addEntry()}.
     */
    public Manifest getManifest() {
        return manifest;
    }

    /**
     * Sets an attribute in the main section of the manifest.
     *
     * @param name  the attribute's name
     * @param value the attribute's value
     * @return {@code this}
     * @throws IllegalStateException if entries have been added or the JAR has been written prior to calling this methods.
     */
    public Jar setAttribute(String name, String value) {
        if (jos != null)
            throw new IllegalStateException("Manifest cannot be modified after entries are added or the JAR is written");
        getManifest().getMainAttributes().putValue(name, value);
        return this;
    }

    public Jar setAttribute(String section, String name, String value) {
        getManifest().getAttributes(section).putValue(name, value);
        return this;
    }

    /**
     * Sets an attribute in the main section of the manifest to a list.
     * The list elements will be joined with a single whitespace character.
     *
     * @param name   the attribute's name
     * @param values the attribute's value
     * @return {@code this}
     * @throws IllegalStateException if entries have been added or the JAR has been written prior to calling this methods.
     */
    public Jar setListAttribute(String name, List<String> values) {
        return setAttribute(name, join(values));
    }

    /**
     * Sets an attribute in a non-main section of the manifest to a list.
     * The list elements will be joined with a single whitespace character.
     *
     * @param section the section's name
     * @param name    the attribute's name
     * @param values  the attribute's value
     * @return {@code this}
     * @throws IllegalStateException if entries have been added or the JAR has been written prior to calling this methods.
     */
    public Jar setListAttribute(String section, String name, List<String> values) {
        return setAttribute(name, section, join(values));
    }

    /**
     * Sets an attribute in the main section of the manifest to a map.
     * The map entries will be joined with a single whitespace character, and each key-value pair will be joined with a '='.
     *
     * @param name   the attribute's name
     * @param values the attribute's value
     * @return {@code this}
     * @throws IllegalStateException if entries have been added or the JAR has been written prior to calling this methods.
     */
    public Jar setMapAttribute(String name, Map<String, String> values) {
        return setAttribute(name, join(values));
    }

    /**
     * Sets an attribute in a non-main section of the manifest to a map.
     * The map entries will be joined with a single whitespace character, and each key-value pair will be joined with a '='.
     *
     * @param section the section's name
     * @param name    the attribute's name
     * @param values  the attribute's value
     * @return {@code this}
     * @throws IllegalStateException if entries have been added or the JAR has been written prior to calling this methods.
     */
    public Jar setMapAttribute(String section, String name, Map<String, String> values) {
        return setAttribute(name, section, join(values));
    }

    /**
     * Returns an attribute's value from this JAR's manifest's main section.
     *
     * @param name the attribute's name
     */
    public String getAttribute(String name) {
        return getManifest().getMainAttributes().getValue(name);
    }

    /**
     * Returns an attribute's value from a non-main section of this JAR's manifest.
     *
     * @param section the manifest's section
     * @param name    the attribute's name
     */
    public String getAttribute(String section, String name) {
        return getManifest().getAttributes(section).getValue(name);
    }

    /**
     * Returns an attribute's list value from this JAR's manifest's main section.
     * The attributes string value will be split on whitespace into the returned list.
     * The returned list may be safely modified.
     *
     * @param name the attribute's name
     */
    public List<String> getListAttribute(String name) {
        return split(getAttribute(name));
    }

    /**
     * Returns an attribute's list value from a non-main section of this JAR's manifest.
     * The attributes string value will be split on whitespace into the returned list.
     * The returned list may be safely modified.
     *
     * @param section the manifest's section
     * @param name    the attribute's name
     */
    public List<String> getListAttribute(String section, String name) {
        return split(getAttribute(section, name));
    }

    /**
     * Returns an attribute's map value from this JAR's manifest's main section.
     * The attributes string value will be split on whitespace into map entries, and each entry will be split on '=' to get the key-value pair.
     * The returned map may be safely modified.
     *
     * @param name the attribute's name
     */
    public Map<String, String> getMapAttribute(String name, String defaultValue) {
        return mapSplit(getAttribute(name), defaultValue);
    }

    /**
     * Returns an attribute's map value from a non-main section of this JAR's manifest.
     * The attributes string value will be split on whitespace into map entries, and each entry will be split on '=' to get the key-value pair.
     * The returned map may be safely modified.
     *
     * @param section the manifest's section
     * @param name    the attribute's name
     */
    public Map<String, String> getMapAttribute(String section, String name, String defaultValue) {
        return mapSplit(getAttribute(section, name), defaultValue);
    }

    private void beginWriting() throws IOException {
        if (jos != null)
            return;
        if (reallyExecutable) {
            final Writer out = new OutputStreamWriter(baos, Charset.defaultCharset());
            out.write("#!/bin/sh\n\nexec java -jar $0 \"$@\"\n\n");
        }
        if (jar != null)
            jos = updateJar(jar, manifest, baos);
        else if (jis != null)
            jos = updateJar(jis, manifest, baos);
        else
            jos = new JarOutputStream(baos, manifest);
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param is   the entry's content
     * @return {@code this}
     */
    public Jar addEntry(String path, InputStream is) throws IOException {
        beginWriting();
        addEntry(jos, path, is);
        return this;
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path    the entry's path within the JAR
     * @param content the entry's content
     * @return {@code this}
     */
    public Jar addEntry(String path, byte[] content) throws IOException {
        beginWriting();
        addEntry(jos, path, content);
        return this;
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param is   the entry's content
     * @return {@code this}
     */
    public Jar addEntry(Path path, InputStream is) throws IOException {
        return addEntry(path.toString(), is);
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param file the file to add as an entry
     * @return {@code this}
     */
    public Jar addEntry(Path path, Path file) throws IOException {
        return addEntry(path, Files.newInputStream(file));
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param file the path of the file to add as an entry
     * @return {@code this}
     */
    public Jar addEntry(Path path, String file) throws IOException {
        return addEntry(path, Paths.get(file));
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param file the file to add as an entry
     * @return {@code this}
     */
    public Jar addEntry(String path, File file) throws IOException {
        return addEntry(path, new FileInputStream(file));
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param file the path of the file to add as an entry
     * @return {@code this}
     */
    public Jar addEntry(String path, String file) throws IOException {
        return addEntry(path, new FileInputStream(file));
    }

    /**
     * Sets a {@link Pack200.Packer Pack200 packer} to use when writing the JAR.
     *
     * @param packer
     * @return {@code this}
     */
    public Jar setPacker(Pack200.Packer packer) {
        this.packer = packer;
        return this;
    }

    public Jar setReallyExecutable(boolean value) {
        this.reallyExecutable = value;
        return this;
    }

    private void write(byte[] content, OutputStream os) throws IOException {
        if (packer != null)
            packer.pack(new JarInputStream(new ByteArrayInputStream(content)), os);
        else
            os.write(content);
    }

    /**
     * Writes this JAR to an output stream, and closes the stream.
     */
    public <T extends OutputStream> T write(T os) throws IOException {
        beginWriting();
        jos.close();

        write(baos.toByteArray(), os);
        os.close();
        return os;
    }

    /**
     * Writes this JAR to a file.
     */
    public File write(File file) throws IOException {
        write(new FileOutputStream(file));
        return file;
    }

    /**
     * Writes this JAR to a file.
     */
    public Path write(Path path) throws IOException {
        write(Files.newOutputStream(path));
        return path;
    }

    /**
     * Writes this JAR to a file.
     */
    public void write(String file) throws IOException {
        write(Paths.get(file));
    }

    /**
     * Returns this JAR file as an array of bytes.
     */
    public byte[] toByteArray() {
        try {
            return write(new ByteArrayOutputStream()).toByteArray();
        } catch (IOException e) {
            throw new AssertionError();
        }
    }

    private static JarOutputStream updateJar(JarInputStream jar, Manifest manifest, OutputStream os) throws IOException {
        final JarOutputStream jarOut = new JarOutputStream(os, manifest);
        for (JarEntry entry; (entry = jar.getNextJarEntry()) != null;) {
            if (entry.getName().equals(entry.toString())) {
                if (entry.getName().equals("META-INF/MANIFEST.MF"))
                    continue;
                jarOut.putNextEntry(entry);
                copy0(jar, jarOut);
                jar.closeEntry();
                jarOut.closeEntry();
            }
        }
        return jarOut;
    }

    private static JarOutputStream updateJar(JarFile jar, Manifest manifest, OutputStream os) throws IOException {
        final JarOutputStream jarOut = new JarOutputStream(os, manifest);
        final Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            if (entry.getName().equals("META-INF/MANIFEST.MF"))
                continue;
            jarOut.putNextEntry(entry);
            copy(jar.getInputStream(entry), jarOut);
            jarOut.closeEntry();
        }
        return jarOut;
    }

    private static void addEntry(JarOutputStream jarOut, String path, InputStream is) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        copy(is, jarOut);
    }

    private static void addEntry(JarOutputStream jarOut, String path, byte[] data) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        jarOut.write(data);
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        try {
            copy0(is, os);
        } finally {
            is.close();
        }
    }

    private static void copy0(InputStream is, OutputStream os) throws IOException {
        final byte[] buffer = new byte[1024];
        for (int bytesRead; (bytesRead = is.read(buffer)) != -1;)
            os.write(buffer, 0, bytesRead);
    }

    /**
     * Turns a {@code String} into an {@code InputStream} containing the string's encoded characters.
     *
     * @param str     the string
     * @param charset the {@link Charset} to use when encoding the string.
     * @return an {@link InputStream} containing the string's encoded characters.
     */
    public static InputStream toInputStream(String str, Charset charset) {
        return new ByteArrayInputStream(str.getBytes(charset));
    }

    private static String join(List<String> list, String separator) {
        if (list == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (String element : list)
            sb.append(element).append(separator);
        sb.delete(sb.length() - separator.length(), sb.length());
        return sb.toString();
    }

    private static String join(Map<String, String> map, String kvSeparator, String separator) {
        if (map == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet())
            sb.append(entry.getKey()).append(kvSeparator).append(entry.getValue()).append(separator);
        sb.delete(sb.length() - separator.length(), sb.length());
        return sb.toString();
    }

    private static String join(List<String> list) {
        return join(list, " ");
    }

    private static String join(Map<String, String> map) {
        return join(map, "=", " ");
    }

    private static List<String> split(String str, String separator) {
        if (str == null)
            return null;
        String[] es = str.split(separator);
        final List<String> list = new ArrayList<>(es.length);
        for (String e : es) {
            e = e.trim();
            if (!e.isEmpty())
                list.add(e);
        }
        return list;
    }

    private static List<String> split(String list) {
        return split(list, " ");
    }

    private static Map<String, String> mapSplit(String map, char kvSeparator, String separator, String defaultValue) {
        if (map == null)
            return null;
        Map<String, String> m = new HashMap<>();
        for (String entry : split(map, separator)) {
            final String key = getBefore(entry, kvSeparator);
            String value = getAfter(entry, kvSeparator);
            if (value == null) {
                if (defaultValue != null)
                    value = defaultValue;
                else
                    throw new IllegalArgumentException("Element " + entry + " in \"" + map + "\" is not a key-value entry separated with " + kvSeparator + " and no default value provided");
            }
            m.put(key, value);
        }
        return m;
    }

    private static Map<String, String> mapSplit(String map, String defaultValue) {
        return mapSplit(map, '=', " ", defaultValue);
    }

    private static String getBefore(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return s;
        return s.substring(0, i);
    }

    private static String getAfter(String s, char separator) {
        final int i = s.indexOf(separator);
        if (i < 0)
            return null;
        return s.substring(i + 1);
    }
}
