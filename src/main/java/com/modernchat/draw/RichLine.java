package com.modernchat.draw;

import lombok.Data;
import net.runelite.api.ChatMessageType;

import java.util.ArrayList;
import java.util.List;

@Data
public final class RichLine
{
    private final List<TextSegment> segs = new ArrayList<>();
    private ChatMessageType type = ChatMessageType.GAMEMESSAGE;
    private long timestamp;
    private String sender;
    private String receiver;
    private String targetName;
    /** Key for duplicate detection: name + original message */
    private String duplicateKey;
    /** True if this message has a collapse count suffix like " (2)" */
    private boolean collapsed;

    /** Id of the MessageNode this line was captured from, or -1 when untracked */
    private int messageNodeId = -1;
    /** The node's effective text when this line was last (re)built, for change detection */
    private String nodeValueSnapshot;
    /** Composed sender part (icons + name + ": ") reused when rebuilding the line text */
    private String senderPrefix;
    /** True when the snapshot matches a live-updating pattern (e.g. system update timer) */
    private boolean liveUpdating;

    // Cached values for performance
    private List<VisualLine> lineCache = null;

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