/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package co.paralleluniverse.capsule;

import com.google.common.jimfs.Jimfs;
import static com.google.common.truth.Truth.assert_;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pron
 */
public class CapsuleLauncherTest {

    private final FileSystem fs = Jimfs.newFileSystem();
    private final Path cache = fs.getPath("/cache");
    private final Path tmp = fs.getPath("/tmp");
    private Properties props;


    @After
    public void tearDown() throws Exception {
        fs.close();
    }

    @Test
    public void testSimpleExtract() throws Exception {
        Jar jar = newCapsuleJar()
                .setAttribute("Application-Class", "com.acme.Foo")
                .addEntry("foo.jar", emptyInputStream())
                .addEntry("a.class", emptyInputStream())
                .addEntry("b.txt", emptyInputStream())
                .addEntry("lib/a.jar", emptyInputStream())
                .addEntry("lib/b.class", emptyInputStream())
                .addEntry("q/w/x.txt", emptyInputStream())
                .addEntry("d\\f\\y.txt", emptyInputStream()) // test with Windows path
                .addEntry("META-INF/x.txt", emptyInputStream());

        List<String> args = list("hi", "there");
        List<String> cmdLine = list();

        Capsule capsule = newCapsuleLauncher(jar).newCapsule();

        ProcessBuilder pb = capsule.prepareForLaunch(cmdLine, args);

        // dumpFileSystem(fs);
        assertEquals(args, getAppArgs(pb));

        Path appCache = cache.resolve("apps").resolve("com.acme.Foo");

        assertEquals("com.acme.Foo", getProperty(pb, "capsule.app"));
        assertEquals("com.acme.Foo", getEnv(pb, "CAPSULE_APP"));
        assertEquals(appCache, path(getProperty(pb, "capsule.dir")));
        assertEquals(absolutePath("capsule.jar"), path(getProperty(pb, "capsule.jar")));
        assertEquals(appCache, path(getEnv(pb, "CAPSULE_DIR")));
        assertEquals(absolutePath("capsule.jar"), path(getEnv(pb, "CAPSULE_JAR")));

        assertEquals(list("com.acme.Foo", "hi", "there"), getMainAndArgs(pb));

        assertTrue(Files.isDirectory(cache));
        assertTrue(Files.isDirectory(cache.resolve("apps")));
        assertTrue(Files.isDirectory(appCache));
        assertTrue(Files.isRegularFile(appCache.resolve(".extracted")));
        assertTrue(Files.isRegularFile(appCache.resolve("foo.jar")));
        assertTrue(Files.isRegularFile(appCache.resolve("b.txt")));
        assertTrue(Files.isDirectory(appCache.resolve("lib")));
        assertTrue(Files.isRegularFile(appCache.resolve("lib").resolve("a.jar")));
        assertTrue(!Files.isRegularFile(appCache.resolve("a.class")));
        assertTrue(!Files.isRegularFile(appCache.resolve("lib").resolve("b.class")));
        assertTrue(!Files.isDirectory(appCache.resolve("META-INF")));
        assertTrue(!Files.isRegularFile(appCache.resolve("META-INF").resolve("x.txt")));

        assertTrue(Files.isDirectory(appCache.resolve("q").resolve("w")));
        assertTrue(Files.isDirectory(appCache.resolve("d").resolve("f")));
        assertTrue(Files.isRegularFile(appCache.resolve("q").resolve("w").resolve("x.txt")));
        assertTrue(Files.isRegularFile(appCache.resolve("d").resolve("f").resolve("y.txt")));

        assert_().that(getClassPath(pb)).has().item(absolutePath("capsule.jar"));
        assert_().that(getClassPath(pb)).has().item(appCache);
        assert_().that(getClassPath(pb)).has().item(appCache.resolve("foo.jar"));
        assert_().that(getClassPath(pb)).has().noneOf(appCache.resolve("lib").resolve("a.jar"));
    }

