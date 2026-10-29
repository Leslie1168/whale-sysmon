package com.whale.sysmon.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CPU：核心数、每核频率区间与当前频率、大小核分组、硬件标识。
 * 全部来自 /sys/devices/system/cpu 与 /proc/cpuinfo，读不到则退化为 --。
 */
public final class Cpu {

    public static final class Core {
        public int index;
        public long minKhz = -1;
        public long maxKhz = -1;
        public long curKhz = -1;
        public long curKhzCached = -1;
    }

    public int coreCount;
    public final List<Core> cores = new ArrayList<Core>();
    public String hardware = "--";   // kirin9000 / qcom ...
    public String soc = "--";        // 营销名，可能空
    public String abi = "--";
    public String governor = "--";
    public String cpuinfoHead = "";  // /proc/cpuinfo 前几行原文

    private static final String BASE = "/sys/devices/system/cpu/";

    public static Cpu read() {
        Cpu c = new Cpu();
        c.coreCount = detectCoreCount();
        for (int i = 0; i < c.coreCount; i++) {
            Core core = new Core();
            core.index = i;
            String base = BASE + "cpu" + i + "/cpufreq/";
            core.minKhz = Sys.readLong(base + "cpuinfo_min_freq");
            if (core.minKhz < 0) core.minKhz = Sys.readLong(base + "scaling_min_freq");
            core.maxKhz = Sys.readLong(base + "cpuinfo_max_freq");
            if (core.maxKhz < 0) core.maxKhz = Sys.readLong(base + "scaling_max_freq");
            core.curKhz = Sys.readLong(base + "scaling_cur_freq");
            if (core.curKhz < 0) core.curKhz = Sys.readLong(base + "cpuinfo_cur_freq");
            core.curKhzCached = Sys.readLong(base + "cpuinfo_cur_freq");
            if (core.curKhzCached < 0) core.curKhzCached = core.curKhz;
            c.cores.add(core);
        }
        c.hardware = Sys.firstProp("--",
                "ro.hardware", "ro.board.platform", "ro.boot.hardware.platform");
        // 华为的 ro.soc.model 只有 "kirin"，不如 ro.board.platform(kirin9000) 信息量大
        String soc = Sys.firstProp("",
                "ro.board.platform", "ro.vendor.qti.soc_name", "ro.config.cpu_info_display",
                "ro.mediatek.platform", "ro.chipname", "ro.hardware.chipname", "ro.soc.model");
        c.soc = soc.isEmpty() ? c.hardware : soc;
        c.abi = Sys.firstProp("--", "ro.product.cpu.abi");
        c.governor = Sys.readLine(BASE + "cpu0/cpufreq/scaling_governor");
        if (c.governor.isEmpty()) c.governor = "--";

        String ci = Sys.read("/proc/cpuinfo");
        if (ci.length() > 0) {
            StringBuilder sb = new StringBuilder();
            String[] lines = ci.split("\n");
            for (int i = 0; i < lines.length && i < 12; i++) sb.append(lines[i]).append('\n');
            c.cpuinfoHead = sb.toString().trim();
        }
        return c;
    }

