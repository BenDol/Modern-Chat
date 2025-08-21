package com.modernchat.feature;

import com.google.inject.Provides;
import com.modernchat.ModernChatConfig;
import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.FontStyle;
import com.modernchat.common.MessageLine;
import com.modernchat.common.NotificationService;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.Margin;
import com.modernchat.draw.Padding;
import com.modernchat.event.ChatResizedEvent;
import com.modernchat.event.ChatSendLockedEvent;
import com.modernchat.event.LegacyChatVisibilityChangeEvent;
import com.modernchat.event.MessageLayerClosedEvent;
import com.modernchat.event.MessageLayerOpenedEvent;
import com.modernchat.event.ModernChatVisibilityChangeEvent;
import com.modernchat.overlay.ChatOverlay;
import com.modernchat.overlay.ChatOverlayConfig;
import com.modernchat.overlay.MessageContainer;
import com.modernchat.overlay.MessageContainerConfig;
import com.modernchat.service.MessageService;
import com.modernchat.util.ChatUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.Locale;

import static com.modernchat.ModernChatConfig.CHAT_HEIGHT;
import static com.modernchat.ModernChatConfig.CHAT_WIDTH;
import static com.modernchat.ModernChatConfig.GROUP;
import static com.modernchat.feature.ChatRedesignFeature.ChatRedesignFeatureConfig;

@Slf4j
@Singleton
public class ChatRedesignFeature extends AbstractChatFeature<ChatRedesignFeatureConfig>
{
    @Override public String getConfigGroup() {
        return "featureRedesign";
    }

    public interface ChatRedesignFeatureConfig extends ChatFeatureConfig
    {
        boolean featureRedesign_Enabled();
        boolean featureRedesign_OpenTabOnIncomingPM();
        boolean featureRedesign_ClickOutsideToClose();
        boolean featureRedesign_ShowNotificationBadge();
        boolean featureRedesign_AllowClickThrough();
        boolean featureRedesign_AutoSelectPrivateTab();
        boolean featureRedesign_Resizeable();
        ChatMode featureRedesign_DefaultChatMode();
        FontStyle featureRedesign_FontStyle();
        int featureRedesign_Padding();
        Color featureRedesign_BackdropColor();
        Color featureRedesign_BorderColor();
        int featureRedesign_InputFontSize();
        Color featureRedesign_InputPrefixColor();
        Color featureRedesign_InputBackgroundColor();
        Color featureRedesign_InputBorderColor();
        Color featureRedesign_InputShadowColor();
        Color featureRedesign_InputTextColor();
        Color featureRedesign_InputCaretColor();
        int featureRedesign_TabFontSize();
        int featureRedesign_TabBadgeFontSize();
        Color featureRedesign_TabBarBackgroundColor();
        Color featureRedesign_TabColor();
        Color featureRedesign_TabSelectedColor();
        Color featureRedesign_TabBorderColor();
        Color featureRedesign_TabBorderSelectedColor();
        Color featureRedesign_TabTextColor();
        Color featureRedesign_TabUnreadPulseToColor();
        Color featureRedesign_TabUnreadPulseFromColor();
        Color featureRedesign_TabNotificationColor();
        Color featureRedesign_TabNotificationTextColor();
        Color featureRedesign_TabCloseButtonColor();
        Color featureRedesign_TabCloseButtonTextColor();

