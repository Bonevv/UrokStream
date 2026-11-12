package bg.urokstream;

import org.junit.Test;
import org.junit.BeforeClass;
import org.libtorrent4j.Priority;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class CoreTest {
    @BeforeClass public static void nativeLibrary() throws Exception { NativeSupport.load(); }
    @Test public void unalignedVideoNeverReadsPastVerifiedPieceOrVideoEnd() {
        PieceMap map = new PieceMap(1000, 2200, 1024);
        assertEquals(0, map.first);
        assertEquals(3, map.last);
        assertEquals(24, map.readable(0, 4096));
        assertEquals(1024, map.readable(24, 4096));
        assertEquals(1, map.pieceAt(24));
        assertEquals(1, map.readable(2199, 100));
        assertThrows(IllegalArgumentException.class, () -> map.pieceAt(2200));
    }

    @Test public void fileEndingExactlyAtBoundaryDoesNotRequestNextPiece() {
        PieceMap map = new PieceMap(1024, 2048, 1024);
        assertEquals(1, map.first);
        assertEquals(2, map.last);
        assertEquals(1024, map.readable(1024, 8192));
    }

    @Test public void largeTorrentOffsetsUse64BitMath() {
        PieceMap map = new PieceMap(343L * 1024 * 1024 * 1024 + 31, 40_000_000, 8_388_608);
        assertEquals((int) (map.offset / map.pieceLength), map.pieceAt(0));
        assertEquals(8_388_577, map.readable(0, Integer.MAX_VALUE));
    }

    @Test public void onlySelectedFileHasDownloadPriorityIn12811FileTorrent() {
        Priority[] priorities = StreamEngine.selectedOnly(12811, 12798);
        assertEquals(Priority.DEFAULT, priorities[12798]);
        assertEquals(1, Arrays.stream(priorities).filter(p -> p != Priority.IGNORE).count());
        assertThrows(IllegalArgumentException.class, () -> StreamEngine.selectedOnly(3, 3));
    }

    @Test public void nestedCatalogAndBulgarianSearchKeepOriginalFileIndex() {
        VideoFile video = new VideoFile(99, "Биология/10. клас/ГЕНЕТИКА.mkv", 2000, 32000);
        Catalog catalog = new Catalog("Уроци", Arrays.asList(video,
                new VideoFile(102, "Биология/10. клас/Клетка.mp4", 500, 34000),
                new VideoFile(103, "Математика/7. клас/Ъгли.mkv", 1000, 34500)), 35500);
        List<Catalog.Row> root = catalog.rows("", "");
        assertEquals(2, root.size());
        assertEquals(2, root.get(0).count);
        assertEquals("Биология/", root.get(0).folder);
        assertEquals(2, catalog.rows("Биология/10. клас/", "").size());
        List<Catalog.Row> results = catalog.rows("Математика/", "генетика");
        assertEquals(1, results.size());
        assertSame(video, results.get(0).video);
        assertEquals(99, results.get(0).video.index);
    }

    @Test public void unsafeTorrentPathsCannotEscapeCache() throws Exception {
        File root = Files.createTempDirectory("torrent-paths").toFile();
        try {
            assertThrows(IOException.class, () -> SafeFiles.resolve(root, "../secret.mp4"));
            assertThrows(IOException.class, () -> SafeFiles.resolve(root, "/outside.mp4"));
            assertThrows(IOException.class, () -> SafeFiles.resolve(root, ""));
            assertEquals(new File(root, "Уроци/урок.mkv").getCanonicalFile(), SafeFiles.resolve(root, "Уроци/урок.mkv"));
        } finally { SafeFiles.deleteTree(root); }
    }

    @Test public void uploadedCatalogLoadsAllVideosWithoutStartingTorrentSession() throws Exception {
        Catalog catalog = Catalog.load(new File(System.getProperty("catalog.path")));
        assertEquals(12805, catalog.videos.size());
        assertEquals("УчаСе", catalog.name);
        assertTrue(catalog.totalSize > 340L * 1024 * 1024 * 1024);
        assertFalse(catalog.rows("", "монохибридно").isEmpty());
    }

    @Test public void cleanupWorksThroughAndroidStyleParentAliasWithoutFollowingChildLinks() throws Exception {
        File root = Files.createTempDirectory("android-cache-alias").toFile();
        File actual = new File(root, "actual");
        File cache = new File(actual, "active-stream");
        File nested = new File(cache, "video");
        assertTrue(nested.mkdirs());
        File outside = new File(root, "outside.txt");
        Files.write(outside.toPath(), new byte[]{1});
        Files.write(new File(nested, "part").toPath(), new byte[]{2});
        Files.createSymbolicLink(new File(cache, "outside-link").toPath(), outside.toPath());
        File alias = new File(root, "alias");
        Files.createSymbolicLink(alias.toPath(), actual.toPath());
        try {
            SafeFiles.deleteTree(new File(alias, "active-stream"));
            assertFalse(cache.exists());
            assertTrue(outside.isFile());
        } finally { SafeFiles.deleteTree(root); }
    }
}