    private static int detectCoreCount() {
        int n = 0;
        String present = Sys.readLine(BASE + "present");
        // 形如 0-7
        if (present.contains("-")) {
            try {
                String[] p = present.split("-");
                n = Integer.parseInt(p[1].trim()) + 1;
            } catch (Throwable ignored) {}
        } else if (!present.isEmpty()) {
            n = present.split(",").length;
        }
        if (n <= 0) {
            String online = Sys.readLine(BASE + "online");
            if (online.contains("-")) {
                try {
                    String[] p = online.split("-");
                    n = Integer.parseInt(p[1].trim()) + 1;
                } catch (Throwable ignored) {}
            }
        }
        if (n <= 0) n = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(n, 32));
    }

    /** 重新读当前频率（监控轮询用，不重建对象）。 */
    public void refreshCur() {
        for (Core c : cores) {
            String base = BASE + "cpu" + c.index + "/cpufreq/";
            long v = Sys.readLong(base + "scaling_cur_freq");
            if (v < 0) v = Sys.readLong(base + "cpuinfo_cur_freq");
            if (v >= 0) c.curKhz = v;
        }
    }

    /** 全局频率区间文本，如 "826MHz - 3130MHz"。 */
    public String freqRange() {
        long min = Long.MAX_VALUE, max = -1;
        for (Core c : cores) {
            if (c.minKhz > 0) min = Math.min(min, c.minKhz);
            if (c.maxKhz > 0) max = Math.max(max, c.maxKhz);
        }
        if (max <= 0) return "--";
        return Sys.hz(min == Long.MAX_VALUE ? 0 : min) + " - " + Sys.hz(max);
    }

    /** 按 max 频率把核心分组，返回 "4 x A55 @ 2.0GHz" 这类结构描述；失败返回空串。 */
    public String clusterDesc() {
        StringBuilder sb = new StringBuilder();
        List<Core> sorted = new ArrayList<Core>(cores);
        java.util.Collections.sort(sorted, new java.util.Comparator<Core>() {
            @Override public int compare(Core a, Core b) { return Long.compare(a.maxKhz, b.maxKhz); }
        });
        long last = -1;
        int run = 0;
        List<long[]> groups = new ArrayList<long[]>();
        for (Core c : sorted) {
            if (c.maxKhz == last) run++;
            else {
                if (last > 0) groups.add(new long[]{last, run});
                last = c.maxKhz;
                run = 1;
            }
        }
        if (last > 0) groups.add(new long[]{last, run});
        for (int i = groups.size() - 1; i >= 0; i--) {
            long[] g = groups.get(i);
            sb.append(g[1]).append(" x ").append(Sys.hz(g[0])).append('\n');
        }
        return sb.toString().trim();
    }

    /** 当前频率文本，Kirin 上的编码（kHz）。 */
    public String curText(int i) {
        if (i < 0 || i >= cores.size()) return "--";
        long v = cores.get(i).curKhz;
        if (v <= 0) return "--";
        return String.format(Locale.US, "%d MHz", v / 1000);
    }

    /** 该核最高频率文本。 */
    public String freqMaxText(int i) {
        if (i < 0 || i >= cores.size()) return "--";
        long v = cores.get(i).maxKhz;
        return v > 0 ? Sys.hz(v) : "--";
    }

    /** 0.0 ~ 1.0，用于绘制柱状图。 */
    public float load(int i) {
        if (i < 0 || i >= cores.size()) return 0f;
        Core c = cores.get(i);
        if (c.maxKhz <= 0) return 0f;
        long cur = c.curKhz > 0 ? c.curKhz : c.curKhzCached;
        if (cur <= 0) return 0f;
        float f = (float) ((double) cur / (double) c.maxKhz);
        return Math.max(0f, Math.min(1f, f));
    }

    /** 单核最高的频率占比。 */
    public float maxRatio() {
        float mx = 0f;
        for (Core c : cores) {
            if (c.maxKhz <= 0) continue;
            long cur = c.curKhz > 0 ? c.curKhz : c.curKhzCached;
            if (cur <= 0) continue;
            mx = Math.max(mx, Math.max(0f, Math.min(1f,
                    (float) ((double) cur / (double) c.maxKhz))));
        }
        return mx;
    }

    /**
     * 全核"频率占用"：各核 当前频率/最高频率 的平均。
     * 注意这不是 CPU 使用率，是频率负载；/proc/stat 不可读时用它兜底。
     */
    public float avgFreqRatio() {
        float sum = 0;
        int n = 0;
        for (Core c : cores) {
            if (c.maxKhz <= 0) continue;
            long cur = c.curKhz > 0 ? c.curKhz : c.curKhzCached;
            if (cur <= 0) continue;
            sum += Math.max(0f, Math.min(1f, (float) ((double) cur / (double) c.maxKhz)));
            n++;
        }
        return n == 0 ? 0f : sum / n;
    }
}
