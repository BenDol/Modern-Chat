package com.modernchat.draw;

import lombok.Data;
import net.runelite.api.ChatMessageType;

import java.util.ArrayList;
import java.util.List;

@Data
public final class RichLine
{
    private final List<TextSegment> segs = new ArrayList<>();
    private ChatMessageType type;
    private long timestamp;

    // Cached values for performance
    private List<VisualLine> lineCache = null;
    private String prefixCache = null;
    private String timestampCache = null;

    public void resetCache() {
        if (lineCache != null) {
            lineCache.clear();
        }
        lineCache = null;

        for (TextSegment seg : segs) {
            seg.resetCache();
        }
    }
}