package bg.urokstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static bg.urokstream.SchoolStyle.*;

@UnstableApi
public class MainActivity extends Activity {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SharedPreferences memory;
    private LinearLayout browser,hero,playerPanel,loading,sidebar,nav;
    private FrameLayout root;
    private HorizontalScrollView tabs;
    private ScrollView sectionRail;
    private TextView greeting,description,screenTitle,empty,playerTitle;
    private EditText search;
    private Button homeButton,classButton,back,sectionsButton,resume;
    private RecyclerView grid;
    private GridLayoutManager layout;
    private Rows adapter;
    private PlayerView playerView;
    private ExoPlayer player;
    private StreamEngine engine;
    private SchoolCatalog library;
    private SchoolCatalog.Subject subject;
    private SchoolCatalog.Course course;
    private SchoolCatalog.Lesson current;
    private String group="school";
    private int grade=-1,generation;
    private boolean resumed;
    private File torrent;
    private final Map<String,Integer> chapterPositions=new LinkedHashMap<>();
    private final List<Button> tabButtons=new ArrayList<>();
    private static final String[] GROUPS={"school","languages","preschool","more"};
    private static final String[] GROUP_LABELS={"Предмети","Чужди езици","Предучилищна","Още"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        memory=getSharedPreferences("my-learning",MODE_PRIVATE);
        grade=memory.getInt("school-grade",-1);
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::goBack);
        createUi();loadLibrary();
    }
    private void createUi(){
        root=new FrameLayout(this);root.setBackgroundColor(BLUE);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        setContentView(root);browser=column();browser.setBackgroundColor(BG);root.addView(browser,fill());
        LinearLayout toolbar=row();toolbar.setPadding(d(12),d(6),d(12),d(6));toolbar.setBackgroundColor(BLUE);
        homeButton=button(this,"⌂",BLUE,Color.WHITE,v->goHome());homeButton.setContentDescription("Всички предмети");
        toolbar.addView(homeButton,new LinearLayout.LayoutParams(d(44),d(44)));
        TextView brand=text(this,"Уча.се",26,YELLOW,true);brand.setPadding(d(10),0,0,0);toolbar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        classButton=button(this,"Моят клас",Color.WHITE,BLUE,v->{if(subject!=null){course=null;search.setText("");render();grid.scrollToPosition(0);}else pickGrade();});
        toolbar.addView(classButton,new LinearLayout.LayoutParams(-2,d(44)));browser.addView(toolbar,new LinearLayout.LayoutParams(-1,d(60)));

        hero=column();hero.setPadding(d(22),d(15),d(22),d(15));
        greeting=text(this,"Уроци за всеки клас",29,INK,true);hero.addView(greeting);
        description=text(this,"Подреждаме твоята библиотека…",16,MUTED,false);hero.addView(description);browser.addView(hero);
        LinearLayout tools=column();tools.setPadding(d(16),d(8),d(16),0);browser.addView(tools);
        resume=button(this,"▶  Продължи урока",0xFFFFF5B0,INK,v->{SchoolCatalog.Lesson l=library==null?null:library.find(memory.getInt("last-index",-1));if(l!=null)play(l);});
        resume.setMaxLines(2);resume.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);resume.setVisibility(View.GONE);
        LinearLayout.LayoutParams resumeLp=new LinearLayout.LayoutParams(-1,d(58));resumeLp.bottomMargin=d(8);tools.addView(resume,resumeLp);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(17);search.setTextColor(INK);search.setHintTextColor(MUTED);
        search.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.NORMAL));search.setHint("Търси заглавие на урок");search.setContentDescription("Търсене на видео урок");
        search.setPadding(d(15),0,d(15),0);search.setBackground(round(this,Color.WHITE,24,BORDER,1));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,a,e)->{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);grid.requestFocus();return true;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){main.removeCallbacks(filter);main.postDelayed(filter,180);}});
        tools.addView(search,new LinearLayout.LayoutParams(-1,d(48)));

        tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout tabRow=row();tabRow.setPadding(d(16),d(10),d(16),d(6));tabs.addView(tabRow);
        for(int i=0;i<GROUPS.length;i++){final int k=i;Button b=button(this,GROUP_LABELS[i],Color.WHITE,INK,v->{group=GROUPS[k];subject=null;course=null;search.setText("");render();grid.scrollToPosition(0);});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,d(44));lp.rightMargin=d(8);tabRow.addView(b,lp);tabButtons.add(b);}
        browser.addView(tabs);
        nav=row();nav.setPadding(d(16),d(6),d(16),d(6));
        back=button(this,"← Назад",Color.WHITE,INK,v->goBack());LinearLayout.LayoutParams backLp=new LinearLayout.LayoutParams(d(105),d(44));backLp.rightMargin=d(12);nav.addView(back,backLp);
        screenTitle=text(this,"Всички предмети",20,INK,true);nav.addView(screenTitle,new LinearLayout.LayoutParams(0,-2,1));
        sectionsButton=button(this,"Раздели ▾",Color.WHITE,BLUE,v->pickChapter());nav.addView(sectionsButton,new LinearLayout.LayoutParams(-2,d(44)));browser.addView(nav);
        empty=text(this,"Само още миг…",18,MUTED,false);empty.setGravity(Gravity.CENTER);browser.addView(empty);
        LinearLayout body=new LinearLayout(this);browser.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        sectionRail=new ScrollView(this);sidebar=column();sidebar.setPadding(d(18),d(8),d(12),d(12));sectionRail.addView(sidebar);body.addView(sectionRail,new LinearLayout.LayoutParams(d(235),-1));
        grid=new RecyclerView(this);grid.setClipToPadding(false);grid.setPadding(d(12),d(4),d(12),d(14));layout=new GridLayoutManager(this,1);grid.setLayoutManager(layout);grid.setItemAnimator(null);
        adapter=new Rows();grid.setAdapter(adapter);grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)updateColumns();});body.addView(grid,new LinearLayout.LayoutParams(0,-1,1));

        playerPanel=column();playerPanel.setBackgroundColor(0xFF10172A);playerPanel.setVisibility(View.GONE);root.addView(playerPanel,fill());
        LinearLayout controls=row();controls.setPadding(d(10),d(8),d(10),d(8));controls.addView(button(this,"← Моите уроци",Color.WHITE,BLUE,v->stopPlayback(true)),new LinearLayout.LayoutParams(d(150),d(46)));
        playerTitle=text(this,"",19,Color.WHITE,true);playerTitle.setMaxLines(2);playerTitle.setPadding(d(12),0,0,0);controls.addView(playerTitle,new LinearLayout.LayoutParams(0,-2,1));playerPanel.addView(controls);
        FrameLayout area=new FrameLayout(this);playerPanel.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        playerView=new PlayerView(this);playerView.setKeepScreenOn(true);playerView.setControllerAutoShow(true);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);area.addView(playerView,fill());
        loading=column();loading.setGravity(Gravity.CENTER);loading.setPadding(d(24),d(20),d(24),d(20));loading.setBackground(round(this,Color.WHITE,16,Color.TRANSPARENT,0));
        KidStyle.Owl owl=new KidStyle.Owl(this);loading.addView(owl,new LinearLayout.LayoutParams(d(86),d(86)));
        TextView preparing=text(this,"Подготвяме твоя урок…",23,INK,true);preparing.setGravity(Gravity.CENTER);loading.addView(preparing);
        TextView hint=text(this,"Още малко и започваме!",16,MUTED,false);loading.addView(hint);
        ProgressBar progress=new ProgressBar(this);LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(d(28),d(28));progressLp.topMargin=d(14);loading.addView(progress,progressLp);
        area.addView(loading,new FrameLayout.LayoutParams(d(295),-2,Gravity.CENTER));render();
    }
    private final Runnable filter=this::render;
    private void updateColumns(){
        if(layout==null)return;
        int width=getResources().getConfiguration().screenWidthDp;
        layout.setSpanCount(course!=null||search.length()>0?1:subject!=null?(width>=900?3:2):width>=1100?3:width>=700?2:1);
        sectionRail.setVisibility(course!=null&&search.length()==0&&width>=900&&!chapterPositions.isEmpty()?View.VISIBLE:View.GONE);
        sectionsButton.setVisibility(course!=null&&search.length()==0&&width<900&&!chapterPositions.isEmpty()?View.VISIBLE:View.GONE);
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);updateColumns();}
    protected Catalog readTorrent(File file)throws IOException{return Catalog.load(file);}
    protected SchoolCatalog readSchoolCatalog(File file)throws IOException{
        SchoolCatalog loaded=new SchoolCatalog(readTorrent(file));
        try(InputStreamReader in=new InputStreamReader(getAssets().open("site-outline.tsv"),StandardCharsets.UTF_8)){loaded.readOutline(in);}
        return loaded;
    }
    private void loadLibrary(){
        worker.execute(()->{try{
            SafeFiles.deleteTree(new File(getFilesDir(),"active-stream"));File file=new File(getFilesDir(),"lessons.torrent");
            if(!file.isFile())try(InputStream in=getAssets().open("lessons.torrent");FileOutputStream out=new FileOutputStream(file)){byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
            SchoolCatalog loaded=readSchoolCatalog(file);
            main.post(()->{if(!isDestroyed()){torrent=file;library=loaded;render();}});
        }catch(Exception|LinkageError e){Log.e("Urokche","Catalog",e);main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Още един опит?").setMessage("Уроците не успяха да се отворят. Нека опитаме отново.").setPositiveButton("Опитай пак",(d,w)->loadLibrary()).setNegativeButton("Назад",(d,w)->finish()).show();});}});
    }
    private void render(){
        if(adapter==null)return;List<Tile> rows=new ArrayList<>();chapterPositions.clear();
        if(library==null){empty.setVisibility(View.VISIBLE);tabs.setVisibility(View.GONE);back.setVisibility(View.GONE);sectionsButton.setVisibility(View.GONE);sectionRail.setVisibility(View.GONE);return;}
        boolean searching=search.length()>0,home=subject==null&&!searching;
        tabs.setVisibility(home?View.VISIBLE:View.GONE);back.setVisibility(home?View.GONE:View.VISIBLE);resume.setVisibility(View.GONE);
        classButton.setVisibility(group.equals("school")||subject!=null?View.VISIBLE:View.GONE);classButton.setText(subject!=null?(group.equals("preschool")?"Друга група":group.equals("languages")?"Друго ниво":group.equals("more")?"Курсове":"Друг клас"):grade<0?"Моят клас":grade+". клас");
        for(int i=0;i<tabButtons.size();i++){Button b=tabButtons.get(i);interactive(b,group.equals(GROUPS[i])?BLUE:Color.WHITE,22,BLUE);b.setTextColor(group.equals(GROUPS[i])?Color.WHITE:INK);}
        hero.setBackgroundColor(course!=null?color(course.subject.name):Color.WHITE);greeting.setTextColor(course!=null?Color.WHITE:INK);description.setTextColor(course!=null?Color.WHITE:MUTED);
        if(searching){greeting.setText("Намери своя урок");description.setText(course==null?"Търсене в наличните видео уроци":course.subject.name+" · "+course.label);screenTitle.setText("Резултати");for(SchoolCatalog.Lesson l:library.search(search.getText().toString(),course,group.equals("school")?grade:-1))rows.add(Tile.lesson(l));}
        else if(course!=null){
            greeting.setText(course.subject.name);description.setText(course.label+" · "+course.lessons.size()+" видео урока");screenTitle.setText("Видео уроци");
            String previous=null;
            for(SchoolCatalog.Lesson l:course.lessons){
                String title=l.sectionTitle.isEmpty()?"Още налични уроци":l.sectionTitle;
                String key=l.sectionLabel+"\t"+title;
                if(!key.equals(previous)&&!l.sectionTitle.isEmpty()){
                    if(!chapterPositions.containsKey(key))chapterPositions.put(key,rows.size());
                    rows.add(Tile.chapter(l.sectionLabel,title));
                }else if(previous!=null&&!key.equals(previous))rows.add(Tile.chapter("",title));
                rows.add(Tile.lesson(l));previous=key;
            }
            rebuildSidebar();
        }else if(subject!=null){greeting.setText(subject.name);description.setText("Избери клас или ниво");screenTitle.setText("Класове и нива");for(SchoolCatalog.Course c:subject.courses)rows.add(Tile.course(c));}
        else{
            greeting.setText(group.equals("preschool")?"Предучилищна подготовка":group.equals("languages")?"Чужди езици":group.equals("more")?"Още за откриване":"Избери предмет и клас");
            description.setText("Твоята библиотека с видео уроци");screenTitle.setText(group.equals("school")&&grade>=0?"Предмети за "+grade+". клас":"Всички предмети");
            for(SchoolCatalog.Subject s:library.subjects(group,grade))rows.add(Tile.subject(s));
            SchoolCatalog.Lesson last=library.find(memory.getInt("last-index",-1));
            if(last!=null&&memory.getLong("last-position",0)>0){resume.setText("▶  Продължи урока\n"+last.title);resume.setVisibility(View.VISIBLE);}
        }
        empty.setText("Няма намерени уроци. Опитай с друга дума.");empty.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);
        adapter.replace(rows);updateColumns();
    }
    private void rebuildSidebar(){
        sidebar.removeAllViews();sidebar.addView(text(this,"Раздели",23,MUTED,true));
        for(String key:chapterPositions.keySet()){
            Button b=button(this,chapterCaption(key),BG,INK,v->jumpToChapter(key));b.setTextSize(16);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setMaxLines(5);b.setMinHeight(d(60));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=d(8);sidebar.addView(b,lp);
        }
    }
    private void jumpToChapter(String title){Integer position=chapterPositions.get(title);if(position==null)return;grid.scrollToPosition(position);main.postDelayed(()->{RecyclerView.ViewHolder h=grid.findViewHolderForAdapterPosition(position+1);if(h!=null)h.itemView.requestFocus();},120);}
    private String chapterCaption(String key){String[] p=key.split("\t",2);for(String other:chapterPositions.keySet())if(!other.equals(key)&&other.endsWith("\t"+p[1]))return p[0].isEmpty()?p[1]:p[0]+" · "+p[1];return p[1];}
    private void pickChapter(){String[] keys=chapterPositions.keySet().toArray(new String[0]);String[] labels=new String[keys.length];for(int i=0;i<keys.length;i++)labels[i]=chapterCaption(keys[i]);new AlertDialog.Builder(this).setTitle("Раздели").setItems(labels,(d,w)->jumpToChapter(keys[w])).setNegativeButton("Назад",null).show();}
    private void pickGrade(){String[] labels=new String[13];labels[0]="Всички класове";for(int i=1;i<=12;i++)labels[i]=i+". клас";new AlertDialog.Builder(this).setTitle("Моят клас").setSingleChoiceItems(labels,grade<0?0:grade,(dialog,which)->{grade=which==0?-1:which;memory.edit().putInt("school-grade",grade).apply();dialog.dismiss();render();grid.scrollToPosition(0);}).setNegativeButton("Назад",null).show();}
    private void openCourse(SchoolCatalog.Course selected){subject=selected.subject;course=selected;group=subject.group;search.setText("");render();grid.scrollToPosition(0);}
    private void goHome(){if(engine!=null)stopPlayback(false);subject=null;course=null;search.setText("");browser.setVisibility(View.VISIBLE);playerPanel.setVisibility(View.GONE);render();grid.scrollToPosition(0);}
    private void goBack(){if(engine!=null){stopPlayback(true);return;}if(search.length()>0)search.setText("");else if(course!=null)course=null;else if(subject!=null)subject=null;else{finish();return;}render();grid.scrollToPosition(0);}

    private void play(SchoolCatalog.Lesson lesson){
        if(torrent==null)return;long saved=memory.getInt("last-index",-1)==lesson.video.index?memory.getLong("last-position",0):0;
        stopPlayback(false);int token=++generation;current=lesson;
        memory.edit().putInt("last-index",lesson.video.index).putLong("last-position",saved).apply();
        StreamEngine selected=new StreamEngine(new File(getFilesDir(),"active-stream"));engine=selected;
        browser.setVisibility(View.GONE);playerPanel.setVisibility(View.VISIBLE);loading.setVisibility(View.VISIBLE);playerTitle.setText(lesson.title);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker.execute(()->{try{selected.start(torrent,lesson.video);main.post(()->{
            if(!resumed||generation!=token||engine!=selected)return;
            player=new ExoPlayer.Builder(this).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(5000,25000,1000,2000).build()).build();playerView.setPlayer(player);
            player.addListener(new Player.Listener(){
                @Override public void onPlayerError(PlaybackException e){if(generation==token&&engine==selected)lessonError(lesson,e);}
                @Override public void onPlaybackStateChanged(int state){if(generation!=token||engine!=selected)return;loading.setVisibility(state==Player.STATE_BUFFERING?View.VISIBLE:View.GONE);if(state==Player.STATE_READY)playerView.requestFocus();if(state==Player.STATE_ENDED)completed(lesson);}
            });
            MediaItem item=MediaItem.fromUri(Uri.parse("torrent://lesson/"+lesson.video.index));
            player.setMediaSource(new ProgressiveMediaSource.Factory(()->new TorrentDataSource(selected,lesson.video)).setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(0)).createMediaSource(item));
            if(saved>0)player.seekTo(saved);player.prepare();player.play();playerView.requestFocus();
        });}catch(Exception|LinkageError e){main.post(()->{if(generation==token&&!isDestroyed())lessonError(lesson,e);});}});
    }
    private void completed(SchoolCatalog.Lesson lesson){
        memory.edit().putBoolean("done."+lesson.video.index,true).putInt("last-index",-1).putLong("last-position",0).apply();stopPlayback(true,false);
        SchoolCatalog.Lesson next=library.next(lesson);AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle("Браво!  ★").setMessage("Още един изгледан урок. Чудесна работа!").setNegativeButton("Моите уроци",null);
        if(next!=null)d.setPositiveButton("Следващ урок",(x,w)->play(next));else d.setPositiveButton("Супер!",null);d.show();
    }
    private void lessonError(SchoolCatalog.Lesson lesson,Throwable error){
        Log.e("Urokche","Playback",error);stopPlayback(true);if(isFinishing()||isDestroyed())return;
        String message=error.getMessage()!=null&&error.getMessage().contains("място")?"Паметта на устройството е почти пълна. Помоли възрастен за помощ.":"Урокът не успя да се зареди. Можеш да опиташ пак или да избереш друг.";
        new AlertDialog.Builder(this).setTitle("Още един опит?").setMessage(message).setPositiveButton("Опитай пак",(d,w)->play(lesson)).setNegativeButton("Друг урок",null).show();
    }
    private void stopPlayback(boolean show){stopPlayback(show,true);}
    private void stopPlayback(boolean show,boolean save){
        generation++;StreamEngine old=engine;engine=null;if(old!=null)old.cancel();
        if(player!=null){if(save&&current!=null&&player.getCurrentPosition()>0)memory.edit().putInt("last-index",current.video.index).putLong("last-position",player.getCurrentPosition()).apply();playerView.setPlayer(null);player.release();player=null;}
        if(old!=null)worker.execute(()->{try{old.close();}catch(IOException e){Log.e("Urokche","Cleanup",e);}});
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(show){playerPanel.setVisibility(View.GONE);browser.setVisibility(View.VISIBLE);render();grid.requestFocus();}
    }
    @Override protected void onStart(){super.onStart();resumed=true;}
    @Override protected void onStop(){resumed=false;stopPlayback(true);super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);worker.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){goBack();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return player!=null&&playerView.dispatchMediaKeyEvent(event)||super.dispatchKeyEvent(event);}
    private int d(int value){return dp(this,value);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private FrameLayout.LayoutParams fill(){return new FrameLayout.LayoutParams(-1,-1);}

    private static final class Tile{
        final int type;SchoolCatalog.Subject subject;SchoolCatalog.Course course;SchoolCatalog.Lesson lesson;String label,title;
        Tile(int type){this.type=type;}static Tile subject(SchoolCatalog.Subject s){Tile t=new Tile(0);t.subject=s;return t;}static Tile course(SchoolCatalog.Course c){Tile t=new Tile(1);t.course=c;return t;}
        static Tile chapter(String l,String n){Tile t=new Tile(2);t.label=l;t.title=n;return t;}static Tile lesson(SchoolCatalog.Lesson l){Tile t=new Tile(3);t.lesson=l;return t;}
    }
    private final class Rows extends RecyclerView.Adapter<Holder>{
        private List<Tile> rows=new ArrayList<>();void replace(List<Tile> value){rows=value;notifyDataSetChanged();}
        @Override public int getItemViewType(int p){return rows.get(p).type;}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=column();RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(d(6),d(5),d(6),d(type==0?16:5));card.setLayoutParams(lp);
            Holder h=new Holder(card);
            if(type==0){card.setPadding(d(14),d(12),d(14),d(14));LinearLayout heading=row();h.icon=new SubjectIcon(MainActivity.this);heading.addView(h.icon,new LinearLayout.LayoutParams(d(38),d(42)));h.title=text(MainActivity.this,"",23,INK,true);h.title.setPadding(d(10),0,0,0);heading.addView(h.title,new LinearLayout.LayoutParams(0,-2,1));card.addView(heading);h.flow=new Flow(MainActivity.this);LinearLayout.LayoutParams f=new LinearLayout.LayoutParams(-1,-2);f.topMargin=d(10);card.addView(h.flow,f);}
            else if(type==1){card.setPadding(d(18),d(18),d(18),d(18));card.setMinimumHeight(d(105));h.title=text(MainActivity.this,"",25,INK,true);h.detail=text(MainActivity.this,"",16,MUTED,false);card.addView(h.title);card.addView(h.detail);}
            else if(type==2){card.setPadding(d(52),d(16),0,d(4));h.detail=text(MainActivity.this,"",15,MUTED,true);h.title=text(MainActivity.this,"",23,INK,true);card.addView(h.detail);card.addView(h.title);}
            else{card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(d(3),d(3),d(3),d(3));h.number=text(MainActivity.this,"",17,INK,true);h.number.setGravity(Gravity.CENTER);h.number.setBackground(round(MainActivity.this,Color.WHITE,24,BORDER,1));LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(d(38),d(38));n.rightMargin=d(12);card.addView(h.number,n);LinearLayout box=row();box.setPadding(d(15),d(14),d(12),d(14));box.setMinimumHeight(d(72));LinearLayout titles=column();h.title=text(MainActivity.this,"",20,INK,false);h.title.setMaxLines(4);titles.addView(h.title);h.detail=text(MainActivity.this,"",14,MUTED,false);titles.addView(h.detail);box.addView(titles,new LinearLayout.LayoutParams(0,-2,1));h.play=text(MainActivity.this,"▷",28,BLUE,true);h.play.setGravity(Gravity.CENTER);box.addView(h.play,new LinearLayout.LayoutParams(d(40),d(40)));box.setBackground(round(MainActivity.this,Color.WHITE,5,BORDER,1));card.addView(box,new LinearLayout.LayoutParams(0,-2,1));}
            return h;
        }
        @Override public void onBindViewHolder(Holder h,int position){
            Tile tile=rows.get(position);
            if(tile.type==0){
                SchoolCatalog.Subject s=tile.subject;h.title.setText(s.name);h.icon.subject(s.name);h.flow.removeAllViews();interactive(h.itemView,Color.WHITE,8,color(s.name));
                List<SchoolCatalog.Course> available=library.courses(s,grade);
                boolean compact=getResources().getConfiguration().screenWidthDp<700&&s.group.equals("school")&&available.stream().anyMatch(c->c.label.matches("\\d+\\. клас"));
                boolean more=false;
                for(SchoolCatalog.Course c:available){
                    if(compact&&!c.label.matches("\\d+\\. клас")){more=true;continue;}
                    Button b=button(MainActivity.this,c.shortLabel(),Color.WHITE,INK,v->openCourse(c));b.setTextSize(16);b.setContentDescription(s.name+", "+c.label);h.flow.addView(b,new ViewGroup.LayoutParams(c.shortLabel().matches("\\d+")?d(46):-2,d(44)));
                }
                if(more)h.flow.addView(button(MainActivity.this,"Още ▾",Color.WHITE,INK,v->{subject=s;course=null;render();grid.scrollToPosition(0);}),new ViewGroup.LayoutParams(-2,d(44)));
                h.itemView.setOnClickListener(v->{subject=s;course=null;render();grid.scrollToPosition(0);});
            }else if(tile.type==1){SchoolCatalog.Course c=tile.course;h.title.setText(c.label);h.detail.setText(c.lessons.size()+" видео урока");interactive(h.itemView,Color.WHITE,8,color(c.subject.name));h.itemView.setOnClickListener(v->openCourse(c));}
            else if(tile.type==2){h.title.setText(tile.title);h.detail.setText(tile.label);h.detail.setVisibility(tile.label.isEmpty()?View.GONE:View.VISIBLE);h.itemView.setFocusable(false);h.itemView.setClickable(false);}
            else{SchoolCatalog.Lesson l=tile.lesson;boolean done=memory.getBoolean("done."+l.video.index,false);h.title.setText(l.title);h.number.setText(l.number>0?String.valueOf(l.number):"•");h.play.setText(done?"✓":"▷");h.play.setTextColor(done?0xFF0BAA70:color(l.course.subject.name));h.detail.setVisibility(search.length()>0?View.VISIBLE:View.GONE);h.detail.setText(l.course.subject.name+" · "+l.course.label);interactive(h.itemView,Color.TRANSPARENT,6,color(l.course.subject.name));h.itemView.setContentDescription("Урок "+l.number+". "+l.title+(done?". Изгледан":""));h.itemView.setOnClickListener(v->play(l));}
        }
        @Override public int getItemCount(){return rows.size();}
    }
    private static final class Holder extends RecyclerView.ViewHolder{
        TextView title,detail,number,play;SubjectIcon icon;Flow flow;Holder(View view){super(view);}
    }
}
