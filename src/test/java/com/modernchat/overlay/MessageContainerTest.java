package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.draw.ImageSegment;
import com.modernchat.draw.RichLine;
import com.modernchat.draw.TextSegment;
import com.modernchat.util.ChatUtil;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageContainerTest
{
    @Test
    public void senderPrefixIsRenderedWithTheLineAndMessageIdIsKept()
    {
        MessageContainer container = createContainer();
        container.pushLine(
            "Alice: hello",
            ChatMessageType.PUBLICCHAT,
            1L,
            "Alice",
            null,
            "Alice",
            "",
            "Alice:hello",
            false,
            41);

        RichLine line = container.getLines().getFirst();
        assertEquals(41, line.getMessageId());
        assertEquals("Alice", line.getSender());
        assertTrue(plainText(line).contains("Alice: hello"));
    }

    @Test
    public void senderPrefixRemainsIntactAcrossFormattedSegments()
    {
        MessageContainer container = createContainer();
        container.pushLine(
            "<col=ff0000>Ali</col><col=00ff00>ce</col><col=0000ff>: hello</col>",
            ChatMessageType.PUBLICCHAT,
            1L,
            "Alice",
            null,
            "Alice",
            "",
            "Alice:hello",
            false,
            41);

        RichLine line = container.getLines().getFirst();
        assertEquals(41, line.getMessageId());
        assertTrue(plainText(line).contains("Alice: hello"));
        assertFalse(plainText(line).contains("<"));
    }

    @Test
    public void formattedCommandReplacesOriginalRowAndKeepsMetadata()
    {
        MessageContainer container = createContainer();
        container.pushLine(
            "Alice: !pets",
            ChatMessageType.PUBLICCHAT,
            2L,
            "Alice",
            null,
            "Alice",
            "[Public] ",
            "Alice:!pets",
            false,
            42);

        assertTrue(container.replaceLineBody(
            42,
            "<col=ff0000>Pets: (1)</col> <img=321>"));

        assertEquals(1, container.getLines().size());
        RichLine line = container.getLines().getFirst();
        assertEquals(42, line.getMessageId());
        assertEquals("Alice", line.getSender());
        assertEquals("[Public] ", line.getPrefix());
        assertFalse(line.getSegs().stream().map(TextSegment::getText).anyMatch("!pets"::equals));
        assertTrue(line.getSegs().stream().anyMatch(segment ->
            segment instanceof ImageSegment && ((ImageSegment) segment).getId() == 321));
        assertTrue(line.getSegs().stream().anyMatch(segment ->
            "Pets: (1)".equals(segment.getText()) && Color.RED.equals(segment.getColor())));
    }

    @Test
    public void rawRuneLiteCommandMarkupRendersWithoutTheLegacyChatBuilder()
    {
        MessageContainer container = createContainer();
        container.pushLine(
            "Alice: !pets",
            ChatMessageType.PUBLICCHAT,
            2L,
            "Alice",
            null,
            "Alice",
            "",
            "Alice:!pets",
            false,
            43);

        assertTrue(container.replaceLineBody(
            43,
            "<colNORMAL>Pets: (1) | owned</col> <img=654>"));

        RichLine line = container.getLines().getFirst();
        String visibleText = line.getSegs().stream()
            .filter(segment -> !(segment instanceof ImageSegment))
            .map(TextSegment::getText)
            .reduce("", String::concat);
        assertTrue(visibleText.contains("Alice: Pets: (1) | owned "));
        assertFalse(visibleText.contains("<colNORMAL>"));
        assertTrue(line.getSegs().stream().anyMatch(segment ->
            segment instanceof ImageSegment && ((ImageSegment) segment).getId() == 654));
    }

    @Test
    public void formattedGimTransportPrefixIsRemovedWithoutLosingRichText()
    {
        assertEquals(
            "<col=00ff00>Level: 99</col><img=7>",
            ChatUtil.normalizeMessageBody("|<col=00ff00>Level: 99</col><img=7>"));
    }

    private static String plainText(RichLine line)
    {
        return line.getSegs().stream().map(TextSegment::getText).reduce("", String::concat);
    }

    private static MessageContainer createContainer()
    {
        MessageContainer container = new MessageContainer();
        container.startUp(new MessageContainerConfig.Default(), ChatMode.PUBLIC, false);
        return container;
    }
}