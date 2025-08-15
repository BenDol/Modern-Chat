package com.modernchat.draw;

import lombok.Data;

@Data
public class Padding
{
    private final int top;
    private final int bottom;
    private final int left;
    private final int right;

    public Padding(int top) {
        this(top, top);
    }

    public Padding(int top, int bottom) {
        this(top, bottom, top, bottom);
    }

    public Padding(int top, int bottom, int left, int right) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
    }

    public int getHeight() {
        return top + bottom;
    }

    public int getWidth() {
        return left + right;
    }

    public int getMaxTopOrBottom() {
        return Math.max(top, bottom);
    }

    public int getMaxLeftOrRight() {
        return Math.max(left, right);
    }
}
