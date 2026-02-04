package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.ModernChatConfigBase;
import com.modernchat.common.ChatProxy;
import com.modernchat.common.ExtendedKeybind;
import com.modernchat.common.WidgetBucket;
import com.modernchat.service.ExtendedInputService;
import com.modernchat.event.ChatToggleEvent;
import com.modernchat.event.DialogOptionsClosedEvent;
import com.modernchat.event.DialogOptionsOpenedEvent;
import com.modernchat.event.LeftDialogClosedEvent;
import com.modernchat.event.LeftDialogOpenedEvent;
import com.modernchat.event.RightDialogClosedEvent;
import com.modernchat.event.RightDialogOpenedEvent;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.GeometryUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.modernchat.feature.ToggleChatFeature.ToggleChatFeatureConfig;

@Slf4j
@Singleton
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
		ExtendedKeybind featureToggle_ExtendedToggleKey();
		boolean featureToggle_EscapeHides();
		boolean featureToggle_StartHidden();
		boolean featureToggle_AutoHideOnSend();
		boolean featureToggle_LockCameraWhenVisible();
	}

	private static final int DEFER_HIDE_DELAY_TICKS = 0;   // initial wait before first check
	private static final int DEFER_HIDE_TIMEOUT_TICKS = 5; // give up if input never clears
	private static final String EXTENDED_BINDING_ID = "toggleChat";
	public static Rectangle LAST_CHAT_BOUNDS = null;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;
	@Inject private MouseManager mouseManager;
	@Inject private WidgetBucket widgetBucket;
	@Inject private ChatProxy chatProxy;
	@Inject private ExtendedInputService extendedInputService;

	private boolean loggedIn = false;

	// Deferred hide state
	private boolean autoHide = false;
	private boolean deferredHideRequested = false;
	private int deferDelayTicksLeft = 0;
	private int deferTimeoutTicksLeft = 0;

	// KeyRemapping escape detection (backup when Escape event is consumed)
	private static final String PRESS_ENTER_TO_CHAT = "Press Enter to Chat...";
	private boolean wasInTypingMode = false;
	private long lastKeyRemappingHideTime = 0;
	private static final long KEY_REMAPPING_HIDE_THROTTLE_MS = 150;

	@Inject
	public ToggleChatFeature(ModernChatConfig config, EventBus eventBus) {
		super(config, eventBus);
	}

	@Override
	protected ToggleChatFeatureConfig partitionConfig(ModernChatConfig config) {
		return new ToggleChatFeatureConfig() {
			@Override public boolean featureToggle_Enabled() { return config.featureToggle_Enabled(); }
			@Override public Keybind featureToggle_ToggleKey() { return config.featureToggle_ToggleKey(); }
			@Override public ExtendedKeybind featureToggle_ExtendedToggleKey() { return config.featureToggle_ExtendedToggleKey(); }
			@Override public boolean featureToggle_EscapeHides() { return config.featureToggle_EscapeHides(); }
			@Override public boolean featureToggle_StartHidden() { return config.featureToggle_StartHidden(); }
			@Override public boolean featureToggle_AutoHideOnSend() { return config.featureToggle_AutoHideOnSend(); }
			@Override public boolean featureToggle_LockCameraWhenVisible() { return config.featureToggle_LockCameraWhenVisible(); }
		};
	}

	@Override
	public boolean isEnabled() {
		return config.featureToggle_Enabled();
	}

	@Override
	public void startUp() {
		super.startUp();

		keyManager.registerKeyListener(this);
		registerExtendedKeybind();

		if (loggedIn) {
			clientThread.invokeAtTickEnd(() -> setHidden(config.featureToggle_StartHidden()));
		}
	}

	@Override
	public void shutDown(boolean fullShutdown) {
		super.shutDown(fullShutdown);

		keyManager.unregisterKeyListener(this);
		unregisterExtendedKeybind();

		clientThread.invoke(() -> setHidden(false));

		loggedIn = false;
		autoHide = false;
	}

	@Override
	public void onFeaturesStarted() {
		super.onFeaturesStarted();

		// We want to register this after all the other features have started,
		// so that it can be overridden.
		keyManager.unregisterKeyListener(this);
		keyManager.registerKeyListener(this);
		registerExtendedKeybind();
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (!chatProxy.isHidden() && config.featureToggle_LockCameraWhenVisible()) {
			switch (e.getKeyCode()) {
				case java.awt.event.KeyEvent.VK_LEFT:
				case java.awt.event.KeyEvent.VK_RIGHT:
				case java.awt.event.KeyEvent.VK_UP:
				case java.awt.event.KeyEvent.VK_DOWN:
					e.consume(); // don’t let the client see the key
					break;
			}
		}

		// HACK: When KeyRemappingPlugin is active and chat shows "Press Enter to Chat...",
		// pressing Backspace would delete the placeholder instead of opening chat.
		// We detect this and simulate a Slash key press to properly enter typing mode.
		if (chatProxy.isLegacy() && !chatProxy.isHidden()) {
			if (e.getKeyCode() != KeyEvent.VK_SLASH) {
				Widget input = ClientUtil.getChatInputWidget(client);
				if (input != null) {
					String raw = input.getText();
					if (raw != null) {
						String t = Text.removeTags(raw).trim();
						if (t.endsWith(PRESS_ENTER_TO_CHAT)) {
							simulateSlashKey();
							e.consume();
						}
					}
				}
			}
		}

		// Handle Escape before isConsumed check - KeyRemapping consumes Escape
		// when exiting typing mode, but we still need to hide our chat
		if (config.featureToggle_EscapeHides() && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
			clientThread.invoke(this::hide);
			return;
		}

		if (e.isConsumed())
			return;

		Keybind kb = config.featureToggle_ToggleKey();
		if (kb == null || kb.getKeyCode() != e.getKeyCode() || kb.getModifiers() != e.getModifiersEx()) {
			// log out the key event and kb for debugging purposes
			log.debug("KeyPressed: keycode={}, char='{}', modifiers={}, kb={}",
				e.getKeyCode(), e.getKeyChar(), e.getModifiersEx(), kb);
            return;
        }

		clientThread.invokeLater(() -> {
			// If we are currently typing in a system prompt,
			// do not toggle chat visibility.
			if (ClientUtil.isSystemWidgetActive(client)) {
				cancelDeferredHide();
				return;
			}

			// If there is actual text ready to send in chat, defer the hide.
			if (hasPendingChatInputCT()) {
				if (!chatProxy.isHidden() && config.featureToggle_AutoHideOnSend()) {
					scheduleDeferredHide();
				}
				return;
			}

			setHidden(!chatProxy.isHidden());
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e) {
		if (e.getGameState() == GameState.LOGGED_IN) {
			clientThread.invokeLater(() -> {
				// If logging in while a prompt is open, avoid immediate hide
				if (config.featureToggle_StartHidden()) {
					scheduleDeferredHide();
				} else {
					show();
				}
			});
			loggedIn = true;
		} else if (e.getGameState() == GameState.LOGIN_SCREEN) {
			loggedIn = false;
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged e) {
		if (e.getIndex() != VarClientStr.INPUT_TEXT)
			return;

		// When the player starts typing into a system prompt, ensure chat is shown
		String s = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
		if (s != null && chatProxy.isLegacyHidden() && chatProxy.isLegacy()) {
			chatProxy.ensureLegacyChatVisible();
			autoHide = true;
		}
	}

	@Subscribe
	public void onLeftDialogOpenedEvent(LeftDialogOpenedEvent e) {
		clientThread.invoke(() -> {
			if (chatProxy.isLegacyHidden()) {
				chatProxy.ensureLegacyChatVisible();
				autoHide = true;
			}
		});
	}

	@Subscribe
	public void onLeftDialogClosedEvent(LeftDialogClosedEvent e) {
		clientThread.invoke(() -> {
			if (autoHide && !chatProxy.isHidden() && chatProxy.isLegacy()) {
				chatProxy.setHidden(true);
			}
		});
	}

	@Subscribe
	public void onRightDialogOpenedEvent(RightDialogOpenedEvent e) {
		clientThread.invoke(() -> {
			if (chatProxy.isLegacyHidden()) {
				chatProxy.ensureLegacyChatVisible();
				autoHide = true;
			}
		});
	}

	@Subscribe
	public void onRightDialogClosedEvent(RightDialogClosedEvent e) {
		clientThread.invoke(() -> {
			if (autoHide && !chatProxy.isHidden() && chatProxy.isLegacy()) {
				chatProxy.setHidden(true);
			}
		});
	}

	@Subscribe
	public void onDialogOptionsOpenedEvent(DialogOptionsOpenedEvent e) {
		clientThread.invoke(() -> {
			if (chatProxy.isLegacyHidden()) {
				chatProxy.ensureLegacyChatVisible();
				autoHide = true;
			}
		});
	}

	@Subscribe
	public void onDialogOptionsClosedEvent(DialogOptionsClosedEvent e) {
		clientThread.invoke(() -> {
			if (autoHide && !chatProxy.isHidden() && chatProxy.isLegacy()) {
				chatProxy.setHidden(true);
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		handleDeferredHide();
		detectKeyRemappingEscape();
	}

	private boolean handleDeferredHide() {
		if (!deferredHideRequested && !autoHide) {
			return false;
		}

		// If a system prompt appears during deferral, abort the hide
		if (ClientUtil.isSystemWidgetActive(client)) {
			cancelDeferredHide();
			return false;
		} else if (autoHide) {
			autoHide = false;
			hide();
			cancelDeferredHide();
			return true;
		}

		// Initial short delay to allow the client to process an Enter press
		if (deferDelayTicksLeft > 0) {
			deferDelayTicksLeft--;
			return false;
		}

		// After delay, wait until the chat input has cleared (message sent)
		Widget chatWidget = widgetBucket.getChatboxViewportWidget();
		if (chatWidget != null) {
			LAST_CHAT_BOUNDS = chatWidget.getBounds();
		}

		boolean isChatBoundsValid = (LAST_CHAT_BOUNDS != null && !GeometryUtil.isInvalidChatBounds(LAST_CHAT_BOUNDS));

		if (!hasPendingChatInputCT() && isChatBoundsValid) {
			hide();
			return true;
		}

		// Still pending; keep waiting up to the timeout
		if (deferTimeoutTicksLeft > 0) {
			deferTimeoutTicksLeft--;
		}
		else if (isChatBoundsValid) {
			// Timed out, do nothing
			cancelDeferredHide();
		}
		return false;
	}

	public void hide() {
		setHidden(true);
	}

	public void show() {
		setHidden(false);
	}

	public void simulateEscapeKey() {
		ClientUtil.simulateKeyInput(client, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
	}

	private void simulateSlashKey() {
		ClientUtil.simulateKeyInput(client, KeyEvent.VK_SLASH, '/');
	}

	public void scheduleDeferredHide() {
		deferredHideRequested = true;
		deferDelayTicksLeft = DEFER_HIDE_DELAY_TICKS;
		deferTimeoutTicksLeft = DEFER_HIDE_TIMEOUT_TICKS;
	}

	public void cancelDeferredHide() {
		deferredHideRequested = false;
		deferDelayTicksLeft = 0;
		deferTimeoutTicksLeft = 0;
	}

	/**
	 * Detects when KeyRemappingPlugin has consumed Escape by watching for
	 * the chatbox input changing to "Press Enter to Chat..."
	 */
	private void detectKeyRemappingEscape() {
		if (chatProxy.isLegacy()) {
			return;
		}

		if (!config.featureToggle_EscapeHides()) {
			return;
		}

		// Throttle detection
		long now = System.currentTimeMillis();
		if ((now - lastKeyRemappingHideTime) < KEY_REMAPPING_HIDE_THROTTLE_MS) {
			return;
		}

		Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
		if (chatboxInput == null) {
			wasInTypingMode = false;
			return;
		}

		String inputText = chatboxInput.getText();
		boolean isInTypingMode = inputText != null && !inputText.contains(PRESS_ENTER_TO_CHAT);

		// Detect transition from typing mode to non-typing mode
		if (wasInTypingMode && !isInTypingMode) {
			// KeyRemapping likely consumed Escape - hide our chat if visible
			if (!chatProxy.isHidden()) {
				lastKeyRemappingHideTime = now;
				hide();
			}
		}

		wasInTypingMode = isInTypingMode;
	}

	private void setHidden(boolean hidden) {
		cancelDeferredHide();

		Widget root = widgetBucket.getChatWidget();
		if (root != null) {
			LAST_CHAT_BOUNDS = root.getBounds();
		}

		boolean wasHidden = chatProxy.isHidden();
		if (wasHidden == hidden)
			return;

		chatProxy.setHidden(hidden);
		eventBus.post(new ChatToggleEvent(hidden));
	}

	private boolean isCanvasFocused() {
		Canvas canvas = client.getCanvas();
		return canvas != null && canvas.hasFocus();
	}

	/** MUST be on client thread: true if the chat input line contains a real message to send. */
	private boolean hasPendingChatInputCT() {
		if (chatProxy.isLegacyHidden())
			return false;

		Widget input = ClientUtil.getChatInputWidget(client);
		if (input == null || input.isHidden())
			return false;

		String raw = input.getText();
		if (raw == null)
			return false;

		String t = Text.removeTags(raw).trim();
		if (t.isEmpty())
			return false;

        // empty chat placeholder
        return !t.endsWith(": *") && !t.endsWith(PRESS_ENTER_TO_CHAT);
    }

	@Override
	public void onFeatureConfigChanged(ConfigChanged e) {
		if (ModernChatConfigBase.Keys.featureToggle_ExtendedToggleKey.equals(e.getKey())) {
			unregisterExtendedKeybind();
			registerExtendedKeybind();
		}
	}

	private void registerExtendedKeybind() {
		ExtendedKeybind keybind = config.featureToggle_ExtendedToggleKey();
		extendedInputService.registerBinding(EXTENDED_BINDING_ID, keybind, (v) -> {
			Keybind primaryKey = config.featureToggle_ToggleKey();
			if (primaryKey != null) {
				// We're simulating the key input here to avoid issues with KeyRemappingPlugin.
				// This way the KeyRemappingPlugin will also see the key event.
				ClientUtil.simulateKeyInput(client, primaryKey.getKeyCode(), KeyEvent.CHAR_UNDEFINED, primaryKey.getModifiers());
			}
		});
	}

	private void unregisterExtendedKeybind() {
		extendedInputService.unregisterBinding(EXTENDED_BINDING_ID);
	}
}
