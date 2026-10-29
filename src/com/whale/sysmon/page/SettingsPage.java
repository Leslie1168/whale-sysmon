package com.whale.sysmon.page;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.whale.sysmon.BuildConfig;
import com.whale.sysmon.ui.Meter;
import com.whale.sysmon.ui.Palette;
import com.whale.sysmon.ui.Ui;

/**
 * 设置页：采样间隔、告警阈值、后台常驻。全部落在 SharedPreferences。
 */
public class SettingsPage {

    public static final String PREFS = "sysmon";

    public static final String K_INTERVAL = "interval_ms";
    public static final String K_ALERT_CPU = "alert_cpu";
    public static final String K_ALERT_TEMP = "alert_temp";
    public static final String K_ALERT_MEM = "alert_mem";
    public static final String K_FGS = "foreground_service";

    private final Context ctx;
    private final ScrollView scroll;
    private final LinearLayout root;
    private final SharedPreferences sp;

    private TextView vInterval, vCpu, vTemp, vMem, vFgs;

    public SettingsPage(Context ctx) {
        this.ctx = ctx;
        this.sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Palette.BG);
        scroll.setClipToPadding(false);

        root = Ui.col(ctx);
        root.setBackgroundColor(Palette.BG);
        int px = Ui.dp(ctx, 16);
        root.setPadding(px, Ui.dp(ctx, 44), px, Ui.dp(ctx, 108));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        build();
    }

    public View view() { return scroll; }

    public void scrollToTop() { scroll.scrollTo(0, 0); }

    private void build() {
        root.removeAllViews();
        root.addView(Ui.tv(ctx, "设置", 30f, Palette.TEXT, Ui.HEAVY));
        root.addView(Ui.tv(ctx, "采样、告警与后台常驻", 12f, Palette.TEXT_DIM, Typeface.DEFAULT),
                Ui.lpTop(ctx, 4));

        // 采样
        LinearLayout g1 = group("采样");
        vInterval = addCycle(g1, "采样间隔", new String[]{"1 秒", "2 秒", "3 秒", "5 秒"},
                new int[]{1000, 2000, 3000, 5000}, K_INTERVAL);
        addNote(g1, "间隔越短曲线越密，耗电略增。后台服务按此间隔采样。");

        // 告警
        LinearLayout g2 = group("告警阈值");
        vCpu = addCycle(g2, "CPU 超过", new String[]{"70%", "80%", "90%", "关闭"},
                new int[]{70, 80, 90, 0}, K_ALERT_CPU);
        vTemp = addCycle(g2, "温度超过", new String[]{"45℃", "50℃", "55℃", "关闭"},
                new int[]{45, 50, 55, 0}, K_ALERT_TEMP);
        vMem = addCycle(g2, "内存超过", new String[]{"75%", "85%", "90%", "关闭"},
                new int[]{75, 85, 90, 0}, K_ALERT_MEM);
        addNote(g2, "超过阈值时通过通知栏提醒，10 分钟内同一指标只提醒一次。");

        // 后台
        LinearLayout g3 = group("后台");
        vFgs = addToggle(g3, "前台常驻服务", K_FGS);
        addNote(g3, "常驻后在通知栏保留一行实时指标，并持续记录长时间曲线。");

        // 关于
        LinearLayout g4 = group("关于");
        addStatic(g4, "应用", "观测台 SysMon");
        addStatic(g4, "版本", BuildConfig.VERSION_NAME);
        addStatic(g4, "包名", ctx.getPackageName());
        addStatic(g4, "数据来源", "Build / getprop / proc / sysfs");
        addNote(g4, "无广告、无账号、无网络上传。所有数据都在本机读取。");
    }

    /* ---------------------------- 组件 ---------------------------- */

    private LinearLayout group(String name) {
        TextView t = Ui.tv(ctx, name, 13f, Palette.CYAN, Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(ctx, 4), 0, 0, 0);
        root.addView(t, Ui.lpTop(ctx, 20));
        LinearLayout card = Ui.card(ctx);
        root.addView(card, Ui.lpTop(ctx, 8));
        return card;
    }

    private void addStatic(LinearLayout card, String k, String v) {
        LinearLayout r = Ui.row(ctx);
        r.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
        r.addView(Ui.tv(ctx, k, 12f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.tv(ctx, v, 12f, Palette.TEXT, Typeface.DEFAULT));
        card.addView(r);
    }

    private void addNote(LinearLayout card, String text) {
        TextView t = Ui.tv(ctx, text, 10f, Palette.TEXT_FAINT, Typeface.DEFAULT);
        t.setLineSpacing(0, 1.3f);
        card.addView(t, Ui.lpTop(ctx, 10));
    }

    /** 循环取值的一行：点一下切下一个值。返回显示值的 TextView。 */
    private TextView addCycle(LinearLayout card, String label, String[] options,
                              int[] values, final String key) {
        LinearLayout r = Ui.row(ctx);
        r.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
        r.addView(Ui.tv(ctx, label, 12f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView val = Ui.tv(ctx, "", 12f, Palette.CYAN, Typeface.DEFAULT);
        val.setGravity(Gravity.END);
        int def = values[0];
        int cur = sp.getInt(key, def);
        val.setText(labelFor(options, values, cur));

        final String[] opts = options;
        final int[] vals = values;
        r.addView(val);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cur = sp.getInt(key, vals[0]);
                int idx = 0;
                for (int i = 0; i < vals.length; i++) if (vals[i] == cur) idx = i;
                idx = (idx + 1) % vals.length;
                sp.edit().putInt(key, vals[idx]).apply();
                val.setText(labelFor(opts, vals, vals[idx]));
            }
        });
        card.addView(r);
        return val;
    }

    private String labelFor(String[] options, int[] values, int cur) {
        for (int i = 0; i < values.length; i++) if (values[i] == cur) return options[i];
        return options[0];
    }

    private TextView addToggle(LinearLayout card, String label, final String key) {
        LinearLayout r = Ui.row(ctx);
        r.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
        r.addView(Ui.tv(ctx, label, 12f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = Ui.tv(ctx, sp.getBoolean(key, false) ? "已开启" : "已关闭",
                12f, sp.getBoolean(key, false) ? Palette.GREEN : Palette.TEXT_FAINT,
                Typeface.DEFAULT);
        val.setGravity(Gravity.END);
        r.addView(val);
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean now = sp.getBoolean(key, false);
                boolean next = !now;
                sp.edit().putBoolean(key, next).apply();
                val.setText(next ? "已开启" : "已关闭");
                val.setTextColor(next ? Palette.GREEN : Palette.TEXT_FAINT);
                toggleService(next);
            }
        });
        card.addView(r);
        return val;
    }

    /** 启停后台常驻服务。 */
    private void toggleService(boolean start) {
        try {
            android.content.Intent i = new android.content.Intent(ctx,
                    com.whale.sysmon.monitor.MonitorService.class);
            if (start) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(i);
                } else {
                    ctx.startService(i);
                }
                android.widget.Toast.makeText(ctx, "后台监控已开启",
                        android.widget.Toast.LENGTH_SHORT).show();
            } else {
                ctx.stopService(i);
                android.widget.Toast.makeText(ctx, "后台监控已关闭",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            android.widget.Toast.makeText(ctx, "启动失败：" + t.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    public int intervalMs() {
        return sp.getInt(K_INTERVAL, 1000);
    }
}
