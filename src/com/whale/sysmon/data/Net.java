package com.whale.sysmon.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 网络：WiFi 明细、IP/DNS、运营商、流量统计。
 * 部分字段在 Android 10+ 受限（SSID/BSSID 需要定位权限），拿不到就如实显示。
 */
public final class Net {

    public String wifiState = "--";
    public String ssid = "--";
    public String bssid = "--";
    public int rssi = 0;
    public int signalPct = -1;
    public int linkSpeed = -1;
    public int frequency = -1;
    public String std = "--";
    public String security = "--";
    public String ip = "--";
    public String ip6 = "--";
    public String gateway = "--";
    public String dns = "--";
    public String mac = "--";
    public String type = "--";        // 当前连接类型
    public String operatorName = "--";
    public String simOperator = "--";
    public String phoneType = "--";
    public String simState = "--";
    public long rxBytes, txBytes;

    public static Net read(Context ctx) {
        Net n = new Net();

        try {
            TrafficStats ts = null;
            n.rxBytes = TrafficStats.getTotalRxBytes();
            n.txBytes = TrafficStats.getTotalTxBytes();
        } catch (Throwable ignored) {}

        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            try {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni != null) n.type = ni.getTypeName();
            } catch (Throwable ignored) {}
            try {
                Network act = cm.getActiveNetwork();
                if (act != null) {
                    LinkProperties lp = cm.getLinkProperties(act);
                    if (lp != null) {
                        for (android.net.LinkAddress la : lp.getLinkAddresses()) {
                            InetAddress a = la.getAddress();
                            if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                                n.ip = a.getHostAddress();
                            } else if (a instanceof Inet6Address && !a.isLoopbackAddress()
                                    && !a.isLinkLocalAddress()) {
                                String s6 = a.getHostAddress();
                                int pct = s6.indexOf('%');
                                if (pct > 0) s6 = s6.substring(0, pct);
                                if (n.ip6.equals("--")) n.ip6 = s6;
                            }
                        }
                        String gw6 = null;
                        for (android.net.RouteInfo ri : lp.getRoutes()) {
                            if (!ri.isDefaultRoute() || ri.getGateway() == null) continue;
                            InetAddress gwa = ri.getGateway();
                            if (gwa.isAnyLocalAddress()) continue;
                            if (gwa instanceof Inet4Address) {
                                n.gateway = gwa.getHostAddress();
                                gw6 = null;
                                break;
                            }
                            if (gw6 == null) gw6 = gwa.getHostAddress();
                        }
                        if (n.gateway.equals("--") && gw6 != null) n.gateway = gw6;
                        List<String> dnsList = new ArrayList<String>();
                        for (InetAddress a : lp.getDnsServers()) {
                            if (a instanceof Inet4Address) dnsList.add(a.getHostAddress());
                        }
                        if (!dnsList.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (String s : dnsList) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(s);
                            }
                            n.dns = sb.toString();
                        }
                    }
                    NetworkCapabilities nc = cm.getNetworkCapabilities(act);
                    if (nc != null) {
                        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) n.type = "WIFI";
                        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) n.type = "移动数据";
                        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) n.type = "以太网";
                    }
                }
            } catch (Throwable ignored) {}
        }

        WifiManager wm = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            try {
                int state = wm.getWifiState();
                n.wifiState = state == WifiManager.WIFI_STATE_ENABLED ? "已开启"
                        : (state == WifiManager.WIFI_STATE_DISABLED ? "已关闭" : "未知");
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) {
                    n.ssid = clean(wi.getSSID());
                    n.bssid = maskMac(wi.getBSSID());
                    n.rssi = wi.getRssi();
                    n.linkSpeed = wi.getLinkSpeed();
                    if (Build.VERSION.SDK_INT >= 30) {
                        n.frequency = wi.getFrequency();
                        n.signalPct = wi.getMaxSupportedRxLinkSpeedMbps() > 0
                                ? WifiManager.calculateSignalLevel(wi.getRssi(), 100) : -1;
                    } else {
                        n.signalPct = WifiManager.calculateSignalLevel(wi.getRssi(), 100);
                    }
                    n.mac = maskMac(wi.getMacAddress());
                    n.std = wifiStandard(wi);
                    n.security = wifiSecurityFromScan(wm, n.bssid);
                    if ("--".equals(n.security)) n.security = wifiSecurity(wi);
                }
                try {
                    android.net.DhcpInfo di = wm.getDhcpInfo();
                    if (di != null) {
                        if (n.ip.equals("--")) n.ip = intToIp(di.ipAddress);
                        if (n.gateway.equals("--")) n.gateway = intToIp(di.gateway);
                        if (n.dns.equals("--")) {
                            String d1 = intToIp(di.dns1);
                            String d2 = intToIp(di.dns2);
                            n.dns = d2.equals("0.0.0.0") ? d1 : (d1 + ", " + d2);
                        }
                    }
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        }

        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                n.operatorName = Sys.clamp(tm.getNetworkOperatorName(), "--");
                n.simOperator = Sys.clamp(tm.getSimOperatorName(), "--");
                int pt = tm.getPhoneType();
                n.phoneType = pt == TelephonyManager.PHONE_TYPE_GSM ? "GSM"
                        : (pt == TelephonyManager.PHONE_TYPE_CDMA ? "CDMA" : "未知");
                int ss = tm.getSimState();
                n.simState = ss == TelephonyManager.SIM_STATE_READY ? "就绪"
                        : (ss == TelephonyManager.SIM_STATE_ABSENT ? "未插卡" : "状态" + ss);
            }
        } catch (Throwable ignored) {}

        return n;
    }

    /** Android 10+ 常把 MAC/BSSID 固定成 02:00:00:00:00:00，这种情况如实标隐藏。 */
    private static String maskMac(String s) {
        if (s == null) return "--";
        s = s.trim();
        if (s.isEmpty() || s.equals("02:00:00:00:00:00") || s.equals("00:00:00:00:00:00")) {
            return "已隐藏（系统限制）";
        }
        return s;
    }

    private static String clean(String s) {
        if (s == null) return "--";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.equals("<unknown ssid>") || s.isEmpty()) return "需定位权限";
        return s;
    }

    /** 从扫描结果里按 BSSID 找 capabilities，如 [WPA2-PSK-CCMP][SAE]。 */
    private static String wifiSecurityFromScan(WifiManager wm, String bssid) {
        try {
            java.util.List<android.net.wifi.ScanResult> rs = wm.getScanResults();
            if (rs == null) return "--";
            for (android.net.wifi.ScanResult r : rs) {
                if (r.BSSID != null && r.BSSID.equalsIgnoreCase(bssid)) {
                    String cap = r.capabilities;
                    if (cap == null || cap.isEmpty()) return "开放";
                    if (cap.contains("SAE") && cap.contains("PSK")) return "WPA2/WPA3 过渡";
                    if (cap.contains("SAE")) return "WPA3 (SAE)";
                    if (cap.contains("WPA3")) return "WPA3";
                    if (cap.contains("WPA2")) return "WPA2";
                    if (cap.contains("WPA")) return "WPA";
                    if (cap.contains("WEP")) return "WEP";
                    return "开放";
                }
            }
        } catch (Throwable ignored) {}
        return "--";
    }

    private static String wifiStandard(WifiInfo wi) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                int s = wi.getWifiStandard();
                switch (s) {
                    case 4: return "Wi-Fi 4 (802.11n)";
                    case 5: return "Wi-Fi 5 (802.11ac)";
                    case 6: return "Wi-Fi 6 (802.11ax)";
                    case 7: return "Wi-Fi 7 (802.11be)";
                    case 1: return "802.11a";
                    case 2: return "802.11b";
                    case 3: return "802.11g";
                    default: return "--";
                }
            }
        } catch (Throwable ignored) {}
        return "--";
    }

    private static String wifiSecurity(WifiInfo wi) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                int s = wi.getCurrentSecurityType();
                switch (s) {
                    case 1: return "开放";
                    case 2: return "WEP";
                    case 3: return "PSK (WPA/WPA2)";
                    case 4: return "EAP";
                    case 5: return "SAE (WPA3)";
                    case 6: return "OWE";
                    case 8: return "WPA3 过渡";
                    default: return "--";
                }
            }
        } catch (Throwable ignored) {}
        return "--";
    }

    private static String intToIp(int i) {
        if (i == 0) return "0.0.0.0";
        return (i & 0xFF) + "." + ((i >> 8) & 0xFF) + "."
                + ((i >> 16) & 0xFF) + "." + ((i >> 24) & 0xFF);
    }

    public String signalText() {
        if (rssi == 0) return "--";
        return rssi + " dBm" + (signalPct >= 0 ? "  (" + signalPct + "%)" : "");
    }

    public String speedText() {
        return linkSpeed > 0 ? linkSpeed + " Mbps" : "--";
    }

    public String freqText() {
        if (frequency <= 0) return "--";
        String band = frequency < 3000 ? "2.4G" : (frequency < 6000 ? "5G" : "6G");
        return frequency + " MHz  (" + band + ")";
    }

    public String rxText() { return Sys.bytes(rxBytes); }
    public String txText() { return Sys.bytes(txBytes); }
}
