package com.whale.sysmon.page;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.whale.sysmon.data.Cpu;
import com.whale.sysmon.data.Dev;
import com.whale.sysmon.data.ShizukuBridge;
import com.whale.sysmon.data.Sys;
import com.whale.sysmon.data.SysLoad;
import com.whale.sysmon.ui.Palette;
import com.whale.sysmon.ui.Ui;

import java.util.List;
import java.util.Locale;

/**
 * 工具页：数据源自检 + 传感器清单 + 报告导出。
 */
public class ToolsPage {

    private final Context ctx;
    private final ScrollView scroll;
    private final LinearLayout root;

    public ToolsPage(Context ctx) {
        this.ctx = ctx;
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
        root.addView(Ui.tv(ctx, "工具", 30f, Palette.TEXT, Ui.HEAVY));
        root.addView(Ui.tv(ctx, "自检、传感器与导出", 12f, Palette.TEXT_DIM, Typeface.DEFAULT),
                Ui.lpTop(ctx, 4));

        buildShizuku();
        buildSelfCheck();
        buildExport();
        buildSensors();
    }

    /* ---------------------------- Shizuku ---------------------------- */

    private void buildShizuku() {
        LinearLayout card = group("Shizuku 提权（可选）");

        boolean inst = ShizukuBridge.installed(ctx);
        boolean conn = ShizukuBridge.connected();
        int perm = ShizukuBridge.permission();

        String state;
        int color;
        if (!inst) { state = "未安装 Shizuku"; color = Palette.TEXT_FAINT; }
        else if (!conn) { state = "Shizuku 未运行"; color = Palette.YELLOW; }
        else if (perm == ShizukuBridge.PERM_GRANTED) { state = "已授权"; color = Palette.GREEN; }
        else { state = "等待授权"; color = Palette.YELLOW; }

        LinearLayout r = Ui.row(ctx);
        r.addView(Ui.tv(ctx, "状态", 12f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.tv(ctx, state, 12f, color, Typeface.DEFAULT_BOLD));
        card.addView(r);

        TextView desc = Ui.tv(ctx, "开启后能读 /proc/stat，CPU 曲线变成真实使用率（现在只能算频率占用）。",
                10f, Palette.TEXT_DIM, Typeface.DEFAULT);
        desc.setLineSpacing(0, 1.35f);
        card.addView(desc, Ui.lpTop(ctx, 10));

        if (inst && !conn) {
            TextView btn = Ui.tv(ctx, "打开 Shizuku", 12f, Palette.BG, Typeface.DEFAULT_BOLD);
            btn.setGravity(android.view.Gravity.CENTER);
            btn.setBackground(Ui.round(ctx, Palette.CYAN, 0, 12f));
            btn.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        android.content.Intent i = ctx.getPackageManager()
                                .getLaunchIntentForPackage("moe.shizuku.privileged.api");
                        if (i != null) ctx.startActivity(i);
                    } catch (Throwable ignored) {}
                }
            });
            card.addView(btn, Ui.lpTop(ctx, 12));
        } else if (inst && conn && perm != ShizukuBridge.PERM_GRANTED) {
            TextView btn = Ui.tv(ctx, "申请授权", 12f, Palette.BG, Typeface.DEFAULT_BOLD);
            btn.setGravity(android.view.Gravity.CENTER);
            btn.setBackground(Ui.round(ctx, Palette.CYAN, 0, 12f));
            btn.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ShizukuBridge.request();
                }
            });
            card.addView(btn, Ui.lpTop(ctx, 12));
        }

        if (conn && perm == ShizukuBridge.PERM_GRANTED) {
            TextView test = Ui.tv(ctx, "测试读取 /proc/stat", 12f, Palette.CYAN, Typeface.DEFAULT_BOLD);
            test.setGravity(android.view.Gravity.CENTER);
            test.setBackground(Ui.round(ctx, Palette.alpha(Palette.CYAN, 0.12f), Palette.STROKE, 12f));
            test.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
            final TextView out = Ui.tv(ctx, "", 9f, Palette.GREEN, Typeface.MONOSPACE);
            out.setLineSpacing(0, 1.3f);
            test.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    String r = ShizukuBridge.exec("head -1 /proc/stat");
                    out.setText(r.isEmpty() ? "读取失败/为空" : r.trim());
                }
            });
            card.addView(test, Ui.lpTop(ctx, 12));
            card.addView(out, Ui.lpTop(ctx, 8));
        }
    }

    /* ---------------------------- 数据源自检 ---------------------------- */

    private void buildSelfCheck() {
        LinearLayout card = group("数据源可用性");
        addProbe(card, "/proc/stat", "/proc/stat", "CPU 使用率");
        addProbe(card, "/proc/meminfo", "/proc/meminfo", "内存");
        addProbe(card, "/proc/cpuinfo", "/proc/cpuinfo", "CPU 型号");
        addProbe(card, "/proc/loadavg", "/proc/loadavg", "平均负载");
        addProbe(card, "cpufreq", "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "核心频率");
        addProbe(card, "thermal_zone0", "/sys/class/thermal/thermal_zone0/temp", "温度");
        addProbe(card, "gpu devfreq", "/sys/class/devfreq/gpufreq/cur_freq", "GPU 频率");
        addProbe(card, "power_supply", "/sys/class/power_supply/Battery/current_now", "电池电流");

        SysLoad sl = new SysLoad();
        sl.sample();

        String src = sl.dbgSrc;
        String hintText;
        int hintColor;
        if (sl.blocked) {
            hintText = "／/proc/stat 直读与子进程都被拦截 → CPU 曲线退化为「核心频率占用」，"
                    + "这是本机 SELinux 限制，不是读数失败。";
            hintColor = Palette.YELLOW;
        } else if ("exec".equals(src)) {
            hintText = "／/proc/stat 直读被拦，子进程读取成功 → CPU 曲线为真实使用率。";
            hintColor = Palette.GREEN;
        } else {
            hintText = "／/proc/stat 直读可用 → CPU 曲线为真实使用率。";
            hintColor = Palette.GREEN;
        }
        TextView hint = Ui.tv(ctx, hintText, 10f, hintColor, Typeface.DEFAULT);
        hint.setLineSpacing(0, 1.35f);
        card.addView(hint, Ui.lpTop(ctx, 12));

        TextView dbg = Ui.tv(ctx, "诊断：src=" + src + "  total=" + sl.dbgTotal
                + "  idle=" + sl.dbgIdle + "  samples=" + sl.dbgSamples, 9f,
                Palette.TEXT_FAINT, Typeface.DEFAULT);
        card.addView(dbg, Ui.lpTop(ctx, 6));
    }

    private void addProbe(LinearLayout card, String label, String path, String what) {
        String content = Sys.read(path);
        if (content.length() == 0) {
            long v = Sys.readLong(path);
            if (v > 0) content = String.valueOf(v);
        }
        boolean ok = content.length() > 0;
        LinearLayout r = Ui.row(ctx);
        r.setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));
        TextView dot = Ui.tv(ctx, ok ? "●" : "○", 9f, ok ? Palette.GREEN : Palette.RED,
                Typeface.DEFAULT);
        r.addView(dot);
        LinearLayout mid = Ui.col(ctx);
        mid.addView(Ui.tv(ctx, "  " + label, 12f, Palette.TEXT, Typeface.DEFAULT));
        mid.addView(Ui.tv(ctx, "  " + what, 9f, Palette.TEXT_FAINT, Typeface.DEFAULT));
        r.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(Ui.tv(ctx, ok ? "可读" : "拦截", 11f,
                ok ? Palette.GREEN : Palette.RED, Typeface.DEFAULT));
        card.addView(r);
    }

    /* ------------------------------ 导出 ------------------------------ */

    private void buildExport() {
        LinearLayout card = group("导出设备报告");
        TextView desc = Ui.tv(ctx, "把当前设备的关键参数汇总成文本，复制到剪贴板，可直接粘给别人。",
                11f, Palette.TEXT_DIM, Typeface.DEFAULT);
        desc.setLineSpacing(0, 1.35f);
        card.addView(desc);

        TextView btn = Ui.tv(ctx, "复制报告", 13f, Palette.BG, Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Ui.round(ctx, Palette.CYAN, 0, 14f));
        btn.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String report = buildReport();
                ClipboardManager cm = (ClipboardManager)
                        ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("设备报告", report));
                Toast.makeText(ctx, "已复制 " + report.length() + " 字", Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(btn, Ui.lpTop(ctx, 12));
    }

    private String buildReport() {
        Dev d = Dev.read(ctx);
        Cpu c = Cpu.read();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 设备报告 ===\n");
        sb.append("机型: ").append(d.manufacturer).append(' ').append(d.model).append('\n');
        sb.append("系统: ").append(d.osName).append(" / Android ").append(d.osVersion)
                .append(" (API ").append(d.apiLevel).append(")\n");
        sb.append("构建: ").append(d.buildId).append("  ").append(d.buildTime).append('\n');
        sb.append("安全补丁: ").append(d.securityPatch).append('\n');
        sb.append("SoC: ").append(c.soc).append("  ").append(c.coreCount).append(" 核\n");
        sb.append("频率: ").append(c.freqRange()).append('\n');
        sb.append("指纹: ").append(d.fingerprint).append('\n');
        sb.append("根: ").append(d.rooted ? "有" : "无")
                .append("  Bootloader: ").append(d.bootloaderLock).append('\n');
        return sb.toString();
    }

    /* ----------------------------- 传感器 ----------------------------- */

    private void buildSensors() {
        LinearLayout card = group("传感器");
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> list = sm.getSensorList(Sensor.TYPE_ALL);
            card.addView(Ui.tv(ctx, "共 " + list.size() + " 个", 12f, Palette.CYAN,
                    Typeface.DEFAULT));
            for (int i = 0; i < list.size() && i < 30; i++) {
                Sensor s = list.get(i);
                LinearLayout r = Ui.row(ctx);
                r.setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));
                LinearLayout mid = Ui.col(ctx);
                mid.addView(Ui.tv(ctx, s.getName(), 11f, Palette.TEXT, Typeface.DEFAULT));
                mid.addView(Ui.tv(ctx, s.getVendor() + " · " + s.getVersion(), 9f,
                        Palette.TEXT_FAINT, Typeface.DEFAULT));
                r.addView(mid, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                r.addView(Ui.tv(ctx, String.format(Locale.US, "%.0f", s.getMaximumRange()),
                        10f, Palette.TEXT_DIM, Ui.NUM));
                card.addView(r);
            }
            if (list.size() > 30) {
                card.addView(Ui.tv(ctx, "…另有 " + (list.size() - 30) + " 个未显示", 10f,
                        Palette.TEXT_FAINT, Typeface.DEFAULT), Ui.lpTop(ctx, 8));
            }
        } catch (Throwable t) {
            card.addView(Ui.tv(ctx, "读取失败：" + t, 11f, Palette.RED, Typeface.DEFAULT));
        }
    }

    private LinearLayout group(String name) {
        TextView t = Ui.tv(ctx, name, 13f, Palette.CYAN, Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(ctx, 4), 0, 0, 0);
        root.addView(t, Ui.lpTop(ctx, 20));
        LinearLayout card = Ui.card(ctx);
        root.addView(card, Ui.lpTop(ctx, 8));
        return card;
    }
}
