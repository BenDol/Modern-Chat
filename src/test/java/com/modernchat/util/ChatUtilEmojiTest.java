package com.modernchat.util;

import com.modernchat.common.MessageLine;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChatUtilEmojiTest
{
	@Test
	public void usesRuneLiteProcessedEmojiTextWhenMessageWasNotFiltered()
	{
		MessageLine line = createLine(":smile:", "😊", ":smile:");

		assertEquals("Alice: 😊", line.getText());
	}

	@Test
	public void keepsFilteredTextInsteadOfRuneLiteProcessedText()
	{
		MessageLine line = createLine(":smile:", "😊", "[filtered]");

		assertTrue(line.getText().contains("[filtered]"));
		assertTrue(!line.getText().contains("😊"));
	}

	private static MessageLine createLine(String original, String processed, String filtered)
	{
		MessageNode node = (MessageNode) Proxy.newProxyInstance(
			MessageNode.class.getClassLoader(), new Class<?>[]{MessageNode.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getValue")) return processed;
				if (method.getName().equals("getId")) return 17;
				return null;
			});
		ChatMessage event = new ChatMessage();
		event.setType(net.runelite.api.ChatMessageType.PUBLICCHAT);
		event.setName("Alice");
		event.setSender("Alice");
		event.setMessage(original);
		event.setMessageNode(node);

		Client client = (Client) Proxy.newProxyInstance(
			Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> null);
		return ChatUtil.createMessageLine(event, client, false, filtered);
	}
}
