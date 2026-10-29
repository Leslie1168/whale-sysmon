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

import com.whale.sysmon.data.Bat;
import com.whale.sysmon.data.Cpu;
import com.whale.sysmon.data.Dev;
import com.whale.sysmon.data.Gpu;
import com.whale.sysmon.data.Mem;
import com.whale.sysmon.data.SysLoad;
import com.whale.sysmon.data.Thermal;
import com.whale.sysmon.ui.Meter;
import com.whale.sysmon.ui.Palette;
import com.whale.sysmon.ui.SparkChart;
import com.whale.sysmon.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 控制台：卡片可编辑 —— 长按卡片或点右上角「编排」进入编辑态，
 * 用 ↑ ↓ 调顺序、▣ 切换大小（大=整行，小=一行两个）。布局持久化。
 */
public class ConsolePage {

    /* 卡片 id */
    private static final String ID_OVERVIEW = "overview";
    private static final String ID_CPU = "cpu";
    private static final String ID_GPU = "gpu";
    private static final String ID_MEM = "mem";
    private static final String ID_TEMP = "temp";
    private static final String ID_BAT = "battery";
    private static final String ID_QUICK = "quick";
    private static final String ID_CORES = "cores";
    private static final String ID_NET = "net";
    private static final String ID_STORE = "store";
    private static final String ID_SYS = "sys";
    private static final String ID_SENSORS = "sensors";
    private static final String ID_DISPLAY = "display";
    private static final String ID_NETRATE = "netrate";
    private static final String ID_IOSTAT = "iostat";
    private static final String ID_TREND = "trend";
    private static final String ID_PROCS = "procs";
    private static final String ID_FPS = "fps";
    private static final String ID_POWER = "power";

    public static final int BIG = 0;
    public static final int SMALL = 1;

    private static final class Spec {
        String id;
        int size;
        Spec(String id, int size) { this.id = id; this.size = size; }
    }

    private final Context ctx;
    private final LinearLayout shell;      // 外壳：固定头 + 滚动区
    private final LinearLayout header;     // 固定不滚的标题行
    private final ScrollView scroll;
    private final LinearLayout root;
    private final LinearLayout host;
    private final SharedPreferences sp;

    private final List<Spec> specs = new ArrayList<Spec>();
    private final Map<String, View> cards = new HashMap<String, View>();

    private boolean editing = false;
    private TextView editBtn;

    /* 运行时引用 */
    private SparkChart cCpu, cGpu, cMem, cTemp;
    private TextView tCpuVal, tGpuVal, tMemVal, tTempVal;
    private TextView tCpuTitle, tGpuTitle, tTempTitle;
    private TextView tCpuStat, tGpuStat, tMemStat, tTempStat;
    private TextView mCpu, mGpu, mMem, mBat;
    private TextView clock, chip;
    private Meter batMeter;
    private TextView batPct, batStatus, batVolt, batCur, batPwr, batTemp, batCycle, batHealth;
    private TextView tCores, tNet, tSys, tStoreTxt;
    private com.whale.sysmon.ui.Meter mStoreBar, mMemBar;
    private TextView tSensors, tDisplay, tIoStat;
    private SparkChart cNetDl, cNetUl;
    private TextView tNetVals;
    private long lastRx = -1, lastTx = -1, lastNetAt = 0;
    private com.whale.sysmon.data.Trend trend;
    private SparkChart cTrend;
    private TextView tTrendInfo;
    private TextView tProcs;
    private SparkChart cFps;
    private TextView tFpsVal, tFpsInfo;
    private TextView tPower;

    private final SysLoad load = new SysLoad();
    private final Cpu cpu;
    private final Dev dev;
    private Gpu gpu;
    private long intervalMs = 1000L;
    private int tickCounter = 0;

    /** 让曲线按采样间隔做插值，配合高刷屏才顺。 */
    public void setIntervalMs(long ms) {
        if (ms <= 0) return;
        intervalMs = ms;
        if (cCpu != null) cCpu.setIntervalMs(ms);
        if (cGpu != null) cGpu.setIntervalMs(ms);
        if (cMem != null) cMem.setIntervalMs(ms);
        if (cTemp != null) cTemp.setIntervalMs(ms);
    }

    public ConsolePage(Context ctx) {
        this.ctx = ctx;
        this.dev = Dev.read(ctx);
        this.cpu = Cpu.read();
        this.sp = ctx.getSharedPreferences(SettingsPage.PREFS, Context.MODE_PRIVATE);

        shell = Ui.col(ctx);
        shell.setBackgroundColor(Palette.BG);

        header = Ui.col(ctx);
        header.setBackgroundColor(Palette.BG);
        int px = Ui.dp(ctx, 16);
        header.setPadding(px, Ui.dp(ctx, 44), px, Ui.dp(ctx, 6));

        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Palette.BG);
        scroll.setClipToPadding(false);

        root = Ui.col(ctx);
        root.setBackgroundColor(Palette.BG);
        root.setPadding(px, 0, px, Ui.dp(ctx, 112));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host = Ui.col(ctx);
        loadLayout();
        buildHeader();
        root.addView(host);

        // 外壳：标题行固定，下面才是可滚动内容
        shell.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        shell.addView(scroll, scrollLp);

