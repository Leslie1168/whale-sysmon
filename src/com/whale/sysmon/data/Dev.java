package com.whale.sysmon.data;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;

/**
 * 设备 / 系统 静态信息。字段顺序对齐主流"设备信息"类应用的呈现习惯。
 */
public final class Dev {

    public String model = "--";
    public String marketName = "--";
    public String manufacturer = "--";
    public String brand = "--";
    public String device = "--";
    public String board = "--";
    public String hardware = "--";
    public String platform = "--";
    public String bootloader = "--";
    public String fingerprint = "--";
    public String serial = "--";
    public String androidId = "--";
    public String bootloaderLock = "--";

    public String osName = "--";
    public String osVersion = "--";
    public String apiLevel = "--";
    public String rdLevel = "--";        // 鸿蒙 API / SDK 扩展
    public String buildId = "--";
    public String buildTime = "--";
    public String incremental = "--";
    public String securityPatch = "--";
    public String baseband = "--";
    public String javaRuntime = "--";
    public String kernel = "--";
    public String language = "--";
    public String timezone = "--";
    public boolean rooted;
    public long uptimeMs;
    public String treble = "--";
    public String abi = "--";

    public static Dev read(Context ctx) {
        Dev d = new Dev();
        d.model = Sys.clamp(Build.MODEL, "--");
        d.manufacturer = Sys.clamp(Build.MANUFACTURER, "--");
        d.brand = Sys.clamp(Build.BRAND, "--");
        d.device = Sys.clamp(Build.DEVICE, "--");
        d.board = Sys.clamp(Build.BOARD, "--");
        d.hardware = Sys.clamp(Build.HARDWARE, "--");
        d.bootloader = Sys.clamp(Build.BOOTLOADER, "--");
        d.fingerprint = Sys.clamp(Build.FINGERPRINT, "--");
        d.platform = Sys.firstProp(Sys.clamp(Build.HARDWARE, "--"),
                "ro.board.platform", "ro.boot.hardware.platform", "ro.hardware");

        // 营销名（各家不同键）
        d.marketName = Sys.firstProp(d.model,
                "ro.product.marketname", "ro.vendor.oplus.market.name",
                "ro.vivo.internet.name", "ro.oppo.market.name",
                "ro.config.marketing_name", "ro.vendor.product.ztename",
                "ro.product.odm.marketname", "ro.vendor.asus.product.mkt_name");

        // Bootloader 锁状态
        String locked = Sys.firstProp("",
                "ro.boot.flash.locked", "ro.secureboot.lockstate", "ro.boot.vbmeta.device_state");
        if (locked.isEmpty()) d.bootloaderLock = "--";
        else if (locked.equals("1") || locked.equalsIgnoreCase("locked")) d.bootloaderLock = "locked";
        else if (locked.equalsIgnoreCase("green")) d.bootloaderLock = "locked";
        else d.bootloaderLock = "unlocked";

        d.androidId = safeAndroidId(ctx);
        d.serial = Sys.firstProp("unknown", "ro.serialno", "ro.boot.serialno");

        // OS
        d.osVersion = Sys.clamp(Build.VERSION.RELEASE, "--");
        d.apiLevel = String.valueOf(Build.VERSION.SDK_INT);
        d.buildId = Sys.clamp(Build.ID, "--");
        d.buildTime = formatTime(Build.TIME);
        d.incremental = Sys.firstProp(Sys.clamp(Build.VERSION.INCREMENTAL, "--"),
                "ro.build.version.incremental");
        // 华为/荣耀把真实补丁放在 ro.huawei.*，安卓兼容层的 ro.build.version 是旧值
        d.securityPatch = Sys.firstProp(Sys.clamp(Build.VERSION.SECURITY_PATCH, "--"),
                "ro.huawei.build.version.security_patch",
                "ro.honor.build.version.security_patch",
                "ro.vendor.build.version.security_patch",
                "ro.build.version.security_patch");
        d.baseband = Sys.firstProp("--", "gsm.version.baseband", "ro.baseband");
        d.javaRuntime = Sys.clamp(System.getProperty("java.vm.version"), "--");
        d.treble = Sys.firstProp("--", "ro.treble.enabled");
        d.abi = Sys.firstProp("--", "ro.product.cpu.abi");

        // 厂商系统名（EMUI / HyperOS / ColorOS / OriginOS ...）
        d.osName = detectOsName();
        d.rdLevel = Sys.firstProp("", "ro.build.hw_emui_api_level");
        if (d.rdLevel.isEmpty()) {
            String ext = Sys.firstProp("", "ro.build.version.emui", "ro.build.version.magic",
                    "ro.mi.os.version.name", "ro.build.version.oplusrom");
            d.rdLevel = ext.isEmpty() ? "--" : ext;
        }

        String k = Sys.readLine("/proc/version");
        if (k.isEmpty()) {
            // /proc/version 常被 SELinux 拦，Java 层的 os.version 就是内核版本
            String osv = System.getProperty("os.version");
            k = osv == null ? "" : ("Linux " + osv);
        }
        d.kernel = k.isEmpty() ? "--" : k;

        d.language = java.util.Locale.getDefault().getDisplayName();
        java.util.TimeZone tz = java.util.TimeZone.getDefault();
        d.timezone = tz.getDisplayName(false, java.util.TimeZone.SHORT) + " (" + tz.getID() + ")";
        d.rooted = checkRoot();
        d.uptimeMs = android.os.SystemClock.elapsedRealtime();
        return d;
    }

