package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.RuneFontStyle;
import com.modernchat.overlay.ChatPeekOverlay;
import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Units;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.event.KeyEvent;

import static com.modernchat.feature.ToggleChatFeature.ToggleChatFeatureConfig;

@Slf4j
public class ToggleChatFeature extends AbstractChatFeature<ToggleChatFeatureConfig>
	implements KeyListener
{
	@Override
	public String getConfigGroup() {
		return "featureToggle";
	}

	public interface ToggleChatFeatureConfig extends ChatFeatureConfig
	{
		boolean featureToggle_Enabled();
		Keybind featureToggle_ToggleKey();
		boolean featureToggle_StartHidden();
		boolean featureToggle_autoHideOnSend();
		boolean featureToggle_lockCameraWhenVisible();
		boolean featureToggle_peekOverlayEnabled();
		Color featureToggle_peekBgColor();
		Color featureToggle_peekBorderColor();
		RuneFontStyle featureToggle_peekFontStyle();
		int featureToggle_peekFontSize();
		int featureToggle_peekOffsetX();
		int featureToggle_peekOffsetY();
	}

	private static final int DEFER_HIDE_DELAY_TICKS = 0;   // initial wait before first check
	private static final int DEFER_HIDE_TIMEOUT_TICKS = 3; // give up if input never clears

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatPeekOverlay chatPeekOverlay;

	private boolean chatHidden = false;

	// Deferred hide state
	private boolean autoHide = false;
	private boolean deferredHideRequested = false;
	private int deferDelayTicksLeft = 0;
	private int deferTimeoutTicksLeft = 0;

	@Inject
	public ToggleChatFeature(ModernChatConfig config, EventBus eventBus) {
		super(config, eventBus);
	}

	@Override
	protected ToggleChatFeatureConfig extractConfig(ModernChatConfig config) {
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

			@Override
			public boolean featureToggle_lockCameraWhenVisible() {
				return config.featureToggle_lockCameraWhenVisible();
			}

			@Override
			public boolean featureToggle_peekOverlayEnabled() {
				return config.featureToggle_peekOverlayEnabled();
			}

			@Override
			public Color featureToggle_peekBgColor() {
				return config.featureToggle_peekBgColor();
			}

			@Override
			public Color featureToggle_peekBorderColor() {
				return config.featureToggle_peekBorderColor();
			}

			@Override
			public RuneFontStyle featureToggle_peekFontStyle() {
				return config.featureToggle_peekFontStyle();
			}

			@Override
			public int featureToggle_peekFontSize() {
				return config.featureToggle_peekFontSize();
			}

			@Override
			public int featureToggle_peekOffsetX() {
				return config.featureToggle_peekOffsetX();
			}

			@Override
			public int featureToggle_peekOffsetY() {
				return config.featureToggle_peekOffsetY();
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

		overlayManager.add(chatPeekOverlay);
		keyManager.registerKeyListener(this);
		chatHidden = config.featureToggle_StartHidden();

		clientThread.invoke(() -> {
			if (chatHidden && ClientUtil.isSystemTextEntryActive(client))
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

		overlayManager.remove(chatPeekOverlay);
		keyManager.unregisterKeyListener(this);
		chatHidden = false;
		clientThread.invoke(() -> {
			applyVisibilityNow();
			cancelDeferredHide();
		});
	}

	@Override
	public void keyTyped(KeyEvent e) {

	}

	@Override
	public void keyReleased(KeyEvent e) {

	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (!chatHidden && config.featureToggle_lockCameraWhenVisible()) {
			switch (e.getKeyCode()) {
				case java.awt.event.KeyEvent.VK_LEFT:
				case java.awt.event.KeyEvent.VK_RIGHT:
				case java.awt.event.KeyEvent.VK_UP:
				case java.awt.event.KeyEvent.VK_DOWN:
					e.consume(); // don’t let the client see the key
					break;
			}
		}

		if (!isCanvasFocused())
			return;

		Keybind kb = config.featureToggle_ToggleKey();
		if (kb == null || !kb.matches(e))
			return;

		clientThread.invoke(() -> {
			// If we are currently typing in a system prompt,
			// do not toggle chat visibility.
			if (ClientUtil.isSystemTextEntryActive(client))
			{
				cancelDeferredHide();
				return;
			}

			// If there is actual text ready to send in chat, defer the hide.
			if (hasPendingChatInputCT())
			{
				if (!chatHidden && config.featureToggle_autoHideOnSend())
					scheduleDeferredHide();
				return;
			}

			cancelDeferredHide();
			chatHidden = !chatHidden;
			applyVisibilityNow();
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
				if (ClientUtil.isSystemTextEntryActive(client))
				{
					cancelDeferredHide();
					chatHidden = false;
					applyVisibilityNow();
				}
			});
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		String name = e.getName();
		String msg  = e.getMessage();
		String line = (name != null && !name.isEmpty()) ? name + ": " + msg : msg;
		String prefix = "";

		Color c;
		switch (e.getType()) {
			case PUBLICCHAT:
				c = Color.WHITE;
				break;
			case FRIENDSCHAT:
				c = new Color(0x00FF80);
				break;
			case CLAN_CHAT:
			case CLAN_GUEST_CHAT:
				c = new Color(0x80C0FF);
				prefix = "[Clan] ";
				break;
			case PRIVATECHAT:
				c = new Color(0xFF80FF);
				break;
			default:
				c = new Color(0xC0C0C0); // light gray
				prefix = "[System] ";
		}

		chatPeekOverlay.pushLine(prefix + line, c);
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
			autoHide = true;
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
		if (ClientUtil.isSystemTextEntryActive(client))
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
			// Timed out, do nothing
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
		if (chatHidden)
			return false;

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
