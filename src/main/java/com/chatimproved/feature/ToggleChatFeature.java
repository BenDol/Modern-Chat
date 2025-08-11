package com.chatimproved.feature;

import com.chatimproved.ChatImprovedConfig;
import com.chatimproved.util.InterfaceUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Canvas;
import java.awt.event.KeyEvent;

import static com.chatimproved.feature.ToggleChatFeature.*;

@Slf4j
public class ToggleChatFeature extends AbstractChatFeature<ToggleChatFeatureConfig>
{
	@ConfigGroup("featureToggle")
	public interface ToggleChatFeatureConfig extends ChatFeatureConfig
	{
		boolean featureToggle_Enabled();
		Keybind featureToggle_ToggleKey();
		boolean featureToggle_StartHidden();
		boolean featureToggle_autoHideOnSend();
	}

	private static final int DEFER_HIDE_DELAY_TICKS = 0;   // initial wait before first check
	private static final int DEFER_HIDE_TIMEOUT_TICKS = 3; // give up if input never clears

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;

	private boolean chatHidden = false;

	// Deferred hide state
	private boolean autoHide = false;
	private boolean deferredHideRequested = false;
	private int deferDelayTicksLeft = 0;
	private int deferTimeoutTicksLeft = 0;

	// Don't consume: let the client still see Enter so messages send
	private final KeyListener toggleListener = new KeyListener()
	{
		@Override public void keyTyped(KeyEvent e) { /* no-op */ }
		@Override public void keyReleased(KeyEvent e) { /* no-op */ }

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (!isCanvasFocused()) return;

			Keybind kb = config.featureToggle_ToggleKey();
			if (kb == null || !kb.matches(e)) return;

			clientThread.invoke(() -> {
				// If we are currently typing in a system prompt,
				// do not toggle chat visibility.
				if (InterfaceUtil.isSystemTextEntryActiveCT(client))
				{
					cancelDeferredHide();
					return;
				}

				// If there is actual text ready to send in chat, defer the hide.
				if (hasPendingChatInputCT())
				{
					// Let Enter submit the message; schedule a hide right after.
					if (!chatHidden) scheduleDeferredHide();
					return;
				}

				cancelDeferredHide();
				chatHidden = !chatHidden;
				applyVisibilityNow();
			});
		}
	};

	@Inject
	public ToggleChatFeature(ChatImprovedConfig config, EventBus eventBus) {
		super(config, eventBus);
	}

	@Override
	protected ToggleChatFeatureConfig extractConfig(ChatImprovedConfig config) {
		return new ToggleChatFeatureConfig()
		{
			@Override
			public boolean featureToggle_Enabled() {
				return config.featureToggle_Enabled();
			}

			@Override
			public Keybind featureToggle_ToggleKey() {
				return config.featureToggle_ToggleKey();
			}

			@Override
			public boolean featureToggle_StartHidden() {
				return config.featureToggle_StartHidden();
			}

			@Override
			public boolean featureToggle_autoHideOnSend() {
				return config.featureToggle_autoHideOnSend();
			}
		};
	}

	@Override
	public boolean isEnabled() {
		return config.featureToggle_Enabled();
	}

	@Override
	public void startUp()
	{
		super.startUp();

		keyManager.registerKeyListener(toggleListener);
		chatHidden = config.featureToggle_StartHidden();

		clientThread.invoke(() -> {
			if (chatHidden && InterfaceUtil.isSystemTextEntryActiveCT(client))
			{
				chatHidden = false;
			}
			applyVisibilityNow();
			cancelDeferredHide();
		});
	}

	@Override
	public void shutDown(boolean fullShutdown)
	{
		super.shutDown(fullShutdown);

		keyManager.unregisterKeyListener(toggleListener);
		chatHidden = false;
		clientThread.invoke(() -> {
			applyVisibilityNow();
			cancelDeferredHide();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(() -> {
				if (config.featureToggle_StartHidden()) {
					scheduleDeferredHide();
				}

				applyVisibilityNow();
				// If logging in while a prompt is open, avoid immediate hide
				if (InterfaceUtil.isSystemTextEntryActiveCT(client))
				{
					cancelDeferredHide();
					chatHidden = false;
					applyVisibilityNow();
				}
			});
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged e)
	{

	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged e)
	{
		if (e.getIndex() != VarClientStr.INPUT_TEXT)
			return;

		// When the player starts typing into a system prompt, ensure chat is shown
		String s = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
		if (s != null && chatHidden)
		{
			chatHidden = false;
			autoHide = config.featureToggle_autoHideOnSend();
			applyVisibilityNow();
		}
		cancelDeferredHide();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		handleDeferredHide();
	}

	private boolean handleDeferredHide() {
		if (!deferredHideRequested && !autoHide)
		{
			return false;
		}

		// If a system prompt appears during deferral, abort the hide
		if (InterfaceUtil.isSystemTextEntryActiveCT(client))
		{
			cancelDeferredHide();
			return false;
		}
		else if (autoHide)
		{
			autoHide = false;
			chatHidden = true;
			applyVisibilityNow();
			cancelDeferredHide();
			return true;
		}

		// Initial short delay to allow the client to process an Enter press
		if (deferDelayTicksLeft > 0)
		{
			deferDelayTicksLeft--;
			return false;
		}

		// After delay, wait until the chat input has cleared (message sent)
		if (!hasPendingChatInputCT())
		{
			chatHidden = true;
			applyVisibilityNow();
			cancelDeferredHide();
			return true;
		}

		// Still pending; keep waiting up to the timeout
		if (deferTimeoutTicksLeft > 0)
		{
			deferTimeoutTicksLeft--;
		}
		else
		{
			// Timed out -> do nothing (leave chat visible to avoid swallowing input)
			cancelDeferredHide();
		}
		return false;
	}

	private void scheduleDeferredHide()
	{
		deferredHideRequested = true;
		deferDelayTicksLeft = DEFER_HIDE_DELAY_TICKS;
		deferTimeoutTicksLeft = DEFER_HIDE_TIMEOUT_TICKS;
	}

	private void cancelDeferredHide()
	{
		deferredHideRequested = false;
		deferDelayTicksLeft = 0;
		deferTimeoutTicksLeft = 0;
	}

	/** MUST be on client thread: apply the current chat visibility state. */
	private void applyVisibilityNow()
	{
		Widget root = client.getWidget(InterfaceID.CHATBOX, 0);
		if (root != null)
		{
			root.setHidden(chatHidden);
		}
	}

	private boolean isCanvasFocused()
	{
		Canvas canvas = client.getCanvas();
		return canvas != null && canvas.hasFocus();
	}

	/** MUST be on client thread: true if the chat input line contains a real message to send. */
	private boolean hasPendingChatInputCT()
	{
		if (chatHidden) return false; // only relevant when visible

		Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (input == null || input.isHidden()) return false;

		String raw = input.getText();
		if (raw == null) return false;

		String t = Text.removeTags(raw).trim();
		if (t.isEmpty()) return false;

        // empty chat placeholder
        return !t.endsWith(": *");
    }
}
