package bg.urokstream;

import org.libtorrent4j.Priority;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.SessionParams;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.TcpEndpoint;
import org.libtorrent4j.TorrentFlags;
import org.libtorrent4j.TorrentHandle;
import org.libtorrent4j.TorrentInfo;
import org.libtorrent4j.TorrentStatus;
import org.libtorrent4j.swig.settings_pack;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** One selected video, one session, one private temporary directory. No Android dependencies. */
public final class StreamEngine implements AutoCloseable {
    private final File directory;
    private final boolean discovery;
    private final Object lock = new Object();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private SessionManager session;
    private TorrentHandle handle;
    private PieceMap pieces;
    private RandomAccessFile input;
    private File videoOnDisk;
    private int window = -1;
    public volatile boolean ready;

    public StreamEngine(File directory) { this(directory, true); }
    StreamEngine(File directory, boolean discovery) {
        this.directory = directory; this.discovery = discovery;
    }

    public static Priority[] selectedOnly(int count, int selected) {
        if (selected < 0 || selected >= count) throw new IllegalArgumentException("Invalid selection");
        Priority[] priorities = new Priority[count];
        Arrays.fill(priorities, Priority.IGNORE);
        priorities[selected] = Priority.DEFAULT;
        return priorities;
    }

    public void start(File torrent, VideoFile video) throws IOException {
        checkCancelled();
        TorrentInfo info = new TorrentInfo(torrent);
        if (video.index >= info.numFiles() || info.files().fileSize(video.index) != video.size
                || !info.files().filePath(video.index).replace('\\', '/').equals(video.path))
            throw new IOException("Изборът не съответства на торента");
        synchronized (lock) {
            checkCancelled();
            if (!directory.exists() && !directory.mkdirs()) throw new IOException("Не може да се създаде кеш");
            videoOnDisk = SafeFiles.resolve(directory, video.path);
            pieces = new PieceMap(video.offset, video.size, info.pieceLength());
            // Sequential mode may eventually cache the whole SELECTED video, plus boundary pieces.
            long required = video.size + 2L * info.pieceLength() + 16L * 1024 * 1024;
            if (directory.getUsableSpace() < required) throw new IOException("Няма достатъчно свободно място за избраното видео");
            SettingsPack settings = new SettingsPack();
            settings.connectionsLimit(40);
            settings.maxPeerlistSize(300);
            settings.maxQueuedDiskBytes(4 * 1024 * 1024);
            settings.uploadRateLimit(256 * 1024);
            settings.setEnableDht(discovery);
            settings.setEnableLsd(discovery);
            settings.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), discovery);
            settings.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), discovery);
            if (discovery) settings.setDhtBootstrapNodes("router.bittorrent.com:6881,router.utorrent.com:6881,dht.transmissionbt.com:6881");
            else settings.listenInterfaces("127.0.0.1:0");
            SessionParams params = new SessionParams(settings);
            params.setPosixDiskIO();
            session = new SessionManager(false);
            session.start(params);
            // File priorities are supplied atomically when adding the torrent, before any payload request.
            session.download(info, directory, null, selectedOnly(info.numFiles(), video.index), null,
                    TorrentFlags.SEQUENTIAL_DOWNLOAD);
        }
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            synchronized (lock) {
                checkCancelled();
                handle = session.find(info.infoHash());
                if (handle != null && handle.isValid()) {
                    handle.unsetFlags(TorrentFlags.AUTO_MANAGED);
                    handle.resume();
                    // MP4 and MKV can need their index at the end before playback can begin.
                    handle.setPieceDeadline(pieces.first, 0);
                    if (pieces.last != pieces.first) handle.setPieceDeadline(pieces.last, 300);
                    prioritizeLocked(0);
                    ready = true;
                    return;
                }
            }
            pause(50);
        }
        throw new IOException("Торент двигателят не се стартира навреме");
    }

    private void prioritizeLocked(long position) {
        int current = pieces.pieceAt(position);
        if (window == current) return;
        handle.clearPieceDeadlines();
        // Keep requests near the reader. Normal-priority download remains confined to the selected file.
        int ahead = Math.max(2, Math.min(12, (int) (24L * 1024 * 1024 / pieces.pieceLength)));
        for (int piece = current; piece <= Math.min(pieces.last, current + ahead); piece++)
            if (!handle.havePiece(piece)) handle.setPieceDeadline(piece, (piece - current) * 250);
        if (!handle.havePiece(pieces.last)) handle.setPieceDeadline(pieces.last, 500);
        window = current;
    }

    public int readAt(long position, byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        long waitUntil = System.nanoTime() + 90_000_000_000L;
        while (true) {
            checkCancelled();
            synchronized (lock) {
                checkCancelled();
                if (!ready || handle == null || !handle.isValid()) throw new IOException("Торентът е спрян");
                if (position >= pieces.length) return -1;
                prioritizeLocked(position);
                if (handle.havePiece(pieces.pieceAt(position))) {
                    if (input == null && videoOnDisk.isFile()) input = new RandomAccessFile(videoOnDisk, "r");
                    if (input != null) {
                        input.seek(position);
                        int count = input.read(buffer, offset, pieces.readable(position, length));
                        if (count >= 0) return count;
                    }
                }
                TorrentStatus status = handle.status();
                if (status.errorCode().isError()) throw new IOException("Торент грешка: " + status.errorCode().getMessage());
            }
            if (System.nanoTime() > waitUntil)
                throw new IOException("Няма достъпни части за това видео. Провери интернет връзката и сийдърите.");
            pause(80);
        }
    }

    public String stats() {
        synchronized (lock) {
            if (!ready || cancelled.get()) return "Свързване с торента…";
            TorrentStatus status = handle.status();
            if (status.errorCode().isError()) return "Грешка: " + status.errorCode().getMessage();
            long wanted = Math.max(1, status.totalWanted());
            return String.format(java.util.Locale.ROOT, "%d пиъри · %d сийдъри · %.0f KiB/s · %d%% от видеото",
                    status.numPeers(), status.numSeeds(), status.downloadRate() / 1024.0,
                    Math.min(100, status.totalWantedDone() * 100 / wanted));
        }
    }

    void connectPeer(String host, int port) { synchronized (lock) { handle.swig().connect_peer(new TcpEndpoint(host, port).swig()); } }
    Priority[] filePriorities() { synchronized (lock) { return handle.filePriorities(); } }

    public void cancel() { cancelled.set(true); }
    private void checkCancelled() throws InterruptedIOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Възпроизвеждането е спряно");
    }
    private void pause(long millis) throws InterruptedIOException {
        checkCancelled();
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException(); }
    }

    @Override public void close() throws IOException {
        cancel();
        synchronized (lock) {
            ready = false;
            try { if (input != null) input.close(); }
            finally {
                input = null; handle = null;
                if (session != null) { session.stop(); session = null; }
            }
            SafeFiles.deleteTree(directory);
        }
    }
}
