package bg.urokstream;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A school library made specifically for the supplied УчаСе torrent. Original torrent indexes stay intact. */
public final class LessonLibrary {
    private static final Pattern GRADE = Pattern.compile("(?<![0-9])([1-9]|1[0-2])\\. клас");
    public final List<Lesson> lessons = new ArrayList<>();

    public static final class Lesson {
        public final VideoFile video;
        public final String subject, contentPath, title;
        public final int grade;
        Lesson(VideoFile video, String subject, String contentPath, int grade) {
            this.video = video; this.subject = subject; this.contentPath = contentPath; this.grade = grade;
            title = cleanTitle(video.name());
        }
        String relative(int selectedGrade) {
            return selectedGrade < 1 ? contentPath : contentPath.replaceAll("(^|/)" + selectedGrade + "\\. клас/", "$1");
        }
    }

    public static final class Entry {
        public final String title, folder;
        public final Lesson lesson;
        public int count;
        Entry(String title, String folder, Lesson lesson) { this.title = title; this.folder = folder; this.lesson = lesson; }
    }

    public LessonLibrary(Catalog catalog) {
        for (VideoFile video : catalog.videos) {
            String path = video.path;
            if (path.startsWith(catalog.name + "/")) path = path.substring(catalog.name.length() + 1);
            String[] parts = path.split("/", 3);
            if (parts.length < 2) continue;
            String subject = parts[0];
            String content = path.substring(subject.length() + 1);
            int grade = -1;
            if (subject.equals("Предучилищна подготовка") && parts.length == 3) {
                subject = parts[1]; content = parts[2]; grade = 0;
            } else {
                Matcher matcher = GRADE.matcher(content);
                if (matcher.find()) grade = Integer.parseInt(matcher.group(1));
            }
            lessons.add(new Lesson(video, subject, content, grade));
        }
    }

    public List<Lesson> forGrade(int grade) {
        List<Lesson> result = new ArrayList<>();
        for (Lesson lesson : lessons) {
            if (grade == -1 || lesson.grade == grade || lesson.grade == -1 && grade > 0 &&
                    (lesson.subject.equals("Чужди езици") || lesson.subject.equals("Личностно развитие") ||
                    lesson.subject.equals("За българи в чужбина") || grade >= 5 && lesson.subject.equals("ИТ") ||
                    grade >= 11 && lesson.subject.startsWith("Химия за"))) result.add(lesson);
        }
        return result;
    }

    public List<Entry> subjects(int grade) {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Lesson lesson : forGrade(grade)) {
            Entry entry = result.get(lesson.subject);
            if (entry == null) { entry = new Entry(lesson.subject, lesson.subject, null); result.put(lesson.subject, entry); }
            entry.count++;
        }
        List<Entry> entries = new ArrayList<>(result.values());
        entries.sort(Comparator.comparingInt(e -> subjectOrder(e.title)));
        return entries;
    }

    public List<Entry> entries(int grade, String subject, String folder, String query) {
        List<Entry> result = new ArrayList<>();
        Map<String, Entry> folders = new LinkedHashMap<>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (Lesson lesson : forGrade(grade)) {
            if (!needle.isEmpty()) {
                if ((lesson.title + " " + lesson.subject + " " + lesson.contentPath).toLowerCase(Locale.ROOT).contains(needle))
                    result.add(new Entry(lesson.title, null, lesson));
                continue;
            }
            if (!lesson.subject.equals(subject)) continue;
            String relative = lesson.relative(grade);
            if (!relative.startsWith(folder)) continue;
            String rest = relative.substring(folder.length());
            int slash = rest.indexOf('/');
            if (slash < 0) result.add(new Entry(lesson.title, null, lesson));
            else {
                String child = rest.substring(0, slash);
                Entry entry = folders.get(child);
                if (entry == null) { entry = new Entry(child, folder + child + "/", null); folders.put(child, entry); }
                entry.count++;
            }
        }
        result.sort(Comparator.comparing(e -> e.lesson.video.name(), String.CASE_INSENSITIVE_ORDER));
        List<Entry> first = new ArrayList<>(folders.values());
        first.sort(Comparator.comparing(e -> e.title, String.CASE_INSENSITIVE_ORDER));
        first.addAll(result);
        return first;
    }

    public Lesson find(int index) {
        for (Lesson lesson : lessons) if (lesson.video.index == index) return lesson;
        return null;
    }

    public Lesson next(Lesson current, int grade) {
        List<Entry> siblings = entries(grade, current.subject, parent(current.relative(grade)), "");
        for (int i = 0; i + 1 < siblings.size(); i++)
            if (siblings.get(i).lesson == current) return siblings.get(i + 1).lesson;
        return null;
    }

    static String parent(String path) { return path.substring(0, path.lastIndexOf('/') + 1); }
    public static String cleanTitle(String name) {
        return name.replaceFirst("\\.[^.]+$", "").replaceFirst("^\\d+[-. ]+", "")
                .replaceFirst("(?i)^Видео урок\\s*", "").trim();
    }
    public static String gradeLabel(int grade) { return grade == 0 ? "За най-малките" : grade < 0 ? "Всички класове" : grade + ". клас"; }
    private static int subjectOrder(String name) {
        String[] order = {"Български език", "Математика", "Околен свят", "Родинознание", "Човекът и природата", "Човекът и обществото", "Литература", "Чужди езици", "История", "География", "Биология", "Физика", "Химия", "ИТ"};
        for (int i = 0; i < order.length; i++) if (name.equals(order[i])) return i;
        return 20;
    }
}
