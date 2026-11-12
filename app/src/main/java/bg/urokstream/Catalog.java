package bg.urokstream;

import org.libtorrent4j.FileStorage;
import org.libtorrent4j.TorrentInfo;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Catalog {
    public final String name;
    public final List<VideoFile> videos;
    public final long totalSize;

    public Catalog(String name, List<VideoFile> videos, long totalSize) {
        this.name = name;
        this.videos = videos;
        this.totalSize = totalSize;
    }

    public static Catalog load(File torrent) throws IOException {
        TorrentInfo info;
        try { info = new TorrentInfo(torrent); }
        catch (IllegalArgumentException e) { throw new IOException("Невалиден .torrent файл", e); }
        if (!info.isValid()) throw new IOException("Невалиден .torrent файл");
        FileStorage files = info.files();
        List<VideoFile> videos = new ArrayList<>();
        for (int i = 0; i < files.numFiles(); i++) {
            if (files.fileFlags(i).and_(FileStorage.FLAG_SYMLINK).non_zero())
                throw new IOException("Торенти със символни връзки не се поддържат");
            if (files.padFileAt(i)) continue;
            String path = files.filePath(i).replace('\\', '/');
            // Validate every entry before libtorrent is allowed to create any files.
            SafeFiles.resolve(new File("catalog-root"), path);
            if (VideoFile.isVideo(path) && files.fileSize(i) > 0) {
                videos.add(new VideoFile(i, path, files.fileSize(i), files.fileOffset(i)));
            }
        }
        if (videos.isEmpty()) throw new IOException("В торента няма видео файлове");
        return new Catalog(info.name(), videos, info.totalSize());
    }

    public static final class Row {
        public final String title;
        public final String folder;
        public final VideoFile video;
        public int count;
        Row(String title, String folder, VideoFile video) {
            this.title = title; this.folder = folder; this.video = video;
        }
    }

    public List<Row> rows(String folder, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        Map<String, Row> folders = new LinkedHashMap<>();
        for (VideoFile video : videos) {
            if (!needle.isEmpty()) {
                if (video.path.toLowerCase(Locale.ROOT).contains(needle)) rows.add(new Row(video.name(), null, video));
                continue;
            }
            if (!video.path.startsWith(folder)) continue;
            String remainder = video.path.substring(folder.length());
            int slash = remainder.indexOf('/');
            if (slash < 0) rows.add(new Row(video.name(), null, video));
            else {
                String child = remainder.substring(0, slash);
                Row row = folders.get(child);
                if (row == null) { row = new Row(child, folder + child + "/", null); folders.put(child, row); }
                row.count++;
            }
        }
        rows.addAll(folders.values());
        rows.sort(Comparator.<Row, Boolean>comparing(r -> r.video != null).thenComparing(r -> r.title, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }
}
