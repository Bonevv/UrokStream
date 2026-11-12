package bg.urokstream;

import org.junit.BeforeClass;
import org.junit.Test;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class LessonLibraryTest {
    @BeforeClass public static void nativeLibrary() throws Exception { NativeSupport.load(); }

    @Test public void firstGradeDoesNotMixWithTenthGradeAndOriginalIndexesSurvive() {
        LessonLibrary library=new LessonLibrary(new Catalog("УчаСе",Arrays.asList(
                new VideoFile(2,"УчаСе/Математика/1. клас/001-Видео урок Числата до 10.mkv",100,0),
                new VideoFile(3,"УчаСе/Математика/1. клас/002-Видео урок Събиране.mkv",100,100),
                new VideoFile(4,"УчаСе/Математика/10. клас/001-Видео урок Уравнения.mkv",100,200),
                new VideoFile(5,"УчаСе/ИТ/Информатика/8. клас/001-Видео урок Данни.mkv",100,300)),400));
        assertEquals(2,library.forGrade(1).size());
        List<LessonLibrary.Entry> entries=library.entries(1,"Математика","","");
        assertEquals(2,entries.size());
        assertEquals("Числата до 10",entries.get(0).title);
        assertEquals(2,entries.get(0).lesson.video.index);
        assertEquals(3,library.next(entries.get(0).lesson,1).video.index);
        assertNull(library.next(entries.get(1).lesson,1));
        assertEquals(8,library.find(5).grade);
    }

    @Test public void preschoolShowsSubjectsDirectlyAndSearchStaysInChosenGrade() {
        LessonLibrary library=new LessonLibrary(new Catalog("УчаСе",Arrays.asList(
                new VideoFile(10,"УчаСе/Предучилищна подготовка/Математика/001-Видео урок Числа.mkv",1,0),
                new VideoFile(11,"УчаСе/Предучилищна подготовка/Околен свят/001-Видео урок Пролет.mkv",1,1),
                new VideoFile(12,"УчаСе/Математика/10. клас/001-Видео урок Числа.mkv",1,2)),3));
        assertEquals(2,library.subjects(0).size());
        assertEquals("Математика",library.subjects(0).get(0).title);
        assertEquals(1,library.entries(0,null,"","числа").size());
        assertEquals(10,library.entries(0,null,"","ЧИСЛА").get(0).lesson.video.index);
    }

    @Test public void completeDedicatedCatalogPreservesEveryLessonAndPreschoolSubjects() throws Exception {
        LessonLibrary library=new LessonLibrary(Catalog.load(new File(System.getProperty("catalog.path"))));
        assertEquals(12805,library.lessons.size());
        assertEquals(12805,library.forGrade(-1).size());
        assertEquals(3,library.subjects(0).size());
        for (LessonLibrary.Lesson lesson:library.forGrade(1)) assertTrue(lesson.grade==1||lesson.grade==-1);
        assertTrue(library.subjects(1).stream().anyMatch(x->x.title.equals("Математика")));
        assertTrue(library.entries(7,null,"","кръстосване").stream().allMatch(e->e.lesson.grade==7||e.lesson.grade==-1));
    }
}
