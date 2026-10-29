package com.whale.sysmon.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 纯代码构建界面用的小工具：圆角卡、文字、指标块。
 */
public final class Ui {

    private Ui() {}

    public static final Typeface NUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    public static final Typeface NUM_BOLD = Typeface.create("sans-serif-medium", Typeface.BOLD);
    public static final Typeface HEAVY = Typeface.create("sans-serif-black", Typeface.NORMAL);

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 全局字号缩放：一处放大，全应用生效。 */
    public static final float SCALE = 1.26f;

    public static float sp(float designSp) {
        return designSp * SCALE;
    }

    public static GradientDrawable round(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1f), stroke);
        return d;
    }

    public static GradientDrawable pill(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) d.setStroke(dp(c, 1f), stroke);
        return d;
    }

    /** 标准卡片：圆角 18dp，深靛底，1px 描边。 */
    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, Palette.CARD, Palette.STROKE, 18f));
        int p = dp(c, 16);
        l.setPadding(p, dp(c, 14), p, dp(c, 14));
        return l;
    }

    public static LinearLayout chartCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(c, Palette.CARD_DEEP, Palette.STROKE, 16f));
        int p = dp(c, 14);
        l.setPadding(p, dp(c, 12), p, dp(c, 10));
        return l;
    }

    public static TextView tv(Context c, String text, float spSize, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(spSize));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView num(Context c, String text, float spSize, int color) {
        return tv(c, text, spSize, color, NUM_BOLD);
    }

    public static TextView label(Context c, String text) {
        return tv(c, text, 11f, Palette.TEXT_FAINT, Typeface.DEFAULT);
    }

    public static TextView sectionTitle(Context c, String text) {
        TextView t = tv(c, text, 13f, Palette.TEXT_DIM, Typeface.DEFAULT);
        t.setPadding(dp(c, 4), 0, 0, 0);
        return t;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpTop(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    public static LinearLayout.LayoutParams lpTopW(Context c, int topDp, int w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                w, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    public static LinearLayout.LayoutParams weight(int weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        return lp;
    }

    /** 指标块：大数字 + 小标签，等分排列用。 */
    public static LinearLayout metric(Context c, String value, String caption, int color) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView val = num(c, value, 22f, color);
        val.setGravity(Gravity.CENTER);
        TextView cap = tv(c, caption, 11f, Palette.TEXT_FAINT, Typeface.DEFAULT);
        cap.setGravity(Gravity.CENTER);
        v.addView(val, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams capLp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = dp(c, 3);
        v.addView(cap, capLp);
        return v;
    }

    /** 横向进度条（圆角），返回容器；内部进度条用 setTag 找。 */
    public static LinearLayout progressBar(Context c, int trackColor, int barColor, float heightDp) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = round(c, trackColor, 0, heightDp / 2f);
        wrap.setBackground(track);
        View bar = new View(c);
        bar.setBackground(round(c, barColor, 0, heightDp / 2f));
        bar.setTag("bar");
        wrap.addView(bar, new LinearLayout.LayoutParams(0, dp(c, heightDp), 0f));
        wrap.setTag("wrap");
        return wrap;
    }

    public static void setProgress(LinearLayout barHolder, float ratio) {
        if (barHolder == null) return;
        View bar = barHolder.findViewWithTag("bar");
        if (bar == null) return;
        float r = Math.max(0f, Math.min(1f, ratio));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
        lp.width = 0;
        lp.weight = Math.max(0.0001f, r);
        bar.setLayoutParams(lp);
        // 剩余空白用同权重占位需要在父容器加 spacer；这里用固定宽度方案
        int full = barHolder.getWidth();
        if (full > 0) {
            lp.weight = 0;
            lp.width = (int) (full * r);
            bar.setLayoutParams(lp);
        }
    }
}
