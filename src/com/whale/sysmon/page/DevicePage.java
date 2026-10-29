package com.whale.sysmon.page;

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

import com.whale.sysmon.data.Bat;
import com.whale.sysmon.data.Camera;
import com.whale.sysmon.data.Cpu;
import com.whale.sysmon.data.Dev;
import com.whale.sysmon.data.Disp;
import com.whale.sysmon.data.Features;
import com.whale.sysmon.data.Gpu;
import com.whale.sysmon.data.Mem;
import com.whale.sysmon.data.Net;
import com.whale.sysmon.data.Store;
import com.whale.sysmon.data.Sys;
import com.whale.sysmon.data.Thermal;
import com.whale.sysmon.ui.Palette;
import com.whale.sysmon.ui.Ui;

import java.util.List;
import java.util.Locale;

/**
 * 设备页：一级是分类列表，点进去看该类别的全部字段。
 */
public class DevicePage {

    private static final String[] GROUPS = {
            "设备", "系统", "处理器", "图形", "屏幕",
            "网络", "存储", "电池", "温度", "相机", "硬件能力", "传感器", "应用"
    };

    private final Context ctx;
    private final ScrollView scroll;
    private final LinearLayout root;

    private int current = -1;   // -1 = 列表态

    private TextView tvUptime, tvTemp, tvBatTemp, tvMemAvail, tvNetSignal;

    public DevicePage(Context ctx) {
        this.ctx = ctx;
        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Palette.BG);
        scroll.setClipToPadding(false);

