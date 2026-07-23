package com.example;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.modernchat.ModernChatPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.slf4j.LoggerFactory;

public class ExamplePluginTest
{
	public static void main(String[] args) throws Exception
	{
		// Dev client only: surface the plugin's debug logging
		((Logger) LoggerFactory.getLogger("com.modernchat")).setLevel(Level.DEBUG);

		// Enable developer mode so the plugin's dev-only commands (::testbroadcast) work
		String[] devArgs = new String[args.length + 1];
		System.arraycopy(args, 0, devArgs, 0, args.length);
		devArgs[args.length] = "--developer-mode";

		ExternalPluginManager.loadBuiltin(ModernChatPlugin.class);
		RuneLite.main(devArgs);
	}
}