        // MessageContainerConfig
        boolean featureRedesign_MessageContainer_PrefixChatType();
        boolean featureRedesign_MessageContainer_ShowTimestamp();
        boolean featureRedesign_MessageContainer_Scrollable();
        boolean featureRedesign_MessageContainer_DrawScrollbar();
        int featureRedesign_MessageContainer_OffsetX();
        int featureRedesign_MessageContainer_OffsetY();
        int featureRedesign_MessageContainer_Margin();
        int featureRedesign_MessageContainer_PaddingTop();
        int featureRedesign_MessageContainer_PaddingLeft();
        int featureRedesign_MessageContainer_PaddingBottom();
        int featureRedesign_MessageContainer_PaddingRight();
        int featureRedesign_MessageContainer_LineSpacing();
        int featureRedesign_MessageContainer_ScrollStep();
        int featureRedesign_MessageContainer_ScrollbarWidth();
        FontStyle featureRedesign_MessageContainer_LineFontStyle();
        int featureRedesign_MessageContainer_LineFontSize();
        int featureRedesign_MessageContainer_TextShadow();
        Color featureRedesign_MessageContainer_BackdropColor();
        Color featureRedesign_MessageContainer_BorderColor();
        Color featureRedesign_MessageContainer_ShadowColor();
        Color featureRedesign_MessageContainer_ScrollbarTrackColor();
        Color featureRedesign_MessageContainer_ScrollbarThumbColor();
    }

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private OverlayManager overlayManager;
    @Inject private WidgetBucket widgetBucket;
    @Inject private MessageService messageService;
    @Inject private NotificationService notificationService;
    @Inject private ChatOverlay overlay;

    private final ModernChatConfig mainConfig;

