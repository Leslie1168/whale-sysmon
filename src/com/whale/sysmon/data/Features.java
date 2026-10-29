package com.whale.sysmon.data;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.ConsumerIrManager;
import android.telephony.TelephonyManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * 硬件能力清单：勾/叉，对齐设备信息X 的"能力"分组。
 */
public final class Features {

    public static final class Item {
        public final String name;
        public final boolean on;
        public Item(String name, boolean on) { this.name = name; this.on = on; }
    }

    public final List<Item> items = new ArrayList<Item>();

    public static Features read(Context ctx) {
        Features f = new Features();
        PackageManager pm = ctx.getPackageManager();

        f.add("NFC", pm.hasSystemFeature(PackageManager.FEATURE_NFC));
        f.add("NFC 卡模拟", Build.VERSION.SDK_INT >= 24
                && pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION));
        f.add("指纹识别", Build.VERSION.SDK_INT >= 23
                && pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT));
        f.add("人脸识别", Build.VERSION.SDK_INT >= 29
                && pm.hasSystemFeature(PackageManager.FEATURE_FACE));
        f.add("虹膜识别", pm.hasSystemFeature(PackageManager.FEATURE_IRIS));
        f.add("蓝牙", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
        f.add("蓝牙 LE", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE));
        f.add("红外发射器", hasIr(ctx));
        f.add("UWB", Build.VERSION.SDK_INT >= 31
                && pm.hasSystemFeature(PackageManager.FEATURE_UWB));
        f.add("WLAN", pm.hasSystemFeature(PackageManager.FEATURE_WIFI));
        f.add("WiFi 直连", pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));
        f.add("以太网", pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET));
        f.add("GPS/GNSS", pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS));
        f.add("USB 主机", pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST));
        f.add("USB 配件", pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY));
        f.add("闪光灯", pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH));
        f.add("蜂窝通信", pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        f.add("eSIM", Build.VERSION.SDK_INT >= 28
                && pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC));
        f.add("加速度计", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER));
        f.add("陀螺仪", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE));
        f.add("磁力计", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_COMPASS));
        f.add("气压计", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER));
        f.add("光线传感器", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT));
        f.add("接近传感器", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY));
        f.add("步骤计数", Build.VERSION.SDK_INT >= 19
                && pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER));
        f.add("心跳传感器", pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE));
        f.add("触摸屏", pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN));
        f.add("多点触控", pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH));
        f.add("无线显示", Build.VERSION.SDK_INT >= 21
                && pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE));
        f.add("低延迟音频", pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY));
        f.add("专业音频", pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO));
        f.add("麦克风", pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
        f.add("自动对焦", pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS));
        f.add("前置相机", pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT));
        f.add("屏幕指纹", Build.VERSION.SDK_INT >= 29
                && pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
                && hasUnderDisplayFp(pm));
        f.add("64 位", Build.SUPPORTED_64_BIT_ABIS != null
                && Build.SUPPORTED_64_BIT_ABIS.length > 0);
        return f;
    }

    private void add(String name, boolean on) {
        items.add(new Item(name, on));
    }

    private static boolean hasIr(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                ConsumerIrManager ir = (ConsumerIrManager) ctx.getSystemService("consumer_ir");
                return ir != null && ir.hasIrEmitter();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean hasUnderDisplayFp(PackageManager pm) {
        for (String n : new String[]{
                "com.huawei.hardware.biometrics.fingerprint.ud", "android.hardware.biometrics.fingerprint.ud"}) {
            if (pm.hasSystemFeature(n)) return true;
        }
        return false;
    }

    public int count() { return items.size(); }

    public int supported() {
        int n = 0;
        for (Item i : items) if (i.on) n++;
        return n;
    }
}
