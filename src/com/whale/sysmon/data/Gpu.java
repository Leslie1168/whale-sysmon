package com.whale.sysmon.data;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.util.Locale;

/**
 * GPU：频率与（能拿到的话）负载。不同 SoC 路径差异极大，这里按候选路径逐个探测。
 * 拿不到就如实显示 --，不编造。
 */
public final class Gpu {

    public long curKhz = -1;
    public long minKhz = -1;
    public long maxKhz = -1;
    public String model = "--";
    public String vendor = "--";
    public String gles = "--";
    public float load = Float.NaN;   // 0..1，拿不到为 NaN
    public String freqPath = "";     // 命中的路径（调试用）
    public String loadPath = "";

    private static final String[] FREQ_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/kernel/gpu/gpu_clock",
            "/sys/kernel/gpu/gpu_max_clock",
            "/sys/kernel/tegra_gpu/gpu_rate",
            "/sys/class/misc/mali0/device/devfreq/cur_freq",
            "/sys/class/devfreq/gpufreq/cur_freq",
            "/sys/devices/platform/gpusysdev.0/devfreq/gpufreq/cur_freq",
    };

    private static final String[] LOAD_PATHS = {
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/kernel/gpu/gpu_busy",
            "/sys/class/misc/mali0/device/utilisation",
            "/sys/class/devfreq/gpufreq/gpu_load",
    };

    private static boolean sLogged = false;

    public static Gpu read(Context ctx) {
        Gpu g = new Gpu();

        // 频率：先看能用 devfreq 目录枚举到的（Kirin/MTK 常见）
        g.curKhz = probeFreq(g);
        if (g.curKhz <= 0) {
            // devfreq 枚举兜底
            String[] dirs = Sys.list("/sys/class/devfreq");
            for (String d : dirs) {
                if (d == null) continue;
                String low = d.toLowerCase(Locale.US);
                if (low.contains("gpu") || low.contains("mali") || low.contains("kgsl")) {
                    long v = Sys.readLong("/sys/class/devfreq/" + d + "/cur_freq");
                    if (v > 0) {
                        g.curKhz = toKhz(v);
                        g.freqPath = "/sys/class/devfreq/" + d + "/cur_freq";
                        long mx = Sys.readLong("/sys/class/devfreq/" + d + "/max_freq");
                        if (mx > 0) g.maxKhz = toKhz(mx);
                        long mn = Sys.readLong("/sys/class/devfreq/" + d + "/min_freq");
                        if (mn > 0) g.minKhz = toKhz(mn);
                        break;
                    }
                }
            }
        }

        // 负载
        g.load = probeLoad(g);

        // 型号：GLES 上下文里的 RENDERER 最准，但要 GL 线程；这里用配置文件兜底
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            android.content.pm.ConfigurationInfo ci = am.getDeviceConfigurationInfo();
            if (ci != null) g.gles = "OpenGL ES " + ci.getGlEsVersion();
        } catch (Throwable ignored) {}
        g.model = gpuModelFromProps();
        g.vendor = gpuVendorFromProps(g.model);

        if (!sLogged) {
            sLogged = true;
            Log.d("SYS_MON_PROBE", "gpu freq=" + g.curKhz + " path=" + g.freqPath
                    + " | load=" + g.load + " path=" + g.loadPath
                    + " | model=" + g.model);
        }
        return g;
    }

    private static long probeFreq(Gpu g) {
        for (String p : FREQ_PATHS) {
            long v = Sys.readLong(p);
            if (v > 0) {
                g.freqPath = p;
                if (g.maxKhz <= 0) {
                    String dir = p.substring(0, p.lastIndexOf('/') + 1);
                    long mx = Sys.readLong(dir + "max_freq");
                    if (mx <= 0) mx = Sys.readLong(dir + "max_gpuclk");
                    if (mx > 0) g.maxKhz = toKhz(mx);
                    long mn = Sys.readLong(dir + "min_freq");
                    if (mn > 0) g.minKhz = toKhz(mn);
                }
                return toKhz(v);
            }
        }
        return -1;
    }

    /** devfreq / kgsl 的 *_freq 单位是 Hz，cpufreq 才是 kHz。统一成 kHz。 */
    private static long toKhz(long v) {
        if (v <= 0) return v;
        return v > 1000000L ? v / 1000L : v;
    }

    private static float probeLoad(Gpu g) {
        for (String p : LOAD_PATHS) {
            String s = Sys.readLine(p);
            if (s.isEmpty()) continue;
            try {
                if (s.contains(" ")) {
                    // kgsl 的 gpubusy 是 "busy total"
                    String[] parts = s.trim().split("\\s+");
                    long busy = Long.parseLong(parts[0]);
                    long total = Long.parseLong(parts[1]);
                    if (total > 0) {
                        g.loadPath = p;
                        return Math.max(0f, Math.min(1f, (float) busy / (float) total));
                    }
                } else {
                    double d = Double.parseDouble(s.trim());
                    if (d > 1.0 && d <= 100.0) d = d / 100.0;
                    g.loadPath = p;
                    return (float) Math.max(0, Math.min(1, d));
                }
            } catch (Throwable ignored) {}
        }
        return Float.NaN;
    }

    private static String gpuModelFromProps() {
        String m = Sys.readLine("/sys/class/misc/mali0/device/gpuinfo");
        if (m.isEmpty()) m = Sys.prop("ro.hardware.egl", "");
        if (m.isEmpty()) m = Sys.firstProp("", "ro.gpu.model", "ro.board.platform");
        if (m.isEmpty()) m = "--";
        return m;
    }

    private static String gpuVendorFromProps(String model) {
        String s = model.toLowerCase(Locale.US);
        if (s.startsWith("mali") || s.contains("mali")) return "ARM";
        if (s.contains("adreno")) return "Qualcomm";
        if (s.contains("powervr") || s.contains("rogue")) return "Imagination";
        if (s.contains("immortalis")) return "ARM";
        if (s.contains("kirin") || s.contains("maleoon")) return "Huawei";
        return "--";
    }

    public void refresh() {
        long v = Sys.readLong(freqPath.isEmpty() ? "" : freqPath);
        if (v > 0) curKhz = toKhz(v);
        float l = probeLoad(new Gpu());
        if (!Float.isNaN(l)) load = l;
    }

    public String freqText() {
        return curKhz > 0 ? Sys.hz(curKhz) : "--";
    }

    public String rangeText() {
        if (maxKhz <= 0) return "--";
        return Sys.hz(minKhz > 0 ? minKhz : 0) + " - " + Sys.hz(maxKhz);
    }

    public float loadNorm() {
        return Float.isNaN(load) ? 0f : load;
    }

    public String loadText() {
        return Float.isNaN(load) ? "--" : String.format(Locale.US, "%.1f%%", load * 100);
    }
}
