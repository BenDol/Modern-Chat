package com.modernchat.draw;

import lombok.Getter;

@Getter
public enum ChannelFilterType {
    PUBLIC("Public Chat", 1 << 0),
    PRIVATE("Private Messages", 1 << 1),
    FRIENDS_CHAT("Friends Chat", 1 << 2),
    CLAN("Clan Chat", 1 << 3),
    TRADE("Trade", 1 << 4),
    GAME("Game", 1 << 5),
    SYSTEM("System", 1 << 6),
    AUTO_TYPER("Auto Typed", 1 << 7);

    private final String displayName;
    private final int flag;

    ChannelFilterType(String displayName, int flag) {
        this.displayName = displayName;
        this.flag = flag;
    }

    /**
     * Check if this filter type is disabled in the given flags.
     * A set bit means the filter is disabled (hidden).
     */
    public boolean isDisabledIn(int flags) {
        return (flags & flag) != 0;
    }

    /**
     * Set or clear this filter type's flag.
     * @param flags current flags
     * @param disabled true to disable (hide), false to enable (show)
     * @return updated flags
     */
    public int setIn(int flags, boolean disabled) {
        if (disabled) {
            return flags | flag;
        } else {
            return flags & ~flag;
        }
    }
}
