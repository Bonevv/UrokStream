package bg.urokstream;

import org.junit.Test;
import org.junit.BeforeClass;
import org.libtorrent4j.Priority;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.TorrentFlags;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.swig.settings_pack;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;
import static org.junit.Assert.*;

/** Transfers only generated test bytes between two local libtorrent sessions. No external trackers. */
public class StreamingTest {
    @BeforeClass public static void nativeLibrary() throws Exception { NativeSupport.load(); }
    @Test(timeout = 45000) public void downloadsSelectedUnalignedVideoAndSupportsRandomAccess() throws Exception {
        File root = Files.createTempDirectory("urok-stream-test").toFile();
        File seedDir = new File(root, "seed");
        File contentDir = new File(seedDir, "fixture");
        assertTrue(contentDir.mkdirs());
        byte[] before = random(16077, 10);
        byte[] selected = random(17317, 20);
        byte[] after = random(300000, 30);
        Files.write(new File(contentDir, "before.mkv").toPath(), before);
        Files.write(new File(contentDir, "selected.mkv").toPath(), selected);
        Files.write(new File(contentDir, "after.mkv").toPath(), after);
        byte[] payload = join(before, selected, after);
        File torrent = new File(root, "fixture.torrent");
        Files.write(torrent.toPath(), torrent(payload, before.length, selected.length, after.length));
        TorrentInfo info = new TorrentInfo(torrent);
        SettingsPack settings = new SettingsPack();
        settings.setEnableDht(false); settings.setEnableLsd(false);
        settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
        settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);
        settings.listenInterfaces("127.0.0.1:0");
        SessionParams params = new SessionParams(settings); params.setPosixDiskIO();
        SessionManager seed = new SessionManager(false);
        StreamEngine reader = new StreamEngine(new File(root, "download"), false);
        try {
            seed.start(params);
            seed.download(info, seedDir, null, null, null, TorrentFlags.SEED_MODE);
            TorrentHandle source = null;
            long deadline = System.nanoTime() + 10_000_000_000L;
            while (System.nanoTime() < deadline) {
                source = seed.find(info.infoHash());
                if (source != null && source.isValid() && seed.swig().listen_port() != 0) break;
                Thread.sleep(50);
            }
            assertNotNull(source);
            source.unsetFlags(TorrentFlags.AUTO_MANAGED); source.resume();
            VideoFile video = new VideoFile(1, "fixture/selected.mkv", selected.length, before.length);
            reader.start(torrent, video);
            reader.connectPeer("127.0.0.1", seed.swig().listen_port());
            Priority[] priorities = reader.filePriorities();
            assertArrayEquals(new Priority[]{Priority.IGNORE, Priority.DEFAULT, Priority.IGNORE}, priorities);
            byte[] output = new byte[selected.length];
            int at = 0;
            while (at < output.length) {
                int read = reader.readAt(at, output, at, Math.min(997, output.length - at));
                assertTrue(read > 0); at += read;
            }
            assertArrayEquals(selected, output);
            byte[] seek = new byte[700];
            int count = reader.readAt(100, seek, 0, seek.length);
            assertArrayEquals(Arrays.copyOfRange(selected, 100, 100 + count), Arrays.copyOf(seek, count));
            assertEquals(-1, reader.readAt(selected.length, seek, 0, 1));
            assertFalse(new File(root, "download/fixture/before.mkv").exists());
            assertFalse(new File(root, "download/fixture/after.mkv").exists());
            reader.cancel();
            assertThrows(java.io.InterruptedIOException.class, () -> reader.readAt(0, seek, 0, 1));
        } finally {
            reader.close(); seed.stop();
            assertFalse(new File(root, "download").exists());
            SafeFiles.deleteTree(root);
        }
    }

    private static byte[] random(int length, long seed) { byte[] bytes = new byte[length]; new Random(seed).nextBytes(bytes); return bytes; }
    private static byte[] join(byte[]... arrays) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] bytes : arrays) out.write(bytes);
        return out.toByteArray();
    }
    private static void ascii(ByteArrayOutputStream out, String value) throws Exception { out.write(value.getBytes(StandardCharsets.UTF_8)); }
    private static byte[] torrent(byte[] payload, int before, int selected, int after) throws Exception {
        ByteArrayOutputStream hashes = new ByteArrayOutputStream();
        for (int at = 0; at < payload.length; at += 4096)
            hashes.write(MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(payload, at, Math.min(payload.length, at + 4096))));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ascii(out, "d4:infod5:filesl");
        ascii(out, "d6:lengthi" + before + "e4:pathl10:before.mkvee");
        ascii(out, "d6:lengthi" + selected + "e4:pathl12:selected.mkvee");
        ascii(out, "d6:lengthi" + after + "e4:pathl9:after.mkveee");
        ascii(out, "4:name7:fixture12:piece lengthi4096e6:pieces" + hashes.size() + ":");
        out.write(hashes.toByteArray()); ascii(out, "ee");
        return out.toByteArray();
    }
}
