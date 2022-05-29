/*
 * Capsule
 * Copyright (c) 2014-2015, Parallel Universe Software Co. All rights reserved.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
//import java.util.jar.Pack200;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

/**
 * A JAR file that can be easily modified.
 * This class is not thread-safe.
 */
public class Jar {
    private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF"; // java.util.jar.JarFile.MANIFEST_NAME
    private static final String ATTR_MANIFEST_VERSION = "Manifest-Version";
    private OutputStream os;
    private final Manifest manifest;
    private final JarInputStream jis;
    private JarOutputStream jos;
    //private Pack200.Packer packer;
    private String jarPrefixStr;
    private Path jarPrefixFile;
    private boolean sealed;

    //<editor-fold defaultstate="collapsed" desc="Constructors">
    /////////// Constructors ///////////////////////////////////
    /**
     * Creates a new, empty, JAR
     */
    public Jar() {
        this.jis = null;
        this.manifest = new Manifest();
    }

    /**
     * Reads in the JAR from the given {@code InputStream}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(InputStream jar) throws IOException {
        this.jis = jar instanceof JarInputStream ? (JarInputStream) jar : newJarInputStream(jar);
        this.manifest = new Manifest(jis.getManifest());
    }

    /**
     * Reads in the JAR from the given {@code Path}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(Path jar) throws IOException {
        this.jis = newJarInputStream(Files.newInputStream(jar));
        this.manifest = new Manifest(jis.getManifest());
    }

    /**
     * Reads in the JAR from the given {@code File}.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(File jar) throws IOException {
        this(jar.toPath());
    }

    /**
     * Reads in the JAR from the given path.
     * Modifications will not be made to the original JAR file, but to a new copy, which is then written with {@link #write(OutputStream) write()}.
     */
    public Jar(String jar) throws IOException {
        this(Paths.get(jar));
    }

