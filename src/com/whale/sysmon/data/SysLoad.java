package com.whale.sysmon.data;

import android.util.Log;

/**
 * 整机 CPU 利用率：两次采样 /proc/stat 差分。另可从 /proc/loadavg 拿平均负载。
 */
public final class SysLoad {

    private long lastTotal = -1;
    private long lastIdle = -1;
    private float lastUsage = Float.NaN;
    private static boolean sLoggedEmpty = false;
    private static boolean sLoggedOnce = false;

    public double load1 = -1, load5 = -1, load15 = -1;

    /** /proc/stat 不可读时为 true（HarmonyOS 等加固系统会对 app 域拦截）。 */
    public boolean blocked = false;

    /* 诊断 */
    public String dbgSrc = "none";
    public long dbgTotal = -1;
    public long dbgIdle = -1;
    public int dbgSamples = 0;

    /** 采样一次，返回 0..1 的整机使用率。首次调用无差分基础，返回上次值。 */
    public float sample() {
        try {
            String stat = Sys.read("/proc/stat");
            dbgSrc = "file";
            if (!looksLikeStat(stat)) {
                // 第二选择：Shizuku 的 shell 通道（uid 2000，能读 /proc/stat）
                if (ShizukuBridge.connected()
                        && ShizukuBridge.permission() == ShizukuBridge.PERM_GRANTED) {
                    stat = ShizukuBridge.cat("/proc/stat");
                    dbgSrc = "shizuku";
                }
            }
            if (!looksLikeStat(stat)) {
                // 第三选择：本进程 fork 一个子进程读（通常也被拦）
                if (canTryExec()) {
                    stat = Sys.exec(1200, "cat", "/proc/stat");
                    dbgSrc = "exec";
                } else {
                    stat = "";
                }
            }
            if (!looksLikeStat(stat)) {
                dbgSrc = "none";
                blocked = true;
                sExecFail++;
                if (!sLoggedEmpty) {
                    sLoggedEmpty = true;
                    Log.d("SYS_MON_PROBE", "sysload: /proc/stat unreadable -> freq fallback");
                }
                return last();
            }
            blocked = false;
            dbgSamples++;

            int nl = stat.indexOf('\n');
            String first = nl > 0 ? stat.substring(0, nl) : stat;
            String[] p = first.trim().split("\\s+");
            // cpu user nice system idle iowait irq softirq steal guest guest_nice
            if (p.length >= 5) {
                long total = 0;
                for (int i = 1; i < p.length && i <= 8; i++) {
                    try { total += Long.parseLong(p[i]); } catch (Throwable ignored) {}
                }
                long idle = 0;
                try { idle = Long.parseLong(p[4]); } catch (Throwable ignored) {}

                if (lastTotal > 0 && total > lastTotal) {
                    long dt = total - lastTotal;
                    long di = idle - lastIdle;
                    if (dt > 0) {
                        float u = 1f - (float) ((double) di / (double) dt);
                        lastUsage = Math.max(0f, Math.min(1f, u));
                    }
                }
                lastTotal = total;
                lastIdle = idle;
                dbgTotal = total;
                dbgIdle = idle;

                if (!sLoggedOnce) {
                    sLoggedOnce = true;
                    Log.d("SYS_MON_PROBE", "sysload src=" + dbgSrc + " total=" + total
                            + " idle=" + idle + " fields=" + p.length + " usage=" + lastUsage);
                }
            }

            String la = Sys.readLine("/proc/loadavg");
            if (la.length() > 0) {
                String[] lp = la.split("\\s+");
                if (lp.length >= 3) {
                    load1 = parse(lp[0]);
                    load5 = parse(lp[1]);
                    load15 = parse(lp[2]);
                }
            }
        } catch (Throwable t) {
            Log.d("SYS_MON_PROBE", "sysload fail " + t);
        }
        return last();
    }

    public float last() {
        return Float.isNaN(lastUsage) ? 0f : lastUsage;
    }

    /** /proc/stat 的第一行必须是 "cpu ..." 且至少 5 个字段。 */
    private static boolean looksLikeStat(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (!t.startsWith("cpu")) return false;
        int nl = t.indexOf('\n');
        String first = nl > 0 ? t.substring(0, nl) : t;
        return first.trim().split("\\s+").length >= 5;
    }

    /** exec 很贵（上百毫秒到秒级），连续失败后降低重试频率。 */
    private static int sExecFail = 0;
    private static long sNextExecAt = 0;

    private static boolean canTryExec() {
        if (sExecFail < 2) return true;
        long now = System.currentTimeMillis();
        if (now < sNextExecAt) return false;
        sNextExecAt = now + 60000L;   // 1 分钟后重试一次
        return true;
    }

    private static double parse(String s) {
        try { return Double.parseDouble(s); } catch (Throwable t) { return -1; }
    }
}
