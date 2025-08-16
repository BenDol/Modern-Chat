package com.modernchat.draw;

import lombok.Data;

import java.awt.Rectangle;

@Data
public class RowHit
{
    private final Rectangle bounds;
    private final RichLine line;
    private final VisualLine vLine;

    public String getTargetName() {
        return line != null ? line.getTargetName() : null;
    }
}
