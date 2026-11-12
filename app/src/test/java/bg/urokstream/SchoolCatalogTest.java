package bg.urokstream;

import org.junit.BeforeClass;
import org.junit.Test;
import java.io.File;
import java.io.FileReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;

public class SchoolCatalogTest {
    @BeforeClass public static void nativeLibrary()throws Exception{NativeSupport.load();}
    private SchoolCatalog actual()throws Exception{
        SchoolCatalog c=new SchoolCatalog(Catalog.load(new File(System.getProperty("catalog.path"))));
        try(FileReader r=new FileReader(System.getProperty("outline.path"),java.nio.charset.StandardCharsets.UTF_8)){c.readOutline(r);}return c;
    }
    @Test public void allOriginalFilesRemainAvailableExactlyOnce()throws Exception{
        SchoolCatalog c=actual();assertEquals(12805,c.lessons.size());
        HashSet<Integer> indexes=new HashSet<>();int nested=0;
        for(SchoolCatalog.Subject s:c.subjects)for(SchoolCatalog.Course course:s.courses)for(SchoolCatalog.Lesson l:course.lessons){nested++;assertTrue(indexes.add(l.video.index));assertSame(l,c.find(l.video.index));}
        assertEquals(12805,nested);
    }
    @Test public void mathFirstGradeHas97VideosInOriginalOrderAndSevenRealSections()throws Exception{
        SchoolCatalog c=actual();SchoolCatalog.Subject math=c.subjects("school",-1).get(0);assertEquals("Математика",math.name);
        SchoolCatalog.Course first=math.courses.stream().filter(x->x.label.equals("1. клас")).findFirst().get();
        assertEquals(97,first.lessons.size());HashSet<String> sections=new HashSet<>();
        for(int i=0;i<97;i++){SchoolCatalog.Lesson l=first.lessons.get(i);assertEquals(i+1,l.number);assertFalse(l.sectionTitle.isEmpty());sections.add(l.sectionTitle);}
        assertEquals(7,sections.size());assertTrue(first.lessons.get(0).sectionTitle.contains("от 1 до 5"));
        assertSame(first.lessons.get(1),c.next(first.lessons.get(0)));assertNull(c.next(first.lessons.get(96)));
    }
    @Test public void informationTechnologyAndLanguagesAreSeparateSubjects()throws Exception{
        SchoolCatalog c=actual();
        assertTrue(c.subjects("school",-1).stream().anyMatch(x->x.name.equals("Информатика")));
        assertTrue(c.subjects("school",-1).stream().anyMatch(x->x.name.equals("Компютърно моделиране и ИТ")));
        assertFalse(c.subjects("school",-1).stream().anyMatch(x->x.name.equals("ИТ")));
        assertTrue(c.subjects("languages",-1).stream().anyMatch(x->x.name.equals("Немски език")));
        assertFalse(c.subjects("languages",-1).stream().anyMatch(x->x.name.equals("Чужди езици")));
    }
    @Test public void preschoolUsesTheThreeActualGroupsAndKeepsAll270Videos()throws Exception{
        SchoolCatalog c=actual();assertEquals(3,c.subjects("preschool",-1).size());int count=0;
        for(SchoolCatalog.Subject s:c.subjects("preschool",-1)){assertEquals(3,s.courses.size());assertEquals("2. група",s.courses.get(0).label);assertEquals("4. група",s.courses.get(2).label);for(SchoolCatalog.Course course:s.courses)count+=course.lessons.size();}
        assertEquals(270,count);
    }
    @Test public void onlyClassesActuallyPresentInTorrentAreOffered()throws Exception{
        SchoolCatalog c=actual();SchoolCatalog.Subject biology=c.subjects("school",-1).stream().filter(x->x.name.equals("Биология")).findFirst().get();
        assertTrue(c.courses(biology,1).isEmpty());assertEquals(1,c.courses(biology,7).size());assertEquals("7",c.courses(biology,7).get(0).shortLabel());
        assertTrue(c.subjects("school",1).stream().noneMatch(x->x.name.equals("Биология")));
    }
    @Test public void externalSectionOutlineCannotInventALesson(){
        SchoolCatalog c=new SchoolCatalog(new Catalog("УчаСе",Arrays.asList(new VideoFile(4,"УчаСе/Математика/1. клас/001-Видео урок Числа.mkv",100,0)),100));
        assertThrows(IOException.class,()->c.readOutline(new StringReader("999\tРаздел\tИзмислен урок\t1\thttps://ucha.se/\n")));
        assertEquals(1,c.lessons.size());assertNull(c.find(999));
    }
}
