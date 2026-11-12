package bg.urokstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int PICK_TORRENT = 10;
    private static final int BG = Color.rgb(13, 22, 37);
    private static final int INK = Color.rgb(235, 242, 250);
    private static final int MUTED = Color.rgb(163, 181, 202);
    private static final int MINT = Color.rgb(83, 224, 183);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private LinearLayout browser;
    private LinearLayout playerPanel;
    private TextView subtitle, breadcrumb, empty, playingTitle, streamStatus;
    private Button builtIn, importButton, up;
    private EditText search;
    private RecyclerView list;
    private Rows adapter;
    private PlayerView playerView;
    private ExoPlayer player;
    private StreamEngine engine;
    private Catalog catalog;
    private File torrent;
    private String folder = "";
    private int loadGeneration;
    private int playGeneration;
    private boolean resumed;
    private boolean loading;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::goBack);
        }
        createUi();
        worker.execute(() -> {
            try { SafeFiles.deleteTree(new File(getFilesDir(), "active-stream")); }
            catch (IOException e) { main.post(() -> showError(e.getMessage())); }
        });
        loadBuiltin();
    }

    private void createUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        browser = column();
        browser.setPadding(dp(20), dp(12), dp(20), dp(8));
        root.addView(browser, match());
        TextView title = text("UrokStream", 28, INK);
        title.setTypeface(null, Typeface.BOLD);
        browser.addView(title);
        subtitle = text("Уроци от торент · избираш видео и гледаш", 14, MUTED);
        browser.addView(subtitle);
        LinearLayout actions = new LinearLayout(this);
        builtIn = button("Вградени уроци", v -> loadBuiltin());
        importButton = button("Отвори .torrent", v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            try { startActivityForResult(intent, PICK_TORRENT); }
            catch (android.content.ActivityNotFoundException e) { showError("На устройството няма файлов мениджър. Вградените уроци са достъпни."); }
        });
        actions.addView(builtIn, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(importButton, new LinearLayout.LayoutParams(0, dp(52), 1));
        browser.addView(actions);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("Търси урок, предмет или клас…");
        search.setContentDescription("Търсене във всички видео файлове");
        search.setTextSize(16);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        browser.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                main.removeCallbacks(filter);
                main.postDelayed(filter, 200);
            }
            public void afterTextChanged(Editable e) {}
        });
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        up = button("↑ Назад", v -> parentFolder());
        navigation.addView(up, new LinearLayout.LayoutParams(dp(110), dp(48)));
        breadcrumb = text("Зареждане на каталога…", 14, MUTED);
        breadcrumb.setMaxLines(2);
        navigation.addView(breadcrumb, new LinearLayout.LayoutParams(0, -2, 1));
        browser.addView(navigation);
        empty = text("", 16, MUTED);
        empty.setPadding(0, dp(12), 0, dp(12));
        browser.addView(empty);
        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null);
        adapter = new Rows();
        list.setAdapter(adapter);
        browser.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView footer = text("Изтегля се избраното видео. Кешът се изчиства при затваряне. Нужни са сийдъри.", 12, MUTED);
        footer.setPadding(0, dp(6), 0, dp(2));
        browser.addView(footer);

        playerPanel = column();
        playerPanel.setBackgroundColor(Color.BLACK);
        playerPanel.setVisibility(View.GONE);
        root.addView(playerPanel, match());
        LinearLayout playerHeader = new LinearLayout(this);
        playerHeader.setGravity(Gravity.CENTER_VERTICAL);
        playerHeader.addView(button("← Каталог", v -> stopPlayback(true)), new LinearLayout.LayoutParams(dp(130), dp(52)));
        playingTitle = text("", 16, INK);
        playingTitle.setMaxLines(2);
        playerHeader.addView(playingTitle, new LinearLayout.LayoutParams(0, -2, 1));
        playerPanel.addView(playerHeader);
        playerView = new PlayerView(this);
        playerView.setKeepScreenOn(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerPanel.addView(playerView, new LinearLayout.LayoutParams(-1, 0, 1));
        streamStatus = text("", 13, MINT);
        streamStatus.setPadding(dp(12), dp(4), dp(12), dp(8));
        playerPanel.addView(streamStatus);
    }

    private final Runnable filter = this::renderRows;

    private void loadBuiltin() {
        final int generation = beginLoad();
        worker.execute(() -> {
            File file = new File(getFilesDir(), "lessons.torrent");
            try {
                if (!file.isFile()) {
                    try (InputStream input = getAssets().open("lessons.torrent")) { copy(input, file); }
                }
                finishLoad(generation, file, Catalog.load(file));
            } catch (Exception | LinkageError e) { loadFailed(generation, e); }
        });
    }

    private int beginLoad() {
        loading = true;
        loadGeneration++;
        builtIn.setEnabled(false); importButton.setEnabled(false);
        subtitle.setText("Четене на .torrent метаданните…");
        return loadGeneration;
    }

    private void finishLoad(int generation, File file, Catalog loaded) {
        main.post(() -> {
            if (isDestroyed() || generation != loadGeneration) return;
            loading = false;
            torrent = file; catalog = loaded; folder = "";
            search.setText("");
            subtitle.setText(String.format(Locale.ROOT, "%s · %,d видеа · %.1f GiB общо", loaded.name,
                    loaded.videos.size(), loaded.totalSize / (1024.0 * 1024 * 1024)));
            builtIn.setEnabled(true); importButton.setEnabled(true);
            renderRows();
        });
    }

    private void loadFailed(int generation, Throwable error) {
        main.post(() -> {
            if (isDestroyed() || generation != loadGeneration) return;
            loading = false;
            builtIn.setEnabled(true); importButton.setEnabled(true);
            subtitle.setText(catalog == null ? "Каталогът не е зареден" : catalog.name);
            showError(message(error));
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_TORRENT || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        final int generation = beginLoad();
        worker.execute(() -> {
            File imported = new File(getFilesDir(), "import-" + System.nanoTime() + ".torrent");
            try {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Файлът не може да се отвори");
                    copy(input, imported);
                }
                Catalog loaded = Catalog.load(imported);
                // The worker is serial: no playback can be reading an older imported torrent here.
                File[] previous = getFilesDir().listFiles((dir, name) -> name.startsWith("import-") && name.endsWith(".torrent"));
                if (previous != null) for (File old : previous) if (!old.equals(imported)) old.delete();
                finishLoad(generation, imported, loaded);
            } catch (Exception | LinkageError e) { imported.delete(); loadFailed(generation, e); }
        });
    }

    private static void copy(InputStream input, File output) throws IOException {
        boolean complete = false;
        try (FileOutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 32L * 1024 * 1024) throw new IOException(".torrent файлът е по-голям от 32 MiB");
                out.write(buffer, 0, read);
            }
            complete = true;
        } finally { if (!complete) output.delete(); }
    }

    private void renderRows() {
        if (catalog == null) return;
        List<Catalog.Row> rows = catalog.rows(folder, search.getText().toString());
        adapter.replace(rows);
        breadcrumb.setText(search.length() > 0 ? "Резултати: " + rows.size() : folder.isEmpty() ? "Всички предмети" : folder);
        up.setEnabled(!folder.isEmpty() || search.length() > 0);
        empty.setText(rows.isEmpty() ? "Няма намерени видеа." : "");
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void parentFolder() {
        if (search.length() > 0) search.setText("");
        else if (!folder.isEmpty()) {
            String withoutSlash = folder.substring(0, folder.length() - 1);
            folder = withoutSlash.substring(0, withoutSlash.lastIndexOf('/') + 1);
        }
        renderRows();
    }

    private void play(VideoFile video) {
        stopPlayback(false);
        int generation = ++playGeneration;
        File selectedTorrent = torrent;
        StreamEngine selected = new StreamEngine(new File(getFilesDir(), "active-stream"));
        engine = selected;
        browser.setVisibility(View.GONE); playerPanel.setVisibility(View.VISIBLE);
        playingTitle.setText(video.name()); streamStatus.setText("Свързване със сийдърите…");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker.execute(() -> {
            try {
                selected.start(selectedTorrent, video);
                main.post(() -> {
                    if (!resumed || generation != playGeneration || engine != selected) return;
                    player = new ExoPlayer.Builder(this).setLoadControl(new DefaultLoadControl.Builder()
                            .setBufferDurationsMs(5_000, 25_000, 1_000, 2_000).build()).build();
                    playerView.setPlayer(player);
                    player.addListener(new Player.Listener() {
                        @Override public void onPlayerError(PlaybackException error) {
                            stopPlayback(true);
                            showError("Видеото не може да се пусне. " + message(error.getCause() == null ? error : error.getCause())
                                    + "\nПровери сийдърите, връзката и поддръжката на кодека на устройството.");
                        }
                        @Override public void onPlaybackStateChanged(int state) {
                            if (state == Player.STATE_ENDED) stopPlayback(true);
                        }
                    });
                    MediaItem media = MediaItem.fromUri(Uri.parse("torrent://video/" + video.index));
                    player.setMediaSource(new ProgressiveMediaSource.Factory(() -> new TorrentDataSource(selected, video))
                            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(0)).createMediaSource(media));
                    player.prepare(); player.play();
                    playerView.requestFocus(); playerView.showController();
                });
            } catch (Exception | LinkageError e) {
                main.post(() -> {
                    if (generation != playGeneration || isDestroyed()) return;
                    stopPlayback(true); showError(message(e));
                });
            }
        });
    }

    private void stopPlayback(boolean showCatalog) {
        playGeneration++;
        StreamEngine old = engine;
        engine = null;
        if (old != null) old.cancel(); // Unblock Media3 before release waits for its playback thread.
        if (player != null) {
            playerView.setPlayer(null); player.release(); player = null;
        }
        if (old != null) worker.execute(() -> {
            try { old.close(); }
            catch (IOException e) { main.post(() -> { if (resumed) showError(e.getMessage()); }); }
        });
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (showCatalog) {
            playerPanel.setVisibility(View.GONE); browser.setVisibility(View.VISIBLE);
            list.requestFocus();
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            StreamEngine current = engine;
            if (current != null && current.ready) worker.execute(() -> {
                String stats = current.stats();
                main.post(() -> { if (engine == current) streamStatus.setText(stats); });
            });
            if (resumed) main.postDelayed(this, 1000);
        }
    };
    @Override protected void onStart() { super.onStart(); resumed = true; main.post(tick); }
    @Override protected void onStop() {
        resumed = false; main.removeCallbacks(tick);
        stopPlayback(true);
        super.onStop();
    }
    @Override protected void onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdown(); super.onDestroy(); }
    @Override public void onBackPressed() { goBack(); }
    private void goBack() {
        if (engine != null) stopPlayback(true);
        else if (search.length() > 0 || !folder.isEmpty()) parentFolder();
        else finish();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (player != null && playerView.dispatchMediaKeyEvent(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private void showError(String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle("UrokStream").setMessage(message).setPositiveButton("Добре", null).show();
    }
    private static String message(Throwable e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }
    private TextView text(String value, int sp, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(sp); text.setTextColor(color); return text;
    }
    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setTextColor(INK); b.setTextSize(13); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setOnClickListener(click); return b;
    }

    private GradientDrawable tile(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(10));
        drawable.setStroke(dp(2), stroke); return drawable;
    }

    private final class Rows extends RecyclerView.Adapter<RowHolder> {
        private List<Catalog.Row> rows = new ArrayList<>();
        void replace(List<Catalog.Row> value) { rows = value; notifyDataSetChanged(); }
        @Override public RowHolder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout item = column(); item.setPadding(dp(14), dp(12), dp(14), dp(12));
            item.setFocusable(true); item.setClickable(true); item.setMinimumHeight(dp(72));
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, tile(Color.rgb(33, 68, 72), MINT));
            states.addState(new int[]{android.R.attr.state_pressed}, tile(Color.rgb(33, 68, 72), MINT));
            states.addState(new int[]{}, tile(Color.rgb(23, 36, 54), Color.TRANSPARENT));
            item.setBackground(states);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, -2); lp.bottomMargin = dp(6); item.setLayoutParams(lp);
            TextView title = text("", 16, INK); title.setMaxLines(3); item.addView(title);
            TextView detail = text("", 12, MUTED); detail.setMaxLines(2); item.addView(detail);
            return new RowHolder(item, title, detail);
        }
        @Override public void onBindViewHolder(RowHolder holder, int position) {
            Catalog.Row row = rows.get(position);
            holder.title.setText((row.video == null ? "▸  " : "▶  ") + row.title);
            holder.detail.setText(row.video == null ? row.count + " видеа" : String.format(Locale.ROOT, "%.1f MiB · %s", row.video.size / (1024.0 * 1024), row.video.path));
            holder.itemView.setOnClickListener(v -> {
                if (loading) return;
                if (row.video == null) { folder = row.folder; renderRows(); list.scrollToPosition(0); }
                else play(row.video);
            });
        }
        @Override public int getItemCount() { return rows.size(); }
    }
    private static final class RowHolder extends RecyclerView.ViewHolder {
        final TextView title, detail;
        RowHolder(View item, TextView title, TextView detail) { super(item); this.title = title; this.detail = detail; }
    }
}
