package com.whale.sysmon.data;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import java.util.Locale;

/**
 * 电池：电量 / 电压 / 温度 / 电流 / 功率 / 状态 / 健康 / 循环次数。
 * 循环次数与电流走 BatteryManager 属性，取不到就显示 --。
 */
public final class Bat {

    public int level = -1;
    public int scale = 100;
    public int status = -1;       // BatteryManager.BATTERY_STATUS_*
    public int health = -1;
    public int plugged = -1;      // 0 无 / 1 AC / 2 USB / 4 无线
    public int voltageMv = -1;
    public int tempTenthC = -1;
    public int chargeUah = -1;
    public long currentUa = Long.MIN_VALUE;  // 负=放电
    public int cycleCount = -1;
    public String tech = "--";
    public int capacityPct = -1;  // 设计/健康百分比（部分机型支持）

    /* sysfs 兜底（部分 ROM BatteryManager 返回 0，只能走文件） */
    public long sysCurrentUa = Long.MIN_VALUE;
    public long sysVoltageUv = Long.MIN_VALUE;
    public long sysCycle = -1;
    public long sysTempTenth = -1;

    private static final String[] PS_PATHS = {
            "/sys/class/power_supply/Battery/",
            "/sys/class/power_supply/battery/",
            "/sys/class/power_supply/hisi_bk_battery/"
    };

    private static long probe(long def, String file) {
        for (String base : PS_PATHS) {
            long v = Sys.readLong(base + file);
            if (v != -1 && v != 0) return v;
            if (v == 0) return 0;
        }
        return def;
    }

    public static Bat read(Context ctx) {
        Bat b = new Bat();
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent i = ctx.registerReceiver(null, f);
            if (i != null) {
                b.level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                b.scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                b.status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                b.health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                b.plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                b.voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                b.tempTenthC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1000);
                b.tech = Sys.clamp(i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY), "--");
            }
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                b.chargeUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                long cur = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (cur != Long.MIN_VALUE && cur != 0) b.currentUa = cur;
                if (Build.VERSION.SDK_INT >= 34) {
                    // BATTERY_PROPERTY_CYCLE_COUNT 是 API34 常量，用反射取以免编译期依赖
                    try {
                        java.lang.reflect.Field cycField = BatteryManager.class
                                .getField("BATTERY_PROPERTY_CYCLE_COUNT");
                        int prop = ((Integer) cycField.get(null)).intValue();
                        b.cycleCount = bm.getIntProperty(prop);
                    } catch (Throwable ignored) {}
                }
                int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (pct >= 0) b.capacityPct = pct;
            }
            if (b.cycleCount < 0) {
                // 部分内核暴露在 sysfs
                long c = Sys.readLong("/sys/class/power_supply/battery/cycle_count");
                if (c >= 0) b.cycleCount = (int) c;
            }

            // sysfs 兜底 + 探针
            b.sysCurrentUa = probe(Long.MIN_VALUE, "current_now");
            b.sysVoltageUv = probe(Long.MIN_VALUE, "voltage_now");
            b.sysCycle = probe(-1, "cycle_count");
            b.sysTempTenth = probe(-1, "temp");
            if (b.sysCycle >= 0 && b.cycleCount < 0) b.cycleCount = (int) b.sysCycle;
            android.util.Log.d("SYS_MON_PROBE", "battery sysfs: cur=" + b.sysCurrentUa
                    + " uV=" + b.sysVoltageUv + " cyc=" + b.sysCycle + " temp=" + b.sysTempTenth
                    + " | bm current=" + b.currentUa);
        } catch (Throwable t) {
            android.util.Log.d("SYS_MON_PROBE", "battery read failed: " + t);
        }
        return b;
    }

    public int percent() {
        if (level < 0 || scale <= 0) return -1;
        return Math.round(level * 100f / scale);
    }

    public float tempC() {
        return tempTenthC == -1000 ? Float.NaN : tempTenthC / 10f;
    }

    public boolean charging() {
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public String statusText() {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            default: return "未知";
        }
    }

    public String plugText() {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "无线";
            default: return "未接";
        }
    }

    public String healthText() {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "良好";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "过热";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "损坏";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "过压";
            case BatteryManager.BATTERY_HEALTH_COLD: return "过冷";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "故障";
            default: return "未知";
        }
    }

    public String voltageText() {
        return voltageMv > 0 ? String.format(Locale.US, "%.3f V", voltageMv / 1000.0) : "--";
    }

    public String tempText() {
        float t = tempC();
        return Float.isNaN(t) ? "--" : String.format(Locale.US, "%.1f ℃", t);
    }

    /** 有效电流（µA）：BatteryManager 优先，为 0 时回落 sysfs。 */
    private long effCurrentUa() {
        if (currentUa != Long.MIN_VALUE && currentUa != 0) return currentUa;
        if (sysCurrentUa != Long.MIN_VALUE && sysCurrentUa != 0) return sysCurrentUa;
        return Long.MIN_VALUE;
    }

    private long effVoltageUv() {
        if (voltageMv > 0) return voltageMv * 1000L;
        if (sysVoltageUv != Long.MIN_VALUE && sysVoltageUv > 0) return sysVoltageUv;
        return -1;
    }

    /** 电流，mA。放电为负值。小于 100mA 时保留一位小数（涓流很明显）。 */
    public String currentText() {
        long ua = effCurrentUa();
        if (ua == Long.MIN_VALUE) return "--";
        double ma = ua / 1000.0;
        if (Math.abs(ma) < 100) return String.format(Locale.US, "%.1f mA", ma);
        return String.format(Locale.US, "%d mA", Math.round(ma));
    }

    /** 瞬时功率，W。 */
    public String powerText() {
        long ua = effCurrentUa();
        long uv = effVoltageUv();
        if (ua == Long.MIN_VALUE || uv <= 0) return "--";
        double w = Math.abs(ua / 1000000.0) * (uv / 1000000.0);
        return String.format(Locale.US, "%.2f W", w);
    }

    public String cycleText() {
        return cycleCount >= 0 ? cycleCount + " 次" : "--";
    }

    /** 电荷量 mAh（ChargeCounter µAh / 1000）。 */
    public String chargeText() {
        return chargeUah > 0 ? String.format(Locale.US, "%d mAh", chargeUah / 1000) : "--";
    }
}
