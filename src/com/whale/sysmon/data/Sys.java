package com.whale.sysmon.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 低级读取工具：文件、getprop、shell。全部做缓存与容错，读不到就返回空串，绝不抛。
 */
public final class Sys {

    private Sys() {}

    private static Map<String, String> sProps;
    private static long sPropsAt;

    /* ---------------------------- 文件 ---------------------------- */

    public static String read(String path) {
        FileInputStream in = null;
        try {
            File f = new File(path);
            if (!f.exists()) return "";
            // 不要用 canRead() 做前置判断：Android/SELinux 下 access() 常误报 false，
            // 但 open() 其实是通的。直接读，失败再返回空。
            in = new FileInputStream(f);
            return drain(in, 8192);
        } catch (Throwable t) {
            return "";
        } finally {
            close(in);
        }
    }

    public static String readLine(String path) {
        String s = read(path);
        int i = s.indexOf('\n');
        return (i >= 0 ? s.substring(0, i) : s).trim();
    }

    public static long readLong(String path) {
        try {
            String s = readLine(path).replaceAll("[^0-9\\-]", "");
            if (s.isEmpty() || s.equals("-")) return -1L;
            return Long.parseLong(s);
        } catch (Throwable t) {
            return -1L;
        }
    }

    private static String drain(InputStream in, int max) throws Exception {
        byte[] buf = new byte[max];
        int n = 0, r;
        while (n < max && (r = in.read(buf, n, max - n)) > 0) n += r;
        return new String(buf, 0, n, "UTF-8");
    }

    private static void close(java.io.Closeable c) {
        if (c != null) try { c.close(); } catch (Throwable ignored) {}
    }

    /** 列出目录下的文件名（不含路径），失败返回空数组。 */
    public static String[] list(String dir) {
        try {
            File f = new File(dir);
            String[] r = f.list();
            return r == null ? new String[0] : r;
        } catch (Throwable t) {
            return new String[0];
        }
    }

    /* --------------------------- getprop --------------------------- */

    /** 一次性读取全部系统属性并缓存。 */
    public static synchronized Map<String, String> props() {
        long now = System.currentTimeMillis();
        if (sProps != null && now - sPropsAt < 15000L) return sProps;
        Map<String, String> m = new HashMap<String, String>();
        String out = exec(4000, "getprop");
        if (out != null && out.length() > 0) {
            for (String line : out.split("\n")) {
                // 格式：[key]: [value]
                int a = line.indexOf('[');
                int b = line.indexOf(']', a + 1);
                int c = line.indexOf('[', b + 1);
                int d = line.indexOf(']', c + 1);
                if (a < 0 || b < 0 || c < 0 || d < 0) continue;
                m.put(line.substring(a + 1, b), line.substring(c + 1, d));
            }
        }
        if (!m.isEmpty()) {
            sProps = m;
            sPropsAt = now;
        }
        return m.isEmpty() && sProps != null ? sProps : m;
    }

    public static String prop(String key, String def) {
        String v = props().get(key);
        if (v == null || v.isEmpty() || "unknown".equals(v)) {
            // 反射兜底（部分 ROM getprop 被子进程隔离）
            v = sysPropReflect(key);
        }
        return (v == null || v.isEmpty()) ? def : v;
    }

    /** 逐个 key 试，返回第一个非空的。 */
    public static String firstProp(String def, String... keys) {
        for (String k : keys) {
            String v = prop(k, "");
            if (v.length() > 0) return v;
        }
        return def;
    }

    private static String sysPropReflect(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object v = c.getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : ((String) v).trim();
        } catch (Throwable t) {
            return "";
        }
    }

    /* ---------------------------- shell ---------------------------- */

    public static String exec(long timeoutMs, String... cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            long deadline = System.currentTimeMillis() + timeoutMs;
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                if (System.currentTimeMillis() > deadline) break;
            }
            p.waitFor(Math.max(200L, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            return sb.toString();
        } catch (Throwable t) {
            return "";
        } finally {
            if (p != null) try { p.destroy(); } catch (Throwable ignored) {}
        }
    }

    /* --------------------------- 格式化 --------------------------- */

    public static String hz(long khz) {
        if (khz <= 0) return "--";
        if (khz < 1000) return khz + " kHz";
        double mhz = khz / 1000.0;
        if (mhz < 1000) return String.format(Locale.US, "%.0f MHz", mhz);
        return String.format(Locale.US, "%.2f GHz", mhz / 1000.0);
    }

    public static String bytes(long b) {
        if (b < 0) return "--";
        if (b < 1024L) return b + " B";
        double k = b / 1024.0;
        if (k < 1024) return String.format(Locale.US, "%.1f KB", k);
        double m = k / 1024.0;
        if (m < 1024) return String.format(Locale.US, "%.1f MB", m);
        double g = m / 1024.0;
        if (g < 1024) return String.format(Locale.US, "%.2f GB", g);
        return String.format(Locale.US, "%.2f TB", g / 1024.0);
    }

    /** 输出成 [B|KB|MB|GB] 的数值，单位分档由调用方决定，默认 GB。 */
    public static double gb(long b) {
        return b <= 0 ? 0 : b / 1073741824.0;
    }

    public static String clamp(String s, String def) {
        return (s == null || s.length() == 0 || "unknown".equalsIgnoreCase(s)
                || "null".equalsIgnoreCase(s)) ? def : s;
    }
}
