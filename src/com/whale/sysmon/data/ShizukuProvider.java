package com.whale.sysmon.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import moe.shizuku.api.BinderContainer;

/**
 * 接收 Shizuku 推过来的 binder。
 * Shizuku(13.x) 不再暴露 bindService 的 service，而是主动 call 客户端的
 * <packageName>.shizuku 这个 provider，把 binder 塞进 extras。
 */
public class ShizukuProvider extends ContentProvider {

    private static final String TAG = "SYS_MON_SHIZUKU";
    public static final String METHOD_SEND_BINDER = "sendBinder";
    public static final String METHOD_GET_BINDER = "getBinder";
    private static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";

    @Override
    public boolean onCreate() {
        Log.d(TAG, "provider onCreate");
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle reply = new Bundle();
        if (extras == null) return reply;
        try {
            extras.setClassLoader(BinderContainer.class.getClassLoader());
        } catch (Throwable ignored) {}

        if (METHOD_SEND_BINDER.equals(method)) {
            try {
                BinderContainer container = extras.getParcelable(EXTRA_BINDER);
                if (container != null && container.binder != null) {
                    IBinder b = container.binder;
                    Log.d(TAG, "binder received, alive=" + b.isBinderAlive());
                    ShizukuBridge.onBinderReceived(b, getContext() == null ? null
                            : getContext().getPackageName());
                } else {
                    Log.d(TAG, "sendBinder but container empty");
                }
            } catch (Throwable t) {
                Log.d(TAG, "handle sendBinder failed: " + t);
            }
        } else if (METHOD_GET_BINDER.equals(method)) {
            IBinder b = ShizukuBridge.rawBinder();
            if (b != null) {
                reply.putParcelable(EXTRA_BINDER, new BinderContainer(b));
            }
        }
        return reply;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
