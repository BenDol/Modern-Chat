package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.draw.RichLine;
import com.modernchat.draw.TextSegment;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertTrue;

public class MessageHighlightTest
{
	@Test
	public void jagexHighlightTagsColorTextAndStripUnknownHighlightTags()
	{
		MessageContainer container = new MessageContainer();
		container.startUp(new MessageContainerConfig.Default(), ChatMode.PUBLIC, false);
		container.pushLine(
			"before @mes_hl_red@red @mes_hl_blue@blue @mes_hl_unknown@after",
			ChatMessageType.GAMEMESSAGE,
			1L,
			null,
			null,
			null,
			"");

		RichLine line = container.getLines().getFirst();
		assertSegment(line, "before ", new Color(0xCFCFCF));
		assertSegment(line, "red ", new Color(0xFF2222));
		assertSegment(line, "blue after", new Color(0x0000FF));
		assertTrue(line.getSegs().stream().map(TextSegment::getText)
			.noneMatch(text -> text != null && text.contains("mes_hl_")));
	}

	@Test
	public void ordinaryAtSignsRemainLiteral()
	{
		MessageContainer container = new MessageContainer();
		container.startUp(new MessageContainerConfig.Default(), ChatMode.PUBLIC, false);
		container.pushLine("email@example.com", ChatMessageType.GAMEMESSAGE, 1L,
			null, null, null, "");

		String text = container.getLines().getFirst().getSegs().stream()
			.map(TextSegment::getText)
			.reduce("", String::concat);
		assertTrue(text.contains("email@example.com"));
	}

	private static void assertSegment(RichLine line, String text, Color color)
	{
		assertTrue("Expected segment '" + text + "' in " + line.getSegs(), line.getSegs().stream()
			.anyMatch(segment -> text.equals(segment.getText()) && color.equals(segment.getColor())));
	}
}
