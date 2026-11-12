package bg.urokstream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Website-style subjects and courses, backed exclusively by files in the supplied torrent. */
public final class SchoolCatalog {
    public final List<Subject> subjects = new ArrayList<>();
    public final List<Lesson> lessons = new ArrayList<>();
    private final Map<Integer,Lesson> byIndex = new LinkedHashMap<>();

    public static final class Subject {
        public final String key, name, group;
        public final List<Course> courses = new ArrayList<>();
        Subject(String key,String name,String group) { this.key=key; this.name=name; this.group=group; }
    }
    public static final class Course {
        public final String key, label;
        public final Subject subject;
        public final List<Lesson> lessons = new ArrayList<>();
        Course(String key,String label,Subject subject) { this.key=key; this.label=label; this.subject=subject; }
        public String shortLabel() { return label.matches("\\d+\\. клас") ? label.substring(0,label.indexOf('.')) : label.replaceFirst("^Ниво ",""); }
        public boolean matchesGrade(int grade) { return grade<0||label.equals(grade+". клас"); }
    }
    public static final class Lesson {
        public final VideoFile video;
        public final String title;
        public final Course course;
        public final int number;
        public String sectionLabel="", sectionTitle="", sourceUrl="";
        Lesson(VideoFile video,Course course) {
            this.video=video; this.course=course; this.title=LessonLibrary.cleanTitle(video.name());
            int n=0; try { n=Integer.parseInt(video.name().split("[-. ]",2)[0]); } catch (NumberFormatException ignored) {}
            this.number=n;
        }
    }

    public SchoolCatalog(Catalog catalog) {
        Map<String,Subject> names=new LinkedHashMap<>(); Map<String,Course> courses=new LinkedHashMap<>();
        for (VideoFile video:catalog.videos) {
            String path=video.path.startsWith(catalog.name+"/")?video.path.substring(catalog.name.length()+1):video.path;
            String[] p=path.split("/"); if (p.length<2) throw new IllegalArgumentException("Lesson outside subject folder");
            String group="school", name=p[0]; int depth=1;
            if (name.equals("Предучилищна подготовка")) { group="preschool"; name=p[1]; depth=2; }
            else if (name.equals("ИТ")||name.equals("Чужди езици")||name.equals("Личностно развитие")||name.equals("За българи в чужбина")) {
                group=name.equals("ИТ")?"school":name.equals("Чужди езици")?"languages":"more"; name=p[1]; depth=2;
            }
            String key=group+"/"+name;
            if(name.startsWith("Химия за кандидат")){group="more";key=group+"/"+name;}
            Subject subject=names.get(key);
            if (subject==null) { subject=new Subject(key,name,group); names.put(key,subject); }
            String label=p.length>depth+1?p[depth]:"Всички уроци";
            String courseKey=key+"/"+label;
            Course course=courses.get(courseKey);
            if (course==null) { course=new Course(courseKey,label,subject); courses.put(courseKey,course); subject.courses.add(course); }
            Lesson lesson=new Lesson(video,course); course.lessons.add(lesson); lessons.add(lesson);
            if (byIndex.put(video.index,lesson)!=null) throw new IllegalArgumentException("Duplicate video index");
        }
        subjects.addAll(names.values()); subjects.sort(Comparator.comparingInt(s->subjectOrder(s.name)));
        for (Subject subject:subjects) {
            subject.courses.sort(Comparator.comparingInt(c->courseOrder(c.label)));
            for (Course course:subject.courses) course.lessons.sort(Comparator.<Lesson>comparingInt(l->l.number==0?Integer.MAX_VALUE:l.number).thenComparing(l->l.video.name()));
        }
    }

    public void readOutline(Reader input) throws IOException {
        try (BufferedReader reader=new BufferedReader(input)) {
            String line;
            while ((line=reader.readLine())!=null) {
                if (line.isEmpty()||line.startsWith("#")) continue;
                String[] p=line.split("\t",-1); if (p.length<5) throw new IOException("Invalid section outline");
                int index; try { index=Integer.parseInt(p[0]); } catch (NumberFormatException e) { throw new IOException(e); }
                Lesson lesson=byIndex.get(index);
                if (lesson==null) throw new IOException("Section outline references an absent video");
                lesson.sectionLabel=p[1]; lesson.sectionTitle=p[2]; lesson.sourceUrl=p[4];
            }
        }
    }
    public List<Subject> subjects(String group,int grade) {
        List<Subject> result=new ArrayList<>();
        for (Subject s:subjects) if (s.group.equals(group)&&!courses(s,grade).isEmpty()) result.add(s);
        return result;
    }
    public List<Course> courses(Subject subject,int grade) {
        List<Course> result=new ArrayList<>();
        for (Course c:subject.courses) if (!subject.group.equals("school")||c.matchesGrade(grade)) result.add(c);
        return result;
    }
    public List<Lesson> search(String query,Course course,int grade) {
        String needle=query.trim().toLowerCase(Locale.ROOT); List<Lesson> result=new ArrayList<>();
        for (Lesson l:course==null?lessons:course.lessons)
            if ((course!=null||grade<0||l.course.matchesGrade(grade))&&(l.title+" "+l.course.subject.name+" "+l.course.label).toLowerCase(Locale.ROOT).contains(needle)) result.add(l);
        return result;
    }
    public Lesson find(int index) { return byIndex.get(index); }
    public Lesson next(Lesson lesson) {
        List<Lesson> siblings=lesson.course.lessons; int position=siblings.indexOf(lesson);
        return position>=0&&position+1<siblings.size()?siblings.get(position+1):null;
    }
    private static int courseOrder(String label) {
        if (label.matches("\\d+\\. клас")) return Integer.parseInt(label.split("\\.")[0]);
        if (label.matches("\\d+\\. група")) return Integer.parseInt(label.split("\\.")[0]);
        String[] levels={"Starters","Ниво A1","Ниво A2","Ниво B1","Ниво B2","Ниво HSK1"};
        for (int i=0;i<levels.length;i++) if (label.equals(levels[i])) return i;
        if (label.startsWith("Изпити след ")) return 30+Integer.parseInt(label.replaceAll("[^0-9]",""));
        if (label.equals("PISA")) return 50;
        if (label.startsWith("Матури след ")) return 60;
        if (label.equals("Матура ПП")) return 70;
        return 80;
    }
    private static int subjectOrder(String name) {
        String[] order={"Математика","Български език","Литература","Химия","Физика","Биология","История","География","Философия","Гражданско образование","Информационни технологии","Компютърно моделиране и ИТ","Информатика","Компютърно моделиране","Програмиране","Човекът и природата","Човекът и обществото","Родинознание","Английски език начално ниво","Английски език","English in English","Немски език","Испански език","Френски език","Руски език","Китайски език","Български език за българи в чужбина","Народознание","Околен свят"};
        for (int i=0;i<order.length;i++) if (name.equals(order[i])) return i;
        return 100;
    }
}
