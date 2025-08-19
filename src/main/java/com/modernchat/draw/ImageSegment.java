package com.modernchat.draw;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.awt.Color;
import java.awt.Image;

@Data
@EqualsAndHashCode(callSuper = true)
public final class ImageSegment extends TextSegment
{
    private final int id;
    private final Color color;
    private Image imageCache = null;
    private boolean allowRetryImage = true;

    public ImageSegment(int id, Color c) {
        super("<img=" + id + ">", c);
        this.id = id;
        color = c;
    }

    @Override
    public void resetCache() {
        super.resetCache();
        imageCache = null;
        allowRetryImage = true;
    }
}