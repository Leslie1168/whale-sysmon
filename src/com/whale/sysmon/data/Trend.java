package com.whale.sysmon.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 长时间趋势：每小时聚合成一个点，最多保留 48 小时。
 * 存在 SharedPreferences 里，进程被杀也不丢。
 */
public final class Trend {

    public static final int N = 48;
    private static final String KEY = "trend_v2";
    private static final long HOUR = 3600_000L;

    public final long[] hour = new long[N];
    public final float[] cpu = new float[N];
    public final float[] mem = new float[N];
    public final float[] temp = new float[N];
    public int count = 0;

    /* 当前小时的累加器 */
    private long curHour = -1;
    private float sumCpu, sumMem, sumTemp;
    private int sumN;

    /* ----------------------------- 读写 ----------------------------- */

    public static Trend load(Context c) {
        Trend t = new Trend();
        try {
            SharedPreferences sp = c.getSharedPreferences(
                    com.whale.sysmon.page.SettingsPage.PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY, "");
            if (raw == null || raw.isEmpty()) return t;
            // 每行: hour,cpu,mem,temp
            for (String line : raw.split(";")) {
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length < 4 || t.count >= N) continue;
                try {
                    t.hour[t.count] = Long.parseLong(p[0]);
                    t.cpu[t.count] = Float.parseFloat(p[1]);
                    t.mem[t.count] = Float.parseFloat(p[2]);
                    t.temp[t.count] = Float.parseFloat(p[3]);
                    t.count++;
                } catch (Throwable ignored) {}
            }
            t.curHour = t.count > 0 ? t.hour[t.count - 1] : -1;
        } catch (Throwable ignored) {}
        return t;
    }

    public void save(Context c) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                if (sb.length() > 0) sb.append(';');
                sb.append(hour[i]).append(',')
                        .append(fmt(cpu[i])).append(',')
                        .append(fmt(mem[i])).append(',')
                        .append(fmt(temp[i]));
            }
            c.getSharedPreferences(com.whale.sysmon.page.SettingsPage.PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, sb.toString()).apply();
        } catch (Throwable ignored) {}
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }

    /** 把一次采样喂进来，按小时聚合。返回 true 表示刚跨小时、需要保存。 */
    public boolean addSample(Context c, float cpuV, float memV, float tempV) {
        long nowHour = System.currentTimeMillis() / HOUR;

        // 跨小时：把上一个小时的平均值落盘，开新桶
        if (curHour >= 0 && nowHour != curHour) {
            flush();
            curHour = nowHour;
            sumCpu = sumMem = sumTemp = 0;
            sumN = 0;
            sumCpu = cpuV;
            sumMem = memV;
            sumTemp = Float.isNaN(tempV) ? 0 : tempV;
            sumN = 1;
            save(c);
            return true;
        }

        if (curHour < 0) curHour = nowHour;
        sumCpu += cpuV;
        sumMem += memV;
        if (!Float.isNaN(tempV)) sumTemp += tempV;
        sumN++;

        // 第一个点立刻落盘，别让用户干等一小时才看到曲线
        if (count == 0 && sumN >= 2) {
            flush();
            save(c);
            sumN = 0;
            sumCpu = sumMem = sumTemp = 0;
            return true;
        }
        return false;
    }

    private void flush() {
        if (sumN <= 0) return;
        if (count >= N) {
            // 前移一格
            for (int i = 1; i < N; i++) {
                hour[i - 1] = hour[i];
                cpu[i - 1] = cpu[i];
                mem[i - 1] = mem[i];
                temp[i - 1] = temp[i];
            }
            count = N - 1;
        }
        hour[count] = curHour * HOUR;
        cpu[count] = sumCpu / sumN;
        mem[count] = sumMem / sumN;
        temp[count] = sumTemp / sumN;
        count++;
    }

    /** 立即把当前桶落盘（退出/服务停止前调用）。 */
    public void persist(Context c) {
        flush();
        save(c);
        sumN = 0;
    }

    /* ---------------------------- 取序列 ---------------------------- */

    public float[] seriesCpu() { return series(cpu); }
    public float[] seriesMem() { return series(mem); }
    public float[] seriesTemp() { return series(temp); }

    private float[] series(float[] src) {
        if (count <= 0) return new float[0];
        float[] out = new float[count];
        System.arraycopy(src, 0, out, 0, count);
        return out;
    }

    public boolean isEmpty() { return count == 0; }

    public String spanText() {
        if (count <= 0) return "--";
        long hours = (hour[count - 1] - hour[0]) / HOUR + 1;
        return count + " 个采样点 · 覆盖约 " + hours + " 小时";
    }
}
