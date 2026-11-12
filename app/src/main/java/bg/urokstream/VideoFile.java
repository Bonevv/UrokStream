package bg.urokstream;

import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

public final class VideoFile {
    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList("mkv", "mp4", "m4v", "webm", "mov", "avi", "ts", "m2ts", "mpg", "mpeg"));
    public final int index;
    public final String path;
    public final long size;
    public final long offset;

    public VideoFile(int index, String path, long size, long offset) {
        this.index = index;
        this.path = path;
        this.size = size;
        this.offset = offset;
    }

    public String name() { return path.substring(path.lastIndexOf('/') + 1); }
    public static boolean isVideo(String path) {
        return EXTENSIONS.contains(path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }
}