        rebuild();
    }

    public View view() { return shell; }

    public void scrollToTop() { scroll.scrollTo(0, 0); }

    /* ---------------------------- 布局状态 ---------------------------- */

    private void loadLayout() {
        specs.clear();
        String saved = sp.getString("console_layout", "");
        if (saved != null && saved.length() > 0) {
            for (String part : saved.split(",")) {
                int c = part.indexOf(':');
                if (c <= 0) continue;
                String id = part.substring(0, c);
                int size;
                try { size = Integer.parseInt(part.substring(c + 1)); }
                catch (Throwable t) { size = BIG; }
                specs.add(new Spec(id, size));
            }
        }
        if (specs.isEmpty()) {
            specs.add(new Spec(ID_OVERVIEW, BIG));
            specs.add(new Spec(ID_CPU, BIG));
            specs.add(new Spec(ID_GPU, BIG));
            specs.add(new Spec(ID_MEM, BIG));
            specs.add(new Spec(ID_TEMP, BIG));
            specs.add(new Spec(ID_BAT, BIG));
            specs.add(new Spec(ID_QUICK, BIG));
        }
        // 去掉已不存在的 id
        List<String> known = new ArrayList<String>();
        known.add(ID_OVERVIEW); known.add(ID_CPU); known.add(ID_GPU);
        known.add(ID_MEM); known.add(ID_TEMP); known.add(ID_BAT); known.add(ID_QUICK);
        known.add(ID_CORES); known.add(ID_NET); known.add(ID_STORE); known.add(ID_SYS);
        known.add(ID_SENSORS); known.add(ID_DISPLAY); known.add(ID_NETRATE); known.add(ID_IOSTAT);
        known.add(ID_TREND); known.add(ID_PROCS); known.add(ID_FPS); known.add(ID_POWER);
        for (int i = specs.size() - 1; i >= 0; i--) {
            if (!known.contains(specs.get(i).id)) specs.remove(i);
        }
    }

    private void saveLayout() {
        StringBuilder sb = new StringBuilder();
        for (Spec s : specs) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s.id).append(':').append(s.size);
        }
        sp.edit().putString("console_layout", sb.toString()).apply();
    }

    /* ------------------------------ 头部 ------------------------------ */

    private void buildHeader() {
        LinearLayout head = Ui.row(ctx);
        LinearLayout left = Ui.col(ctx);
        left.addView(Ui.tv(ctx, "观测台", 30f, Palette.TEXT, Ui.HEAVY));
        left.addView(Ui.tv(ctx, "本机实时性能监测", 12f, Palette.TEXT_DIM, Typeface.DEFAULT),
                Ui.lpTop(ctx, 5));
        head.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = Ui.col(ctx);
        right.setGravity(Gravity.END);
        clock = Ui.tv(ctx, "--:--", 11f, Palette.TEXT_FAINT, Ui.NUM);
        right.addView(clock);
        editBtn = Ui.tv(ctx, "编排", 12f, Palette.CYAN, Typeface.DEFAULT_BOLD);
        editBtn.setBackground(Ui.pill(ctx, Palette.alpha(Palette.CYAN, 0.12f), 0, 12f));
        editBtn.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 5), Ui.dp(ctx, 12), Ui.dp(ctx, 5));
        LinearLayout.LayoutParams ebLp = Ui.lpTop(ctx, 8);
        right.addView(editBtn, ebLp);
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleEdit(); }
        });
        head.addView(right);

        header.addView(head);
    }

    private void toggleEdit() {
        editing = !editing;
        editBtn.setText(editing ? "完成" : "编排");
        editBtn.setTextColor(editing ? Palette.GREEN : Palette.CYAN);
        editBtn.setBackground(Ui.pill(ctx,
                Palette.alpha(editing ? Palette.GREEN : Palette.CYAN, 0.12f), 0, 12f));
        rebuild();
    }

    /* ------------------------------ 重排 ------------------------------ */

    private void rebuild() {
        host.removeAllViews();
        int i = 0;
        while (i < specs.size()) {
            Spec s = specs.get(i);
            if (s.size == SMALL) {
                // 攒两个小卡成一行
                LinearLayout rowView = Ui.row(ctx);
                rowView.setGravity(Gravity.TOP);
                boolean paired = false;
                View a = wrap(s);
                rowView.addView(a, Ui.weight(1));
                if (i + 1 < specs.size() && specs.get(i + 1).size == SMALL) {
                    View gap = new View(ctx);
                    rowView.addView(gap, Ui.lp(Ui.dp(ctx, 12), 1));
                    rowView.addView(wrap(specs.get(i + 1)), Ui.weight(1));
                    paired = true;
                }
                host.addView(rowView, Ui.lpTop(ctx, 14));
                i += paired ? 2 : 1;
            } else {
                host.addView(wrap(s), Ui.lpTop(ctx, 14));
                i++;
            }
        }
        if (editing) {
            TextView add = Ui.tv(ctx, "＋  添加卡片", 13f, Palette.CYAN, Typeface.DEFAULT_BOLD);
            add.setGravity(Gravity.CENTER);
            add.setBackground(Ui.round(ctx, Palette.alpha(Palette.CYAN, 0.10f), Palette.STROKE, 16f));
            add.setPadding(0, Ui.dp(ctx, 16), 0, Ui.dp(ctx, 16));
            add.setClickable(true);
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showAddDialog(); }
            });
            host.addView(add, Ui.lpTop(ctx, 14));
        }
    }

    /** 可添加的卡片清单。 */
    private static final String[] ALL_IDS = {
            ID_OVERVIEW, ID_CPU, ID_GPU, ID_MEM, ID_TEMP, ID_BAT,
            ID_CORES, ID_NET, ID_STORE, ID_SYS, ID_QUICK,
            ID_SENSORS, ID_DISPLAY, ID_NETRATE, ID_IOSTAT, ID_TREND, ID_PROCS, ID_FPS, ID_POWER
    };
    private static final String[] ALL_NAMES = {
            "概要（四项指标）", "CPU 曲线", "GPU 曲线", "内存曲线", "温度曲线", "电池健康",
            "CPU 核心频率", "网络概览", "存储占用", "运行时（开机时长等）", "快捷操作",
            "传感器列表", "屏幕参数", "网速曲线", "电池详情", "24 小时趋势",
            "进程 CPU（需 Shizuku）", "屏幕帧率（需 Shizuku）", "耗电排行（需 Shizuku）"
    };

    private void showAddDialog() {
        java.util.List<String> names = new java.util.ArrayList<String>();
        final java.util.List<String> ids = new java.util.ArrayList<String>();
        for (int i = 0; i < ALL_IDS.length; i++) {
            boolean exists = false;
            for (Spec sp : specs) if (sp.id.equals(ALL_IDS[i])) { exists = true; break; }
            if (!exists) {
                names.add(ALL_NAMES[i]);
                ids.add(ALL_IDS[i]);
            }
        }
        if (names.isEmpty()) {
            android.widget.Toast.makeText(ctx, "所有卡片都已添加", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(ctx, com.whale.sysmon.R.style.DarkDialog)
                .setTitle("添加卡片")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        specs.add(new Spec(ids.get(which), BIG));
                        saveLayout();
                        rebuild();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void remove(String id) {
        for (int i = specs.size() - 1; i >= 0; i--) {
            if (specs.get(i).id.equals(id)) specs.remove(i);
        }
        saveLayout();
        rebuild();
    }

    /** 给卡片套一层（编辑态加控制条）。 */
    private View wrap(final Spec spec) {
        View card = cardOf(spec.id, spec.size);
        // 卡片实例是复用的，重排前必须先从旧父容器摘掉，否则 addView 会抛
        // IllegalStateException: child already has a parent
        ViewGroup oldParent = (ViewGroup) card.getParent();
        if (oldParent != null) oldParent.removeView(card);

        LinearLayout outer = Ui.col(ctx);
        if (editing) {
            LinearLayout bar = Ui.row(ctx);
            bar.setPadding(0, 0, 0, Ui.dp(ctx, 6));
            bar.addView(ctrl("↑", new Runnable() {
                @Override public void run() { move(spec.id, -1); }
            }));
            bar.addView(ctrl("↓", new Runnable() {
                @Override public void run() { move(spec.id, 1); }
            }));
            bar.addView(ctrl(spec.size == BIG ? "▣ 大" : "▤ 小", new Runnable() {
                @Override public void run() { toggleSize(spec.id); }
            }));
            bar.addView(ctrl("✕", new Runnable() {
                @Override public void run() { remove(spec.id); }
            }));
            bar.addView(new View(ctx), Ui.weight(1));
            if (spec.size != SMALL) {
                TextView name = Ui.tv(ctx, labelOf(spec.id), 10f, Palette.TEXT_FAINT,
                        Typeface.DEFAULT);
                bar.addView(name);
            }
            outer.addView(bar);
        }
        outer.addView(card);
        if (!editing) {
            outer.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    if (!editing) toggleEdit();
                    return true;
                }
            });
        }
        return outer;
    }

    private View ctrl(String text, final Runnable action) {
        TextView t = Ui.tv(ctx, text, 11f, Palette.CYAN, Typeface.DEFAULT_BOLD);
        t.setBackground(Ui.pill(ctx, Palette.alpha(Palette.CYAN, 0.14f), 0, 9f));
        t.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 5), Ui.dp(ctx, 9), Ui.dp(ctx, 5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(ctx, 5);
        t.setLayoutParams(lp);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        return t;
    }

    private String labelOf(String id) {
        if (ID_OVERVIEW.equals(id)) return "概要";
        if (ID_CPU.equals(id)) return "CPU";
        if (ID_GPU.equals(id)) return "GPU";
        if (ID_MEM.equals(id)) return "内存";
        if (ID_TEMP.equals(id)) return "温度";
        if (ID_BAT.equals(id)) return "电池";
        if (ID_CORES.equals(id)) return "核心";
        if (ID_NET.equals(id)) return "网络";
        if (ID_STORE.equals(id)) return "存储";
        if (ID_SYS.equals(id)) return "运行时";
        if (ID_SENSORS.equals(id)) return "传感器";
        if (ID_DISPLAY.equals(id)) return "屏幕";
        if (ID_NETRATE.equals(id)) return "网速";
        if (ID_IOSTAT.equals(id)) return "电池详情";
        if (ID_TREND.equals(id)) return "24h 趋势";
        if (ID_PROCS.equals(id)) return "进程";
        if (ID_FPS.equals(id)) return "帧率";
        if (ID_POWER.equals(id)) return "耗电";
        return "快捷";
    }

    private void move(String id, int delta) {
        int idx = -1;
        for (int i = 0; i < specs.size(); i++) if (specs.get(i).id.equals(id)) { idx = i; break; }
        if (idx < 0) return;
        int to = idx + delta;
        if (to < 0 || to >= specs.size()) return;
        Spec a = specs.get(idx);
        specs.set(idx, specs.get(to));
        specs.set(to, a);
        saveLayout();
        rebuild();
    }

    private void toggleSize(String id) {
        for (Spec s : specs) {
            if (s.id.equals(id)) {
                s.size = s.size == BIG ? SMALL : BIG;
                break;
            }
        }
        saveLayout();
        rebuild();
    }

    /** 取卡片视图（复用实例，图表数据不丢）。 */
    private View cardOf(String id, int size) {
        // 大小不同需要不同的内边距/高度，图表卡按 size 重建成本高，
        // 这里统一用同一个实例，靠 setPadding 与高度参数适配。
        View cached = cards.get(id);
        if (cached == null) {
            cached = createCard(id);
            cards.put(id, cached);
        }
        applySize(cached, id, size);
        return cached;
    }

    private void applySize(View v, String id, int size) {
        LinearLayout card = (LinearLayout) v;
        int hp = size == SMALL ? 12 : 16;
        card.setPadding(Ui.dp(ctx, hp), Ui.dp(ctx, size == SMALL ? 11 : 14),
                Ui.dp(ctx, hp), Ui.dp(ctx, size == SMALL ? 11 : 14));

        View chart = card.findViewWithTag("chart");
        if (chart != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chart.getLayoutParams();
            lp.height = Ui.dp(ctx, size == SMALL ? 56 : 92);
            chart.setLayoutParams(lp);
        }
        View title = card.findViewWithTag("chartTitle");
        if (title instanceof TextView) {
            ((TextView) title).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                    Ui.sp(size == SMALL ? 12f : 13f));
        }
        View value = card.findViewWithTag("chartValue");
        if (value instanceof TextView) {
            ((TextView) value).setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,
                    Ui.sp(size == SMALL ? 15f : 19f));
        }
    }

    /* ------------------------------ 卡片 ------------------------------ */

    private View createCard(String id) {
        if (ID_OVERVIEW.equals(id)) return buildOverview();
        if (ID_CPU.equals(id)) return buildChart(ID_CPU, "CPU 使用率", Palette.CYAN);
        if (ID_GPU.equals(id)) return buildChart(ID_GPU, "GPU 频率", Palette.PINK);
        if (ID_MEM.equals(id)) return buildChart(ID_MEM, "内存占用率", Palette.PURPLE);
        if (ID_TEMP.equals(id)) return buildChart(ID_TEMP, "温度曲线", Palette.GREEN);
        if (ID_BAT.equals(id)) return buildBattery();
        if (ID_CORES.equals(id)) return buildCores();
        if (ID_NET.equals(id)) return buildNet();
        if (ID_STORE.equals(id)) return buildStore();
        if (ID_SYS.equals(id)) return buildSys();
        if (ID_SENSORS.equals(id)) return buildSensors();
        if (ID_DISPLAY.equals(id)) return buildDisplay();
        if (ID_NETRATE.equals(id)) return buildNetRate();
        if (ID_IOSTAT.equals(id)) return buildIoStat();
        if (ID_TREND.equals(id)) return buildTrend();
        if (ID_PROCS.equals(id)) return buildProcs();
        if (ID_FPS.equals(id)) return buildFps();
        if (ID_POWER.equals(id)) return buildPower();
        return buildQuick();
    }

    private View buildOverview() {
        LinearLayout sum = Ui.card(ctx);
        LinearLayout sumHead = Ui.row(ctx);
        sumHead.addView(Ui.tv(ctx, dev.manufacturer.toUpperCase(Locale.US) + "  " + dev.model,
                13f, Palette.TEXT, Typeface.DEFAULT_BOLD), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sumHead.addView(Ui.tv(ctx, "●", 10f, Palette.GREEN, Typeface.DEFAULT));
        sum.addView(sumHead);
        sum.addView(Ui.tv(ctx, dev.osName, 11f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                Ui.lpTop(ctx, 3));

        mCpu = Ui.num(ctx, "--", 20f, Palette.CYAN);
        mGpu = Ui.num(ctx, "--", 20f, Palette.PINK);
        mMem = Ui.num(ctx, "--", 20f, Palette.PURPLE);
        mBat = Ui.num(ctx, "--", 20f, Palette.GREEN);

        // 窄屏 2x2（避免 100% 被截断），宽屏一行四个（避免中间大片空白）
        int wDp = (int) (ctx.getResources().getDisplayMetrics().widthPixels
                / ctx.getResources().getDisplayMetrics().density);
        boolean wide = wDp >= 600;
        if (wide) {
            LinearLayout r = Ui.row(ctx);
            r.addView(metricBlock(mCpu, "CPU"), Ui.weight(1));
            r.addView(metricBlock(mGpu, "GPU"), Ui.weight(1));
            r.addView(metricBlock(mMem, "内存"), Ui.weight(1));
            r.addView(metricBlock(mBat, "电量"), Ui.weight(1));
            sum.addView(r, Ui.lpTop(ctx, 14));
        } else {
            LinearLayout r1 = Ui.row(ctx);
            r1.addView(metricBlock(mCpu, "CPU"), Ui.weight(1));
            r1.addView(metricBlock(mGpu, "GPU"), Ui.weight(1));
            sum.addView(r1, Ui.lpTop(ctx, 14));
            LinearLayout r2 = Ui.row(ctx);
            r2.addView(metricBlock(mMem, "内存"), Ui.weight(1));
            r2.addView(metricBlock(mBat, "电量"), Ui.weight(1));
            sum.addView(r2, Ui.lpTop(ctx, 10));
        }
        return sum;
    }

    private LinearLayout metricBlock(TextView value, String caption) {
        LinearLayout v = Ui.col(ctx);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        value.setGravity(Gravity.CENTER);
        value.setMaxLines(1);
        v.addView(value, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView cap = Ui.tv(ctx, caption, 11f, Palette.TEXT_FAINT, Typeface.DEFAULT);
        cap.setGravity(Gravity.CENTER);
        v.addView(cap, Ui.lpTop(ctx, 4));
        return v;
    }

    private View buildChart(String id, String title, int color) {
        LinearLayout card = Ui.chartCard(ctx);

        LinearLayout head = Ui.row(ctx);
        TextView t = Ui.tv(ctx, title, 13f, Palette.TEXT, Typeface.DEFAULT);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = Ui.num(ctx, "--", 19f, color);
        val.setMaxLines(1);
        val.setTag("chartValue");
        t.setTag("chartTitle");
        head.addView(val);
        card.addView(head);

        SparkChart chart = new SparkChart(ctx);
        chart.setChartColor(color);
        chart.setSmooth(true);
        chart.setIntervalMs(intervalMs);
        chart.setTag("chart");
        LinearLayout.LayoutParams cLp = Ui.lpTop(ctx, 10);
        cLp.height = Ui.dp(ctx, 92);
        card.addView(chart, cLp);

        TextView stat = Ui.tv(ctx, "", 10f, Palette.TEXT_FAINT, Typeface.DEFAULT);
        stat.setMaxLines(2);
        card.addView(stat, Ui.lpTop(ctx, 8));

        if (ID_CPU.equals(id)) { cCpu = chart; tCpuVal = val; tCpuStat = stat; tCpuTitle = t; }
        else if (ID_GPU.equals(id)) { cGpu = chart; tGpuVal = val; tGpuStat = stat; tGpuTitle = t; }
        else if (ID_MEM.equals(id)) { cMem = chart; tMemVal = val; tMemStat = stat; }
        else { cTemp = chart; tTempVal = val; tTempStat = stat; tTempTitle = t; }
        return card;
    }

    private View buildBattery() {
        LinearLayout card = Ui.card(ctx);
        LinearLayout head = Ui.row(ctx);
        head.addView(Ui.tv(ctx, "电池健康", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        batStatus = Ui.tv(ctx, "--", 10f, Palette.TEXT_DIM, Typeface.DEFAULT);
        batStatus.setBackground(Ui.pill(ctx, Palette.alpha(Palette.TEXT_DIM, 0.16f), 0, 10f));
        batStatus.setPadding(Ui.dp(ctx, 9), Ui.dp(ctx, 3), Ui.dp(ctx, 9), Ui.dp(ctx, 3));
        head.addView(batStatus);
        card.addView(head);

        LinearLayout pctRow = Ui.row(ctx);
        pctRow.addView(Ui.tv(ctx, "当前电量", 11f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        batPct = Ui.num(ctx, "--", 15f, Palette.GREEN);
        pctRow.addView(batPct);
        card.addView(pctRow, Ui.lpTop(ctx, 12));

        batMeter = new Meter(ctx);
        batMeter.setBarColor(Palette.GREEN);
        batMeter.setThicknessDp(8f);
        card.addView(batMeter, Ui.lpTop(ctx, 8));

        LinearLayout row1 = Ui.row(ctx);
        batVolt = Ui.num(ctx, "--", 17f, Palette.TEXT);
        batCur = Ui.num(ctx, "--", 17f, Palette.TEXT);
        batTemp = Ui.num(ctx, "--", 17f, Palette.TEXT);
        row1.addView(metricBlock(batVolt, "电压"), Ui.weight(1));
        row1.addView(metricBlock(batCur, "电流"), Ui.weight(1));
        row1.addView(metricBlock(batTemp, "温度"), Ui.weight(1));
        card.addView(row1, Ui.lpTop(ctx, 16));

        LinearLayout row2 = Ui.row(ctx);
        batCycle = Ui.num(ctx, "--", 17f, Palette.TEXT);
        batPwr = Ui.num(ctx, "--", 17f, Palette.YELLOW);
        batHealth = Ui.num(ctx, "--", 17f, Palette.GREEN);
        row2.addView(metricBlock(batCycle, "循环"), Ui.weight(1));
        row2.addView(metricBlock(batPwr, "实时功耗"), Ui.weight(1));
        row2.addView(metricBlock(batHealth, "健康状态"), Ui.weight(1));
        card.addView(row2, Ui.lpTop(ctx, 12));
        return card;
    }

    private View buildCores() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "CPU 核心频率", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));
        tCores = Ui.tv(ctx, "--", 11f, Palette.CYAN, Typeface.MONOSPACE);
        tCores.setLineSpacing(0, 1.45f);
        card.addView(tCores, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildNet() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "网络", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));
        tNet = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.DEFAULT);
        tNet.setLineSpacing(0, 1.45f);
        card.addView(tNet, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildStore() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "存储与内存", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));

        tStoreTxt = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.DEFAULT);
        card.addView(tStoreTxt, Ui.lpTop(ctx, 12));

        mStoreBar = new com.whale.sysmon.ui.Meter(ctx);
        mStoreBar.setBarColor(Palette.YELLOW);
        mStoreBar.setThicknessDp(7f);
        card.addView(mStoreBar, Ui.lpTop(ctx, 8));

        card.addView(Ui.tv(ctx, "内存", 11f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                Ui.lpTop(ctx, 12));
        mMemBar = new com.whale.sysmon.ui.Meter(ctx);
        mMemBar.setBarColor(Palette.PURPLE);
        mMemBar.setThicknessDp(7f);
        card.addView(mMemBar, Ui.lpTop(ctx, 6));
        return card;
    }

    private View buildSys() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "运行时", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));
        tSys = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.DEFAULT);
        tSys.setLineSpacing(0, 1.5f);
        card.addView(tSys, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildSensors() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "传感器", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));
        tSensors = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.DEFAULT);
        tSensors.setLineSpacing(0, 1.45f);
        card.addView(tSensors, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildDisplay() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "屏幕", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));
        tDisplay = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.DEFAULT);
        tDisplay.setLineSpacing(0, 1.5f);
        card.addView(tDisplay, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildNetRate() {
        LinearLayout card = Ui.card(ctx);
        LinearLayout head = Ui.row(ctx);
        head.addView(Ui.tv(ctx, "实时网速", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tNetVals = Ui.num(ctx, "--", 15f, Palette.CYAN);
        head.addView(tNetVals);
        card.addView(head);

        cNetDl = new SparkChart(ctx);
        cNetDl.setChartColor(Palette.CYAN);
        cNetDl.setSmooth(true);
        cNetDl.setIntervalMs(intervalMs);
        LinearLayout.LayoutParams l1 = Ui.lpTop(ctx, 8);
        l1.height = Ui.dp(ctx, 46);
        card.addView(cNetDl, l1);

        card.addView(Ui.tv(ctx, "上行", 10f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                Ui.lpTop(ctx, 6));
        cNetUl = new SparkChart(ctx);
        cNetUl.setChartColor(Palette.GREEN);
        cNetUl.setSmooth(true);
        cNetUl.setIntervalMs(intervalMs);
        LinearLayout.LayoutParams l2 = Ui.lpTop(ctx, 4);
        l2.height = Ui.dp(ctx, 34);
        card.addView(cNetUl, l2);
        return card;
    }

    private View buildIoStat() {
        LinearLayout card = Ui.card(ctx);
        card.addView(Ui.tv(ctx, "电池详情", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD));
        tIoStat = Ui.tv(ctx, "--", 11f, Palette.CYAN, Typeface.DEFAULT);
        tIoStat.setLineSpacing(0, 1.5f);
        card.addView(tIoStat, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildTrend() {
        LinearLayout card = Ui.chartCard(ctx);
        LinearLayout head = Ui.row(ctx);
        head.addView(Ui.tv(ctx, "24 小时趋势 · CPU", 13f, Palette.TEXT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = Ui.num(ctx, "--", 19f, Palette.YELLOW);
        head.addView(val);
        card.addView(head);

        cTrend = new SparkChart(ctx);
        cTrend.setChartColor(Palette.YELLOW);
        cTrend.setSmooth(false);
        cTrend.setTag("chart");
        LinearLayout.LayoutParams clp = Ui.lpTop(ctx, 10);
        clp.height = Ui.dp(ctx, 92);
        card.addView(cTrend, clp);

        tTrendInfo = Ui.tv(ctx, "", 10f, Palette.TEXT_FAINT, Typeface.DEFAULT);
        card.addView(tTrendInfo, Ui.lpTop(ctx, 8));

        // 首次加载历史
        refreshTrend(true);
        return card;
    }

    /** 把每小时一个点插值铺满整条曲线。 */
    private void refreshTrend(boolean reset) {
        try {
            if (trend == null) trend = com.whale.sysmon.data.Trend.load(ctx);
            float[] arr = trend.seriesCpu();
            if (cTrend == null) return;
            if (arr.length == 0) {
                tTrendInfo.setText("还没有历史数据，后台常驻后每小时记一个点");
                return;
            }
            cTrend.clear();
            int cap = 120;
            for (int i = 0; i < cap; i++) {
                float t = arr.length == 1 ? 0 : i * (arr.length - 1) / (float) (cap - 1);
                int i0 = (int) t;
                float frac = t - i0;
                int i1 = Math.min(arr.length - 1, i0 + 1);
                float v = arr[i0] * (1 - frac) + arr[i1] * frac;
                cTrend.push(Math.max(0f, Math.min(1f, v)));
            }
            tTrendInfo.setText(trend.spanText()
                    + "   最新均值 " + Math.round(arr[arr.length - 1] * 100) + "%");
        } catch (Throwable ignored) {}
    }

    private View buildPower() {
        LinearLayout card = Ui.card(ctx);
        LinearLayout head = Ui.row(ctx);
        head.addView(Ui.tv(ctx, "耗电排行", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.tv(ctx, "需 Shizuku", 9f, Palette.TEXT_FAINT, Typeface.DEFAULT));
        card.addView(head);
        tPower = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.MONOSPACE);
        tPower.setLineSpacing(0, 1.5f);
        card.addView(tPower, Ui.lpTop(ctx, 10));
        card.addView(Ui.tv(ctx, "系统估算值，非实测", 9f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                Ui.lpTop(ctx, 8));
        return card;
    }

    private View buildFps() {
        LinearLayout card = Ui.chartCard(ctx);
        LinearLayout head = Ui.row(ctx);
        head.addView(Ui.tv(ctx, "屏幕帧率", 13f, Palette.TEXT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tFpsVal = Ui.num(ctx, "--", 19f, Palette.YELLOW);
        head.addView(tFpsVal);
        card.addView(head);

        cFps = new SparkChart(ctx);
        cFps.setChartColor(Palette.YELLOW);
        cFps.setSmooth(true);
        cFps.setIntervalMs(intervalMs);
        cFps.setTag("chart");
        LinearLayout.LayoutParams clp = Ui.lpTop(ctx, 10);
        clp.height = Ui.dp(ctx, 92);
        card.addView(cFps, clp);

        tFpsInfo = Ui.tv(ctx, "", 10f, Palette.TEXT_FAINT, Typeface.DEFAULT);
        card.addView(tFpsInfo, Ui.lpTop(ctx, 8));
        return card;
    }

    private View buildProcs() {
        LinearLayout card = Ui.card(ctx);
        LinearLayout head = Ui.row(ctx);
        head.addView(Ui.tv(ctx, "进程 CPU", 13f, Palette.TEXT, Typeface.DEFAULT_BOLD),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.tv(ctx, "需 Shizuku", 9f, Palette.TEXT_FAINT, Typeface.DEFAULT));
        card.addView(head);
        tProcs = Ui.tv(ctx, "--", 11f, Palette.TEXT, Typeface.MONOSPACE);
        tProcs.setLineSpacing(0, 1.5f);
        card.addView(tProcs, Ui.lpTop(ctx, 10));
        return card;
    }

    private View buildQuick() {
        LinearLayout wrap = Ui.col(ctx);
        LinearLayout qRow = Ui.row(ctx);
        qRow.setGravity(Gravity.TOP);
        qRow.addView(quick("刷新", "立即重采样", Palette.CYAN), Ui.weight(1));
        View g1 = new View(ctx);
        qRow.addView(g1, Ui.lp(Ui.dp(ctx, 12), 1));
        qRow.addView(quick("常驻", "后台监控", Palette.PURPLE), Ui.weight(1));
        View g2 = new View(ctx);
        qRow.addView(g2, Ui.lp(Ui.dp(ctx, 12), 1));
        qRow.addView(quick("报告", "生成快照", Palette.GREEN), Ui.weight(1));
        wrap.addView(qRow);
        return wrap;
    }

    private View quick(String title, String sub, int color) {
        LinearLayout box = Ui.col(ctx);
        box.setBackground(Ui.round(ctx, Palette.CARD, Palette.STROKE, 16f));
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(ctx, 16);
        box.setPadding(p, p, p, p);
        box.addView(Ui.tv(ctx, "◈", 20f, color, Ui.NUM));
        box.addView(Ui.tv(ctx, title, 12f, Palette.TEXT, Typeface.DEFAULT), Ui.lpTop(ctx, 8));
        box.addView(Ui.tv(ctx, sub, 10f, Palette.TEXT_FAINT, Typeface.DEFAULT), Ui.lpTop(ctx, 3));
        return box;
    }

    /* ------------------------------ 采样 ------------------------------ */

    public void sample() {
        tickCounter++;
        try {
            // CPU
            load.sample();
            float cpuU;
            if (load.blocked) {
                cpu.refreshCur();
                cpuU = cpu.avgFreqRatio();
                if (tCpuTitle != null) tCpuTitle.setText("CPU 频率占用");
            } else {
                cpuU = load.last();
                if (tCpuTitle != null) tCpuTitle.setText("CPU 使用率");
            }
            if (mCpu != null) mCpu.setText(pct(cpuU));
            if (tCpuVal != null) tCpuVal.setText(pct1(cpuU));
            if (cCpu != null) cCpu.push(cpuU);
            if (tCpuStat != null) {
                tCpuStat.setText(load.blocked ? freqLoadLine() : statLine(cCpu));
            }

            // GPU
            if (gpu == null) gpu = Gpu.read(ctx);
            else gpu.refresh();
            float gpuU;
            String gpuTxt;
            if (!Float.isNaN(gpu.load)) {
                gpuU = gpu.load;
                gpuTxt = pct1(gpuU);
                if (mGpu != null) mGpu.setText(pct(gpuU));
                if (chip != null) chip.setText(cpu.soc);
                if (tGpuTitle != null) tGpuTitle.setText("GPU 负载率");
            } else {
                long mx = gpu.maxKhz > 0 ? gpu.maxKhz : gpu.curKhz;
                gpuU = mx > 0 ? Math.max(0f, Math.min(1f, (float) gpu.curKhz / (float) mx)) : 0f;
                gpuTxt = gpu.freqText();
                if (mGpu != null) {
                    mGpu.setText(gpu.curKhz > 0
                            ? String.format(Locale.US, "%.0f", gpu.curKhz / 1000.0) : "--");
                }
                if (tGpuTitle != null) tGpuTitle.setText("GPU 频率");
            }
            if (tGpuVal != null) tGpuVal.setText(gpuTxt);
            if (cGpu != null) cGpu.push(gpuU);
            if (tGpuStat != null) {
                tGpuStat.setText(gpu.curKhz > 0 ? gpu.rangeText() : statLine(cGpu));
            }

            // 内存
            Mem mem = Mem.read();
            float memU = mem.usedRatio();
            if (mMem != null) mMem.setText(pct(memU));
            if (tMemVal != null) tMemVal.setText(pct1(memU));
            if (cMem != null) cMem.push(memU);
            if (tMemStat != null) tMemStat.setText(statLine(cMem));

            // 温度
            Thermal th = Thermal.read();
            Bat bat = Bat.read(ctx);
            float tempC = !Float.isNaN(th.max) ? th.max : bat.tempC();
            if (!Float.isNaN(tempC)) {
                if (tTempVal != null) tTempVal.setText(String.format(Locale.US, "%.1f℃", tempC));
                if (cTemp != null) cTemp.push((tempC - 20f) / 70f);
            }
            if (tTempTitle != null && !Float.isNaN(th.max)) {
                tTempTitle.setText(th.maxName.length() > 0 ? ("温度 · " + th.maxName) : "温度曲线");
            }
            if (tTempStat != null) tTempStat.setText(statLineTemp(cTemp));

            // 电量
            int bp = bat.percent();
            if (mBat != null) mBat.setText(bp >= 0 ? bp + "%" : "--");

            if (batStatus != null) batStatus.setText(bat.charging() ? "充电中" : "放电中");
            if (batPct != null) batPct.setText(bp >= 0 ? bp + "%" : "--");
            if (batMeter != null) batMeter.setRatio(bp / 100f);
            if (batVolt != null) batVolt.setText(bat.voltageText());
            if (batCur != null) batCur.setText(bat.currentText());
            if (batTemp != null) batTemp.setText(bat.tempText());
            if (batCycle != null) {
                batCycle.setText(bat.cycleCount >= 0 ? String.valueOf(bat.cycleCount) : "--");
            }
            if (batPwr != null) batPwr.setText(bat.powerText());
            if (batHealth != null) batHealth.setText(bat.healthText());

            // 24h 趋势：喂一次采样（按小时聚合）
            if (trend == null) trend = com.whale.sysmon.data.Trend.load(ctx);
            boolean crossed = trend.addSample(ctx, cpuU, memU,
                    Float.isNaN(tempC) ? Float.NaN : tempC);
            if (crossed) refreshTrend(false);

            // 耗电排行（batterystats 很重，30 拍一次）
            if (tPower != null && tickCounter % 30 == 0) {
                if (!com.whale.sysmon.data.ShellData.ready()) {
                    tPower.setText("需要 Shizuku 授权（工具页 → Shizuku 提权）");
                } else {
                    java.util.List<com.whale.sysmon.data.ShellData.Power> ps =
                            com.whale.sysmon.data.ShellData.topPower(8);
                    if (ps.isEmpty()) {
                        tPower.setText("读取失败（batterystats 可能刚重置）");
                    } else {
                        double total = com.whale.sysmon.data.ShellData.totalPowerMah();
                        StringBuilder sb = new StringBuilder();
                        if (total > 0) {
                            sb.append("总计 ").append(String.format(Locale.US, "%.0f", total))
                              .append(" mAh\n");
                        }
                        for (int i = 0; i < ps.size(); i++) {
                            com.whale.sysmon.data.ShellData.Power pw = ps.get(i);
                            sb.append(String.format(Locale.US, "%6.1f  ", pw.mah)).append(pw.label);
                            if (i < ps.size() - 1) sb.append('\n');
                        }
                        tPower.setText(sb.toString());
                    }
                }
            }

            // 帧率（每 4 拍一次，SurfaceFlinger 查询有开销）
            if (tFpsVal != null && tickCounter % 4 == 0) {
                if (!com.whale.sysmon.data.ShellData.ready()) {
                    tFpsVal.setText("--");
                    tFpsInfo.setText("需要 Shizuku 授权");
                } else {
                    float[] fs = com.whale.sysmon.data.ShellData.frameStats();
                    if (fs == null || fs[0] <= 0) {
                        tFpsVal.setText("--");
                        tFpsInfo.setText("拿不到帧数据（窗口可能静止）");
                    } else {
                        float fps = fs[0];
                        float hz = fs[1];
                        tFpsVal.setText(String.format(Locale.US, "%.0f fps", fps));
                        tFpsInfo.setText(String.format(Locale.US,
                                "屏幕 %d Hz   达成率 %.0f%%",
                                Math.round(hz), hz > 0 ? fps / hz * 100 : 0));
                        if (cFps != null) cFps.push(hz > 0 ? fps / hz : 0f);
                    }
                }
            }

            // 进程 CPU（低频刷新，dumpsys 有开销）
            if (tProcs != null && tickCounter % 5 == 0) {
                if (!com.whale.sysmon.data.ShellData.ready()) {
                    tProcs.setText("需要 Shizuku 授权（工具页 → Shizuku 提权）");
                } else {
                    java.util.List<com.whale.sysmon.data.ShellData.Proc> ps =
                            com.whale.sysmon.data.ShellData.topProcs(6);
                    if (ps.isEmpty()) {
                        tProcs.setText("读取失败");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < ps.size(); i++) {
                            com.whale.sysmon.data.ShellData.Proc pr = ps.get(i);
                            sb.append(String.format(Locale.US, "%3d%%  ", pr.pct)).append(pr.name);
                            if (i < ps.size() - 1) sb.append('\n');
                        }
                        tProcs.setText(sb.toString());
                    }
                }
            }

            // 核心频率卡
            if (tCores != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cpu.coreCount; i++) {
                    sb.append("core ").append(i)
                      .append("   ").append(cpu.curText(i))
                      .append("   ").append(pct(cpu.load(i)));
                    if (i < cpu.coreCount - 1) sb.append("\n");
                }
                tCores.setText(sb.length() == 0 ? "--" : sb.toString());
            }

            // 网络卡
            if (tNet != null) {
                com.whale.sysmon.data.Net n = com.whale.sysmon.data.Net.read(ctx);
                tNet.setText(n.ssid + "\n" + n.ip + "   " + n.signalText()
                        + "\n↓ " + n.rxText() + "   ↑ " + n.txText()
                        + "\n" + n.std + "  ·  " + n.speedText());
            }

            // 存储卡
            if (tStoreTxt != null) {
                com.whale.sysmon.data.Store st = com.whale.sysmon.data.Store.internal();
                com.whale.sysmon.data.Mem mm = Mem.read();
                tStoreTxt.setText("存储  " + st.usedText() + " / " + st.totalText()
                        + "  " + st.pctText());
                if (mStoreBar != null) mStoreBar.setRatio(st.ratio());
                if (mMemBar != null) mMemBar.setRatio(mm.usedRatio());
            }

            // 运行时卡
            if (tSys != null) {
                tSys.setText("开机 " + dev.uptimeText()
                        + "\nSoC  " + cpu.soc
                        + "\n温度 " + (Float.isNaN(th.max) ? "--" : th.maxText())
                        + "\n电量 " + (bat.percent() >= 0 ? bat.percent() + "%" : "--")
                        + "   " + bat.statusText());
            }

            // 传感器卡
            if (tSensors != null) {
                int n = 0;
                StringBuilder sb = new StringBuilder();
                try {
                    android.hardware.SensorManager sm = (android.hardware.SensorManager)
                            ctx.getSystemService(Context.SENSOR_SERVICE);
                    java.util.List<android.hardware.Sensor> list = sm.getSensorList(
                            android.hardware.Sensor.TYPE_ALL);
                    n = list == null ? 0 : list.size();
                    int shown = 0;
                    for (android.hardware.Sensor se : list) {
                        if (shown >= 6) break;
                        if (shown > 0) sb.append('\n');
                        sb.append(se.getName());
                        shown++;
                    }
                } catch (Throwable ignored) {}
                tSensors.setText("共 " + n + " 个\n" + sb);
            }

            // 屏幕卡
            if (tDisplay != null) {
                com.whale.sysmon.data.Disp dp = com.whale.sysmon.data.Disp.read(ctx);
                tDisplay.setText(dp.resText() + "   " + dp.ratioText()
                        + "\n" + dp.densityText()
                        + "\n刷新 " + dp.refreshText() + "   支持 " + dp.refreshListText()
                        + "\n" + dp.sizeText() + "   HDR " + dp.hdr);
            }

            // 网速卡
            if (tNetVals != null) {
                long now = System.currentTimeMillis();
                long rx = android.net.TrafficStats.getTotalRxBytes();
                long tx = android.net.TrafficStats.getTotalTxBytes();
                double dls = 0, uls = 0;
                if (lastRx > 0 && now > lastNetAt) {
                    double dt = (now - lastNetAt) / 1000.0;
                    dls = Math.max(0, (rx - lastRx) / dt);
                    uls = Math.max(0, (tx - lastTx) / dt);
                }
                lastRx = rx;
                lastTx = tx;
                lastNetAt = now;
                tNetVals.setText("↓" + rate(dls) + "  ↑" + rate(uls));
                if (cNetDl != null) cNetDl.push(scaleRate(dls));
                if (cNetUl != null) cNetUl.push(scaleRate(uls));
            }

            // 电池详情卡
            if (tIoStat != null) {
                StringBuilder bs = new StringBuilder();
                bs.append("电压 ").append(bat.voltageText())
                  .append("   电流 ").append(bat.currentText())
                  .append("\n功率 ").append(bat.powerText())
                  .append("   温度 ").append(bat.tempText())
                  .append("\n电量 ").append(bat.percent() >= 0 ? bat.percent() + "%" : "--")
                  .append("   ").append(bat.statusText()).append(" / ").append(bat.plugText())
                  .append("\n健康 ").append(bat.healthText())
                  .append("   技术 ").append(bat.tech);
                if (com.whale.sysmon.data.ShellData.ready() && tickCounter % 5 == 0) {
                    long cc = com.whale.sysmon.data.ShellData.chargeCounterUah();
                    if (cc > 0) {
                        bs.append("\n电荷量 ").append(String.format(Locale.US, "%d mAh", cc / 1000));
                    }
                    String load = com.whale.sysmon.data.ShellData.loadLine();
                    if (load != null && load.length() > 0) {
                        bs.append("\n").append(load);
                    }
                }
                tIoStat.setText(bs.toString());
            }

            if (clock != null) {
                clock.setText(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                        .format(new java.util.Date()));
            }
        } catch (Throwable ignored) {}
    }

    /** 字节/秒 → 易读文本。 */
    private String rate(double bps) {
        if (bps < 1024) return String.format(Locale.US, "%.0f B/s", bps);
        if (bps < 1024 * 1024) return String.format(Locale.US, "%.1f KB/s", bps / 1024);
        return String.format(Locale.US, "%.2f MB/s", bps / 1048576);
    }

    /** 把速率映射到 0..1 用于画曲线：100MB/s 为满格。 */
    private float scaleRate(double bps) {
        return (float) Math.max(0, Math.min(1, bps / (100.0 * 1048576)));
    }

    private String pct(float v) { return Math.round(v * 100) + "%"; }
    private String pct1(float v) { return String.format(Locale.US, "%.1f%%", v * 100); }

    private String statLine(SparkChart c) {
        if (c == null) return "";
        float[] s = c.stats();
        if (s == null) return "等待数据…";
        return String.format(Locale.US, "均 %.1f%%  高 %.1f%%  低 %.1f%%",
                s[0] * 100, s[1] * 100, s[2] * 100);
    }

    private String freqLoadLine() {
        return "频率负载  峰值 " + pct(cpu.maxRatio()) + "  均值 " + pct1(cpu.avgFreqRatio());
    }

    private String statLineTemp(SparkChart c) {
        if (c == null) return "";
        float[] s = c.stats();
        if (s == null) return "等待数据…";
        return String.format(Locale.US, "均 %.1f℃  高 %.1f℃  低 %.1f℃",
                20 + s[0] * 70, 20 + s[1] * 70, 20 + s[2] * 70);
    }
}
