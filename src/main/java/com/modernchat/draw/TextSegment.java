package com.modernchat.draw;

import lombok.Data;

import java.awt.Color;

@Data
public final class TextSegment
{
    private final String text;
    private final Color color;
    private String textCache = null;

    public TextSegment(String t, Color c) {
        text = t;
        color = c;
    }

    public void resetCache() {
        textCache = null;
    }
}