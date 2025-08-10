package com.example;

import com.chatimproved.ChatImprovedPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ChatImprovedPlugin.class);
		RuneLite.main(args);
	}
}