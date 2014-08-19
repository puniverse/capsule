package co.paralleluniverse.filesystem;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathURLStreamHandler extends URLStreamHandler {
    public static final URLStreamHandler INSTANCE = new PathURLStreamHandler();

    protected PathURLStreamHandler() {
    }
    
    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        try {
            URI uri = url.toURI();
            Path path = Paths.get(uri);
            return new PathURLConnection(url, path);
        } catch (URISyntaxException e) {
            throw new IOException("invalid URL", e);
        }
    }
}
