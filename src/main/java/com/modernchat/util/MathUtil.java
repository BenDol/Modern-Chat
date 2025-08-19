package com.modernchat.util;

public class MathUtil
{
    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (Math.min(v, 1f));
    }
}
