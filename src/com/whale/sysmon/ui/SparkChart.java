package com.whale.sysmon.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * 滚动折线图：网格 + 折线 + 渐变压填。
 * 开启 smooth 后按采样间隔做逐帧插值，曲线是连续滑动而不是每秒跳一格，
 * 这样 90Hz 屏幕上才看得出顺滑。
 */
public class SparkChart extends View {

    private static final int CAP = 120;

    private final float[] data = new float[CAP];
    private int count = 0;
    private int head = 0;

    private int color = Palette.CYAN;
    private float minShown = 0f;
    private float maxShown = 1f;
    private boolean showGrid = true;
    private float lineWidth = 2.2f;

    /* 平滑滚动 */
    private boolean smooth = true;
    private long intervalMs = 1000L;
    private long lastPushAt = 0L;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path fillPath = new Path();

    public SparkChart(Context c) { this(c, null); }

    public SparkChart(Context c, AttributeSet a) {
        super(c, a);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(color);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(Palette.GRID);
        gridPaint.setStrokeWidth(dp(1f));

        fillPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    public void setChartColor(int c) {
        this.color = c;
        linePaint.setColor(c);
        invalidate();
    }

    public void setSmooth(boolean on) {
        smooth = on;
        invalidate();
    }

    /** 采样间隔，用于计算插值速度。 */
    public void setIntervalMs(long ms) {
        if (ms > 0) intervalMs = ms;
    }

    /** 把 0..1 的值推进窗口。 */
    public void push(float v) {
        if (Float.isNaN(v)) return;
        v = Math.max(0f, Math.min(1f, v));
        if (count < CAP) {
            data[(head + count) % CAP] = v;
            count++;
        } else {
            data[head] = v;
            head = (head + 1) % CAP;
        }
        lastPushAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public void fill(float v) {
        for (int i = 0; i < CAP; i++) push(v);
    }

    public void clear() {
        count = 0;
        head = 0;
        invalidate();
    }

    public int size() { return count; }

    public float last() {
        if (count == 0) return Float.NaN;
        return data[(head + count - 1) % CAP];
    }

    public float[] stats() {
        if (count == 0) return null;
        float sum = 0, mx = -1, mn = 2;
        for (int i = 0; i < count; i++) {
            float v = data[(head + i) % CAP];
            sum += v;
            if (v > mx) mx = v;
            if (v < mn) mn = v;
        }
        return new float[]{sum / count, mx, mn};
    }

    public void setShowGrid(boolean b) {
        showGrid = b;
        invalidate();
    }

    public void setLineWidthDp(float v) {
        lineWidth = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padL = dp(1f), padR = dp(1f);
        float plotW = w - padL - padR;

        if (showGrid) {
            int vLines = 6;
            for (int i = 0; i <= vLines; i++) {
                float x = padL + plotW * i / vLines;
                cv.drawLine(x, 0, x, h, gridPaint);
            }
            int hLines = 3;
            for (int i = 0; i <= hLines; i++) {
                float y = h * i / hLines;
                cv.drawLine(padL, y, padL + plotW, y, gridPaint);
            }
        }

        if (count >= 2) {
            linePaint.setStrokeWidth(dp(lineWidth));
            path.reset();
            fillPath.reset();

            float stepX = plotW / (CAP - 1);
            // 窗口满了之后按帧插值整体左移，形成连续滚动
            float offset = 0f;
            if (smooth && count >= CAP && lastPushAt > 0) {
                long dt = SystemClock.uptimeMillis() - lastPushAt;
                offset = Math.max(0f, Math.min(1f, dt / (float) intervalMs));
            }

            boolean started = false;
            float firstX = 0, lastX = 0, lastY = 0;
            for (int i = 0; i < count; i++) {
                float v = data[(head + i) % CAP];
                float norm = (v - minShown) / Math.max(0.0001f, (maxShown - minShown));
                norm = Math.max(0f, Math.min(1f, norm));
                float x = padL + stepX * (i - offset);
                float y = h - dp(2f) - norm * (h - dp(4f));
                if (!started) {
                    path.moveTo(x, y);
                    fillPath.moveTo(x, h);
                    fillPath.lineTo(x, y);
                    firstX = x;
                    started = true;
                } else {
                    path.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
                lastX = x;
                lastY = y;
            }

            if (started) {
                fillPath.lineTo(lastX, h);
                fillPath.lineTo(firstX, h);
                fillPath.close();
                fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                        Palette.alpha(color, 0.30f), Palette.alpha(color, 0.02f),
                        Shader.TileMode.CLAMP));
                cv.drawPath(fillPath, fillPaint);

                cv.drawPath(path, linePaint);

                glowPaint.setColor(Palette.alpha(color, 0.35f));
                cv.drawCircle(lastX, lastY, dp(4.5f), glowPaint);
                glowPaint.setColor(color);
                cv.drawCircle(lastX, lastY, dp(2.2f), glowPaint);
            }
        }

        // 只在可见时持续重绘，省电
        if (smooth && isShown()) {
            postInvalidateOnAnimation();
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
