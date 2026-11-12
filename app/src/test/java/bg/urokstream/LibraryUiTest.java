package bg.urokstream;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.media3.common.util.UnstableApi;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Actual Android layouts and bundled torrent metadata. No peer connections or video downloads. */
@UnstableApi
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LibraryUiTest {
    public static class PreviewActivity extends MainActivity {
        @Override protected SchoolCatalog readSchoolCatalog(File torrent)throws java.io.IOException{
            try{NativeSupport.load();}catch(Exception e){throw new java.io.IOException(e);}
            return super.readSchoolCatalog(torrent);
        }
    }
    @Test @Config(qualifiers="w360dp-h740dp-mdpi") public void phoneCanOpenAClassAndItsNumberedLessons()throws Exception{check(360,740,"phone");}
    @Test @Config(qualifiers="w800dp-h1000dp-mdpi") public void tabletCanOpenAClassAndItsNumberedLessons()throws Exception{check(800,1000,"tablet");}
    @Test @Config(qualifiers="w1000dp-h600dp-mdpi") public void tvLayoutHasFocusableClassesAndLessonRows()throws Exception{check(1000,600,"tv");}

    private void check(int width,int height,String name)throws Exception{
        try(ActivityController<PreviewActivity> controller=Robolectric.buildActivity(PreviewActivity.class).setup()){
            PreviewActivity activity=controller.get();View decor=activity.getWindow().getDecorView();
            View grade=null;
            for(int i=0;i<1000&&grade==null;i++){
                shadowOf(Looper.getMainLooper()).idle();layout(decor,width,height);
                grade=findDescription(decor,"Математика, 1. клас");if(grade==null)Thread.sleep(10);
            }
            assertNotNull("Class buttons should be available",grade);assertTrue(grade.isFocusable());
            assertTrue(grade.getWidth()>=44);assertTrue(grade.getHeight()>=44);
            screenshot(decor,width,height,name+"-subjects.png");
            assertTrue(grade.performClick());shadowOf(Looper.getMainLooper()).idle();layout(decor,width,height);
            assertNotNull(findText(decor,"Първи раздел"));assertNotNull(findText(decor,"1. клас · 97 видео урока"));
            View lesson=findDescriptionPrefix(decor,"Урок 1. Ориентиране в природата");assertNotNull(lesson);assertTrue(lesson.isFocusable());
            assertTrue(lesson.requestFocusFromTouch());assertTrue(lesson.hasFocus());
            screenshot(decor,width,height,name+"-lessons.png");
        }
    }
    private static void layout(View view,int w,int h){view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);}
    private static View findDescription(View v,String value){if(value.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findDescription(g.getChildAt(i),value);if(x!=null)return x;}}return null;}
    private static View findDescriptionPrefix(View v,String value){if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(value))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findDescriptionPrefix(g.getChildAt(i),value);if(x!=null)return x;}}return null;}
    private static View findText(View v,String value){if(v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findText(g.getChildAt(i),value);if(x!=null)return x;}}return null;}
    private static void screenshot(View view,int w,int h,String name)throws Exception{
        File folder=new File(System.getProperty("ui.screenshots"));assertTrue(folder.isDirectory()||folder.mkdirs());
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));
        try(FileOutputStream out=new FileOutputStream(new File(folder,name))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();
    }
}
