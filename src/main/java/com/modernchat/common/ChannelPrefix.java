package com.modernchat.common;

import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Channel prefixes the vanilla chatbox understands (see the chatdefault_onkey
 * script), listed in the order the game checks them. The '@' forms also make
 * the channel the player's default chatbox mode.
 */
@Getter
public enum ChannelPrefix
{
    GROUP_SLASHES_SET("////@", ChatMode.CLAN_GIM, 4, true, true),
    GROUP_SLASHES("////", ChatMode.CLAN_GIM, 4, false, true),
    GUEST_SLASHES_SET("///@", ChatMode.CLAN_GUEST, 3, true, false),
    GUEST_SLASHES("///", ChatMode.CLAN_GUEST, 3, false, false),
    CLAN_SLASHES_SET("//@", ChatMode.CLAN_MAIN, 2, true, false),
    CLAN_SLASHES("//", ChatMode.CLAN_MAIN, 2, false, false),
    GUEST_SET("/@gc ", ChatMode.CLAN_GUEST, 3, true, false),
    GUEST("/gc ", ChatMode.CLAN_GUEST, 3, false, false),
    CLAN_SET("/@c ", ChatMode.CLAN_MAIN, 2, true, false),
    CLAN("/c ", ChatMode.CLAN_MAIN, 2, false, false),
    GROUP_SET("/@g ", ChatMode.CLAN_GIM, 4, true, true),
    GROUP("/g ", ChatMode.CLAN_GIM, 4, false, true),
    FRIENDS_SET("/@f ", ChatMode.FRIENDS_CHAT, 1, true, false),
    FRIENDS("/f ", ChatMode.FRIENDS_CHAT, 1, false, false),
    PUBLIC_SET("/@p ", ChatMode.PUBLIC, 0, true, false),
    PUBLIC("/p ", ChatMode.PUBLIC, 0, false, false),
    PUBLIC_SET_BARE("/@p", ChatMode.PUBLIC, 0, true, false),
    FRIENDS_SET_BARE("/@f", ChatMode.FRIENDS_CHAT, 1, true, false),
    CLAN_SET_BARE("/@c", ChatMode.CLAN_MAIN, 2, true, false),
    GUEST_SET_BARE("/@gc", ChatMode.CLAN_GUEST, 3, true, false),
    GROUP_SET_BARE("/@g", ChatMode.CLAN_GIM, 4, true, true);

    private final String prefix;
    private final ChatMode mode;
    /** Value the game's chatbox mode takes when this prefix sets it. */
    private final int chatboxMode;
    private final boolean setsChatboxMode;
    private final boolean groupIronmanOnly;

    ChannelPrefix(String prefix, ChatMode mode, int chatboxMode, boolean setsChatboxMode, boolean groupIronmanOnly) {
        this.prefix = prefix;
        this.mode = mode;
        this.chatboxMode = chatboxMode;
        this.setsChatboxMode = setsChatboxMode;
        this.groupIronmanOnly = groupIronmanOnly;
    }

    /**
     * Finds the channel prefix the given input starts with, or null when it has none.
     * Entries are tested in declaration order, which mirrors the game's own precedence
     * (so "////" wins over "///", and "/@gc" over "/@g"). The group prefixes are only
     * recognised for group ironman accounts, exactly like the vanilla chatbox.
     * Matching is deliberately case-insensitive everywhere; vanilla is only partly so.
     */
    public static @Nullable ChannelPrefix match(String text, boolean groupIronman) {
        if (text == null || !text.startsWith("/"))
            return null;

        for (ChannelPrefix p : values()) {
            if (p.groupIronmanOnly && !groupIronman)
                continue;
            if (text.regionMatches(true, 0, p.prefix, 0, p.prefix.length()))
                return p;
        }
        return null;
    }
}