    @Getter private volatile boolean sizeApplied;

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
            @Override public boolean featureRedesign_OpenTabOnIncomingPM() { return cfg.featureRedesign_OpenTabOnIncomingPM(); }
            @Override public boolean featureRedesign_ClickOutsideToClose() { return cfg.featureRedesign_ClickOutsideToClose(); }
            @Override public boolean featureRedesign_ShowNotificationBadge() { return cfg.featureRedesign_ShowNotificationBadge(); }
            @Override public boolean featureRedesign_AllowClickThrough() { return cfg.featureRedesign_AllowClickThrough(); }
            @Override public boolean featureRedesign_AutoSelectPrivateTab() { return cfg.featureRedesign_AutoSelectPrivateTab(); }
            @Override public boolean featureRedesign_Resizeable() { return cfg.featureRedesign_Resizeable(); }
            @Override public FontStyle featureRedesign_FontStyle() { return cfg.featureRedesign_FontStyle(); }
            @Override public int featureRedesign_Padding() { return cfg.featureRedesign_Padding(); }
            @Override public int featureRedesign_InputFontSize() { return cfg.featureRedesign_InputFontSize(); }
            @Override public Color featureRedesign_BackdropColor() { return cfg.featureRedesign_BackdropColor(); }
            @Override public Color featureRedesign_BorderColor() { return cfg.featureRedesign_BorderColor(); }
            @Override public Color featureRedesign_InputPrefixColor() { return cfg.featureRedesign_InputPrefixColor(); }
            @Override public Color featureRedesign_InputBackgroundColor() { return cfg.featureRedesign_InputBackgroundColor(); }
            @Override public Color featureRedesign_InputBorderColor() { return cfg.featureRedesign_InputBorderColor(); }
            @Override public Color featureRedesign_InputShadowColor() { return cfg.featureRedesign_InputShadowColor(); }
            @Override public Color featureRedesign_InputTextColor() { return cfg.featureRedesign_InputTextColor(); }
            @Override public Color featureRedesign_InputCaretColor() { return cfg.featureRedesign_InputCaretColor(); }
            @Override public int featureRedesign_TabFontSize() { return cfg.featureRedesign_TabFontSize(); }
            @Override public int featureRedesign_TabBadgeFontSize() { return cfg.featureRedesign_TabBadgeFontSize(); }
            @Override public Color featureRedesign_TabBarBackgroundColor() { return cfg.featureRedesign_TabBarBackgroundColor(); }
            @Override public Color featureRedesign_TabColor() { return cfg.featureRedesign_TabColor(); }
            @Override public Color featureRedesign_TabSelectedColor() { return cfg.featureRedesign_TabSelectedColor(); }
            @Override public Color featureRedesign_TabBorderColor() { return cfg.featureRedesign_TabBorderColor(); }
            @Override public Color featureRedesign_TabBorderSelectedColor() { return cfg.featureRedesign_TabBorderSelectedColor(); }
            @Override public Color featureRedesign_TabTextColor() { return cfg.featureRedesign_TabTextColor(); }
            @Override public Color featureRedesign_TabUnreadPulseToColor() { return cfg.featureRedesign_TabUnreadPulseToColor(); }
            @Override public Color featureRedesign_TabUnreadPulseFromColor() { return cfg.featureRedesign_TabUnreadPulseFromColor(); }
            @Override public Color featureRedesign_TabNotificationColor() { return cfg.featureRedesign_TabNotificationColor(); }
            @Override public Color featureRedesign_TabNotificationTextColor() { return cfg.featureRedesign_TabNotificationTextColor(); }
            @Override public Color featureRedesign_TabCloseButtonColor() { return cfg.featureRedesign_TabCloseButtonColor(); }
            @Override public Color featureRedesign_TabCloseButtonTextColor() { return cfg.featureRedesign_TabCloseButtonTextColor(); }
            @Override public ChatMode featureRedesign_DefaultChatMode() { return cfg.featureRedesign_DefaultChatMode(); }
            @Override public boolean featureRedesign_MessageContainer_PrefixChatType() { return cfg.featureRedesign_MessageContainer_PrefixChatType(); }
            @Override public boolean featureRedesign_MessageContainer_ShowTimestamp() { return cfg.featureRedesign_MessageContainer_ShowTimestamp(); }
            @Override public boolean featureRedesign_MessageContainer_Scrollable() { return cfg.featureRedesign_MessageContainer_Scrollable(); }
            @Override public boolean featureRedesign_MessageContainer_DrawScrollbar() { return cfg.featureRedesign_MessageContainer_DrawScrollbar(); }
            @Override public int featureRedesign_MessageContainer_OffsetX() { return cfg.featureRedesign_MessageContainer_OffsetX(); }
            @Override public int featureRedesign_MessageContainer_OffsetY() { return cfg.featureRedesign_MessageContainer_OffsetY(); }
            @Override public int featureRedesign_MessageContainer_Margin() { return cfg.featureRedesign_MessageContainer_Margin(); }
            @Override public int featureRedesign_MessageContainer_PaddingTop() { return cfg.featureRedesign_MessageContainer_PaddingTop(); }
            @Override public int featureRedesign_MessageContainer_PaddingLeft() { return cfg.featureRedesign_MessageContainer_PaddingLeft(); }
            @Override public int featureRedesign_MessageContainer_PaddingBottom() { return cfg.featureRedesign_MessageContainer_PaddingBottom(); }
            @Override public int featureRedesign_MessageContainer_PaddingRight() { return cfg.featureRedesign_MessageContainer_PaddingRight(); }
            @Override public int featureRedesign_MessageContainer_LineSpacing() { return cfg.featureRedesign_MessageContainer_LineSpacing(); }
            @Override public int featureRedesign_MessageContainer_ScrollStep() { return cfg.featureRedesign_MessageContainer_ScrollStep(); }
            @Override public int featureRedesign_MessageContainer_ScrollbarWidth() { return cfg.featureRedesign_MessageContainer_ScrollbarWidth(); }
            @Override public FontStyle featureRedesign_MessageContainer_LineFontStyle() { return cfg.featureRedesign_MessageContainer_LineFontStyle(); }
            @Override public int featureRedesign_MessageContainer_LineFontSize() { return cfg.featureRedesign_MessageContainer_LineFontSize(); }
            @Override public int featureRedesign_MessageContainer_TextShadow() { return cfg.featureRedesign_MessageContainer_TextShadow(); }
            @Override public Color featureRedesign_MessageContainer_BackdropColor() { return cfg.featureRedesign_MessageContainer_BackdropColor(); }
            @Override public Color featureRedesign_MessageContainer_BorderColor() { return cfg.featureRedesign_MessageContainer_BorderColor(); }
            @Override public Color featureRedesign_MessageContainer_ShadowColor() { return cfg.featureRedesign_MessageContainer_ShadowColor(); }
            @Override public Color featureRedesign_MessageContainer_ScrollbarTrackColor() { return cfg.featureRedesign_MessageContainer_ScrollbarTrackColor(); }
            @Override public Color featureRedesign_MessageContainer_ScrollbarThumbColor() { return cfg.featureRedesign_MessageContainer_ScrollbarThumbColor(); }
        };
    }

    protected ChatOverlayConfig partitionConfig(ChatRedesignFeatureConfig cfg) {
        return new ChatOverlayConfig.Default() {
            @Override public boolean isEnabled() { return cfg.featureRedesign_Enabled(); }
            @Override public boolean isHideOnSend() { return mainConfig.featureToggle_AutoHideOnSend(); }
            @Override public boolean isHideOnEscape() { return mainConfig.featureToggle_EscapeHides(); }
            @Override public boolean isStartHidden() { return mainConfig.featureToggle_StartHidden(); }
            @Override public boolean isShowNotificationBadge() { return cfg.featureRedesign_ShowNotificationBadge(); }
            @Override public boolean isAllowClickThrough() { return cfg.featureRedesign_AllowClickThrough(); }
            @Override public boolean isAutoSelectPrivateTab() { return cfg.featureRedesign_AutoSelectPrivateTab(); }
            @Override public boolean isResizeable() { return cfg.featureRedesign_Resizeable(); }
            @Override public FontStyle getFontStyle() { return cfg.featureRedesign_FontStyle(); }
            @Override public Padding getPadding() { return new Padding(cfg.featureRedesign_Padding()); }
            @Override public boolean isOpenTabOnIncomingPM() { return cfg.featureRedesign_OpenTabOnIncomingPM(); }
            @Override public boolean isClickOutsideToClose() { return cfg.featureRedesign_ClickOutsideToClose(); }
            @Override public ChatMode getDefaultChatMode() { return cfg.featureRedesign_DefaultChatMode(); }
            @Override public int getInputFontSize() { return cfg.featureRedesign_InputFontSize(); }
            @Override public Color getBackdropColor() { return cfg.featureRedesign_BackdropColor(); }
            @Override public Color getBorderColor() { return cfg.featureRedesign_BorderColor(); }
            @Override public Color getInputPrefixColor() { return cfg.featureRedesign_InputPrefixColor(); }
            @Override public Color getInputBackgroundColor() { return cfg.featureRedesign_InputBackgroundColor(); }
            @Override public Color getInputBorderColor() { return cfg.featureRedesign_InputBorderColor(); }
            @Override public Color getInputShadowColor() { return cfg.featureRedesign_InputShadowColor(); }
            @Override public Color getInputTextColor() { return cfg.featureRedesign_InputTextColor(); }
            @Override public Color getInputCaretColor() { return cfg.featureRedesign_InputCaretColor();}
            @Override public int getTabFontSize() { return cfg.featureRedesign_TabFontSize(); }
            @Override public int getTabBadgeFontSize() { return cfg.featureRedesign_TabBadgeFontSize(); }
            @Override public Color getTabBarBackgroundColor() { return cfg.featureRedesign_TabBarBackgroundColor(); }
            @Override public Color getTabColor() { return cfg.featureRedesign_TabColor(); }
            @Override public Color getTabSelectedColor() { return cfg.featureRedesign_TabSelectedColor(); }
            @Override public Color getTabBorderColor() { return cfg.featureRedesign_TabBorderColor(); }
            @Override public Color getTabBorderSelectedColor() { return cfg.featureRedesign_TabBorderSelectedColor(); }
            @Override public Color getTabTextColor() { return cfg.featureRedesign_TabTextColor(); }
            @Override public Color getTabUnreadPulseToColor() { return cfg.featureRedesign_TabUnreadPulseToColor(); }
            @Override public Color getTabUnreadPulseFromColor() { return cfg.featureRedesign_TabUnreadPulseFromColor(); }
            @Override public Color getTabNotificationColor() { return cfg.featureRedesign_TabNotificationColor(); }
            @Override public Color getTabNotificationTextColor() { return cfg.featureRedesign_TabNotificationTextColor(); }
            @Override public Color getTabCloseButtonColor() { return cfg.featureRedesign_TabCloseButtonColor(); }
            @Override public Color getTabCloseButtonTextColor() { return cfg.featureRedesign_TabCloseButtonTextColor(); }

            @Override public MessageContainerConfig getMessageContainerConfig() { return containerConfig; }

            final MessageContainerConfig containerConfig = new MessageContainerConfig.Default() {
                @Override public boolean isPrefixChatType() { return cfg.featureRedesign_MessageContainer_PrefixChatType(); }
                @Override public boolean isShowTimestamp() { return cfg.featureRedesign_MessageContainer_ShowTimestamp(); }
                @Override public boolean isScrollable() { return cfg.featureRedesign_MessageContainer_Scrollable(); }
                @Override public boolean isDrawScrollbar() { return cfg.featureRedesign_MessageContainer_DrawScrollbar(); }
                @Override public Point getOffset() { return new Point(cfg.featureRedesign_MessageContainer_OffsetX(), cfg.featureRedesign_MessageContainer_OffsetY()); }
                @Override public Margin getMargin() { return new Margin(cfg.featureRedesign_MessageContainer_Margin()); }
                @Override public Padding getPadding() { return new Padding(cfg.featureRedesign_MessageContainer_PaddingTop(), cfg.featureRedesign_MessageContainer_PaddingBottom(), cfg.featureRedesign_MessageContainer_PaddingLeft(), cfg.featureRedesign_MessageContainer_PaddingRight()); }
                @Override public int getLineSpacing() { return cfg.featureRedesign_MessageContainer_LineSpacing(); }
                @Override public int getScrollStep() { return cfg.featureRedesign_MessageContainer_ScrollStep(); }
                @Override public int getScrollbarWidth() { return cfg.featureRedesign_MessageContainer_ScrollbarWidth(); }
                @Override public FontStyle getLineFontStyle() { return cfg.featureRedesign_MessageContainer_LineFontStyle(); }
                @Override public int getLineFontSize() { return cfg.featureRedesign_MessageContainer_LineFontSize(); }
                @Override public int getTextShadow() { return cfg.featureRedesign_MessageContainer_TextShadow(); }
                @Override public Color getBackdropColor() { return cfg.featureRedesign_MessageContainer_BackdropColor(); }
                @Override public Color getBorderColor() { return cfg.featureRedesign_MessageContainer_BorderColor(); }
                @Override public Color getShadowColor() { return cfg.featureRedesign_MessageContainer_ShadowColor(); }
                @Override public Color getScrollbarTrackColor() { return cfg.featureRedesign_MessageContainer_ScrollbarTrackColor(); }
                @Override public Color getScrollbarThumbColor() { return cfg.featureRedesign_MessageContainer_ScrollbarThumbColor(); }
                @Override public Color getWelcomeColor() { return mainConfig.general_WelcomeChatColor(); }
                @Override public Color getPublicColor() { return mainConfig.general_PublicChatColor(); }
                @Override public Color getPrivateColor() { return mainConfig.general_PrivateChatColor(); }
                @Override public Color getFriendColor() { return mainConfig.general_FriendsChatColor(); }
                @Override public Color getClanColor() { return mainConfig.general_ClanChatColor(); }
                @Override public Color getSystemColor() { return mainConfig.general_SystemChatColor(); }
                @Override public Color getTradeColor() { return mainConfig.general_TradeChatColor(); }
            };
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
        clientThread.invoke(() -> {
            if (!overlay.isLegacyShowing())
                overlay.hideLegacyChat(false);
        });
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        overlay.shutDown();
        overlayManager.remove(overlay);

        super.shutDown(fullShutdown);

        // Restore original
        clientThread.invokeAtTickEnd(this::showLegacyChatAndHideOverlay);
    }

    private void loadChatSize() {
        overlay.setDesiredChatSize(
            configManager.getRSProfileConfiguration(GROUP, CHAT_WIDTH),
            configManager.getRSProfileConfiguration(GROUP, CHAT_HEIGHT));
    }

    @Subscribe
    public void onModernChatVisibilityChangeEvent(ModernChatVisibilityChangeEvent e) {
        // load on the first show to avoid legacy chat size bricking the original view
        if (e.isVisible() && !sizeApplied)
            loadChatSize();
    }

    @Subscribe
    public void onChatResizedEvent(ChatResizedEvent e) {
        configManager.setRSProfileConfiguration(GROUP, CHAT_WIDTH, e.getWidth());
        configManager.setRSProfileConfiguration(GROUP, CHAT_HEIGHT, e.getHeight());
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged e) {
        // When the profile changes, we need to refresh
        if (!overlay.isHidden())
            loadChatSize();
        else
            sizeApplied = false;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equals(ModernChatConfig.GROUP))
            return;

        String key = e.getKey();
        if (!key.startsWith(getConfigGroup() + "_"))
            return;

        overlay.dirty();
    }

    @Subscribe
    public void onLegacyChatVisibilityChangeEvent(LegacyChatVisibilityChangeEvent e) {
        if (e.isVisible() && !overlay.isLegacyShowing()) {
            overlay.hideLegacyChat(false);
        }
    }

    @Subscribe
    public void onMessageLayerOpenedEvent(MessageLayerOpenedEvent e) {
        clientThread.invoke(() -> {
            overlay.showLegacyChat();
        });
    }

    @Subscribe
    public void onMessageLayerClosedEvent(MessageLayerClosedEvent e) {
        clientThread.invoke(() -> {
            overlay.hideLegacyChat(false);
        });
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        if (e.getScriptId() == ScriptID.BUILD_CHATBOX) {
            clientThread.invoke(() -> {
                if (!overlay.isLegacyShowing())
                    overlay.hideLegacyChat(false);
            });
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e) {
        // If the chatbox is loaded, we can suppress original message lines
        if (e.getGroupId() == InterfaceID.CHATBOX) {
            clientThread.invoke(() -> {
                if (!overlay.isLegacyShowing())
                    overlay.hideLegacyChat(false);
            });
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed e) {
        // If the chatbox is loaded, we can suppress original message lines
        if (e.getGroupId() == InterfaceID.CHATBOX) {
            clientThread.invoke(() -> overlay.resetChatbox());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGGED_IN) {
            clientThread.invoke(() -> overlay.hideLegacyChat(false));

            overlay.refreshTabs();

            clientThread.invokeAtTickEnd(() -> overlay.selectDefaultTab());

            if (mainConfig.featureToggle_StartHidden())
                clientThread.invokeAtTickEnd(() -> overlay.setHidden(true));
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        overlay.inputTick();
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        MessageLine line = ChatUtil.createMessageLine(e, client);
        if (line == null) {
            log.error("Failed to parse chat message event: {}", e);
            return; // Ignore empty messages
        }

        log.debug("Chat message received: {}", line);
        overlay.addMessage(line);
    }

    @Subscribe
    public void onChatSendLockedEvent(ChatSendLockedEvent e) {
        if (e.isPrivate())
            return;

        var lockDelay = Math.max(0, messageService.getSendLockedUntil() - System.currentTimeMillis());

        ChatMessageBuilder messageBuilder = new ChatMessageBuilder()
            .append("You are sending messages too quickly. Please wait ")
            .append(Color.RED, String.format(Locale.ROOT, "%.1f", lockDelay / 1000.0))
            .append(Color.RED, " seconds");

        notificationService.pushChatMessage(messageBuilder);
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