    private static String safeAndroidId(Context ctx) {
        try {
            String s = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            return s == null ? "--" : s;
        } catch (Throwable t) {
            return "--";
        }
    }

    private static String detectOsName() {
        // 华为设备优先报 HarmonyOS（hw_sc.build.platform.version），EMUI 只是兼容层版本
        String harmony = Sys.firstProp("", "hw_sc.build.platform.version",
                "ro.build.version.harmony", "persist.sys.harmony.version");
        if (!harmony.isEmpty()) return "HarmonyOS " + harmony;
        String emui = Sys.prop("ro.build.version.emui", "");
        if (!emui.isEmpty()) return "EMUI " + emui.replace("EmotionUI_", "");
        String magic = Sys.prop("ro.build.version.magic", "");
        if (!magic.isEmpty()) return "MagicOS " + magic;
        String mi = Sys.prop("ro.mi.os.version.name", "");
        if (!mi.isEmpty()) return "HyperOS " + mi;
        String miui = Sys.prop("ro.miui.ui.version.name", "");
        if (!miui.isEmpty()) return "MIUI " + miui;
        String color = Sys.prop("ro.build.version.coloros", "");
        if (!color.isEmpty()) return "ColorOS " + color;
        String oplus = Sys.prop("ro.build.version.oplusrom", "");
        if (!oplus.isEmpty()) return "ColorOS " + oplus;
        String orig = Sys.prop("ro.vivo.os.version", "");
        if (!orig.isEmpty()) return "OriginOS " + orig;
        String fly = Sys.prop("ro.build.version.flyme", "");
        if (!fly.isEmpty()) return "Flyme " + fly;
        String oneui = Sys.prop("ro.build.version.oneui", "");
        if (!oneui.isEmpty()) return "One UI " + oneui;
        String zui = Sys.prop("ro.build.version.zui", "");
        if (!zui.isEmpty()) return "ZUI " + zui;
        return "Android " + Build.VERSION.RELEASE;
    }

    private static boolean checkRoot() {
        String[] paths = {
                "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
                "/data/local/bin/su", "/data/local/xbin/su", "/system/sd/xbin/su"
        };
        for (String p : paths) {
            try {
                if (new java.io.File(p).exists()) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static String formatTime(long ms) {
        try {
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
            return f.format(new java.util.Date(ms));
        } catch (Throwable t) {
            return "--";
        }
    }

    public String uptimeText() {
        long s = uptimeMs / 1000L;
        long d = s / 86400L;
        long h = (s % 86400L) / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (d > 0) return d + " 天 " + h + " 小时 " + m + " 分";
        if (h > 0) return h + " 时 " + m + " 分 " + sec + " 秒";
        return m + " 分 " + sec + " 秒";
    }
}
