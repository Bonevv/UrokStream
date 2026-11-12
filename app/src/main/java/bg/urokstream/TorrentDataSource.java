package bg.urokstream;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;

/** Media3 random access over verified torrent pieces. No HTTP server and no sparse-file playback. */
@UnstableApi
public final class TorrentDataSource extends BaseDataSource {
    private final StreamEngine engine;
    private final VideoFile video;
    private Uri uri;
    private long position;
    private long remaining;
    private boolean opened;

    public TorrentDataSource(StreamEngine engine, VideoFile video) { super(true); this.engine = engine; this.video = video; }

    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);
        if (spec.position < 0 || spec.position > video.size) throw new IOException("Позиция извън видеото");
        uri = spec.uri;
        position = spec.position;
        remaining = spec.length == C.LENGTH_UNSET ? video.size - position : Math.min(spec.length, video.size - position);
        opened = true;
        transferStarted(spec);
        return remaining;
    }

    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (remaining == 0) return C.RESULT_END_OF_INPUT;
        int count = engine.readAt(position, bytes, offset, (int) Math.min(remaining, length));
        if (count < 0) return C.RESULT_END_OF_INPUT;
        position += count;
        remaining -= count;
        bytesTransferred(count);
        return count;
    }

    @Override public Uri getUri() { return uri; }
    @Override public void close() {
        uri = null;
        if (opened) { opened = false; transferEnded(); }
    }
}
