package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.Padding;
import com.modernchat.event.ChatVisibilityChangeEvent;
import com.modernchat.event.MessageLayerClosedEvent;
import com.modernchat.event.MessageLayerOpenedEvent;
import com.modernchat.overlay.ChatOverlay;
import com.modernchat.overlay.ChatOverlayConfig;
import com.modernchat.overlay.MessageContainerConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.awt.Color;

import static com.modernchat.feature.ChatRedesignFeature.ChatRedesignFeatureConfig;

@Slf4j
@Singleton
public class ChatRedesignFeature extends AbstractChatFeature<ChatRedesignFeatureConfig>
{
    @Override public String getConfigGroup() { return "featureRedesign"; }

    public interface ChatRedesignFeatureConfig extends ChatFeatureConfig
    {
        boolean featureRedesign_Enabled();
    }

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private OverlayManager overlayManager;
    @Inject private WidgetBucket widgetBucket;
    @Inject private ChatOverlay overlay;

    private final ModernChatConfig mainConfig;

    @Inject
    public ChatRedesignFeature(ModernChatConfig config, EventBus eventBus) {
        super(config, eventBus);
        mainConfig = config;
    }

    @Override
    protected ChatRedesignFeatureConfig partitionConfig(ModernChatConfig cfg) {
        return new ChatRedesignFeatureConfig() {
            @Override public boolean featureRedesign_Enabled() { return cfg.featureRedesign_Enabled(); }
        };
    }

    protected ChatOverlayConfig partitionConfig(ChatRedesignFeatureConfig cfg) {
        return new ChatOverlayConfig.Default() {
            @Override public boolean isEnabled() { return cfg.featureRedesign_Enabled(); }
            @Override public boolean isHideOnSend() { return mainConfig.featureToggle_AutoHideOnSend(); }
            @Override public boolean isHideOnEscape() { return mainConfig.featureToggle_EscapeHides(); }
            @Override public Padding getPadding() { return super.getPadding();}
            @Override public int getInputLineSpacing() { return super.getInputLineSpacing(); }

            @Override
            public int getInputFontSize() {
                return super.getInputFontSize();
            }

            @Override
            public Color getBackdropColor() {
                return super.getBackdropColor();
            }

            @Override
            public Color getBorderColor() {
                return super.getBorderColor();
            }

            @Override
            public Color getInputPrefixColor() {
                return super.getInputPrefixColor();
            }

            @Override
            public Color getInputBackgroundColor() {
                return super.getInputBackgroundColor();
            }

            @Override
            public Color getInputBorderColor() {
                return super.getInputBorderColor();
            }

            @Override
            public Color getInputShadowColor() {
                return super.getInputShadowColor();
            }

            @Override
            public Color getInputTextColor() {
                return super.getInputTextColor();
            }

            @Override
            public Color getInputCaretColor() {
                return super.getInputCaretColor();
            }

            @Override
            public MessageContainerConfig getMessageContainerConfig() {
                return super.getMessageContainerConfig();
            }
        };
    }

    @Override
    public boolean isEnabled() {
        return config.featureRedesign_Enabled();
    }

    @Override
    public void startUp() {
        super.startUp();

        overlay.startUp(partitionConfig(config));
        overlayManager.add(overlay);

        // Hide original message lines on the client thread
        clientThread.invoke(this::hideLegacyChat);
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        overlay.shutDown();
        overlayManager.remove(overlay);

        // Restore original message lines
        clientThread.invoke(this::showLegacyChat);

        super.shutDown(fullShutdown);
    }

    @Subscribe
    public void onChatVisibilityChangeEvent(ChatVisibilityChangeEvent e) {
        if (e.isVisible()) {
            hideLegacyChat();
            overlay.focusInput();
        }
    }

    @Subscribe
    public void onMessageLayerOpenedEvent(MessageLayerOpenedEvent e) {
        clientThread.invoke(this::showLegacyChat);
    }

    @Subscribe
    public void onMessageLayerClosedEvent(MessageLayerClosedEvent e) {
        clientThread.invoke(this::hideLegacyChat);
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        if (e.getScriptId() == ScriptID.BUILD_CHATBOX) {
            clientThread.invoke(this::hideLegacyChat);
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e) {
        // If the chatbox is loaded, we can suppress original message lines
        if (e.getGroupId() == InterfaceID.CHATBOX) {
            clientThread.invoke(this::hideLegacyChat);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGGED_IN) {
            clientThread.invoke(this::hideLegacyChat);
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        overlay.inputTick();
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        Player localPlayer = client.getLocalPlayer();
        ChatMessageType type = e.getType();
        String name = (type == ChatMessageType.PRIVATECHATOUT
            ? (localPlayer != null ? localPlayer.getName() : "Me")
            : e.getName());
        String msg = e.getMessage();
        String line = (name != null && !name.isEmpty()) ? name + ": " + msg : msg;
        long timestamp = e.getTimestamp() > 0 ? e.getTimestamp() : System.currentTimeMillis();

        overlay.addMessage(line, type, timestamp);
    }

    public void showLegacyChat() {
        overlay.showLegacyChat();
    }

    public void hideLegacyChat() {
        overlay.hideLegacyChat();
    }
}
