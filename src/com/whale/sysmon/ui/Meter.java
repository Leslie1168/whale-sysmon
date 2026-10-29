package com.whale.sysmon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 圆角进度条（自绘，避免嵌套 weight 的布局坑）。
 */
public class Meter extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float ratio = 0f;
    private float thicknessDp = 7f;
    private int color = Palette.CYAN;

    public Meter(Context c) {
        super(c);
        track.setColor(Palette.alpha(Palette.TEXT_DIM, 0.18f));
        bar.setColor(color);
    }

    public void setRatio(float r) {
        ratio = Math.max(0f, Math.min(1f, r));
        invalidate();
    }

    public void setBarColor(int c) {
        color = c;
        bar.setColor(c);
        invalidate();
    }

    public void setThicknessDp(float dp) {
        thicknessDp = dp;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = Ui.dp(getContext(), thicknessDp);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float h = getHeight();
        float r = h / 2f;
        rect.set(0, 0, getWidth(), h);
        cv.drawRoundRect(rect, r, r, track);
        if (ratio > 0.001f) {
            rect.set(0, 0, Math.max(h, getWidth() * ratio), h);
            cv.drawRoundRect(rect, r, r, bar);
        }
    }
}
