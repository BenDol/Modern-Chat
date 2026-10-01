package com.modernchat.service;

import com.modernchat.draw.UsernameHit;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import org.junit.Test;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PlayerMenuServiceTest
{
	@Test
	public void addsOnlyTheFourRequestedUsernameActions()
	{
		List<String> options = new ArrayList<>();
		MenuEntry menuEntry = (MenuEntry) Proxy.newProxyInstance(
			MenuEntry.class.getClassLoader(), new Class<?>[]{MenuEntry.class},
			(proxy, method, args) -> {
				if (method.getName().equals("setOption")) {
					options.add((String) args[0]);
				}
				return proxy;
			});
		Menu menu = (Menu) Proxy.newProxyInstance(
			Menu.class.getClassLoader(), new Class<?>[]{Menu.class},
			(proxy, method, args) -> method.getName().equals("createMenuEntry") ? menuEntry : null);
		Client client = (Client) Proxy.newProxyInstance(
			Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(proxy, method, args) -> method.getName().equals("getMenu") ? menu : null);

		PlayerMenuService service = new PlayerMenuService(client, null, null, null);
		service.addMenuEntries(new UsernameHit(new Rectangle(0, 0, 50, 16), "Alice", "Alice"));

		assertEquals(List.of("Add friend", "Add ignore", "Look up", "Copy to clipboard"), options);
	}
}
