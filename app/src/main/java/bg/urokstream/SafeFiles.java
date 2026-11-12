package bg.urokstream;

import java.io.File;
import java.io.IOException;

public final class SafeFiles {
    private SafeFiles() {}

    public static File resolve(File root, String path) throws IOException {
        if (path.isEmpty() || new File(path).isAbsolute() || path.indexOf('\0') >= 0)
            throw new IOException("Невалиден път в торента");
        File canonicalRoot = root.getCanonicalFile();
        File result = new File(canonicalRoot, path).getCanonicalFile();
        if (!result.getPath().startsWith(canonicalRoot.getPath() + File.separator))
            throw new IOException("Невалиден път в торента");
        return result;
    }

    public static void deleteTree(File root) throws IOException {
        if (!root.exists()) {
            // File.exists() also returns false for a dangling link. Remove the directory entry.
            root.delete();
            return;
        }
        // Android can expose filesDir through /data/user/0 -> /data/data. Resolve the parent first.
        File[] children = root.getCanonicalFile().listFiles();
        if (children != null) {
            for (File child : children) {
                // Do not follow links outside our owned stream directory.
                if (child.getCanonicalFile().equals(child.getAbsoluteFile())) deleteTree(child);
                else if (!child.delete()) throw new IOException("Кешът не може да се изтрие");
            }
        }
        if (!root.delete()) throw new IOException("Кешът не може да се изтрие");
    }
}
