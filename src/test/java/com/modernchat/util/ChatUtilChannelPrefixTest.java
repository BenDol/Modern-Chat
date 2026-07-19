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
    public void vanillaWordAliasesRouteToTheirChannel() {
        assertPrefix("/p hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/f hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefix("/c hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("/gc hello", ChatMode.CLAN_GUEST, false, "hello");
        assertPrefix("/g hello", ChatMode.CLAN_GIM, false, "hello");
    }

    @Test
    public void stickyVanillaAliasesSetTheChannel() {
        assertPrefix("/@p hello", ChatMode.PUBLIC, true, "hello");
        assertPrefix("/@f hello", ChatMode.FRIENDS_CHAT, true, "hello");
        assertPrefix("/@c hello", ChatMode.CLAN_MAIN, true, "hello");
        assertPrefix("/@gc hello", ChatMode.CLAN_GUEST, true, "hello");
        assertPrefix("/@g hello", ChatMode.CLAN_GIM, true, "hello");
    }

    @Test
    public void bareStickyMarkerIsStickyClan() {
        // "/@" is the vanilla shorthand for "/@c"
        assertPrefix("/@", ChatMode.CLAN_MAIN, true, "");
        assertPrefix("/@ hello", ChatMode.CLAN_MAIN, true, "hello");
    }

    @Test
    public void slashRunsAllowTextDirectly() {
        assertPrefix("//hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("// hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefix("///hello", ChatMode.CLAN_GUEST, false, "hello");
        assertPrefix("////hello", ChatMode.CLAN_GIM, false, "hello");
    }

    @Test
    public void slashRunStickyMarkerNeedsSpaceOrEnd() {
        assertPrefix("//@ hi", ChatMode.CLAN_MAIN, true, "hi");
        assertPrefix("///@ hello", ChatMode.CLAN_GUEST, true, "hello");
        // '@' glued to text is part of the message, not a sticky marker
        assertPrefix("//@Bob hi", ChatMode.CLAN_MAIN, false, "@Bob hi");
        assertPrefix("//@hello", ChatMode.CLAN_MAIN, false, "@hello");
        assertPrefix("////@hello", ChatMode.CLAN_GIM, false, "@hello");
    }

    @Test
    public void bareSlashRoutesToFriendsChat() {
        assertPrefix("/hello there", ChatMode.FRIENDS_CHAT, false, "hello there");
        assertPrefix("/ hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefix("/pub hello", ChatMode.FRIENDS_CHAT, false, "pub hello");
    }

    @Test
    public void extendedAliasesInactiveByDefault() {
        // Vanilla sends unknown words verbatim to the friends channel
        assertPrefix("/s hello", ChatMode.FRIENDS_CHAT, false, "s hello");
        assertPrefix("/say hello", ChatMode.FRIENDS_CHAT, false, "say hello");
        assertPrefix("/public hello", ChatMode.FRIENDS_CHAT, false, "public hello");
        assertPrefix("/cc hello", ChatMode.FRIENDS_CHAT, false, "cc hello");
        assertPrefix("/fc hello", ChatMode.FRIENDS_CHAT, false, "fc hello");
        assertPrefix("/clan hello", ChatMode.FRIENDS_CHAT, false, "clan hello");
        assertPrefix("/guest hello", ChatMode.FRIENDS_CHAT, false, "guest hello");
        assertPrefix("/gim hello", ChatMode.FRIENDS_CHAT, false, "gim hello");
        assertPrefix("/group hello", ChatMode.FRIENDS_CHAT, false, "group hello");
        // Sticky forms of unrecognized words stay plain messages
        assertNull(ChatUtil.parseChannelPrefix("/@say hello"));
        assertNull(ChatUtil.parseChannelPrefix("/@clan hello"));
    }

    @Test
    public void extendedAliasesRouteWhenEnabled() {
        assertPrefixExtended("/s hello", ChatMode.PUBLIC, false, "hello");
        assertPrefixExtended("/say hello", ChatMode.PUBLIC, false, "hello");
        assertPrefixExtended("/public hello", ChatMode.PUBLIC, false, "hello");
        assertPrefixExtended("/cc hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefixExtended("/fc hello", ChatMode.FRIENDS_CHAT, false, "hello");
        assertPrefixExtended("/clan hello", ChatMode.CLAN_MAIN, false, "hello");
        assertPrefixExtended("/guest hello", ChatMode.CLAN_GUEST, false, "hello");
        assertPrefixExtended("/gim hello", ChatMode.CLAN_GIM, false, "hello");
        assertPrefixExtended("/group hello", ChatMode.CLAN_GIM, false, "hello");
    }

    @Test
    public void stickyExtendedAliasesSetTheChannel() {
        assertPrefixExtended("/@say hello", ChatMode.PUBLIC, true, "hello");
        assertPrefixExtended("/@fc hello", ChatMode.FRIENDS_CHAT, true, "hello");
        assertPrefixExtended("/@clan hello", ChatMode.CLAN_MAIN, true, "hello");
        assertPrefixExtended("/@guest hello", ChatMode.CLAN_GUEST, true, "hello");
        assertPrefixExtended("/@group hello", ChatMode.CLAN_GIM, true, "hello");
    }

    @Test
    public void vanillaAliasesStillRouteWhenExtendedEnabled() {
        assertPrefixExtended("/p hello", ChatMode.PUBLIC, false, "hello");
        assertPrefixExtended("/@c hello", ChatMode.CLAN_MAIN, true, "hello");
        assertPrefixExtended("/hello there", ChatMode.FRIENDS_CHAT, false, "hello there");
    }

    @Test
    public void aliasesAreCaseInsensitive() {
        assertPrefix("/P hello", ChatMode.PUBLIC, false, "hello");
        assertPrefix("/@GC hello", ChatMode.CLAN_GUEST, true, "hello");
        assertPrefixExtended("/@CLAN hello", ChatMode.CLAN_MAIN, true, "hello");
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
        assertPrefixExtended("/clan", ChatMode.CLAN_MAIN, false, "");
    }

    @Test
    public void nonPrefixedInputIsNotParsed() {
        assertNull(ChatUtil.parseChannelPrefix(null));
        assertNull(ChatUtil.parseChannelPrefix(""));
        assertNull(ChatUtil.parseChannelPrefix("hello"));
        assertNull(ChatUtil.parseChannelPrefix(" /p hello")); // leading space stays a normal message
        assertNull(ChatUtil.parseChannelPrefix("/@nope hello")); // unknown sticky alias
        assertNull(ChatUtil.parseChannelPrefix("/@nope hello", true)); // unknown even with extended aliases
        assertNull(ChatUtil.parseChannelPrefix("/////hello")); // too many slashes
    }

    private static void assertPrefix(String input, ChatMode mode, boolean sticky, String message) {
        assertPrefix(ChatUtil.parseChannelPrefix(input), input, mode, sticky, message);
    }

    private static void assertPrefixExtended(String input, ChatMode mode, boolean sticky, String message) {
        assertPrefix(ChatUtil.parseChannelPrefix(input, true), input, mode, sticky, message);
    }

    private static void assertPrefix(ChannelPrefix p, String input, ChatMode mode, boolean sticky, String message) {
        assertNotNull(input, p);
        assertEquals(input, mode, p.getMode());
        assertEquals(input, sticky, p.isSticky());
        assertEquals(input, message, p.getMessage());
    }
}
