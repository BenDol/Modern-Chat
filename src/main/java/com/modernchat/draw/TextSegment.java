package com.modernchat.draw;

import lombok.Data;

import java.awt.Color;

@Data
public class TextSegment implements Segment
{
    private final String text;
    private final Color color;
    private String textCache = null;

    public TextSegment() {
        this.text = "";
        this.color = Color.BLACK;
    }

    public TextSegment(String t, Color c) {
        text = t;
        color = c;
    }

    @Override
    public void resetCache() {
        textCache = null;
    }
}