    //<editor-fold defaultstate="collapsed" desc="Utilities">
    /////////// Utilities ///////////////////////////////////
    private Jar newCapsuleJar() {
        return new Jar()
                .setAttribute("Manifest-Version", "1.0")
                .setAttribute("Main-Class", "Capsule");
    }
    
    private CapsuleLauncher newCapsuleLauncher(Jar jar) throws IOException {
        Path capsuleJar = path("capsule.jar");
        jar.write(capsuleJar);
        
        return new CapsuleLauncher(capsuleJar).setCacheDir(cache);
    }

    private Path path(String first, String... more) {
        return fs.getPath(first, more);
    }

    private Path absolutePath(String first, String... more) {
        return fs.getPath(first, more).toAbsolutePath();
    }

    private InputStream emptyInputStream() {
        return Jar.toInputStream("", UTF_8);
    }

    private List<Path> paths(String cp) {
        final List<Path> res = new ArrayList<>();
        for (String p : cp.split(":"))
            res.add(path(p));
        return res;
    }

    private List<Path> getClassPath(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int i = cmd.indexOf("-classpath");
        if (i < 0)
            return null;
        final String cp = cmd.get(i + 1);
        // return Arrays.asList(cp.split(":"));
        return paths(cp);
    }

    private String getProperty(ProcessBuilder pb, String prop) {
        return getOption(pb, "-D" + prop, '=');
    }

    private String getEnv(ProcessBuilder pb, String envVar) {
        return pb.environment().get(envVar);
    }

    private String getOption(ProcessBuilder pb, String opt) {
        return getOption(pb, opt, ':');
    }

    private String getOption(ProcessBuilder pb, String opt, char separator) {
        final List<String> jvmargs = getJvmArgs(pb);
        for (String a : jvmargs) {
            if (a.startsWith(opt)) {
                String res = getAfter(a, separator);
                return res != null ? res : "";
            }
        }
        return null;
    }

    private List<String> getJvmArgs(ProcessBuilder pb) {
        boolean classpath = false;
        int i = 0;
        for (String x : pb.command().subList(1, pb.command().size())) {
            if (x.equals("-jar") || (!x.startsWith("-") && !classpath))
                break;
            if (x.equals("-classpath") || x.equals("-cp"))
                classpath = true;
            else
                classpath = false;
            i++;
        }
        return pb.command().subList(1, i + 1);
    }

    private String getMainJar(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int start = getJvmArgs(pb).size() + 1;
        if (cmd.get(start).equals("-jar"))
            return cmd.get(start + 1);
        return null;
    }

    private String getMainClass(ProcessBuilder pb) {
        final List<String> cmd = pb.command();
        final int start = getJvmArgs(pb).size() + 1;
        if (cmd.get(start).equals("-jar"))
            return null;
        return cmd.get(start);
    }

    private List<String> getAppArgs(ProcessBuilder pb) {
        List<String> jvmArgs = getJvmArgs(pb);
        final List<String> cmd = pb.command();
        final int start = jvmArgs.size() + 1;
        return cmd.subList(start + (cmd.get(start).equals("-jar") ? 2 : 1), cmd.size());
    }

    private static List<String> getMainAndArgs(ProcessBuilder pb) {
        List<String> cmd = pb.command();
        cmd = cmd.subList(1, cmd.size());

        boolean prevClassPath = false;
        int i = 0;
        for (String c : cmd) {
            if (c.startsWith("-") || prevClassPath)
                i++;
            else
                break;
            prevClassPath = c.equals("-classpath");
        }
        return cmd.subList(i, cmd.size());
    }

    private List<Path> toPath(List<String> ps) {
        final List<Path> pss = new ArrayList<Path>(ps.size());
        for (String p : ps)
            pss.add(path(p));
        return pss;
    }

    private List<Path> toAbsolutePath(List<Path> ps) {
        final List<Path> pss = new ArrayList<Path>(ps.size());
        for (Path p : ps)
            pss.add(p.toAbsolutePath().normalize());
        return pss;
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

    @SafeVarargs
    private static <T> List<T> list(T... xs) {
        return Arrays.asList(xs);
    }
    //</editor-fold>
}
