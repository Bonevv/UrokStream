package bg.urokstream;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

final class SchoolStyle {
    static final int BLUE=0xFF00A6F6,YELLOW=0xFFFFE548,INK=0xFF45506D,MUTED=0xFF8299AA,BG=0xFFF0F4F5,BORDER=0xFFD3D8DD;
    static int dp(Context c,int n) { return Math.round(n*c.getResources().getDisplayMetrics().density); }
    static TextView text(Context c,String value,int size,int color,boolean bold) {
        TextView t=new TextView(c); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-condensed",bold?Typeface.BOLD:Typeface.NORMAL)); return t;
    }
    static GradientDrawable round(Context c,int color,int radius,int border,int stroke) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); if(stroke>0)d.setStroke(dp(c,stroke),border); return d;
    }
    static void interactive(View v,int color,int radius,int accent) {
        StateListDrawable state=new StateListDrawable(); Context c=v.getContext();
        state.addState(new int[]{android.R.attr.state_focused},round(c,0xFFE7F5FF,radius,accent,3));
        state.addState(new int[]{android.R.attr.state_pressed},round(c,0xFFE7F5FF,radius,accent,3));
        state.addState(new int[]{},round(c,color,radius,BORDER,color==Color.TRANSPARENT?0:1));
        v.setBackground(state); v.setFocusable(true); v.setClickable(true);
    }
    static Button button(Context c,String label,int color,int ink,View.OnClickListener click) {
        Button b=new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(17); b.setTextColor(ink);
        b.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(c,10),0,dp(c,10),0); interactive(b,color,22,BLUE); b.setOnClickListener(click); return b;
    }
    static int color(String name) {
        if(name.equals("Математика"))return 0xFFB33C78;
        if(name.contains("Български"))return 0xFF299DDD;
        if(name.equals("Литература"))return 0xFF00C7B2;
        if(name.contains("Химия"))return 0xFF9339B4;
        if(name.equals("Физика"))return 0xFF1E87C4;
        if(name.contains("Биология")||name.contains("природата"))return 0xFF0BB764;
        if(name.equals("История"))return 0xFFE1A900;
        if(name.equals("География"))return 0xFF00AE9D;
        if(name.contains("моделиране")||name.contains("Информ"))return 0xFF65ACC8;
        return 0xFF6369C7;
    }
    static final class SubjectIcon extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private String name=""; private int color=BLUE;
        SubjectIcon(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void subject(String name){this.name=name;color=color(name);invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);
            float s=Math.min(getWidth(),getHeight())/48f;c.save();c.scale(s,s);
            if(name.equals("География")){c.drawCircle(24,24,17,p);c.drawOval(15,7,33,41,p);c.drawLine(7,24,41,24,p);c.drawLine(11,14,37,14,p);c.drawLine(11,34,37,34,p);}
            else if(name.contains("Биология")||name.contains("природата")){c.drawOval(12,7,36,38,p);c.drawLine(24,11,24,44,p);c.drawLine(24,29,34,19,p);}
            else if(name.equals("Литература")){c.drawRoundRect(10,6,36,42,2,2,p);c.drawLine(17,6,17,42,p);c.drawLine(23,7,23,22,p);c.drawLine(23,22,29,18,p);c.drawLine(29,18,33,22,p);c.drawLine(33,22,33,7,p);}
            else if(name.contains("Информ")||name.contains("моделиране")||name.contains("Програмиране")){c.drawRect(5,9,43,35,p);c.drawLine(24,35,24,43,p);c.drawLine(13,43,35,43,p);p.setStyle(Paint.Style.FILL);p.setTextSize(15);p.setTypeface(Typeface.MONOSPACE);c.drawText("</>",10,28,p);}
            else {p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(name.equals("Български език")?28:38);c.drawText(name.equals("Математика")?"π":name.contains("Български")?"аб":name.equals("Химия")?"⚗":name.equals("Физика")?"⚡":name.equals("История")?"▥":"✦",24,36,p);p.setTextAlign(Paint.Align.LEFT);}
            c.restore();
        }
    }
    static final class Flow extends ViewGroup {
        private final int gap;
        Flow(Context c){super(c);gap=dp(c,6);}
        @Override protected void onMeasure(int ws,int hs){
            int width=MeasureSpec.getSize(ws),x=0,y=0,line=0;
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
                measureChild(child,ws,hs);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                if(x>0&&x+w>width){x=0;y+=line+gap;line=0;}x+=w+gap;line=Math.max(line,h);
            }
            setMeasuredDimension(width,y+line);
        }
        @Override protected void onLayout(boolean change,int l,int t,int r,int b){
            int x=0,y=0,line=0,width=r-l;
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);if(child.getVisibility()==GONE)continue;
                int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                if(x>0&&x+w>width){x=0;y+=line+gap;line=0;}child.layout(x,y,x+w,y+h);x+=w+gap;line=Math.max(line,h);
            }
        }
        @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
    }
}