    /**
     * Creates a copy
     */
    public Jar(Jar jar) {
        this.jis = jar.jis;
        this.manifest = new Manifest(jar.manifest);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Manifest">
    /////////// Manifest ///////////////////////////////////
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
    public final Jar setAttribute(String name, String value) {
        verifyNotSealed();
        if (jos != null)
            throw new IllegalStateException("Manifest cannot be modified after entries are added.");
        getManifest().getMainAttributes().putValue(name, value);
        return this;
    }

    /**
     * Sets an attribute in a non-main section of the manifest.
     *
     * @param section the section's name
     * @param name    the attribute's name
     * @param value   the attribute's value
     * @return {@code this}
     * @throws IllegalStateException if entries have been added or the JAR has been written prior to calling this methods.
     */
    public final Jar setAttribute(String section, String name, String value) {
        verifyNotSealed();
        if (jos != null)
            throw new IllegalStateException("Manifest cannot be modified after entries are added.");
        Attributes attr = getManifest().getAttributes(section);
        if (attr == null) {
            attr = new Attributes();
            getManifest().getEntries().put(section, attr);
        }
        attr.putValue(name, value);
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
    public Jar setListAttribute(String name, Collection<?> values) {
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
    public Jar setListAttribute(String section, String name, Collection<?> values) {
        return setAttribute(section, name, join(values));
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
    public Jar setMapAttribute(String name, Map<String, ?> values) {
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
    public Jar setMapAttribute(String section, String name, Map<String, ?> values) {
        return setAttribute(section, name, join(values));
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
        Attributes attr = getManifest().getAttributes(section);
        return attr != null ? attr.getValue(name) : null;
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
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Entries">
    /////////// Entries ///////////////////////////////////
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
        return addEntry(path != null ? path.toString() : "", is);
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
        try (FileInputStream fos = new FileInputStream(file)) {
            return addEntry(path, fos);
        }
    }

    /**
     * Adds an entry to this JAR.
     *
     * @param path the entry's path within the JAR
     * @param file the path of the file to add as an entry
     * @return {@code this}
     */
    public Jar addEntry(String path, String file) throws IOException {
        try (FileInputStream fos = new FileInputStream(file)) {
            return addEntry(path, fos);
        }
    }

    /**
     * Adds a class entry to this JAR.
     *
     * @param clazz the class to add to the JAR.
     * @return {@code this}
     */
    public Jar addClass(Class<?> clazz) throws IOException {
        final String resource = clazz.getName().replace('.', '/') + ".class";
        return addEntry(resource, clazz.getClassLoader().getResourceAsStream(resource));
    }

    /**
     * Adds a directory (with all its subdirectories) or the contents of a zip/JAR to this JAR.
     *
     * @param path     the path within the JAR where the root of the directory will be placed, or {@code null} for the JAR's root
     * @param dirOrZip the directory to add as an entry or a zip/JAR file whose contents will be extracted and added as entries
     * @param filter   a filter to select particular classes
     * @return {@code this}
     */
    public Jar addEntries(Path path, Path dirOrZip, Filter filter) throws IOException {
        if (Files.isDirectory(dirOrZip))
            addDir(path, dirOrZip, filter, true);
        else {
            try (JarInputStream jis1 = newJarInputStream(Files.newInputStream(dirOrZip))) {
                addEntries(path, jis1, filter);
            }
        }
        return this;
    }

    /**
     * Adds a directory (with all its subdirectories) or the contents of a zip/JAR to this JAR.
     *
     * @param path     the path within the JAR where the root of the directory will be placed, or {@code null} for the JAR's root
     * @param dirOrZip the directory to add as an entry or a zip/JAR file whose contents will be extracted and added as entries
     * @return {@code this}
     */
    public Jar addEntries(Path path, Path dirOrZip) throws IOException {
        return addEntries(path, dirOrZip, null);
    }

    /**
     * Adds a directory (with all its subdirectories) or the contents of a zip/JAR to this JAR.
     *
     * @param path     the path within the JAR where the root of the directory/zip will be placed, or {@code null} for the JAR's root
     * @param dirOrZip the directory to add as an entry or a zip/JAR file whose contents will be extracted and added as entries
     * @param filter   a filter to select particular classes
     * @return {@code this}
     */
    public Jar addEntries(String path, Path dirOrZip, Filter filter) throws IOException {
        return addEntries(path != null ? Paths.get(path) : null, dirOrZip, filter);
    }

    /**
     * Adds a directory (with all its subdirectories) or the contents of a zip/JAR to this JAR.
     *
     * @param path     the path within the JAR where the root of the directory/zip will be placed, or {@code null} for the JAR's root
     * @param dirOrZip the directory to add as an entry or a zip/JAR file whose contents will be extracted and added as entries
     * @return {@code this}
     */
    public Jar addEntries(String path, Path dirOrZip) throws IOException {
        return addEntries(path, dirOrZip, null);
    }

    /**
     * Adds the contents of the zip/JAR contained in the given byte array to this JAR.
     *
     * @param path the path within the JAR where the root of the zip will be placed, or {@code null} for the JAR's root
     * @param zip  the contents of the zip/JAR file
     * @return {@code this}
     */
    public Jar addEntries(Path path, ZipInputStream zip) throws IOException {
        return addEntries(path, zip, null);
    }

    /**
     * Adds the contents of the zip/JAR contained in the given byte array to this JAR.
     *
     * @param path   the path within the JAR where the root of the zip will be placed, or {@code null} for the JAR's root
     * @param zip    the contents of the zip/JAR file
     * @param filter a filter to select particular classes
     * @return {@code this}
     */
    public Jar addEntries(Path path, ZipInputStream zip, Filter filter) throws IOException {
        beginWriting();
        try (ZipInputStream zis = zip) {
            for (ZipEntry entry; (entry = zis.getNextEntry()) != null;) {
                final String target = path != null ? path.resolve(entry.getName()).toString() : entry.getName();
                if (target.equals(MANIFEST_NAME))
                    continue;
                if (filter == null || filter.filter(target))
                    addEntryNoClose(jos, target, zis);
            }
        }
        return this;
    }

    /**
     * Adds the contents of a Java package to this JAR.
     *
     * @param clazz A class whose package we wish to add to the JAR.
     * @return {@code this}
     */
    public Jar addPackageOf(Class<?> clazz) throws IOException {
        return addPackageOf(clazz, null);
    }

    /**
     * Adds the contents of a Java package to this JAR.
     *
     * @param clazz  a class whose package we wish to add to the JAR.
     * @param filter a filter to select particular classes
     * @return {@code this}
     */
    public Jar addPackageOf(Class<?> clazz, Filter filter) throws IOException {
        try {
            final String path = clazz.getPackage().getName().replace('.', '/');
            URL dirURL = clazz.getClassLoader().getResource(path);
            if (dirURL != null && dirURL.getProtocol().equals("file"))
                addDir(Paths.get(path), Paths.get(dirURL.toURI()), filter, false);
            else {
                if (dirURL == null) // In case of a jar file, we can't actually find a directory.
                    dirURL = clazz.getClassLoader().getResource(clazz.getName().replace('.', '/') + ".class");

                if (dirURL.getProtocol().equals("jar")) {
                    final URI jarUri = new URI(dirURL.getPath().substring(0, dirURL.getPath().indexOf('!')));
                    try (JarInputStream jis1 = newJarInputStream(Files.newInputStream(Paths.get(jarUri)))) {
                        for (JarEntry entry; (entry = jis1.getNextJarEntry()) != null;) {
                            try {
                                if (entry.getName().startsWith(path + '/')) {
                                    if (filter == null || filter.filter(entry.getName()))
                                        addEntryNoClose(jos, entry.getName(), jis1);
                                }
                            } catch (ZipException e) {
                                if (!e.getMessage().startsWith("duplicate entry"))
                                    throw e;
                            }
                        }
                    }
                } else
                    throw new AssertionError();
            }
            return this;
        } catch (URISyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private void addDir(final Path path, final Path dir1, final Filter filter, final boolean recursive) throws IOException {
        final Path dir = dir1.toAbsolutePath();
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) throws IOException {
                return (recursive || dir.equals(d)) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path p = dir.relativize(file.toAbsolutePath());
                final Path target = path != null ? path.resolve(p.toString()) : p;
                if (!target.toString().equals(MANIFEST_NAME)) {
                    if (filter == null || filter.filter(target.toString()))
                        addEntry(target, file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void addEntry(JarOutputStream jarOut, String path, InputStream is) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        copy(is, jarOut);
        jarOut.closeEntry();
    }

    private static void addEntryNoClose(JarOutputStream jarOut, String path, InputStream is) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        copy0(is, jarOut);
        jarOut.closeEntry();
    }

    private static void addEntry(JarOutputStream jarOut, String path, byte[] data) throws IOException {
        jarOut.putNextEntry(new JarEntry(path));
        jarOut.write(data);
        jarOut.flush();
        jarOut.closeEntry();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Jar File Properties">
    /////////// Jar File Properties ///////////////////////////////////
    /**
     * Sets a {@link Pack200} packer to use when writing the JAR.
     *
     * @param packer
     * @return {@code this}
     */
    /*public Jar setPacker(Pack200.Packer packer) {
        this.packer = packer;
        return this;
    }*/

    /**
     * If set to true true, a header will be added to the JAR file when written, that will make the JAR an executable file in POSIX environments.
     *
     * @param value
     * @return {@code this}
     */
    public Jar setReallyExecutable(boolean value) {
        setJarPrefix(value ? "#!/bin/sh\n\nexec java -jar $0 \"$@\"\n" : null);
        return this;
    }

    /**
     * Sets a string that will be prepended to the JAR file's data.
     *
     * @param value the prefix, or {@code null} for none.
     * @return {@code this}
     */
    public Jar setJarPrefix(String value) {
        verifyNotSealed();
        if (jos != null)
            throw new IllegalStateException("Really executable cannot be set after entries are added.");
        if (value != null && jarPrefixFile != null)
            throw new IllegalStateException("A prefix has already been set (" + jarPrefixFile + ")");
        this.jarPrefixStr = value;
        return this;
    }

    /**
     * Sets a file whose contents will be prepended to the JAR file's data.
     *
     * @param file the prefix file, or {@code null} for none.
     * @return {@code this}
     */
    public Jar setJarPrefix(Path file) {
        verifyNotSealed();
        if (jos != null)
            throw new IllegalStateException("Really executable cannot be set after entries are added.");
        if (file != null && jarPrefixStr != null)
            throw new IllegalStateException("A prefix has already been set (" + jarPrefixStr + ")");
        this.jarPrefixFile = file;
        return this;
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Writing">
    /////////// Writing ///////////////////////////////////
    /**
     * Sets an {@link OutputStream} to which the JAR will be written.
     * If used, this method must be called before any entries have been added or the JAR written. Calling this method prevents this
     * object from using an internal buffer to store the JAR, and therefore, none of the other {@code write} methods can be called.
     *
     * @param os the target OutputStream of this JAR.
     * @return {@code this}
     */
    public Jar setOutputStream(OutputStream os) {
        if (os == null)
            throw new NullPointerException("The OutputStream is null");
        if (jos != null)
            throw new IllegalStateException("Entries have already been added, the JAR has been written or setOutputStream has already been called.");
        this.os = os;
        return this;
    }

    /**
     * Same as {@link #setOutputStream(OutputStream) setOutputStream(Files.newOutputStream(out))}.
     * If used, this method must be called before any entries have been added or the JAR written. Calling this method prevents this
     * object from using an internal buffer to store the JAR, and therefore, none of the other {@code write} methods can be called.
     *
     * @param out the target file to which this JAR will be written.
     * @return {@code this}
     */
    public Jar setOutput(Path out) throws IOException {
        return setOutputStream(Files.newOutputStream(out));
    }

    /**
     * Same as {@link #setOutputStream(OutputStream) setOutputStream(new FileOutputStream(out))}.
     * If used, this method must be called before any entries have been added or the JAR written. Calling this method prevents this
     * object from using an internal buffer to store the JAR, and therefore, none of the other {@code write} methods can be called.
     *
     * @param out the target file to which this JAR will be written.
     * @return {@code this}
     */
    public Jar setOutput(File out) throws IOException {
        return setOutputStream(new FileOutputStream(out));
    }

    private void beginWriting() throws IOException {
        verifyNotSealed();
        if (jos != null)
            return;
        if (os == null)
            this.os = new ByteArrayOutputStream();
        writePrefix(os);
        if (getAttribute(ATTR_MANIFEST_VERSION) == null)
            setAttribute(ATTR_MANIFEST_VERSION, "1.0");
        jos = new JarOutputStream(os, manifest);
        if (jis != null)
            addEntries(null, jis);
    }

    private void writePrefix(OutputStream os) throws IOException {
        if (jarPrefixStr != null) {
            final Writer out = new OutputStreamWriter(os, UTF_8);
            out.write(jarPrefixStr);
            out.flush();
        } else if (jarPrefixFile != null)
            Files.copy(jarPrefixFile, os);
        if (jarPrefixStr != null || jarPrefixFile != null) {
            os.write('\n');
            os.flush();
        }
    }

    public Jar close() throws IOException {
        if (sealed)
            return this;
        beginWriting();
        // writeManifest(); - some JDK Jar classes (like JarInputStream) assume that the manifest must be the first entry
        jos.close();
        this.sealed = true;
        return this;
    }

    /**
     * Writes this JAR to an output stream, and closes the stream.
     */
    public <T extends OutputStream> T write(T os) throws IOException {
        close();

        if (!(this.os instanceof ByteArrayOutputStream))
            throw new IllegalStateException("Cannot write to another target if setOutputStream has been called");
        final byte[] content = ((ByteArrayOutputStream) this.os).toByteArray();

        /*if (packer != null)
            packer.pack(new JarInputStream(new ByteArrayInputStream(content)), os);
        else*/
            os.write(content);

        os.close();
        return os;
    }

    private void verifyNotSealed() {
        if (sealed)
            throw new IllegalStateException("This JAR has been sealed (when it was written)");
    }

    /**
     * Writes this JAR to a file.
     */
    public File write(File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            write(fos);
            return file;
        }
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
    //</editor-fold>

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

    //<editor-fold defaultstate="collapsed" desc="Filter">
    /////////// Filter ///////////////////////////////////
    public static interface Filter {
        boolean filter(String entryName);
    }

    public static Filter matches(String regex) {
        final Pattern p = Pattern.compile(regex);
        return new Filter() {

            @Override
            public boolean filter(String entryName) {
                return p.matcher(entryName).matches();
            }
        };
    }

    public static Filter notMatches(String regex) {
        final Filter f = matches(regex);
        return new Filter() {

            @Override
            public boolean filter(String entryName) {
                return !f.filter(entryName);
            }
        };
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Utils">
    /////////// Utils ///////////////////////////////////
    private static JarInputStream newJarInputStream(InputStream in) throws IOException {
        return new co.paralleluniverse.common.JarInputStream(in);
    }

    private static ZipInputStream newZipInputStream(InputStream in) throws IOException {
        return new co.paralleluniverse.common.ZipInputStream(in);
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
        os.flush();
    }

    private static String join(Collection<?> list, String separator) {
        if (list == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (Object element : list)
            sb.append(element.toString()).append(separator);
        if (!list.isEmpty())
            sb.delete(sb.length() - separator.length(), sb.length());
        return sb.toString();
    }

    private static String join(Map<String, ?> map, char kvSeparator, String separator) {
        if (map == null)
            return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> entry : map.entrySet())
            sb.append(entry.getKey()).append(kvSeparator).append(entry.getValue().toString()).append(separator);
        if (!map.isEmpty())
            sb.delete(sb.length() - separator.length(), sb.length());
        return sb.toString();
    }

    private static String join(Collection<?> list) {
        return join(list, " ");
    }

    private static String join(Map<String, ?> map) {
        return join(map, '=', " ");
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

    private static Map<String, String> split(String map, char kvSeparator, String separator, String defaultValue) {
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
        return split(map, '=', " ", defaultValue);
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

    private static Path nullPath() {
        return null;
    }
        //</editor-fold>
}
