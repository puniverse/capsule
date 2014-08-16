package co.paralleluniverse.filesystem.jimfs;

import co.paralleluniverse.filesystem.PathURLConnection;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Handler extends URLStreamHandler {
    // static final URLStreamHandler INSTANCE = new Handler();

    public Handler() {
        System.err.println("ZZZZZ");
        Thread.dumpStack();
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
