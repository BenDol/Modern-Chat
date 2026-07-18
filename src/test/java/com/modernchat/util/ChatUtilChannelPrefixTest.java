package com.modernchat.util;

import com.modernchat.common.ChatMode;
import com.modernchat.util.ChatUtil.ChannelPrefix;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ChatUtilChannelPrefixTest
{
    @Test
    public void wordAliasesRouteToTheirChannel() {
        assertPrefix("/p hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/public hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/s hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/say hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/f hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefix("/cc hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefix("/fc hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefix("/c hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("/clan hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("/gc hello", ChatMode.CLAN_GUEST, false, "hello");
        assertPrefix("/guest hello", ChatMode.CLAN_GUEST, false, "hello");
        assertPrefix("/g hello", ChatMode.CLAN_GIM, false, "hello");
        assertPrefix("/gim hello", ChatMode.CLAN_GIM, false, "hello");
        assertPrefix("/group hello", ChatMode.CLAN_GIM, false, "hello");
    }

    @Test
    public void stickyWordAliasesSetTheChannel() {
        assertPrefix("/@p hello", ChatMode.PUBLIC, true, "hello");
        assertPrefix("/@say hello", ChatMode.PUBLIC, true, "hello");
        assertPrefix("/@fc hello", ChatMode.FRIENDS_CHAT, true, "hello");
        assertPrefix("/@clan hello", ChatMode.CLAN_MAIN, true, "hello");
        assertPrefix("/@gc hello", ChatMode.CLAN_GUEST, true, "hello");
        assertPrefix("/@group hello", ChatMode.CLAN_GIM, true, "hello");
    }

    @Test
    public void slashRunsAllowTextDirectly() {
        assertPrefix("//hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("// hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("///hello", ChatMode.CLAN_GUEST, false, "hello");
        assertPrefix("////hello", ChatMode.CLAN_GIM, false, "hello");
        assertPrefix("//@hello", ChatMode.CLAN_MAIN, true, "hello");
        assertPrefix("///@ hello", ChatMode.CLAN_GUEST, true, "hello");
        assertPrefix("////@hello", ChatMode.CLAN_GIM, true, "hello");
    }

    @Test
    public void bareSlashRoutesToFriendsChat() {
        assertPrefix("/hello there", ChatMode.FRIENDS_CHAT, false, "hello there");
        assertPrefix("/ hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefix("/pub hello", ChatMode.FRIENDS_CHAT, false, "pub hello");
    }

    @Test
    public void aliasesAreCaseInsensitive() {
        assertPrefix("/P hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/@CLAN hello", ChatMode.CLAN_MAIN, true, "hello");
    }

    @Test
    public void stickyAliasWithoutMessageJustSwitches() {
        assertPrefix("/@c", ChatMode.CLAN_MAIN, true, "");
        assertPrefix("/@p", ChatMode.PUBLIC, true, "");
        assertPrefix("//@", ChatMode.CLAN_MAIN, true, "");
        assertPrefix("////@", ChatMode.CLAN_GIM, true, "");
    }

    @Test
    public void wordAliasWithoutMessageHasEmptyRemainder() {
        assertPrefix("/p", ChatMode.PUBLIC, false, "");
        assertPrefix("/clan", ChatMode.CLAN_MAIN, false, "");
    }

    @Test
    public void nonPrefixedInputIsNotParsed() {
        assertNull(ChatUtil.parseChannelPrefix(null));
        assertNull(ChatUtil.parseChannelPrefix(""));
        assertNull(ChatUtil.parseChannelPrefix("hello"));
        assertNull(ChatUtil.parseChannelPrefix(" /p hello")); // leading space stays a normal message
        assertNull(ChatUtil.parseChannelPrefix("/@nope hello")); // unknown sticky alias
        assertNull(ChatUtil.parseChannelPrefix("/////hello")); // too many slashes
    }

    private static void assertPrefix(String input, ChatMode mode, boolean sticky, String message) {
        ChannelPrefix p = ChatUtil.parseChannelPrefix(input);
        assertNotNull(input, p);
        assertEquals(input, mode, p.getMode());
        assertEquals(input, sticky, p.isSticky());
        assertEquals(input, message, p.getMessage());
    }
}
