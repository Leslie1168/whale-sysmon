package com.whale.sysmon.data;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import java.util.Locale;

/**
 * 屏幕信息：分辨率、密度、刷新率、HDR、色域、尺寸。
 */
public final class Disp {

    public int widthPx, heightPx;
    public int densityDpi;
    public float density;
    public float refreshRate;
    public float[] supportedRates;
    public float xdpi, ydpi;
    public String hdr = "不支持";
    public boolean wideColor;
    public float sizeInch;
    public int maxRefreshRate;

    public static Disp read(Context ctx) {
        Disp d = new Disp();
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            Display dp = wm.getDefaultDisplay();
            DisplayMetrics m = new DisplayMetrics();
            dp.getRealMetrics(m);
            d.widthPx = m.widthPixels;
            d.heightPx = m.heightPixels;
            d.densityDpi = m.densityDpi;
            d.density = m.density;
            d.xdpi = m.xdpi;
            d.ydpi = m.ydpi;
            d.refreshRate = dp.getRefreshRate();

            if (d.xdpi > 0 && d.ydpi > 0) {
                double w = d.widthPx / (double) d.xdpi;
                double h = d.heightPx / (double) d.ydpi;
                d.sizeInch = (float) Math.sqrt(w * w + h * h);
            }

            if (Build.VERSION.SDK_INT >= 23) {
                Display.Mode[] modes = dp.getSupportedModes();
                d.supportedRates = new float[modes.length];
                float mx = 0;
                for (int i = 0; i < modes.length; i++) {
                    d.supportedRates[i] = modes[i].getRefreshRate();
                    if (modes[i].getRefreshRate() > mx) mx = modes[i].getRefreshRate();
                }
                d.maxRefreshRate = Math.round(mx);
                d.wideColor = dp.isWideColorGamut();
                Display.HdrCapabilities h = dp.getHdrCapabilities();
                if (h != null && h.getSupportedHdrTypes() != null
                        && h.getSupportedHdrTypes().length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int t : h.getSupportedHdrTypes()) {
                        if (sb.length() > 0) sb.append(" / ");
                        sb.append(hdrName(t));
                    }
                    d.hdr = sb.toString();
                }
            }
        } catch (Throwable t) {
            // 保持默认
        }
        return d;
    }

    private static String hdrName(int t) {
        switch (t) {
            case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION: return "Dolby Vision";
            case Display.HdrCapabilities.HDR_TYPE_HDR10: return "HDR10";
            case Display.HdrCapabilities.HDR_TYPE_HLG: return "HLG";
            case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS: return "HDR10+";
            default: return "Type" + t;
        }
    }

    public String resText() {
        return widthPx + " × " + heightPx;
    }

    public String densityText() {
        return densityDpi + " dpi  (×" + String.format(Locale.US, "%.2f", density) + ")";
    }

    public String refreshText() {
        return String.format(Locale.US, "%.0f Hz", refreshRate);
    }

    public String refreshListText() {
        if (supportedRates == null || supportedRates.length == 0) return "--";
        java.util.TreeSet<Integer> set = new java.util.TreeSet<Integer>();
        for (float f : supportedRates) set.add(Math.round(f));
        StringBuilder sb = new StringBuilder();
        for (int v : set) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(v);
        }
        return sb.append(" Hz").toString();
    }

    public String sizeText() {
        return sizeInch > 0 ? String.format(Locale.US, "%.2f 英寸", sizeInch) : "--";
    }

    /** 宽高比，如 19.5:9 */
    public String ratioText() {
        if (widthPx <= 0 || heightPx <= 0) return "--";
        int a = Math.min(widthPx, heightPx);
        int b = Math.max(widthPx, heightPx);
        int g = gcd(a, b);
        return (b / g) + ":" + (a / g);
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int t = a % b; a = b; b = t; }
        return Math.max(1, a);
    }
}
