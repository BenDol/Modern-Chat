package com.modernchat.feature;

import com.google.inject.Provides;
import com.modernchat.ModernChatConfig;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.Padding;
import com.modernchat.event.LegacyChatVisibilityChangeEvent;
import com.modernchat.event.MessageLayerClosedEvent;
import com.modernchat.event.MessageLayerOpenedEvent;
import com.modernchat.overlay.ChatOverlay;
import com.modernchat.overlay.ChatOverlayConfig;
import com.modernchat.overlay.MessageContainer;
import com.modernchat.overlay.MessageContainerConfig;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
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
        boolean featureRedesign_StartHidden();
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

    @Provides
    MessageContainer provideMessageContainer(ChatRedesignFeatureConfig cfg) {
        return new MessageContainer();
    }

    @Override
    protected ChatRedesignFeatureConfig partitionConfig(ModernChatConfig cfg) {
        return new ChatRedesignFeatureConfig() {
            @Override public boolean featureRedesign_Enabled() { return cfg.featureRedesign_Enabled(); }
            @Override public boolean featureRedesign_StartHidden() { return cfg.featureToggle_StartHidden(); }
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
        clientThread.invoke(() -> overlay.hideLegacyChat(false));
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        overlay.shutDown();
        overlayManager.remove(overlay);

        super.shutDown(fullShutdown);

        // Restore original
        clientThread.invoke(this::showLegacyChatAndHideOverlay);
    }

    @Subscribe
    public void onLegacyChatVisibilityChangeEvent(LegacyChatVisibilityChangeEvent e) {
        if (e.isVisible()) {
            overlay.hideLegacyChat();
            overlay.focusInput();
        }
    }

    @Subscribe
    public void onMessageLayerOpenedEvent(MessageLayerOpenedEvent e) {
        clientThread.invoke(() -> overlay.showLegacyChat());
    }

    @Subscribe
    public void onMessageLayerClosedEvent(MessageLayerClosedEvent e) {
        clientThread.invoke(() -> overlay.hideLegacyChat(false));
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        if (e.getScriptId() == ScriptID.BUILD_CHATBOX) {
            clientThread.invoke(() -> overlay.hideLegacyChat(false));
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e) {
        // If the chatbox is loaded, we can suppress original message lines
        if (e.getGroupId() == InterfaceID.CHATBOX) {
            clientThread.invoke(() -> overlay.hideLegacyChat(false));
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGGED_IN) {
            clientThread.invoke(() -> overlay.hideLegacyChat(false));
        }

        if (e.getGameState() == GameState.LOGGED_IN || e.getGameState() == GameState.LOGIN_SCREEN)
            overlay.refreshTabs();

        if (config.featureRedesign_StartHidden())
            clientThread.invokeAtTickEnd(() -> overlay.setHidden(true));
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        overlay.inputTick();
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
            return;

        String localPlayerName = localPlayer.getName();
        if (StringUtil.isNullOrEmpty(localPlayerName))
            return;

        long timestamp = e.getTimestamp() > 0 ? e.getTimestamp() : System.currentTimeMillis();

        ChatMessageType type = e.getType();
        String msg = e.getMessage();
        String name = e.getName();
        String receiverName = null;
        String senderName = e.getSender();
        String prefix = "";

        if (type == ChatMessageType.PRIVATECHATOUT) {
            receiverName = name;
            senderName = "You";
        }
        else if (type == ChatMessageType.PRIVATECHAT) {
            receiverName = localPlayerName;
            senderName = name;
        }
        else if (ChatUtil.isClanMessage(type) || ChatUtil.isFriendsChatMessage(type)) {
            senderName = name;
            prefix = e.getSender() != null ? "(" + e.getSender() + ") " : "";
        }
        else if (senderName == null) {
            senderName = name;
        }

        if (receiverName == null) {
            receiverName = localPlayerName;
        }

        String line = (senderName != null && !senderName.isEmpty()) ? senderName + ": " + msg : msg;

        log.info("Chat message received: type={}, sender={}, receiver={}, message={}",
                type, senderName, receiverName, line);

        overlay.addMessage(line, type, timestamp, senderName, receiverName, prefix);
    }

    @Subscribe
    public void onFriendsChatChanged(FriendsChatChanged e) {
        clientThread.invokeAtTickEnd(() -> overlay.refreshTabs());
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged e)  {
        clientThread.invokeAtTickEnd(() -> overlay.refreshTabs());
    }

    public void showLegacyChatAndHideOverlay() {
        overlay.showLegacyChat();
    }

    public void hideLegacyChatAndShowOverlay() {
        overlay.hideLegacyChat();
    }
}
