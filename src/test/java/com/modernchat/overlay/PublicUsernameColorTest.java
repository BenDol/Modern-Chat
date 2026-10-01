package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.draw.SenderSegment;
import com.modernchat.draw.TextSegment;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PublicUsernameColorTest
{
	@Test
	public void senderAndColonAreMarkedSeparatelyFromMessageBodyAcrossFormatting()
	{
		Color usernameColor = new Color(0x00FF00);
		MessageContainerConfig config = new MessageContainerConfig.Default() {
			@Override public Color getPublicUsernameColor() { return usernameColor; }
		};
		MessageContainer container = new MessageContainer();
		container.startUp(config, ChatMode.PUBLIC, false);
		container.pushLine(
			"<col=ff0000>Ali</col><col=00ff00>ce</col><col=0000ff>: hello</col>",
			ChatMessageType.PUBLICCHAT,
			1L,
			"Alice",
			null,
			"Alice",
			"");

		String senderText = container.getLines().getFirst().getSegs().stream()
			.filter(SenderSegment.class::isInstance)
			.map(TextSegment::getText)
			.reduce("", String::concat);
		assertEquals("Alice:", senderText);
		assertTrue(container.getLines().getFirst().getSegs().stream()
			.anyMatch(segment -> !(segment instanceof SenderSegment) && " hello".equals(segment.getText())));
		assertEquals(usernameColor, config.getUsernameColor());
	}

	@Test
	public void transparentUsernameColorFallsBackToPublicMessageColor()
	{
		MessageContainerConfig config = new MessageContainerConfig.Default() {
			@Override public Color getPublicColor() { return Color.CYAN; }
		};
		MessageContainer container = new MessageContainer();
		container.startUp(config, ChatMode.PUBLIC, false);
		container.pushLine("Alice: hi", ChatMessageType.PUBLICCHAT, 1L,
			"Alice", null, "Alice", "");

		assertEquals(0, config.getUsernameColor().getAlpha());
		assertTrue(container.getLines().getFirst().getSegs().stream()
			.anyMatch(segment -> segment instanceof SenderSegment
				&& "Alice:".equals(segment.getText())
				&& Color.CYAN.equals(segment.getColor())));
	}
}
