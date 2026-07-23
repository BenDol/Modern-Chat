package com.modernchat.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChatUtilBroadcastTest
{
    @Test
    public void resolvesTrailingUrlCode() {
        assertEquals(0, ChatUtil.getBroadcastUrlIndex("Patch notes are here|0"));
        assertEquals(1, ChatUtil.getBroadcastUrlIndex("Read more|1"));
        assertEquals(10, ChatUtil.getBroadcastUrlIndex("Update|a"));
    }

    @Test
    public void displayTextDropsTheCode() {
        assertEquals("Patch notes are here", ChatUtil.getBroadcastDisplayText("Patch notes are here|0"));
    }

    @Test
    public void displayTextKeepsInternalPipes() {
        // The code is end-anchored, so a display text that itself contains a pipe survives
        assertEquals(0, ChatUtil.getBroadcastUrlIndex("A | B here|0"));
        assertEquals("A | B here", ChatUtil.getBroadcastDisplayText("A | B here|0"));
    }

    @Test
    public void noPipeCarriesNoCode() {
        assertEquals(-1, ChatUtil.getBroadcastUrlIndex("Just a plain broadcast"));
        assertNull(ChatUtil.getBroadcastDisplayText("Just a plain broadcast"));
    }

    @Test
    public void multiCharSuffixIsNotACode() {
        // Only a single trailing char after '|' is a code
        assertEquals(-1, ChatUtil.getBroadcastUrlIndex("Text|ab"));
    }

    @Test
    public void unknownAlphabetCharIsNotACode() {
        assertEquals(-1, ChatUtil.getBroadcastUrlIndex("Text|!"));
        assertNull(ChatUtil.getBroadcastDisplayText("Text|!"));
    }

    @Test
    public void nullAndTooShortAreSafe() {
        assertEquals(-1, ChatUtil.getBroadcastUrlIndex(null));
        assertEquals(-1, ChatUtil.getBroadcastUrlIndex("|"));
        assertNull(ChatUtil.getBroadcastDisplayText(null));
    }
}
