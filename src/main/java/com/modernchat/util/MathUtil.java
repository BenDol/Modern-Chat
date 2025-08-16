package com.modernchat.util;

public class MathUtil
{
    public static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
