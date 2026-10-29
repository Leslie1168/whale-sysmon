package com.whale.sysmon.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 底部悬浮胶囊导航，抄 OmniWatch 那颗。
 */
public class BottomNav extends LinearLayout {

    public interface OnSelect {
        void onSelect(int index);
    }

    private final String[] icons = {"◉", "▤", "◈", "⚙"};
    private final String[] labels;
    private final TextView[] iconViews;
    private final TextView[] labelViews;
    private int current = 0;
    private OnSelect listener;

    public BottomNav(Context c, String[] labels) {
        super(c);
        this.labels = labels;
        int n = labels.length;
        iconViews = new TextView[n];
        labelViews = new TextView[n];

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackground(Ui.pill(c, Palette.CARD, Palette.STROKE, 30f));
        int p = Ui.dp(c, 6);
        setPadding(p, Ui.dp(c, 6), p, Ui.dp(c, 6));

        for (int i = 0; i < n; i++) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 4));

            TextView ic = Ui.tv(c, icons[i % icons.length], 15f, Palette.TEXT_FAINT, Typeface.DEFAULT);
            ic.setGravity(Gravity.CENTER);
            ic.setIncludeFontPadding(false);
            item.addView(ic, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView lb = Ui.tv(c, labels[i], 10f, Palette.TEXT_FAINT, Typeface.DEFAULT);
            lb.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lbLp.topMargin = Ui.dp(c, 3);
            item.addView(lb, lbLp);

            final int idx = i;
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(idx); }
            });

            iconViews[i] = ic;
            labelViews[i] = lb;
            addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        refresh();
    }

    public void setListener(OnSelect l) { listener = l; }

    public int current() { return current; }

    public void select(int index) {
        if (index < 0 || index >= iconViews.length) return;
        current = index;
        refresh();
        if (listener != null) listener.onSelect(index);
    }

    public void setSelected(int index) {
        if (index < 0 || index >= iconViews.length) return;
        current = index;
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < iconViews.length; i++) {
            boolean on = i == current;
            int color = on ? Palette.CYAN : Palette.TEXT_FAINT;
            iconViews[i].setTextColor(color);
            labelViews[i].setTextColor(color);
            iconViews[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, on ? 16f : 15f);
            labelViews[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }
}
