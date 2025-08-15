package com.modernchat.util;

import java.awt.Color;

public class ColorUtil
{
    public static Color parseHexColor(String hex, Color fallback) {
        try {
            String h = hex.trim();
            if (h.startsWith("#")) h = h.substring(1);
            if (h.length() == 3) {
                char r = h.charAt(0), g = h.charAt(1), b = h.charAt(2);
                h = "" + r + r + g + g + b + b;
            }
            long v = Long.parseLong(h, 16);
            if (h.length() <= 6)
                return new Color(((int) v & 0xFFFFFF) | 0xFF000000, true);
            return new Color((int) v, true);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