        root = Ui.col(ctx);
        root.setBackgroundColor(Palette.BG);
        int px = Ui.dp(ctx, 16);
        root.setPadding(px, Ui.dp(ctx, 44), px, Ui.dp(ctx, 112));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        showList();
    }

    public View view() { return scroll; }

    public void scrollToTop() { scroll.scrollTo(0, 0); }

    /** 返回键：详情态回列表，列表态返回 false 交给系统。 */
    public boolean onBack() {
        if (current >= 0) {
            showList();
            return true;
        }
        return false;
    }

    public void refresh() {
        try {
            if (tvUptime != null) tvUptime.setText(Dev.read(ctx).uptimeText());
            if (tvTemp != null) tvTemp.setText(Thermal.read().maxText());
            if (tvBatTemp != null) tvBatTemp.setText(Bat.read(ctx).tempText());
            if (tvMemAvail != null) tvMemAvail.setText(Mem.read().availText());
            if (tvNetSignal != null) tvNetSignal.setText(Net.read(ctx).signalText());
        } catch (Throwable ignored) {}
    }

    /* ------------------------------ 列表态 ------------------------------ */

    private void showList() {
        current = -1;
        tvUptime = tvTemp = tvBatTemp = tvMemAvail = tvNetSignal = null;
        root.removeAllViews();
        scroll.scrollTo(0, 0);

        Dev d = Dev.read(ctx);

        root.addView(Ui.tv(ctx, "设备", 30f, Palette.TEXT, Ui.HEAVY));
        root.addView(Ui.tv(ctx, d.model + "  ·  " + d.osName, 12f, Palette.TEXT_DIM,
                Typeface.DEFAULT), Ui.lpTop(ctx, 5));

        // 头部概要卡
        LinearLayout hero = Ui.card(ctx);
        LinearLayout hr = Ui.row(ctx);
        hr.addView(Ui.tv(ctx, d.manufacturer.toUpperCase(Locale.US), 16f,
                Palette.CYAN, Typeface.DEFAULT_BOLD));
        hr.addView(Ui.tv(ctx, "  " + d.model, 16f, Palette.TEXT, Typeface.DEFAULT));
        hero.addView(hr);
        hero.addView(Ui.tv(ctx, d.osName + "  ·  Android " + d.osVersion
                + "  ·  API " + d.apiLevel, 11f, Palette.TEXT_DIM, Typeface.DEFAULT),
                Ui.lpTop(ctx, 6));
        root.addView(hero, Ui.lpTop(ctx, 18));

        // 分类入口
        for (int i = 0; i < GROUPS.length; i++) {
            final int idx = i;
            LinearLayout item = Ui.row(ctx);
            item.setBackground(Ui.round(ctx, Palette.CARD, Palette.STROKE, 16f));
            int p = Ui.dp(ctx, 16);
            item.setPadding(p, Ui.dp(ctx, 15), p, Ui.dp(ctx, 15));
            item.setClickable(true);

            LinearLayout mid = Ui.col(ctx);
            mid.addView(Ui.tv(ctx, GROUPS[i], 14f, Palette.TEXT, Typeface.DEFAULT_BOLD));
            mid.addView(Ui.tv(ctx, summaryOf(i), 10f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                    Ui.lpTop(ctx, 4));
            item.addView(mid, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            item.addView(Ui.tv(ctx, "›", 20f, Palette.TEXT_FAINT, Typeface.DEFAULT));

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showGroup(idx); }
            });
            root.addView(item, Ui.lpTop(ctx, 10));
        }

        root.addView(Ui.tv(ctx, "共 " + GROUPS.length + " 类 · 数据来自 Build / getprop / proc / sysfs",
                10f, Palette.TEXT_FAINT, Typeface.DEFAULT), Ui.lpTop(ctx, 18));
    }

    /** 列表项下方的摘要，让用户没点进去就知道里面有什么。 */
    private String summaryOf(int i) {
        try {
            switch (i) {
                case 0: {
                    Dev d = Dev.read(ctx);
                    return d.model + " · " + d.device + " · " + d.serial;
                }
                case 1: {
                    Dev d = Dev.read(ctx);
                    return d.osName + " · 补丁 " + d.securityPatch;
                }
                case 2: {
                    Cpu c = Cpu.read();
                    return c.soc + " · " + c.coreCount + " 核 · " + c.freqRange();
                }
                case 3: {
                    Gpu g = Gpu.read(ctx);
                    return g.model + " · " + g.freqText();
                }
                case 4: {
                    Disp dp = Disp.read(ctx);
                    return dp.resText() + " · " + dp.refreshText() + " · " + dp.densityDpi + " dpi";
                }
                case 5: {
                    Net n = Net.read(ctx);
                    return n.type + " · " + n.ip + " · " + n.ssid;
                }
                case 6: {
                    Store s = Store.internal();
                    return s.usedText() + " / " + s.totalText() + " · " + s.pctText();
                }
                case 7: {
                    Bat b = Bat.read(ctx);
                    return (b.percent() >= 0 ? b.percent() + "%" : "--") + " · "
                            + b.statusText() + " · " + b.tempText();
                }
                case 8: {
                    Thermal t = Thermal.read();
                    return t.maxText() + " " + shortName(t.maxName)
                            + " · " + t.zones.size() + " 个传感器";
                }
                case 9: {
                    Camera c = Camera.read(ctx);
                    return c.cams.size() + " 个摄像头";
                }
                case 10: {
                    Features f = Features.read(ctx);
                    return f.supported() + " / " + f.count() + " 项支持";
                }
                case 11:
                    return sensorCount() + " 个";
                default: {
                    int n3 = com.whale.sysmon.data.ShellData.appCount3rd();
                    return (n3 >= 0 ? n3 + " 个第三方" : "需 Shizuku 授权")
                            + " · 应用清单";
                }
            }
        } catch (Throwable t) {
            return "--";
        }
    }

    /* ------------------------------ 详情态 ------------------------------ */

    private void showGroup(int idx) {
        current = idx;
        tvUptime = tvTemp = tvBatTemp = tvMemAvail = tvNetSignal = null;
        root.removeAllViews();
        scroll.scrollTo(0, 0);

        // 返回栏
        LinearLayout bar = Ui.row(ctx);
        TextView back = Ui.tv(ctx, "‹  返回", 13f, Palette.CYAN, Typeface.DEFAULT_BOLD);
        back.setPadding(0, Ui.dp(ctx, 8), Ui.dp(ctx, 16), Ui.dp(ctx, 8));
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showList(); }
        });
        bar.addView(back);
        root.addView(bar);

        root.addView(Ui.tv(ctx, GROUPS[idx], 26f, Palette.TEXT, Ui.HEAVY),
                Ui.lpTop(ctx, 6));

        LinearLayout card = Ui.card(ctx);
        root.addView(card, Ui.lpTop(ctx, 14));

        switch (idx) {
            case 0: groupDevice(card); break;
            case 1: groupSystem(card); break;
            case 2: groupCpu(card); break;
            case 3: groupGpu(card); break;
            case 4: groupDisp(card); break;
            case 5: groupNet(card); break;
            case 6: groupStore(card); break;
            case 7: groupBat(card); break;
            case 8: groupThermal(card); break;
            case 9: groupCamera(card); break;
            case 10: groupCaps(card); break;
            case 11: groupSensors(card); break;
            default: groupApps(card); break;
        }
    }

    private void groupDevice(LinearLayout c) {
        Dev d = Dev.read(ctx);
        add(c, "设备名称", d.marketName);
        add(c, "型号", d.model);
        add(c, "制造商", d.manufacturer);
        add(c, "品牌", d.brand);
        add(c, "设备代号", d.device);
        add(c, "主板", d.board);
        add(c, "平台", d.platform);
        add(c, "Bootloader", d.bootloaderLock);
        add(c, "硬件序列号", d.serial);
        add(c, "Android ID", d.androidId);
        addMulti(c, "设备指纹", d.fingerprint);
    }

    private void groupSystem(LinearLayout c) {
        Dev d = Dev.read(ctx);
        add(c, "系统", d.osName);
        add(c, "兼容安卓", "Android " + d.osVersion);
        add(c, "API 级别", d.apiLevel);
        add(c, "版本扩展", d.rdLevel);
        add(c, "构建编号", d.buildId);
        add(c, "构建时间", d.buildTime);
        add(c, "设备版本号", d.incremental);
        add(c, "安全补丁", d.securityPatch);
        add(c, "基带版本", d.baseband);
        addMulti(c, "内核", d.kernel);
        add(c, "Java 运行时", d.javaRuntime);
        add(c, "ABI", d.abi);
        addMulti(c, "支持的 ABI", joinAbis());
        add(c, "Project Treble", yesNo("true".equalsIgnoreCase(d.treble)
                || "enabled".equalsIgnoreCase(d.treble)));
        add(c, "动态分区", yesNo("true".equalsIgnoreCase(
                Sys.prop("ro.boot.dynamic_partitions", ""))));
        add(c, "A/B 分区", Sys.prop("ro.boot.slot_suffix", "").isEmpty() ? "不支持" : "支持");
        add(c, "SELinux", Sys.prop("ro.boot.selinux", "--"));
        add(c, "Root 权限", d.rooted ? "有" : "无");
        tvUptime = addLive(c, "已开机时长", d.uptimeText());
        add(c, "语言", d.language);
        add(c, "时区", d.timezone);
    }

    private void groupCpu(LinearLayout c) {
        Cpu cpu = Cpu.read();
        add(c, "SoC", cpu.soc);
        add(c, "CPU 硬件", cpu.hardware);
        add(c, "核心数", cpu.coreCount + " 核");
        add(c, "频率范围", cpu.freqRange());
        addMulti(c, "核心分组", cpu.clusterDesc());
        add(c, "调度器", cpu.governor);
        add(c, "64 位", sys64() ? "是" : "否");
        add(c, "当前最高频", cpu.curText(topCore(cpu)));
        // 每个核当前频率
        for (int i = 0; i < cpu.cores.size(); i++) {
            add(c, "核心 " + i, cpu.curText(i) + "   /   " + cpu.freqMaxText(i));
        }
    }

    private void groupGpu(LinearLayout c) {
        Gpu g = Gpu.read(ctx);
        add(c, "GPU 型号", g.model);
        add(c, "GPU 供应商", g.vendor);
        add(c, "GPU 当前频率", g.freqText());
        add(c, "GPU 频率范围", g.rangeText());
        add(c, "图形接口", g.gles);
        add(c, "GPU 负载", g.loadText());
        addMulti(c, "读取路径", g.freqPath.isEmpty() ? "--" : g.freqPath);
        add(c, "负载节点", g.loadPath.isEmpty() ? "无（该 SoC 不暴露）" : g.loadPath);
    }

    private void groupDisp(LinearLayout c) {
        Disp d = Disp.read(ctx);
        add(c, "分辨率", d.resText());
        add(c, "宽高比", d.ratioText());
        add(c, "屏幕密度", d.densityText());
        add(c, "物理尺寸", d.sizeText());
        add(c, "刷新率", d.refreshText());
        add(c, "支持刷新率", d.refreshListText());
        add(c, "HDR", d.hdr);
        add(c, "广色域", yesNo(d.wideColor));
        add(c, "X/Y DPI", String.format(Locale.US, "%.1f / %.1f", d.xdpi, d.ydpi));
    }

    private void groupNet(LinearLayout c) {
        Net n = Net.read(ctx);
        add(c, "当前网络", n.type);
        add(c, "WiFi 状态", n.wifiState);
        add(c, "WiFi 名称", n.ssid);
        add(c, "BSSID", n.bssid);
        tvNetSignal = addLive(c, "信号强度", n.signalText());
        add(c, "Wi-Fi 标准", n.std);
        add(c, "安全性", n.security);
        add(c, "链路速率", n.speedText());
        add(c, "频段", n.freqText());
        add(c, "IPv4", n.ip);
        addMulti(c, "IPv6", n.ip6);
        add(c, "网关", n.gateway);
        addMulti(c, "DNS", n.dns);
        add(c, "WLAN MAC", n.mac);
        add(c, "运营商", n.operatorName);
        add(c, "SIM 卡", n.simOperator + "  ·  " + n.simState);
        add(c, "电话类型", n.phoneType);
        add(c, "累计接收", n.rxText());
        add(c, "累计发送", n.txText());
    }

    private void groupStore(LinearLayout c) {
        Store s = Store.internal();
        Mem m = Mem.read();
        add(c, "内部存储", s.usedText() + " / " + s.totalText());
        add(c, "占用比例", s.pctText() + "   可用 " + s.freeText());
        add(c, "内存总计", m.totalText());
        add(c, "内存已用", m.usedText());
        tvMemAvail = addLive(c, "内存可用", m.availText());
        add(c, "Swap", m.swapText());
        add(c, "Buffers", Sys.bytes(m.buffersKb * 1024L));
        add(c, "Cached", Sys.bytes(m.cachedKb * 1024L));
        add(c, "Slab", Sys.bytes(m.slabKb * 1024L));
    }

    private void groupBat(LinearLayout c) {
        Bat b = Bat.read(ctx);
        add(c, "电量", (b.percent() >= 0 ? b.percent() + "%" : "--") + "   " + b.statusText());
        add(c, "电压", b.voltageText());
        add(c, "电流", b.currentText());
        add(c, "实时功率", b.powerText());
        tvBatTemp = addLive(c, "温度", b.tempText());
        add(c, "健康状态", b.healthText());
        add(c, "电池技术", b.tech);
        add(c, "充电方式", b.plugText());
        add(c, "电荷量", b.chargeText());
        add(c, "循环次数", b.cycleCount >= 0 ? b.cycleCount + " 次"
                : "系统未提供（厂商私有接口）");
    }

    private void groupThermal(LinearLayout c) {
        Thermal t = Thermal.read();
        tvTemp = addLive(c, "最高温度", t.maxText() + "  " + shortName(t.maxName));
        add(c, "最高位置", t.maxName.isEmpty() ? "--" : t.maxName);
        add(c, "平均温度", t.avgText());
        add(c, "传感器数量", t.zones.size() + " 个");

        if (!t.normalized.isEmpty()) {
            add(c, "─", "thermalservice 规范读数");
            for (Thermal.Zone z : t.normalized) {
                add(c, z.name, String.format(Locale.US, "%.1f℃", z.celsius));
            }
            add(c, "─", "sysfs thermal_zone 原始读数");
        }
        for (int i = 0; i < t.zones.size() && i < 20; i++) {
            Thermal.Zone z = t.zones.get(i);
            // -40 / 125 这类是内核未初始化的默认值，别当真实读数显示
            boolean bogus = z.celsius < -10f || z.celsius > 120f;
            add(c, z.name, bogus ? "--" : String.format(Locale.US, "%.1f℃", z.celsius));
        }
    }

    private void groupCamera(LinearLayout c) {
        Camera cam = Camera.read(ctx);
        add(c, "摄像头数量", cam.cams.size() + " 个");
        // 闪光灯按系统能力报（Camera2 的 FLASH_INFO_AVAILABLE 在部分华为机型上恒 false）
        boolean flash = false;
        try {
            flash = ctx.getPackageManager()
                    .hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH);
        } catch (Throwable ignored) {}
        add(c, "闪光灯", flash ? "有" : "无");
        for (Camera.Cam x : cam.cams) {
            add(c, "镜头 " + x.id + " · " + x.facing, x.maxPhoto);
            if (!"--".equals(x.maxVideo)) add(c, "  最高录像", x.maxVideo);
            add(c, "  光圈 / 防抖", x.aperture + (x.ois ? "  OIS" : ""));
        }
    }

    private void groupCaps(LinearLayout c) {
        Features f = Features.read(ctx);
        add(c, "支持数量", f.supported() + " / " + f.count());
        LinearLayout rowView = null;
        for (int i = 0; i < f.items.size(); i++) {
            if (i % 2 == 0) {
                rowView = Ui.row(ctx);
                rowView.setPadding(0, Ui.dp(ctx, 5), 0, Ui.dp(ctx, 5));
                c.addView(rowView);
            }
            rowView.addView(capCell(f.items.get(i)), Ui.weight(1));
        }
        if (rowView != null && f.items.size() % 2 == 1) {
            rowView.addView(new View(ctx), Ui.weight(1));
        }
    }

    private void groupSensors(LinearLayout c) {
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> list = sm.getSensorList(Sensor.TYPE_ALL);
            add(c, "总数", list.size() + " 个");
            for (Sensor s : list) {
                add(c, s.getName(), String.format(Locale.US, "%.0f", s.getMaximumRange()));
            }
        } catch (Throwable t) {
            add(c, "读取失败", String.valueOf(t));
        }
    }

    private void groupApps(LinearLayout c) {
        boolean ready = com.whale.sysmon.data.ShellData.ready();
        add(c, "Shizuku 通道", ready ? "已授权" : "未授权（工具页可开）");
        int all = com.whale.sysmon.data.ShellData.appCount();
        int third = com.whale.sysmon.data.ShellData.appCount3rd();
        add(c, "应用总数", all >= 0 ? all + " 个" : "需 Shizuku 授权");
        add(c, "第三方应用", third >= 0 ? third + " 个" : "需 Shizuku 授权");
        if (ready) {
            java.util.List<String> pkgs = com.whale.sysmon.data.ShellData.thirdPartyPackages();
            for (int i = 0; i < pkgs.size() && i < 40; i++) {
                add(c, String.valueOf(i + 1), pkgs.get(i));
            }
            if (pkgs.size() > 40) {
                add(c, "…", "另有 " + (pkgs.size() - 40) + " 个未显示");
            }
        }
    }

    /* ---------------------------- 小工具 ---------------------------- */

    private View capCell(Features.Item it) {
        LinearLayout r = Ui.row(ctx);
        r.addView(Ui.tv(ctx, it.on ? "✓" : "✕", 11f,
                it.on ? Palette.GREEN : Palette.TEXT_FAINT, Typeface.DEFAULT_BOLD));
        TextView name = Ui.tv(ctx, " " + it.name, 11f,
                it.on ? Palette.TEXT : Palette.TEXT_FAINT, Typeface.DEFAULT);
        name.setMaxLines(1);
        r.addView(name);
        return r;
    }

    private boolean sys64() {
        return android.os.Build.SUPPORTED_64_BIT_ABIS != null
                && android.os.Build.SUPPORTED_64_BIT_ABIS.length > 0;
    }

    private String joinAbis() {
        String[] a = android.os.Build.SUPPORTED_ABIS;
        if (a == null || a.length == 0) return "--";
        StringBuilder sb = new StringBuilder();
        for (String s : a) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    private int topCore(Cpu cpu) {
        int idx = 0;
        long mx = -1;
        for (int i = 0; i < cpu.cores.size(); i++) {
            if (cpu.cores.get(i).maxKhz > mx) { mx = cpu.cores.get(i).maxKhz; idx = i; }
        }
        return idx;
    }

    private int sensorCount() {
        try {
            SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
            List<Sensor> l = sm.getSensorList(Sensor.TYPE_ALL);
            return l == null ? 0 : l.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private String yesNo(boolean b) { return b ? "支持" : "不支持"; }

    /** cpu-1-0-step -> CPU；gpuss-0-usr -> GPU；其余取前段。 */
    private String shortName(String n) {
        if (n == null || n.isEmpty()) return "";
        String s = n.toLowerCase(java.util.Locale.US);
        if (s.startsWith("cpu")) return "CPU";
        if (s.startsWith("gpuss") || s.startsWith("gpu")) return "GPU";
        if (s.startsWith("apc")) return "CPU";
        if (s.contains("batt") || s.contains("bms") || s.contains("battery")) return "电池";
        if (s.contains("ddr")) return "DDR";
        if (s.contains("modem") || s.contains("pa")) return "射频";
        if (s.contains("skin")) return "外壳";
        int d = s.indexOf('-');
        return d > 0 ? s.substring(0, d) : s;
    }

    private void add(LinearLayout card, String k, String v) {
        card.addView(row(k, v, Palette.TEXT, false));
    }

    private void addMulti(LinearLayout card, String k, String v) {
        card.addView(row(k, v, Palette.TEXT, true));
    }

    private TextView addLive(LinearLayout card, String k, String v) {
        LinearLayout r = row(k, v, Palette.CYAN, false);
        card.addView(r);
        return (TextView) r.getChildAt(1);
    }

    private LinearLayout row(String k, String v, int valueColor, boolean multiline) {
        if (multiline) {
            LinearLayout box = Ui.col(ctx);
            box.setPadding(0, Ui.dp(ctx, 7), 0, Ui.dp(ctx, 7));
            box.addView(Ui.tv(ctx, k, 12f, Palette.TEXT_FAINT, Typeface.DEFAULT));
            TextView vv = Ui.tv(ctx, v == null ? "--" : v, 12f, valueColor, Typeface.DEFAULT);
            vv.setLineSpacing(0, 1.3f);
            box.addView(vv, Ui.lpTop(ctx, 4));
            return box;
        }
        LinearLayout r = Ui.row(ctx);
        r.setPadding(0, Ui.dp(ctx, 7), 0, Ui.dp(ctx, 7));
        r.addView(Ui.tv(ctx, k, 12f, Palette.TEXT_FAINT, Typeface.DEFAULT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView vv = Ui.tv(ctx, v == null ? "--" : v, 12f, valueColor, Typeface.DEFAULT);
        vv.setGravity(Gravity.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(ctx, 14);
        r.addView(vv, lp);
        return r;
    }
}
