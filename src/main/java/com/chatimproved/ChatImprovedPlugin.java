package com.chatimproved;

import javax.inject.Inject;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.event.KeyEvent;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Chat Improved",
	description = "An improved chat plugin for RuneLite that enhances the chat experience with additional features.",
	tags = {"chat", "improved", "quality of life"}
)
public class ChatImprovedPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;
	@Inject private ChatImprovedConfig config;

	private boolean chatHidden = false;

	// Hotkey listener uses the config's keybind and only toggles when the game world is the target
	private final KeyListener toggleListener = new KeyListener()
	{
		@Override public void keyTyped(KeyEvent e) { /* no-op */ }
		@Override public void keyReleased(KeyEvent e) { /* no-op */ }

		@Override
		public void keyPressed(KeyEvent e)
		{
			// Only when the game canvas has focus
			if (!isCanvasFocused()) return;

			Keybind kb = config.toggleKey();
			if (kb == null || !kb.matches(e)) return;

			// IMPORTANT: do NOT e.consume() here — let Enter reach the client

			clientThread.invoke(() -> {
				// If there is actual text ready to send in chat, don't toggle.
				if (hasPendingChatInputCT()) return;

				// If another in-game text input is open (name/amount/etc), don't toggle.
				if (isAnyNonChatInputOpenCT()) return;

				chatHidden = !chatHidden;
				applyHiddenNow(); // on client thread
			});
		}
	};

	@Provides
	ChatImprovedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatImprovedConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(toggleListener);
		chatHidden = config.startHidden();
		clientThread.invoke(this::applyHiddenNow);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(toggleListener);
		chatHidden = false;
		clientThread.invoke(this::applyHiddenNow);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::applyHiddenNow);
		}
	}

	private void toggleChatHidden()
	{
		chatHidden = !chatHidden;
		applyHiddenAsync();
	}

	private void applyHiddenAsync()
	{
		clientThread.invoke(this::applyHiddenNow);
	}

	private void applyHiddenNow()
	{
		// MUST be on client thread
		Widget root = client.getWidget(WidgetID.CHATBOX_GROUP_ID, 0);
		if (root != null)
		{
			root.setHidden(chatHidden);
		}
	}

	/**
	 * True when keys are going to the 3D game world (Canvas), not RuneLite text fields.
	 */
	private boolean isCanvasFocused()
	{
		Canvas canvas = client.getCanvas();
		return canvas != null && canvas.hasFocus();
	}

	private boolean hasPendingChatInputCT()
	{
		if (chatHidden) return false; // only relevant when visible

		Widget input = client.getWidget(WidgetInfo.CHATBOX_INPUT);
		if (input == null || input.isHidden()) return false;

		String raw = input.getText();
		if (raw == null) return false;

		String t = Text.removeTags(raw).trim();
		if (t.isEmpty()) return false;

		// Ignore placeholder prompts
		if (t.endsWith(": *"))
		{
			return false;
		}

		return true; // there’s real text queued to send
	}

	private boolean isAnyNonChatInputOpenCT()
	{
		int inputType = 0;
		try { inputType = client.getVar(VarClientInt.INPUT_TYPE); } catch (Throwable ignored) {}
		if (inputType == 0) return false;

		// If the only active input is the chat line and it has text, we already handled it.
		// Treat other input modes (name/amount/quest) as “busy”.
		return !hasPendingChatInputCT();
	}
}
