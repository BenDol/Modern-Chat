package com.modernchat.util;

import net.runelite.api.ChatMessageType;
import net.runelite.api.IconID;
import net.runelite.api.events.ChatMessage;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class SenderIconTest
{
	@Test
	public void preservesAllPlayerAccountIconsOnChatSender()
	{
		List<Integer> accountIcons = Arrays.asList(
			IconID.PLAYER_MODERATOR.getIndex(),
			IconID.JAGEX_MODERATOR.getIndex(),
			IconID.IRONMAN.getIndex(),
			IconID.ULTIMATE_IRONMAN.getIndex(),
			IconID.HARDCORE_IRONMAN.getIndex(),
			IconID.LEAGUE.getIndex(),
			IconID.GROUP_IRONMAN.getIndex(),
			IconID.HARDCORE_GROUP_IRONMAN.getIndex(),
			IconID.UNRANKED_GROUP_IRONMAN.getIndex());
		StringBuilder rawName = new StringBuilder();
		for (int iconId : accountIcons) {
			rawName.append("<img=").append(iconId).append('>');
		}
		rawName.append("Alice");

		ChatMessage message = new ChatMessage();
		message.setType(ChatMessageType.PUBLICCHAT);
		message.setName(rawName.toString());
		message.setSender(rawName.toString());
		ChatUtil.SenderReceiver sender = ChatUtil.getSenderAndReceiver(message, "Local");

		assertEquals("Alice", sender.getSenderName());
		assertEquals(accountIcons, sender.getSenderIconIds());
		assertEquals(accountIcons.get(0).intValue(), sender.getSenderIconId());
	}
}
