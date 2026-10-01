package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.draw.ImageSegment;
import com.modernchat.draw.RichLine;
import com.modernchat.draw.TextSegment;
import net.runelite.api.ChatMessageType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuneLiteCommandReplacementTest
{
	@Test
	public void replacesCommandResultInPlaceAndRetainsRowMetadata()
	{
		MessageContainer container = new MessageContainer();
		container.startUp(new MessageContainerConfig.Default(), ChatMode.PUBLIC, false);
		container.pushLine("Alice: !pets", ChatMessageType.PUBLICCHAT, 2L,
			"Alice", null, "Alice", "[Public] ", "Alice:!pets", false, 45);

		assertTrue(container.replaceLineBody(45, "<colNORMAL>Pets found</col> <img=321>"));
		assertEquals(1, container.getLines().size());
		RichLine line = container.getLines().getFirst();
		assertEquals(45, line.getMessageId());
		assertEquals("Alice", line.getSender());
		assertEquals("[Public] ", line.getPrefix());
		assertFalse(line.getSegs().stream().map(TextSegment::getText).anyMatch("!pets"::equals));
		assertTrue(line.getSegs().stream().anyMatch(segment ->
			segment instanceof ImageSegment && ((ImageSegment) segment).getId() == 321));
		assertTrue(line.getSegs().stream().map(TextSegment::getText)
			.filter(text -> text != null).reduce("", String::concat).contains("Alice: Pets found "));
	}
}
