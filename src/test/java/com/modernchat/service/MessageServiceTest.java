package com.modernchat.service;

import com.modernchat.common.ChatMode;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptEventBuilder;
import net.runelite.api.ScriptID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MessageServiceTest
{
	@Test
	public void channelMessagesEnablePacketsBeforeRunningTheSendScript() throws Exception
	{
		for (ChatMode mode : new ChatMode[]{ChatMode.PUBLIC, ChatMode.FRIENDS_CHAT,
			ChatMode.CLAN_MAIN, ChatMode.CLAN_GUEST, ChatMode.CLAN_GIM}) {
			Fixture fixture = new Fixture();
			fixture.service.sendMessage("hello", mode, null);

			assertArrayEquals(new Object[]{ScriptID.CHAT_SEND, "hello", mode.getValue(),
				mode == ChatMode.CLAN_GIM ? 1 : 0, 0, -1}, fixture.scriptArguments);
			assertEquals(1, fixture.runCount);
		}
	}

	@Test
	public void privateMessagesEnablePacketsBeforeRunningTheSendScript() throws Exception
	{
		Fixture fixture = new Fixture();
		fixture.service.sendPrivateChat("hello", "Alice");

		assertArrayEquals(new Object[]{ScriptID.PRIVMSG, "Alice", "hello"}, fixture.scriptArguments);
		assertEquals(1, fixture.runCount);
	}

	@Test
	public void privateModeUsesThePrivateSendScript() throws Exception
	{
		Fixture fixture = new Fixture();
		fixture.service.sendMessage("hello", ChatMode.PRIVATE, "Alice");

		assertArrayEquals(new Object[]{ScriptID.PRIVMSG, "Alice", "hello"}, fixture.scriptArguments);
		assertEquals(1, fixture.runCount);
	}

	private static final class Fixture
	{
		private final MessageService service = new MessageService();
		private Object[] scriptArguments;
		private boolean canSendPackets;
		private int runCount;

		private Fixture() throws Exception
		{
			ScriptEvent event = (ScriptEvent) Proxy.newProxyInstance(
				ScriptEvent.class.getClassLoader(), new Class<?>[]{ScriptEvent.class},
				(proxy, method, args) -> {
					if (method.getName().equals("setCanSendPackets")) {
						canSendPackets = (Boolean) args[0];
						return proxy;
					}
					if (method.getName().equals("run")) {
						assertTrue("Chat scripts must be allowed to send packets before execution", canSendPackets);
						runCount++;
					}
					return null;
				});
			ScriptEventBuilder builder = (ScriptEventBuilder) Proxy.newProxyInstance(
				ScriptEventBuilder.class.getClassLoader(), new Class<?>[]{ScriptEventBuilder.class},
				(proxy, method, args) -> method.getName().equals("build") ? event : null);
			Client client = (Client) Proxy.newProxyInstance(
				Client.class.getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) -> {
					switch (method.getName()) {
						case "isClientThread": return true;
						case "createScriptEventBuilder":
							scriptArguments = (Object[]) args[0];
							return builder;
						case "runScript":
							fail("Chat submission must not use runScript's packet-disabled event");
					}
					return null;
				});
			ClientThread clientThread = new ClientThread();
			setField(clientThread, "client", client);
			FilterService filterService = new FilterService();
			filterService.startUp();
			setField(service, "client", client);
			setField(service, "clientThread", clientThread);
			setField(service, "eventBus", new EventBus());
			setField(service, "filterService", filterService);
		}
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
