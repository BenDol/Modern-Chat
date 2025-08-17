package com.modernchat.util;

import lombok.extern.slf4j.Slf4j;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

@Slf4j
public class FontUtil
{
    public static Font safeLoadFont(String path) {
        try {
            return loadFont(path);
        } catch (Exception ex) {
            log.warn("Failed to load font {}: {}", path, ex.toString());
            return null;
        }
    }

    public static Font loadFont(String path) {
        try (var in = FontUtil.class.getResourceAsStream(path)) {
            if (in == null) {
                 throw new IllegalArgumentException("Font resource not found: " + path);
            }
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            return base;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load font: " + path, ex);
        }
    }
}
