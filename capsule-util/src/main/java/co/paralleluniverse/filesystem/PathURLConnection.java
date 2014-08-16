package co.paralleluniverse.filesystem;

/*
 * https://github.com/marschall/path-classloader/blob/master/src%2Fmain%2Fjava%2Fcom%2Fgithub%2Fmarschall%2Fpathclassloader%2FPathURLConnection.java
 */
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class PathURLConnection extends URLConnection {
    private final Path path;

    public PathURLConnection(URL url, Path path) {
        super(url);
        this.path = path;
    }

    @Override
    public void connect() throws IOException {
        // nothing to do
    }

    @Override
    public long getContentLengthLong() {
        try {
            return Files.size(this.path);
        } catch (IOException e) {
            throw new RuntimeException("could not get size of: " + this.path, e);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(this.path);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return Files.newOutputStream(this.path);
    }

    @Override
    public String getContentType() {
        try {
            return Files.probeContentType(this.path);
        } catch (IOException e) {
            throw new RuntimeException("could not get content type of: " + this.path, e);
        }
    }

    @Override
    public long getLastModified() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(this.path, BasicFileAttributes.class);
            return attributes.lastModifiedTime().toMillis();
        } catch (IOException e) {
            throw new RuntimeException("could not get last modified time of: " + this.path, e);
        }
    }
}
