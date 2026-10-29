package com.whale.sysmon.ui;

/**
 * 设计令牌。抄 OmniWatch 那套冷暗调 + 霓虹色语义。
 */
public final class Palette {

    private Palette() {}

    /* 背景层次 */
    public static final int BG        = 0xFF070A14;
    public static final int CARD      = 0xFF121826;
    public static final int CARD_DEEP = 0xFF0E1420;
    public static final int STROKE    = 0xFF1E2636;
    public static final int STROKE_HI = 0xFF2A3547;

    /* 语义霓虹 */
    public static final int CYAN   = 0xFF35D6F0;
    public static final int PINK   = 0xFFFF5C7A;
    public static final int PURPLE = 0xFFA78BFA;
    public static final int GREEN  = 0xFF3DDC84;
    public static final int YELLOW = 0xFFFFC64D;
    public static final int RED    = 0xFFFF5757;

    /* 文字 */
    public static final int TEXT       = 0xFFFFFFFF;
    public static final int TEXT_DIM   = 0xFF8A93A8;
    public static final int TEXT_FAINT = 0xFF5A6377;

    /* 网格 */
    public static final int GRID = 0xFF1A2233;

    /** 带透明的颜色 */
    public static int alpha(int color, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255);
        return (al << 24) | (color & 0x00FFFFFF);
    }
}
