package com.whale.sysmon;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.whale.sysmon.page.ConsolePage;
import com.whale.sysmon.page.DevicePage;
import com.whale.sysmon.page.SettingsPage;
import com.whale.sysmon.page.ToolsPage;
import com.whale.sysmon.ui.BottomNav;
import com.whale.sysmon.ui.Palette;
import com.whale.sysmon.ui.Ui;

/**
 * 壳：内容区 + 底部悬浮导航。控制台页在前台时才跑采样循环。
 */
public class MainActivity extends Activity {

    private FrameLayout content;
    private ConsolePage console;
    private DevicePage device;
    private ToolsPage tools;
    private SettingsPage settings;
    private BottomNav nav;

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean pageVisible = true;
    private int intervalMs = 1000;
    private int currentTab = 0;

    private final Runnable sampler = new Runnable() {
        @Override public void run() {
            if (!pageVisible || currentTab != 0 || console == null) return;
            try {
                if (settings != null) intervalMs = settings.intervalMs();
                console.setIntervalMs(intervalMs);
                console.sample();
            } catch (Throwable ignored) {}
            h.postDelayed(this, Math.max(500, intervalMs));
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            getWindow().setStatusBarColor(Palette.BG);
            getWindow().setNavigationBarColor(Palette.BG);
        } catch (Throwable ignored) {}

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Palette.BG);

        content = new FrameLayout(this);
        shell.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        console = new ConsolePage(this);
        device = new DevicePage(this);
        tools = new ToolsPage(this);
        settings = new SettingsPage(this);

        // 平板/横屏时限制内容最大宽度并居中，否则卡片被拉满整屏很空
        int screenDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int maxDp = 860;
        // 每个页面必须用独立的 LayoutParams 实例，共用会被后一次 addView 改写
        java.util.List<View> pages = new java.util.ArrayList<View>();
        pages.add(console.view());
        pages.add(device.view());
        pages.add(tools.view());
        pages.add(settings.view());
        for (View pv : pages) {
            FrameLayout.LayoutParams lp;
            if (screenDp > maxDp) {
                lp = new FrameLayout.LayoutParams(Ui.dp(this, maxDp),
                        ViewGroup.LayoutParams.MATCH_PARENT);
                lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            } else {
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
            }
            content.addView(pv, lp);
        }
        device.view().setVisibility(View.GONE);
        tools.view().setVisibility(View.GONE);
        settings.view().setVisibility(View.GONE);

        nav = new BottomNav(this, new String[]{"控制台", "设备", "工具", "设置"});
        int navW = screenDp > maxDp ? Ui.dp(this, maxDp - 40)
                : ViewGroup.LayoutParams.MATCH_PARENT;
        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                navW, Ui.dp(this, 62));
        navLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        navLp.leftMargin = Ui.dp(this, 20);
        navLp.rightMargin = Ui.dp(this, 20);
        navLp.bottomMargin = Ui.dp(this, 22);
        shell.addView(nav, navLp);

        nav.setListener(new BottomNav.OnSelect() {
            @Override public void onSelect(int index) { showTab(index); }
        });

        setContentView(shell);
        com.whale.sysmon.data.ShizukuBridge.init(this);
        applyHighRefreshRate();
        requestPermsOnce();
    }

    /**
     * 高刷适配：系统默认会把没声明帧率的应用压在 60Hz。
     * 这里读屏幕支持的最高刷新率并显式请求，90/120Hz 屏才会真的跑满。
     */
    private void applyHighRefreshRate() {
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            float max = d.getRefreshRate();
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                for (android.view.Display.Mode m : d.getSupportedModes()) {
                    if (m.getRefreshRate() > max) max = m.getRefreshRate();
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                // android-34 的 Window 上没暴露该方法，走反射避免编译期绑定
                try {
                    java.lang.reflect.Method m = android.view.Window.class
                            .getMethod("setFrameRate", float.class, int.class);
                    m.invoke(getWindow(), Float.valueOf(max), Integer.valueOf(0));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /** Android 10+ 要定位权限才能读到 WiFi 名称，没有就永远显示「未知」。 */
    private void requestPermsOnce() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 23) return;
            java.util.List<String> need = new java.util.ArrayList<String>();
            String[] wants = {
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE,
                    android.Manifest.permission.POST_NOTIFICATIONS
            };
            for (String w : wants) {
                if (checkSelfPermission(w) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    need.add(w);
                }
            }
            if (!need.isEmpty()) {
                requestPermissions(need.toArray(new String[0]), 1001);
            }
        } catch (Throwable ignored) {}
    }

    private void showTab(int index) {
        currentTab = index;
        console.view().setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        device.view().setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        tools.view().setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        settings.view().setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        switch (index) {
            case 0: console.scrollToTop(); break;
            case 1: device.refresh(); break;
            case 2: break;
            case 3: break;
        }
        if (index == 0) {
            h.removeCallbacks(sampler);
            h.post(sampler);
        } else {
            h.removeCallbacks(sampler);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        pageVisible = true;
        if (currentTab == 0) {
            h.removeCallbacks(sampler);
            h.post(sampler);
        }
    }

    @Override
    public void onBackPressed() {
        // 设备页在详情态时，返回键先回列表
        if (currentTab == 1 && device != null && device.onBack()) return;
        if (currentTab != 0) {
            nav.setSelected(0);
            showTab(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        pageVisible = false;
        h.removeCallbacks(sampler);
    }
}
