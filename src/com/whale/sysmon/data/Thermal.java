package com.whale.sysmon.data;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.List;

/**
 * 温度：扫描 /sys/class/thermal/thermal_zone*，按 zone 名归类。
 * 单位为 0.1℃ 或 0.001℃ 因内核而异，用电池温度交叉校准。
 */
public final class Thermal {

    public static final class Zone {
        public String name = "";
        public float celsius = Float.NaN;
        public String key = "";       // 归类后的短名
    }

    public final List<Zone> zones = new ArrayList<Zone>();    // sysfs 全部
    public final List<Zone> normalized = new ArrayList<Zone>(); // thermalservice 规范名
    public float max = Float.NaN;
    public float avg = Float.NaN;
    public String maxName = "";

    private static boolean sLogged = false;
    private static long sLastHighLog = 0;

    public static Thermal read() {
        Thermal t = new Thermal();
        String base = "/sys/class/thermal/";
        String[] dirs = Sys.list("/sys/class/thermal");
        List<String> names = new ArrayList<String>();
        for (String d : dirs) {
            if (d != null && d.startsWith("thermal_zone")) names.add(d);
        }
        Collections.sort(names);
        for (String d : names) {
            String ztype = Sys.readLine(base + d + "/type");
            long raw = Sys.readLong(base + d + "/temp");
            if (raw == -1) continue;
            String zname = ztype.isEmpty() ? d : ztype;
            if (!isThermalName(zname)) continue;
            Zone z = new Zone();
            z.name = zname;
            z.celsius = normalize(raw);
            z.key = classify(z.name);
            if (Float.isNaN(z.celsius)) continue;
            t.zones.add(z);
        }
        float mx = Float.NaN, sum = 0;
        int n = 0;
        float socMax = Float.NaN;
        String socName = "";
        for (Zone z : t.zones) {
            if (z.celsius > 105f || z.celsius < -20f) continue;  // 过滤明显异常
            if ("CPU".equals(z.key) || "GPU".equals(z.key)) {
                if (Float.isNaN(socMax) || z.celsius > socMax) {
                    socMax = z.celsius;
                    socName = z.name;
                }
            }
            if (Float.isNaN(mx) || z.celsius > mx) {
                mx = z.celsius;
                t.maxName = z.name;
            }
            sum += z.celsius;
            n++;
        }
        // 有 Shizuku 时优先用 thermalservice 的规范名（gpu / cluster0-2 / Battery）
        List<ShellData.Zone> norm = ShellData.thermalZonesCached();
        if (norm != null && !norm.isEmpty()) {
            t.normalized.clear();
            for (ShellData.Zone z : norm) {
                Zone nz = new Zone();
                nz.name = z.name;
                nz.celsius = z.celsius;
                nz.key = classify(z.name);
                t.normalized.add(nz);
            }
        }

        // 优先报核心（CPU/GPU）温度，否则报全局最高（会含 modem 等）
        if (!Float.isNaN(socMax)) {
            t.max = socMax;
            t.maxName = socName;
        } else {
            t.max = mx;
        }
        t.avg = n > 0 ? sum / n : Float.NaN;

        // 有 thermalservice 规范温度时，用它来定"最高温度"（更准，不会把 modem 当 CPU）
        if (!t.normalized.isEmpty()) {
            float nmx = Float.NaN;
            String nn = "";
            for (Zone z : t.normalized) {
                if (!"CPU".equals(z.key) && !"GPU".equals(z.key)) continue;
                if (Float.isNaN(nmx) || z.celsius > nmx) {
                    nmx = z.celsius;
                    nn = z.name;
                }
            }
            if (!Float.isNaN(nmx)) {
                t.max = nmx;
                t.maxName = nn;
            }
        }

        java.util.Collections.sort(t.zones, new java.util.Comparator<Zone>() {
            @Override public int compare(Zone a, Zone b) {
                return Float.compare(b.celsius, a.celsius);
            }
        });

        // 诊断：最高温超过 60℃ 时列出所有高温项，便于定位异常传感器
        if (t.max > 60f && System.currentTimeMillis() - sLastHighLog > 10000L) {
            sLastHighLog = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder("HIGH max=" + t.max
                    + " from=" + t.maxName + " zones=" + t.zones.size() + " |");
            int shown = 0;
            for (Zone z : t.zones) {
                if (z.celsius < 45f) continue;
                sb.append(' ').append(z.name).append('=').append(z.celsius);
                if (++shown >= 12) break;
            }
            Log.d("SYS_MON_T", sb.toString());
        }

        if (!sLogged) {
            sLogged = true;
            StringBuilder sb = new StringBuilder("thermal zones=" + t.zones.size()
                    + " max=" + t.max + " (" + t.maxName + ")\n");
            for (int i = 0; i < Math.min(12, t.zones.size()); i++) {
                Zone z = t.zones.get(i);
                sb.append("  ").append(z.name).append('=').append(z.celsius).append('\n');
            }
            Log.d("SYS_MON_PROBE", sb.toString());
        }
        return t;
    }

    /**
     * 常见内核单位不同，统一到摄氏度。超过 3000 认为 0.001℃，超过 120 认为 0.1℃。
     * 换算后不在 1~150℃ 的一律判无效 —— 高通的 `soc` 节点返回的是电量百分比(97)，
     * 会被误当成 97℃，这类必须在名字层就挡掉。
     */
    private static float normalize(long raw) {
        if (raw <= 0) return Float.NaN;
        float c;
        if (raw > 3000) c = raw / 1000f;
        else if (raw > 120) c = raw / 10f;
        else c = raw;
        if (c < 1f || c > 150f) return Float.NaN;
        return c;
    }

    /** 名字层过滤：这些根本不是温度传感器。 */
    private static boolean isThermalName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(java.util.Locale.US).trim();
        if (n.equals("soc")) return false;                    // 电量百分比
        if (n.contains("ibat") || n.contains("vbat")) return false;  // 电流 / 电压
        if (n.contains("bcl") || n.contains("vph")) return false;    // 各种限值
        if (n.contains("lvl")) return false;
        if (n.contains("current") || n.contains("voltage")) return false;
        return true;
    }

    private static String classify(String name) {
        String n = name.toLowerCase(java.util.Locale.US);
        if (n.contains("cpu") || n.contains("soc") || n.contains("tsens")) return "CPU";
        if (n.contains("gpu") || n.contains("kgsl") || n.contains("mali")) return "GPU";
        if (n.contains("batt") || n.contains("chg")) return "Battery";
        if (n.contains("skin") || n.contains("shell") || n.contains("board") || n.contains("pa")) return "Skin";
        if (n.contains("charger") || n.contains("usb")) return "Charger";
        return "Other";
    }

    public String maxText() {
        return Float.isNaN(max) ? "--" : String.format(java.util.Locale.US, "%.1f ℃", max);
    }

    public String avgText() {
        return Float.isNaN(avg) ? "--" : String.format(java.util.Locale.US, "%.1f ℃", avg);
    }

    /** 归一化到 0..1，用于曲线（20℃~90℃ 映射）。 */
    public float norm() {
        if (Float.isNaN(max)) return 0f;
        float v = (max - 20f) / 70f;
        return Math.max(0f, Math.min(1f, v));
    }
}
