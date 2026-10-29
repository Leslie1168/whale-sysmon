package com.whale.sysmon.data;

import java.util.HashMap;
import java.util.Map;

/**
 * 内存：解析 /proc/meminfo，给出总量 / 已用 / 可用 / 缓存 / Swap。
 */
public final class Mem {

    public long totalKb = -1;
    public long freeKb = -1;
    public long availKb = -1;
    public long buffersKb = -1;
    public long cachedKb = -1;
    public long swapTotalKb = -1;
    public long swapFreeKb = -1;
    public long shmemKb = -1;
    public long slabKb = -1;

    private static final Map<String, String> RAW = new HashMap<String, String>();

    public static Mem read() {
        Mem m = new Mem();
        String txt = Sys.read("/proc/meminfo");
        if (txt.length() == 0) return m;
        for (String line : txt.split("\n")) {
            int c = line.indexOf(':');
            if (c < 0) continue;
            String key = line.substring(0, c).trim();
            String val = line.substring(c + 1).trim();
            int sp = val.indexOf(' ');
            String num = sp > 0 ? val.substring(0, sp) : val;
            long v;
            try { v = Long.parseLong(num); } catch (Throwable t) { continue; }
            if ("MemTotal".equals(key)) m.totalKb = v;
            else if ("MemFree".equals(key)) m.freeKb = v;
            else if ("MemAvailable".equals(key)) m.availKb = v;
            else if ("Buffers".equals(key)) m.buffersKb = v;
            else if ("Cached".equals(key)) m.cachedKb = v;
            else if ("SwapTotal".equals(key)) m.swapTotalKb = v;
            else if ("SwapFree".equals(key)) m.swapFreeKb = v;
            else if ("Shmem".equals(key)) m.shmemKb = v;
            else if ("Slab".equals(key)) m.slabKb = v;
            if (m.availKb < 0) {
                // 老内核没有 MemAvailable，用 Free+Buffers+Cached 近似
                long f = m.freeKb > 0 ? m.freeKb : 0;
                long b = m.buffersKb > 0 ? m.buffersKb : 0;
                long cc = m.cachedKb > 0 ? m.cachedKb : 0;
                if (f + b + cc > 0) m.availKb = f + b + cc;
            }
        }
        if (m.availKb < 0 && m.totalKb > 0) m.availKb = m.freeKb;
        return m;
    }

    public long usedKb() {
        if (totalKb <= 0) return -1;
        long avail = availKb > 0 ? availKb : freeKb;
        return Math.max(0, totalKb - avail);
    }

    public float usedRatio() {
        if (totalKb <= 0) return 0f;
        long u = usedKb();
        if (u < 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) ((double) u / (double) totalKb)));
    }

    public String totalText() { return Sys.bytes(totalKb * 1024L); }
    public String usedText() { return Sys.bytes(usedKb() * 1024L); }
    public String availText() { return Sys.bytes((availKb > 0 ? availKb : freeKb) * 1024L); }
    public String swapText() {
        if (swapTotalKb <= 0) return "未启用";
        long u = swapTotalKb - Math.max(0, swapFreeKb);
        return Sys.bytes(u * 1024L) + " / " + Sys.bytes(swapTotalKb * 1024L);
    }
}
