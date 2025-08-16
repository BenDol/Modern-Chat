package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.WidgetBucket;
import com.modernchat.event.ChatToggleEvent;
import com.modernchat.event.ModernChatVisibilityChangeEvent;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.GeometryUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

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
		boolean featureToggle_EscapeHides();
		boolean featureToggle_StartHidden();
		boolean featureToggle_AutoHideOnSend();
		boolean featureToggle_LockCameraWhenVisible();
	}

	private static final int DEFER_HIDE_DELAY_TICKS = 0;   // initial wait before first check
	private static final int DEFER_HIDE_TIMEOUT_TICKS = 5; // give up if input never clears
	public static Rectangle LAST_CHAT_BOUNDS = null;

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private KeyManager keyManager;
	@Inject private WidgetBucket widgetBucket;

	private final AtomicBoolean chatHidden = new AtomicBoolean(false);

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
	protected ToggleChatFeatureConfig partitionConfig(ModernChatConfig config) {
		return new ToggleChatFeatureConfig() {
			@Override public boolean featureToggle_Enabled() { return config.featureToggle_Enabled(); }
			@Override public Keybind featureToggle_ToggleKey() { return config.featureToggle_ToggleKey(); }
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
		chatHidden.set(config.featureToggle_StartHidden());

		clientThread.invoke(() -> {
			if (chatHidden.get() && ClientUtil.isSystemTextEntryActive(client)) {
				chatHidden.set(false);
			}
			applyVisibilityNow();
			cancelDeferredHide();
		});
	}

	@Override
	public void shutDown(boolean fullShutdown) {
		super.shutDown(fullShutdown);

		keyManager.unregisterKeyListener(this);
		chatHidden.set(false);
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
		if (!chatHidden.get() && config.featureToggle_LockCameraWhenVisible()) {
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

		if (e.isConsumed())
			return;

		Keybind kb = config.featureToggle_ToggleKey();
		if (kb == null || !kb.matches(e)) {
			if (config.featureToggle_EscapeHides()) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					hide();
				}
            }
            return;
        }

		clientThread.invoke(() -> {
			// If we are currently typing in a system prompt,
			// do not toggle chat visibility.
			if (ClientUtil.isSystemTextEntryActive(client)) {
				cancelDeferredHide();
				return;
			}

			// If there is actual text ready to send in chat, defer the hide.
			if (hasPendingChatInputCT()) {
				if (!chatHidden.get() && config.featureToggle_AutoHideOnSend())
					scheduleDeferredHide();
				return;
			}

			cancelDeferredHide();
			chatHidden.set(!chatHidden.get());
			applyVisibilityNow();
		});
	}

	@Subscribe
	public void onModernChatVisibilityChangeEvent(ModernChatVisibilityChangeEvent e) {
		chatHidden.set(!e.isVisible());
		cancelDeferredHide();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e) {
		if (e.getGameState() == GameState.LOGGED_IN) {
			clientThread.invoke(() -> {
				applyVisibilityNow();

				// If logging in while a prompt is open, avoid immediate hide
				if (ClientUtil.isSystemTextEntryActive(client)) {
					cancelDeferredHide();
					chatHidden.set(false);
					applyVisibilityNow();
				}

				if (config.featureToggle_StartHidden()) {
					scheduleDeferredHide();
				}
			});
		}
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged e) {
		if (e.getIndex() != VarClientStr.INPUT_TEXT)
			return;

		// When the player starts typing into a system prompt, ensure chat is shown
		String s = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
		if (s != null && chatHidden.get()) {
			chatHidden.set(false);
			autoHide = true;
			applyVisibilityNow();
		}
		cancelDeferredHide();
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		handleDeferredHide();
	}

	private boolean handleDeferredHide() {
		if (!deferredHideRequested && !autoHide) {
			return false;
		}

		// If a system prompt appears during deferral, abort the hide
		if (ClientUtil.isSystemTextEntryActive(client)) {
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
		chatHidden.set(true);
		applyVisibilityNow();
		cancelDeferredHide();
	}

	public void show() {
		chatHidden.set(false);
		applyVisibilityNow();
		cancelDeferredHide();
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

	/** MUST be on client thread: apply the current chat visibility state. */
	private void applyVisibilityNow() {
		Widget root = widgetBucket.getChatWidget();
		if (root != null) {
			LAST_CHAT_BOUNDS = root.getBounds();
 			ClientUtil.setChatHidden(client, chatHidden.get());
			eventBus.post(new ChatToggleEvent(chatHidden.get()));
		}
	}

	private boolean isCanvasFocused() {
		Canvas canvas = client.getCanvas();
		return canvas != null && canvas.hasFocus();
	}

	/** MUST be on client thread: true if the chat input line contains a real message to send. */
	private boolean hasPendingChatInputCT() {
		if (chatHidden.get())
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
        return !t.endsWith(": *");
    }
}
