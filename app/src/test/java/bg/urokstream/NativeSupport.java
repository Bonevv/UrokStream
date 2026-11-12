package bg.urokstream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class NativeSupport {
    private static boolean initialized;

    static synchronized void load() throws Exception {
        if (initialized) return;
        Path library = Files.createTempFile("urokstream-libtorrent-", ".so");
        library.toFile().deleteOnExit();
        try (InputStream input = NativeSupport.class.getResourceAsStream("/lib/x86_64/libtorrent4j.so")) {
            if (input == null) throw new IllegalStateException("The native tests require Linux x86_64");
            Files.copy(input, library, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        System.setProperty("libtorrent4j.jni.path", library.toAbsolutePath().toString());
        initialized = true;
    }
}
