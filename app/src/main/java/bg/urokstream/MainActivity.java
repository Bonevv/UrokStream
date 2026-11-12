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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static bg.urokstream.KidStyle.*;

@UnstableApi
public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences memory;
    private FrameLayout root, playerArea;
    private LinearLayout browser, playerPanel, hero, loading;
    private TextView greeting, encouragement, section, empty, playerTitle, waiting;
    private Button back, classButton, resume;
    private EditText search;
    private RecyclerView grid;
    private GridLayoutManager layout;
    private Cards adapter;
    private PlayerView playerView;
    private ExoPlayer player;
    private StreamEngine engine;
    private LessonLibrary library;
    private LessonLibrary.Lesson current;
    private String subject, folder = "";
    private File torrent;
    private int grade = -2, generation;
    private boolean pickingGrade, resumed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        memory = getSharedPreferences("my-learning", MODE_PRIVATE);
        grade = memory.getInt("grade", -2); pickingGrade = grade == -2;
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::goBack);
        createUi(); loadLibrary();
    }

    private void createUi() {
        root = new FrameLayout(this); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        browser = column(); browser.setPadding(d(20), d(8), d(20), d(8)); root.addView(browser, fill());
        LinearLayout top = row();
        TextView brand = text(this, "УчаСе", 24, PURPLE, true);
        brand.setContentDescription("УчаСе, моята библиотека с уроци");
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        classButton = button(this, "Моят клас", Color.WHITE, v -> { pickingGrade = true; search.setText(""); render(); grid.scrollToPosition(0); });
        top.addView(classButton, new LinearLayout.LayoutParams(-2, d(48)));
        browser.addView(top, new LinearLayout.LayoutParams(-1, d(54)));

        hero = row(); hero.setPadding(d(18), d(6), d(8), d(6)); hero.setBackground(round(this, 0xFFF0EBFF, 26, Color.TRANSPARENT));
        LinearLayout welcome = column();
        greeting = text(this, "Здравей, откривателю!", 23, INK, true); welcome.addView(greeting);
        encouragement = text(this, "Подреждаме твоите уроци…", 14, MUTED, false); welcome.addView(encouragement);
        hero.addView(welcome, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(new Owl(this), new LinearLayout.LayoutParams(d(100), -1));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, heroHeight()); heroLp.topMargin = d(8); heroLp.bottomMargin = d(12); browser.addView(hero, heroLp);

        resume = button(this, "▶  Продължи урока", MINT, v -> {
            LessonLibrary.Lesson last = library == null ? null : library.find(memory.getInt("last-index", -1));
            if (last != null) play(last);
        });
        resume.setMaxLines(2); resume.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); resume.setVisibility(View.GONE);
        LinearLayout.LayoutParams resumeLp = new LinearLayout.LayoutParams(-1, d(60)); resumeLp.bottomMargin = d(10); browser.addView(resume, resumeLp);

        search = new EditText(this); search.setSingleLine(true); search.setTextSize(16); search.setTextColor(INK); search.setHintTextColor(MUTED);
        search.setHint("⌕  Намери любим урок…"); search.setContentDescription("Търси урок в моя клас");
        search.setPadding(d(16), 0, d(16), 0); search.setBackground(round(this, Color.WHITE, 18, Color.TRANSPARENT));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, action, event) -> {
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            grid.requestFocus(); return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { main.removeCallbacks(filter); main.postDelayed(filter, 180); }
            public void afterTextChanged(Editable text) {}
        });
        browser.addView(search, new LinearLayout.LayoutParams(-1, d(50)));

        LinearLayout navigation = row();
        back = button(this, "← Назад", Color.WHITE, v -> goBack());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(d(112), d(44)); backLp.rightMargin = d(10); navigation.addView(back, backLp);
        section = text(this, "Моята библиотека", 20, INK, true); navigation.addView(section, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, d(60)); browser.addView(navigation, navLp);

        empty = text(this, "Само още миг…", 17, MUTED, false); empty.setGravity(Gravity.CENTER); browser.addView(empty);
        grid = new RecyclerView(this); grid.setClipToPadding(false); grid.setPadding(d(4), d(4), d(4), d(10));
        layout = new GridLayoutManager(this, 2); grid.setLayoutManager(layout); grid.setItemAnimator(null);
        adapter = new Cards(); grid.setAdapter(adapter);
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> { if (r-l != or-ol) updateColumns(); });
        browser.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text(this, "Малка крачка. Голямо откритие.  ✦", 12, MUTED, false); footer.setGravity(Gravity.CENTER); browser.addView(footer);

        playerPanel = column(); playerPanel.setBackgroundColor(0xFF101529); playerPanel.setVisibility(View.GONE); root.addView(playerPanel, fill());
        LinearLayout controls = row(); controls.setPadding(d(12), d(8), d(12), d(8));
        Button close = button(this, "← Моите уроци", 0xFFEDE7FF, v -> stopPlayback(true));
        controls.addView(close, new LinearLayout.LayoutParams(d(160), d(48)));
        playerTitle = text(this, "", 17, Color.WHITE, true); playerTitle.setMaxLines(2); playerTitle.setPadding(d(14), 0, 0, 0);
        controls.addView(playerTitle, new LinearLayout.LayoutParams(0, -2, 1)); playerPanel.addView(controls);
        playerArea = new FrameLayout(this); playerPanel.addView(playerArea, new LinearLayout.LayoutParams(-1, 0, 1));
        playerView = new PlayerView(this); playerView.setControllerAutoShow(true); playerView.setKeepScreenOn(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER); playerArea.addView(playerView, fill());
        loading = column(); loading.setGravity(Gravity.CENTER); loading.setPadding(d(24), d(20), d(24), d(20));
        loading.setBackground(round(this, 0xFFF0EBFF, 28, Color.TRANSPARENT));
        loading.addView(new Owl(this), new LinearLayout.LayoutParams(d(100), d(100)));
        waiting = text(this, "Подготвяме твоя урок…", 20, INK, true); waiting.setGravity(Gravity.CENTER); loading.addView(waiting);
        TextView hint = text(this, "Още малко и започваме!", 14, MUTED, false); hint.setGravity(Gravity.CENTER); loading.addView(hint);
        ProgressBar progress = new ProgressBar(this); LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(d(28),d(28)); progressLp.topMargin=d(16); loading.addView(progress,progressLp);
        FrameLayout.LayoutParams loadingLp = new FrameLayout.LayoutParams(d(300), -2, Gravity.CENTER); playerArea.addView(loading, loadingLp);
        render();
    }

    private final Runnable filter = this::render;
    private int heroHeight() { return d(getResources().getConfiguration().screenHeightDp < 500 ? 92 : 122); }
    private void updateColumns() {
        if (layout == null) return;
        int width = getResources().getConfiguration().screenWidthDp;
        boolean home = pickingGrade || subject == null && search.length() == 0;
        int columns = home ? width >= 1000 ? 4 : width >= 650 ? 3 : 2 : width >= 1000 ? 3 : width >= 650 ? 2 : 1;
        layout.setSpanCount(columns);
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config); hero.getLayoutParams().height=heroHeight(); hero.requestLayout(); updateColumns();
    }

    private void loadLibrary() {
        worker.execute(() -> {
            try {
                SafeFiles.deleteTree(new File(getFilesDir(), "active-stream"));
                File file = new File(getFilesDir(), "lessons.torrent");
                if (!file.isFile()) try (InputStream in=getAssets().open("lessons.torrent"); FileOutputStream out=new FileOutputStream(file)) {
                    byte[] bytes=new byte[32768]; int count; while ((count=in.read(bytes))!=-1) out.write(bytes,0,count);
                }
                LessonLibrary loaded = new LessonLibrary(Catalog.load(file));
                main.post(() -> { if (!isDestroyed()) { torrent=file; library=loaded; render(); } });
            } catch (Exception | LinkageError e) {
                Log.e("Urokche", "Cannot load bundled lessons",e);
                main.post(() -> { if (!isFinishing()&&!isDestroyed()) new AlertDialog.Builder(this).setTitle("Още един опит?")
                        .setMessage("Уроците не успяха да се отворят. Нека опитаме отново.")
                        .setPositiveButton("Опитай пак",(d,w)->loadLibrary()).setNegativeButton("Назад",(d,w)->finish()).show(); });
            }
        });
    }

    private void render() {
        if (adapter == null) return;
        updateColumns();
        List<Tile> tiles=new ArrayList<>();
        if (library == null) { empty.setVisibility(View.VISIBLE); search.setVisibility(View.GONE); classButton.setVisibility(View.GONE); back.setVisibility(View.GONE); return; }
        classButton.setVisibility(pickingGrade ? View.GONE : View.VISIBLE); classButton.setText(LessonLibrary.gradeLabel(grade)+" ▾");
        search.setVisibility(pickingGrade ? View.GONE : View.VISIBLE);
        resume.setVisibility(View.GONE);
        if (pickingGrade) {
            greeting.setText("Здравей, откривателю!"); encouragement.setText("Избери своя клас. Да учим с усмивка!"); section.setText("В кой клас си?");
            back.setVisibility(grade==-2 ? View.GONE : View.VISIBLE);
            for (int g=0;g<=12;g++) tiles.add(new Tile(g)); tiles.add(new Tile(-1));
        } else if (search.length()>0) {
            greeting.setText("Да открием твоя урок!"); encouragement.setText("Търсим в "+LessonLibrary.gradeLabel(grade).toLowerCase(java.util.Locale.ROOT));
            section.setText("Намерени уроци"); back.setVisibility(View.VISIBLE);
            for (LessonLibrary.Entry entry:library.entries(grade,null,"",search.getText().toString())) tiles.add(new Tile(entry,false));
        } else if (subject==null) {
            greeting.setText("Какво ще открием днес?"); encouragement.setText("Избери предмет и намери своя следващ урок."); section.setText("Моите предмети"); back.setVisibility(View.GONE);
            for (LessonLibrary.Entry entry:library.subjects(grade)) tiles.add(new Tile(entry,true));
            LessonLibrary.Lesson last=library.find(memory.getInt("last-index",-1));
            if (last!=null && library.forGrade(grade).contains(last) && memory.getLong("last-position",0)>0) {
                resume.setText("▶  Продължи урока\n"+last.title); resume.setVisibility(View.VISIBLE);
            }
        } else {
            greeting.setText(subject); encouragement.setText("Един урок. Много нови открития!");
            section.setText(folder.isEmpty() ? "Избери си урок" : folder.substring(0,folder.length()-1).substring(folder.substring(0,folder.length()-1).lastIndexOf('/')+1));
            back.setVisibility(View.VISIBLE);
            for (LessonLibrary.Entry entry:library.entries(grade,subject,folder,"")) tiles.add(new Tile(entry,false));
        }
        empty.setText("Няма урок с това име. Опитай с друга дума."); empty.setVisibility(tiles.isEmpty()?View.VISIBLE:View.GONE);
        adapter.replace(tiles);
    }

    private void chooseGrade(int selected) {
        grade=selected; memory.edit().putInt("grade",grade).apply(); pickingGrade=false; subject=null; folder=""; render(); grid.scrollToPosition(0);
    }
    private void goBack() {
        if (engine!=null) { stopPlayback(true); return; }
        if (search.length()>0) search.setText("");
        else if (pickingGrade && grade!=-2) pickingGrade=false;
        else if (!folder.isEmpty()) folder=LessonLibrary.parent(folder.substring(0,folder.length()-1));
        else if (subject!=null) subject=null;
        else { finish(); return; }
        render(); grid.scrollToPosition(0);
    }

    private void play(LessonLibrary.Lesson lesson) {
        if (library==null || torrent==null) return;
        long saved=memory.getInt("last-index",-1)==lesson.video.index ? memory.getLong("last-position",0) : 0;
        stopPlayback(false);
        int myGeneration=++generation; current=lesson;
        memory.edit().putInt("last-index",lesson.video.index).putLong("last-position",saved).apply();
        StreamEngine selected=new StreamEngine(new File(getFilesDir(),"active-stream")); engine=selected;
        browser.setVisibility(View.GONE); playerPanel.setVisibility(View.VISIBLE); loading.setVisibility(View.VISIBLE);
        waiting.setText("Подготвяме твоя урок…"); playerTitle.setText(lesson.title);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker.execute(() -> {
            try {
                selected.start(torrent,lesson.video);
                main.post(() -> {
                    if (!resumed || generation!=myGeneration || engine!=selected) return;
                    player=new ExoPlayer.Builder(this).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(5000,25000,1000,2000).build()).build();
                    playerView.setPlayer(player);
                    player.addListener(new Player.Listener() {
                        @Override public void onPlayerError(PlaybackException error) { if (generation==myGeneration&&engine==selected) lessonError(lesson,error); }
                        @Override public void onPlaybackStateChanged(int state) {
                            if (generation!=myGeneration||engine!=selected) return;
                            loading.setVisibility(state==Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                            if (state==Player.STATE_READY) { playerView.requestFocus(); }
                            if (state==Player.STATE_ENDED) completed(lesson);
                        }
                    });
                    MediaItem item=MediaItem.fromUri(Uri.parse("torrent://lesson/"+lesson.video.index));
                    player.setMediaSource(new ProgressiveMediaSource.Factory(() -> new TorrentDataSource(selected,lesson.video))
                            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(0)).createMediaSource(item));
                    if (saved>0) player.seekTo(saved);
                    player.prepare(); player.play(); playerView.requestFocus();
                });
            } catch (Exception | LinkageError error) { main.post(() -> { if (generation==myGeneration&&!isDestroyed()) lessonError(lesson,error); }); }
        });
    }

    private void completed(LessonLibrary.Lesson lesson) {
        memory.edit().putBoolean("done."+lesson.video.index,true).putInt("last-index",-1).putLong("last-position",0).apply();
        stopPlayback(true,false);
        LessonLibrary.Lesson next=library.next(lesson,grade);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Браво, откривателю!  ★")
                .setMessage("Още един научен урок. Чудесна работа!")
                .setNegativeButton("Моите уроци",null);
        if (next!=null) dialog.setPositiveButton("Следващ урок",(d,w)->play(next));
        else dialog.setPositiveButton("Супер!",null);
        dialog.show();
    }
    private void lessonError(LessonLibrary.Lesson lesson,Throwable error) {
        Log.e("Urokche","Lesson playback failed",error); stopPlayback(true);
        if (isFinishing()||isDestroyed()) return;
        String message="Урокът не успя да се зареди. Можеш да опиташ пак или да избереш друг.";
        if (error.getMessage()!=null&&error.getMessage().contains("място")) message="Паметта на устройството е почти пълна. Помоли възрастен за помощ.";
        new AlertDialog.Builder(this).setTitle("Още един опит?").setMessage(message)
                .setPositiveButton("Опитай пак",(d,w)->play(lesson)).setNegativeButton("Друг урок",null).show();
    }

    private void stopPlayback(boolean show) { stopPlayback(show,true); }
    private void stopPlayback(boolean show,boolean save) {
        generation++;
        StreamEngine old=engine; engine=null; if (old!=null) old.cancel();
        if (player!=null) {
            if (save&&current!=null&&player.getCurrentPosition()>0) memory.edit().putInt("last-index",current.video.index).putLong("last-position",player.getCurrentPosition()).apply();
            playerView.setPlayer(null); player.release(); player=null;
        }
        if (old!=null) worker.execute(() -> { try { old.close(); } catch (IOException e) { Log.e("Urokche","Cache cleanup",e); } });
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (show) { playerPanel.setVisibility(View.GONE); browser.setVisibility(View.VISIBLE); render(); grid.requestFocus(); }
    }
    @Override protected void onStart() { super.onStart(); resumed=true; }
    @Override protected void onStop() { resumed=false; stopPlayback(true); super.onStop(); }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy(); }
    @Override public void onBackPressed() { goBack(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return player!=null&&playerView.dispatchMediaKeyEvent(event)||super.dispatchKeyEvent(event); }
    private int d(int value) { return dp(this,value); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private FrameLayout.LayoutParams fill() { return new FrameLayout.LayoutParams(-1,-1); }

    private static final class Tile {
        final LessonLibrary.Entry entry; final int grade; final boolean subject;
        Tile(int grade) { this.grade=grade; this.entry=null; this.subject=false; }
        Tile(LessonLibrary.Entry entry,boolean subject) { this.entry=entry; this.subject=subject; this.grade=-2; }
    }
    private final class Cards extends RecyclerView.Adapter<Holder> {
        private List<Tile> tiles=new ArrayList<>();
        void replace(List<Tile> value) { tiles=value; notifyDataSetChanged(); }
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type) {
            LinearLayout card=column(); card.setPadding(d(17),d(14),d(17),d(14)); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setElevation(d(1));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2); lp.setMargins(d(5),d(5),d(5),d(7)); card.setLayoutParams(lp);
            TextView symbol=text(MainActivity.this,"",30,PURPLE,true); symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); card.addView(symbol);
            TextView title=text(MainActivity.this,"",18,INK,true); title.setMaxLines(3); card.addView(title);
            TextView detail=text(MainActivity.this,"",13,MUTED,false); detail.setPadding(0,d(6),0,0); detail.setMaxLines(2); card.addView(detail);
            return new Holder(card,symbol,title,detail);
        }
        @Override public void onBindViewHolder(Holder h,int position) {
            Tile tile=tiles.get(position); LessonLibrary.Entry e=tile.entry;
            boolean completed=e!=null&&e.lesson!=null&&memory.getBoolean("done."+e.lesson.video.index,false);
            int background=e==null ? COLORS[Math.floorMod(tile.grade,COLORS.length)] : tile.subject ? color(e.title) : e.lesson==null ? 0xFFF0EBFF : completed ? 0xFFEAF8F1 : Color.WHITE;
            interactive(h.itemView,background,22);
            h.symbol.setTextSize(e==null||tile.subject?30:23);
            h.itemView.setMinimumHeight(d(e==null||tile.subject?148:126));
            if (e==null) {
                h.symbol.setText(tile.grade<0?"✦":tile.grade==0?"🌻":String.valueOf(tile.grade));
                h.title.setText(LessonLibrary.gradeLabel(tile.grade)); h.detail.setText(tile.grade<0?"Цялата библиотека  →":"Моето приключение  →");
            } else if (tile.subject) {
                h.symbol.setText(icon(e.title)); h.title.setText(e.title); h.detail.setText(e.count+" урока за откриване  →");
            } else if (e.lesson==null) {
                h.symbol.setText("📚"); h.title.setText(e.title); h.detail.setText(e.count+" урока  →");
            } else {
                h.symbol.setText(completed?"★":"▶"); h.symbol.setTextSize(23);
                h.title.setText(e.lesson.title);
                h.detail.setText(completed?"Научено! Браво на теб.":search.length()>0?e.lesson.subject+" · "+LessonLibrary.gradeLabel(e.lesson.grade):"Гледай урока");
            }
            h.itemView.setContentDescription(h.title.getText()+". "+h.detail.getText());
            h.itemView.setOnClickListener(v -> {
                if (e==null) chooseGrade(tile.grade);
                else if (tile.subject) { subject=e.title; folder=""; render(); grid.scrollToPosition(0); }
                else if (e.lesson==null) { folder=e.folder; render(); grid.scrollToPosition(0); }
                else play(e.lesson);
            });
        }
        @Override public int getItemCount() { return tiles.size(); }
    }
    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView symbol,title,detail;
        Holder(View view,TextView symbol,TextView title,TextView detail) { super(view); this.symbol=symbol; this.title=title; this.detail=detail; }
    }
}
