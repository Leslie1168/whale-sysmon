package com.whale.sysmon.data;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只有拿到 Shizuku(shell) 权限才读得到的数据，统一在这里解析。
 * 没授权时所有方法返回空/默认值，调用方按"不可用"处理。
 */
public final class ShellData {

    private static final String TAG = "SYS_MON_SHELL";

    public static final class Proc {
        public String name = "";
        public int pct = 0;
        public String raw = "";
    }

    public static final class Zone {
        public String name = "";
        public float celsius = 0;
    }

    private ShellData() {}

    public static boolean ready() {
        try {
            return ShizukuBridge.connected()
                    && ShizukuBridge.permission() == ShizukuBridge.PERM_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /* ------------------------- 进程 CPU 排行 ------------------------- */

    private static final Pattern PROC_LINE =
            Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)%\\s+(\\d+)/(\\S+)");

    /** 返回占用最高的前 n 个进程。 */
    public static List<Proc> topProcs(int n) {
        List<Proc> out = new ArrayList<Proc>();
        if (!ready()) return out;
        try {
            String txt = ShizukuBridge.dumpsys("cpuinfo");
            if (txt.isEmpty()) return out;
            for (String line : txt.split("\n")) {
                Matcher m = PROC_LINE.matcher(line);
                if (m.find()) {
                    Proc p = new Proc();
                    p.pct = (int) Float.parseFloat(m.group(1));
                    String nm = m.group(3);
                    int colon = nm.indexOf(':');
                    if (colon > 0) nm = nm.substring(0, colon);
                    p.name = nm;
                    p.raw = line.trim();
                    // 系统进程名很长，取短名
                    if (p.name.length() > 24) {
                        p.name = p.name.substring(0, 22) + "…";
                    }
                    out.add(p);
                    if (out.size() >= n) break;
                }
            }
            // 按占比降序
            java.util.Collections.sort(out, new java.util.Comparator<Proc>() {
                @Override public int compare(Proc a, Proc b) { return b.pct - a.pct; }
            });
        } catch (Throwable t) {
            Log.d(TAG, "topProcs failed " + t);
        }
        return out;
    }

    /** 系统平均负载那行。 */
    public static String loadLine() {
        if (!ready()) return "";
        String txt = ShizukuBridge.dumpsys("cpuinfo");
        if (txt.isEmpty()) return "";
        for (String line : txt.split("\n")) {
            String t = line.trim();
            if (t.startsWith("Load:")) return t;
        }
        return "";
    }

    /* ---------------------------- 电池 ---------------------------- */

    /** dumpsys battery 里的某个字段。 */
    public static String batteryField(String key) {
        if (!ready()) return "";
        try {
            String txt = ShizukuBridge.dumpsys("battery");
            if (txt.isEmpty()) return "";
            for (String line : txt.split("\n")) {
                String t = line.trim();
                if (t.toLowerCase().startsWith(key.toLowerCase() + ":")) {
                    return t.substring(t.indexOf(':') + 1).trim();
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /** 电荷量，单位 µAh。 */
    public static long chargeCounterUah() {
        String s = batteryField("Charge counter");
        if (s.isEmpty()) return -1;
        try { return Long.parseLong(s); } catch (Throwable t) { return -1; }
    }

    /** 电池健康码（2=good）。 */
    public static int batteryHealth() {
        String s = batteryField("health");
        if (s.isEmpty()) return -1;
        try { return Integer.parseInt(s); } catch (Throwable t) { return -1; }
    }

    /* ---------------------------- 温度 ---------------------------- */

    private static final Pattern TEMP_LINE =
            Pattern.compile("Temperature\\{mValue=([\\-0-9.]+),\\s*mType=(\\d+),\\s*mName=([^,}]+)");

    private static List<Zone> sThermalCache;
    private static long sThermalAt = 0;

    /** 带 3 秒缓存的版本，供每秒采样使用。 */
    public static List<Zone> thermalZonesCached() {
        long now = System.currentTimeMillis();
        if (sThermalCache != null && now - sThermalAt < 3000L) return sThermalCache;
        List<Zone> z = thermalZones();
        if (!z.isEmpty()) {
            sThermalCache = z;
            sThermalAt = now;
        }
        return sThermalCache != null ? sThermalCache : z;
    }

    /** thermalservice 里带名字的温度（gpu / cluster0-2 / Battery）。 */
    public static List<Zone> thermalZones() {
        List<Zone> out = new ArrayList<Zone>();
        if (!ready()) return out;
        try {
            String txt = ShizukuBridge.dumpsys("thermalservice");
            if (txt.isEmpty()) return out;
            Matcher m = TEMP_LINE.matcher(txt);
            while (m.find()) {
                Zone z = new Zone();
                z.celsius = Float.parseFloat(m.group(1));
                z.name = m.group(3).trim();
                boolean dup = false;
                for (Zone e : out) if (e.name.equals(z.name)) { dup = true; break; }
                if (!dup) out.add(z);
            }
        } catch (Throwable t) {
            Log.d(TAG, "thermalZones failed " + t);
        }
        return out;
    }

    /* ---------------------------- 耗电 ---------------------------- */

    public static final class Power {
        public String label = "";
        public double mah = 0;
        public String detail = "";
    }

    private static java.util.Map<String, String> sUid2Pkg;
    private static long sUid2PkgAt = 0;
    private static List<Power> sPowerCache;
    private static long sPowerAt = 0;
    private static double sPowerTotal = -1;

    /** uid 数字 -> 包名（u0a225 => 10225）。 */
    private static java.util.Map<String, String> uidToPackage() {
        long now = System.currentTimeMillis();
        if (sUid2Pkg != null && now - sUid2PkgAt < 120000L) return sUid2Pkg;
        java.util.Map<String, String> m = new java.util.HashMap<String, String>();
        try {
            String txt = ShizukuBridge.exec("pm list packages -U 2>/dev/null");
            if (txt != null && !txt.isEmpty()) {
                for (String line : txt.split("\n")) {
                    String t = line.trim();
                    if (!t.startsWith("package:")) continue;
                    int ui = t.indexOf("uid:");
                    if (ui < 0) continue;
                    String pkg = t.substring(8, ui).trim();
                    String uid = t.substring(ui + 4).trim();
                    if (pkg.isEmpty() || uid.isEmpty()) continue;
                    m.put(uid, pkg);
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "uidToPackage failed " + t);
        }
        if (!m.isEmpty()) {
            sUid2Pkg = m;
            sUid2PkgAt = now;
        }
        return m;
    }

    /** u0a225 这种形式转成 10225。 */
    private static String normalizeUid(String raw) {
        try {
            if (raw.startsWith("u") && raw.contains("a")) {
                int a = raw.indexOf("a");
                int appId = Integer.parseInt(raw.substring(a + 1));
                return String.valueOf(10000 + appId);
            }
        } catch (Throwable ignored) {}
        return raw;
    }

    /**
     * 按应用排的耗电（mAh）。batterystats 很重，结果缓存 30 秒。
     * 注意：这是系统自己的估算值，不是实测。
     */
    public static List<Power> topPower(int n) {
        long now = System.currentTimeMillis();
        if (sPowerCache != null && now - sPowerAt < 30000L) {
            return sPowerCache.size() > n
                    ? new ArrayList<Power>(sPowerCache.subList(0, n)) : sPowerCache;
        }
        List<Power> out = new ArrayList<Power>();
        sPowerTotal = -1;
        if (!ready()) return out;
        try {
            String txt = ShizukuBridge.exec(
                    "dumpsys batterystats --charged 2>/dev/null | grep -A40 'Estimated power use'");
            if (txt == null || txt.isEmpty()) return out;

            java.util.Map<String, String> uids = uidToPackage();
            boolean inUid = false;
            for (String line : txt.split("\n")) {
                String t = line.trim();
                if (t.startsWith("Capacity:")) {
                    try {
                        int c = t.indexOf("Computed drain:");
                        if (c > 0) {
                            String v = t.substring(c + 15).trim();
                            int comma = v.indexOf(',');
                            if (comma > 0) v = v.substring(0, comma).trim();
                            sPowerTotal = Double.parseDouble(v);
                        }
                    } catch (Throwable ignored) {}
                    continue;
                }
                if (!t.startsWith("UID ")) continue;
                // UID u0a225: 98.7 ( screen=98.6 cpu=0.0295 )
                int colon = t.indexOf(':');
                if (colon < 0) continue;
                String uidRaw = t.substring(4, colon).trim();
                String rest = t.substring(colon + 1).trim();

                Power pw = new Power();
                int sp = rest.indexOf(' ');
                String num = sp > 0 ? rest.substring(0, sp) : rest;
                try { pw.mah = Double.parseDouble(num); } catch (Throwable e) { continue; }

                String uidNum = normalizeUid(uidRaw);
                String pkg = uids.get(uidNum);
                pw.label = pkg != null ? pkg : ("uid " + uidNum);

                int p1 = rest.indexOf('(');
                int p2 = rest.indexOf(')');
                if (p1 >= 0 && p2 > p1) pw.detail = rest.substring(p1 + 1, p2).trim();
                out.add(pw);
            }

            java.util.Collections.sort(out, new java.util.Comparator<Power>() {
                @Override public int compare(Power a, Power b) {
                    return Double.compare(b.mah, a.mah);
                }
            });
            sPowerCache = out;
            sPowerAt = now;
        } catch (Throwable t) {
            Log.d(TAG, "topPower failed " + t);
        }
        List<Power> r = sPowerCache != null ? sPowerCache : out;
        return r.size() > n ? new ArrayList<Power>(r.subList(0, n)) : r;
    }

    public static double totalPowerMah() {
        if (sPowerTotal >= 0) return sPowerTotal;
        topPower(1);
        return sPowerTotal;
    }

    /* ---------------------------- 帧率 ---------------------------- */

    /**
     * 读 SurfaceFlinger 的帧时间戳算实际帧率。
     * 返回 {实际fps, 屏幕刷新率}，拿不到返回 null。
     */
    public static float[] frameStats() {
        if (!ready()) return null;
        try {
            String layer = findAppLayer();
            if (layer == null) return null;
            String out = ShizukuBridge.exec(
                    "dumpsys SurfaceFlinger --latency '" + layer + "' 2>/dev/null");
            if (out == null || out.isEmpty()) return null;

            long periodNs = 0;
            long first = -1, last = -1;
            int n = 0;
            boolean firstLine = true;
            for (String line : out.split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (firstLine) {
                    try { periodNs = Long.parseLong(t); } catch (Throwable ignored) {}
                    firstLine = false;
                    continue;
                }
                String[] p = t.split("[ \t]+");
                if (p.length < 3) continue;
                long actual;
                try { actual = Long.parseLong(p[1]); } catch (Throwable e) { continue; }
                if (actual <= 0 || actual == Long.MAX_VALUE) continue;
                if (first < 0) first = actual;
                last = actual;
                n++;
            }
            if (n < 2 || first < 0 || last <= first) return null;
            float fps = (n - 1) * 1e9f / (float) (last - first);
            float refresh = periodNs > 0 ? (1e9f / (float) periodNs) : 0f;
            return new float[]{fps, refresh};
        } catch (Throwable t) {
            Log.d(TAG, "frameStats failed " + t);
            return null;
        }
    }

    /** 找当前窗口对应的 layer 名。 */
    private static String findAppLayer() {
        try {
            String list = ShizukuBridge.exec("dumpsys SurfaceFlinger --list 2>/dev/null");
            if (list == null || list.isEmpty()) return null;
            String best = null;
            for (String line : list.split("\n")) {
                String t = line.trim();
                if (!t.contains("MainActivity")) continue;
                if (!t.contains("sysmon")) continue;
                if (t.endsWith("]")) continue;      // 跳过 ActivityRecord{...}
                best = t;                            // 取最后一个（最上层）
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    /* ---------------------------- 应用 ---------------------------- */

    /** 已安装应用数量（含系统）。 */
    public static int appCount() {
        if (!ready()) return -1;
        try {
            String txt = ShizukuBridge.exec("pm list packages 2>/dev/null | wc -l");
            if (txt == null) return -1;
            String s = txt.trim();
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(0, nl).trim();
            if (s.isEmpty()) return -1;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 第三方应用数量。 */
    public static int appCount3rd() {
        if (!ready()) return -1;
        try {
            String txt = ShizukuBridge.exec("pm list packages -3 2>/dev/null | wc -l");
            if (txt == null) return -1;
            String s = txt.trim();
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(0, nl).trim();
            if (s.isEmpty()) return -1;
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 所有第三方包名。 */
    public static List<String> thirdPartyPackages() {
        List<String> out = new ArrayList<String>();
        if (!ready()) return out;
        String txt = ShizukuBridge.exec("pm list packages -3 2>/dev/null");
        if (txt == null || txt.isEmpty()) return out;
        for (String line : txt.split("\n")) {
            String t = line.trim();
            if (t.startsWith("package:")) out.add(t.substring(8));
        }
        return out;
    }
}
