package com.whale.sysmon.data;

import android.os.Environment;
import android.os.StatFs;

/**
 * 存储：内部 / 系统分区容量，以及 /proc/partitions 里的物理分区。
 */
public final class Store {

    public long totalBytes;
    public long freeBytes;
    public long usedBytes;

    public static Store internal() {
        Store s = new Store();
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long block = fs.getBlockSizeLong();
            s.totalBytes = fs.getBlockCountLong() * block;
            s.freeBytes = fs.getAvailableBlocksLong() * block;
            s.usedBytes = s.totalBytes - s.freeBytes;
        } catch (Throwable t) {
            // 保持 0
        }
        return s;
    }

    public float ratio() {
        if (totalBytes <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) ((double) usedBytes / (double) totalBytes)));
    }

    public String usedText() { return Sys.bytes(usedBytes); }
    public String totalText() { return Sys.bytes(totalBytes); }
    public String freeText() { return Sys.bytes(freeBytes); }
    public String pctText() {
        return String.valueOf(Math.round(ratio() * 100f)) + "%";
    }
}
