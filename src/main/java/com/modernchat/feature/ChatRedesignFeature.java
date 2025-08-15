package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.WidgetBucket;
import com.modernchat.event.ChatVisibilityChangeEvent;
import com.modernchat.event.MessageLayerClosedEvent;
import com.modernchat.event.MessageLayerOpenedEvent;
import com.modernchat.overlay.ChatOverlay;
import com.modernchat.overlay.ChatOverlayConfig;
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

    @Inject
    public ChatRedesignFeature(ModernChatConfig config, EventBus eventBus) {
        super(config, eventBus);
    }

    @Override
    protected ChatRedesignFeatureConfig partitionConfig(ModernChatConfig cfg) {
        return new ChatRedesignFeatureConfig() {
            @Override public boolean featureRedesign_Enabled() { return true;/*cfg.featureRedesign_Enabled();*/ }
        };
    }

    protected ChatOverlayConfig partitionConfig(ChatRedesignFeatureConfig cfg) {
        return new ChatOverlayConfig.Default() {
            @Override public boolean isEnabled() { return cfg.featureRedesign_Enabled(); }
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
            clientThread.invoke(() -> {
                hideLegacyChat();
                overlay.focusInput();
            });
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

    private boolean isLegacyChatVisible() {
        Widget chatArea = widgetBucket.getChatBoxArea();
        return chatArea != null && !chatArea.isHidden();
    }

    public void showLegacyChat() {
        setLegacyChatAreaHidden(false);
        overlay.setHidden(true);
    }

    public void hideLegacyChat() {
        setLegacyChatAreaHidden(true);
        overlay.setHidden(false);
    }

    private void setLegacyChatAreaHidden(boolean hidden) {
        Widget frame = widgetBucket.getChatBoxArea();
        if (frame != null) {
            frame.setHidden(hidden);
        }
    }


}
