package bg.urokstream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

final class KidStyle {
    static final int BG = Color.rgb(248, 249, 255), INK = Color.rgb(39, 46, 76), MUTED = Color.rgb(113, 122, 149);
    static final int PURPLE = Color.rgb(117, 85, 216), MINT = Color.rgb(202, 244, 226);
    static final int[] COLORS = {0xFFFFE4B6, 0xFFDCE4FF, 0xFFD5F3E4, 0xFFFFDCE6, 0xFFD8F0FF, 0xFFE9DEFF};
    static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static int color(String subject) { return COLORS[Math.floorMod(subject.hashCode(), COLORS.length)]; }
    static String icon(String name) {
        if (name.equals("Математика")) return "123";
        if (name.contains("Български")) return "АБВ";
        if (name.contains("Литература")) return "📚";
        if (name.contains("География")) return "🌍";
        if (name.contains("История")) return "🏛";
        if (name.contains("Биология") || name.contains("природата")) return "🌱";
        if (name.contains("Химия")) return "🧪";
        if (name.contains("Физика")) return "🚀";
        if (name.contains("езици")) return "🌎";
        if (name.equals("ИТ")) return "💻";
        if (name.contains("Родинознание") || name.contains("обществото")) return "🏡";
        if (name.contains("Околен")) return "🌻";
        if (name.contains("развитие")) return "✨";
        return "🎒";
    }
    static TextView text(Context c, String value, int size, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }
    static GradientDrawable round(Context c, int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius));
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 3), stroke);
        return d;
    }
    static void interactive(View view, int color, int radius) {
        Context c = view.getContext();
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused}, round(c, color, radius, PURPLE));
        background.addState(new int[]{android.R.attr.state_pressed}, round(c, color, radius, PURPLE));
        background.addState(new int[]{}, round(c, color, radius, Color.TRANSPARENT));
        view.setBackground(background); view.setFocusable(true); view.setClickable(true);
        view.setOnFocusChangeListener((v, focus) -> v.animate().scaleX(focus ? 1.025f : 1).scaleY(focus ? 1.025f : 1).setDuration(120).start());
    }
    static Button button(Context c, String title, int color, View.OnClickListener click) {
        Button b = new Button(c); b.setText(title); b.setTextSize(15); b.setTextColor(INK); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        interactive(b, color, 18); b.setOnClickListener(click); return b;
    }

    /** A tiny original vector mascot, drawn at any density without bitmap memory. */
    static final class Owl extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Owl(Context c) { super(c); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        private void oval(Canvas c, int color, float l, float t, float r, float b) { p.setColor(color); c.drawOval(l,t,r,b,p); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.save();
            float scale = Math.min(getWidth(), getHeight()) / 140f;
            c.translate((getWidth() - 140*scale)/2, (getHeight() - 140*scale)/2); c.scale(scale, scale);
            oval(c, 0x207555D8, 20,118,126,135);
            oval(c, 0xFFFFCD64, 38,113,61,129); oval(c, 0xFFFFCD64, 82,113,105,129);
            oval(c, PURPLE, 25,26,118,123);
            p.setColor(PURPLE); Path ears = new Path(); ears.moveTo(29,53); ears.lineTo(25,13); ears.lineTo(57,32);
            ears.moveTo(88,32); ears.lineTo(120,13); ears.lineTo(115,56); c.drawPath(ears,p);
            oval(c, 0xFFD8CAFF, 15,64,43,110); oval(c, 0xFFD8CAFF, 101,64,129,110);
            oval(c, Color.WHITE, 35,38,73,82); oval(c, Color.WHITE, 72,38,110,82);
            oval(c, INK, 48,49,66,71); oval(c, INK, 79,49,97,71);
            oval(c, Color.WHITE, 52,51,58,57); oval(c, Color.WHITE, 83,51,89,57);
            oval(c, 0xFFFFB6BD, 34,73,48,81); oval(c, 0xFFFFB6BD, 97,73,111,81);
            p.setColor(0xFFFFCD64); Path beak = new Path(); beak.moveTo(64,76); beak.lineTo(81,76); beak.lineTo(72,87); beak.close(); c.drawPath(beak,p);
            p.setColor(0xFF8BE0C0); c.drawRoundRect(43,93,100,121,5,5,p);
            p.setColor(Color.WHITE); p.setStrokeWidth(3); c.drawLine(72,96,72,118,p); c.drawLine(49,101,64,101,p); c.drawLine(80,101,94,101,p);
            p.setColor(0xFFFFCD64); p.setTextSize(22); c.drawText("✦",4,34,p); c.drawText("✦",119,65,p);
            c.restore();
        }
    }
}
