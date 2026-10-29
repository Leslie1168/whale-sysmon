package com.whale.sysmon.data;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.InputStream;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuApplication;
import moe.shizuku.server.IShizukuService;

/**
 * Shizuku 通道：拿到 shell(uid 2000) 权限执行命令。
 *
 * Shizuku 13.x 不再暴露可 bind 的 service，而是主动 call 客户端的
 * {@code <packageName>.shizuku} provider 塞 binder 过来（见 ShizukuProvider）。
 * 拿到 binder 后必须先 attachApplication，否则所有方法都报
 * "Not an attached client"。
 */
public final class ShizukuBridge {

    private static final String TAG = "SYS_MON_SHIZUKU";
    private static final String PKG = "moe.shizuku.privileged.api";
    private static final String DESCRIPTOR = "moe.shizuku.server.IShizukuService";
    private static final int TRANSACT_ATTACH_V13 = 18;   // attachApplication(Bundle) = 17 -> +1

    /* Bundle key，从 Shizuku APK 的 dex 里提取出来的真实值 */
    private static final String K_API_VERSION = "shizuku:attach-api-version";
    private static final String K_PACKAGE = "shizuku:attach-package-name";
    private static final String K_R_PERMISSION = "shizuku:attach-reply-permission-granted";
    private static final String K_R_UID = "shizuku:attach-reply-uid";
    private static final String K_R_VERSION = "shizuku:attach-reply-version";
    private static final int OUR_API_VERSION = 13;

    public static final int PERM_GRANTED = 0;
    public static final int PERM_DENIED = 1;
    public static final int PERM_UNAVAILABLE = -1;

    private static volatile IBinder sBinder;
    private static volatile IShizukuService sService;
    private static volatile boolean sAttached = false;
    private static volatile boolean sPermGranted = false;
    private static volatile int sServerUid = -1;
    private static volatile int sServerVersion = -1;
    private static String sPkg;
    private static Context sApp;

    private ShizukuBridge() {}

    public static void init(Context ctx) {
        if (sApp == null) {
            sApp = ctx.getApplicationContext();
            sPkg = ctx.getPackageName();
        }
        if (connected() && !sAttached) attach();   // provider 早于 init，这里补一次
        Log.d(TAG, "init installed=" + installed(ctx) + " connected=" + connected()
                + " attached=" + sAttached + " pkg=" + sPkg);
    }

    public static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /* --------------------------- attach --------------------------- */

    private static final IShizukuApplication APP = new IShizukuApplication.Stub() {
        @Override public void bindApplication(Bundle data) throws RemoteException {
            try {
                sPermGranted = data.getBoolean(K_R_PERMISSION, false);
                sServerUid = data.getInt(K_R_UID, -1);
                sServerVersion = data.getInt(K_R_VERSION, -1);
                sAttached = true;
                Log.d(TAG, "bindApplication uid=" + sServerUid
                        + " ver=" + sServerVersion + " granted=" + sPermGranted);
            } catch (Throwable t) {
                Log.d(TAG, "bindApplication parse failed: " + t);
            }
        }

        @Override public void dispatchRequestPermissionResult(int requestCode, Bundle data)
                throws RemoteException {
            try {
                sPermGranted = data.getBoolean("shizuku:request-permission-reply-allowed", false);
                Log.d(TAG, "permission result code=" + requestCode + " granted=" + sPermGranted);
            } catch (Throwable t) {
                Log.d(TAG, "perm result parse failed: " + t);
            }
        }

        @Override public void showPermissionConfirmation(int requestUid, int requestPid,
                                                        String requestPackageName, int requestCode) {
            // Sui only
        }
    };

    /** provider 收到 binder 后回调。pkg 由 provider 传入（此时 init() 可能还没跑）。 */
    public static void onBinderReceived(IBinder b, String pkg) {
        if (b == null) return;
        try {
            if (pkg != null && sPkg == null) sPkg = pkg;
            sBinder = b;
            sService = IShizukuService.Stub.asInterface(b);
            Log.d(TAG, "binder set, alive=" + b.isBinderAlive() + " pkg=" + sPkg);
            attach();
        } catch (Throwable t) {
            Log.d(TAG, "onBinderReceived failed: " + t);
        }
    }

    private static void attach() {
        IBinder b = sBinder;
        if (sPkg == null && sApp != null) sPkg = sApp.getPackageName();
        if (b == null || sPkg == null) {
            Log.d(TAG, "attach skipped: binder=" + (b != null) + " pkg=" + sPkg);
            return;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            Bundle args = new Bundle();
            args.putInt(K_API_VERSION, OUR_API_VERSION);
            args.putString(K_PACKAGE, sPkg);

            data.writeInterfaceToken(DESCRIPTOR);
            data.writeStrongBinder(APP.asBinder());
            data.writeInt(1);              // Bundle 非空
            args.writeToParcel(data, 0);

            boolean ok = b.transact(TRANSACT_ATTACH_V13, data, reply, 0);
            reply.readException();
            Log.d(TAG, "attach transact ok=" + ok);
        } catch (Throwable t) {
            Log.d(TAG, "attach failed: " + t);
            // 老协议兜底：transact(14) 只带 binder + packageName
            attachLegacy(b);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    /** Shizuku 11 及以前的 attach 协议。 */
    private static void attachLegacy(IBinder b) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeStrongBinder(APP.asBinder());
            data.writeString(sPkg);
            b.transact(14, data, reply, 0);
            reply.readException();
            Log.d(TAG, "attachLegacy ok");
        } catch (Throwable t) {
            Log.d(TAG, "attachLegacy failed: " + t);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public static IBinder rawBinder() { return sBinder; }

    public static boolean connected() {
        try {
            return sService != null && sBinder != null && sBinder.isBinderAlive();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean attached() { return sAttached; }

    public static int serverUid() { return sServerUid; }

    /* --------------------------- 权限 --------------------------- */

    /** 0=已授权 1=未授权 -1=没连上。 */
    public static int permission() {
        if (!connected()) return PERM_UNAVAILABLE;
        if (sPermGranted) return PERM_GRANTED;
        try {
            return sService.checkSelfPermission() ? PERM_GRANTED : PERM_DENIED;
        } catch (Throwable t) {
            Log.d(TAG, "checkSelfPermission failed: " + t);
            return PERM_UNAVAILABLE;
        }
    }

    public static boolean request() {
        if (!connected()) return false;
        try {
            sService.requestPermission(1);
            return true;
        } catch (Throwable t) {
            Log.d(TAG, "requestPermission failed: " + t);
            return false;
        }
    }

    /* --------------------------- 执行 --------------------------- */

    public static String exec(String cmd) {
        if (!connected()) return "";
        IRemoteProcess proc = null;
        InputStream in = null;
        try {
            proc = sService.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            if (proc == null) return "";
            ParcelFileDescriptor pfd = proc.getInputStream();
            if (pfd == null) return "";
            in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            long deadline = System.currentTimeMillis() + 4000;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
                if (System.currentTimeMillis() > deadline) break;
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.d(TAG, "exec failed: " + t);
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (proc != null) proc.destroy(); } catch (Throwable ignored) {}
        }
    }

    public static String cat(String path) {
        return exec("cat " + path + " 2>/dev/null");
    }

    public static String dumpsys(String service) {
        return exec("dumpsys " + service + " 2>/dev/null");
    }
}
