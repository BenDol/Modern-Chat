package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.ChatMode;
import com.modernchat.common.FontStyle;
import com.modernchat.common.MessageLine;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.ChatColors;
import com.modernchat.draw.Margin;
import com.modernchat.draw.Padding;
import com.modernchat.event.ChatMenuOpenedEvent;
import com.modernchat.event.ModernChatVisibilityChangeEvent;
import com.modernchat.overlay.ChatPeekOverlay;
import com.modernchat.overlay.MessageContainerConfig;
import com.modernchat.util.ChatUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

import static com.modernchat.feature.PeekChatFeature.PeekChatFeatureConfig;

@Slf4j
@Singleton
public class PeekChatFeature extends AbstractChatFeature<PeekChatFeatureConfig>
{
	@Override
	public String getConfigGroup() {
		return "featurePeek";
	}

	public interface PeekChatFeatureConfig extends ChatFeatureConfig, ChatColors {
		boolean featurePeek_Enabled();
		boolean featurePeek_FollowChatBox();
		boolean featurePeek_ShowPrivateMessages();
		boolean featurePeek_ShowTimestamp();
		boolean featurePeek_HideSplitPrivateMessages();
		Color featurePeek_BackgroundColor();
		Color featurePeek_BorderColor();
		FontStyle featurePeek_FontStyle();
		int featurePeek_FontSize();
		int featurePeek_TextShadow();
		int featurePeek_Padding();
		int featurePeek_OffsetX();
		int featurePeek_OffsetY();
		int featurePeek_MarginRight();
		int featurePeek_MarginBottom();
		boolean featurePeek_PrefixChatTypes();
		boolean featurePeek_FadeEnabled();
		int featurePeek_FadeDelay();
		int featurePeek_FadeDuration();
		boolean featureRedesign_ShowNpc();
	}

	@Inject private Client client;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatPeekOverlay chatPeekOverlay;
	@Inject private WidgetBucket widgetBucket;

	private final ModernChatConfig mainConfig;

	@Inject
	public PeekChatFeature(ModernChatConfig config, EventBus eventBus) {
		super(config, eventBus);
		mainConfig = config;
	}

	@Override
	protected PeekChatFeatureConfig partitionConfig(ModernChatConfig config) {
		return new PeekChatFeatureConfig() {
			@Override public boolean featurePeek_Enabled() { return config.featurePeek_Enabled(); }
			@Override public boolean featurePeek_FollowChatBox() { return config.featurePeek_FollowChatBox(); }
			@Override public boolean featurePeek_ShowPrivateMessages() { return config.featurePeek_ShowPrivateMessages(); }
			@Override public boolean featurePeek_ShowTimestamp() { return config.featurePeek_ShowTimestamp(); }
			@Override public boolean featurePeek_HideSplitPrivateMessages() { return config.featurePeek_HideSplitPrivateMessages(); }
			@Override public Color featurePeek_BackgroundColor() { return config.featurePeek_BackgroundColor(); }
			@Override public Color featurePeek_BorderColor() { return config.featurePeek_BorderColor(); }
			@Override public FontStyle featurePeek_FontStyle() { return config.featurePeek_FontStyle(); }
			@Override public int featurePeek_FontSize() { return config.featurePeek_FontSize(); }
			@Override public int featurePeek_TextShadow() { return config.featurePeek_TextShadow(); }
			@Override public int featurePeek_Padding() { return config.featurePeek_Padding(); }
			@Override public int featurePeek_OffsetX() { return config.featurePeek_OffsetX(); }
			@Override public int featurePeek_OffsetY() { return config.featurePeek_OffsetY(); }
			@Override public int featurePeek_MarginRight() { return config.featurePeek_MarginRight(); }
			@Override public int featurePeek_MarginBottom() { return config.featurePeek_MarginBottom(); }
			@Override public Color getPublicColor() { return config.general_PublicChatColor(); }
			@Override public boolean featurePeek_PrefixChatTypes() { return config.featurePeek_PrefixChatTypes(); }
			@Override public boolean featurePeek_FadeEnabled() { return config.featurePeek_FadeEnabled(); }
			@Override public int featurePeek_FadeDelay() { return config.featurePeek_FadeDelay(); }
			@Override public int featurePeek_FadeDuration() { return config.featurePeek_FadeDuration(); }
			@Override public boolean featureRedesign_ShowNpc() { return config.featureRedesign_ShowNpc(); }

			public Color featurePeek_FriendsChatColor() { return config.general_FriendsChatColor(); }
			public Color featurePeek_ClanChatColor() { return config.general_ClanChatColor(); }
			public Color featurePeek_PrivateChatColor() { return config.general_PrivateChatColor(); }
			public Color featurePeek_SystemChatColor() { return config.general_SystemChatColor(); }
			public Color featurePeek_TradeChatColor() { return config.general_TradeChatColor(); }
			public Color featurePeek_WelcomeChatColor() { return config.general_WelcomeChatColor(); }
		};
	}

	protected MessageContainerConfig partitionConfig(PeekChatFeatureConfig cfg) {
		return new MessageContainerConfig.Default() {
			@Override
			public boolean isEnabled() { return cfg.featurePeek_Enabled(); }
			@Override public boolean isPrefixChatType() { return cfg.featurePeek_PrefixChatTypes(); }
			@Override public boolean isShowTimestamp() { return cfg.featurePeek_ShowTimestamp(); }
			@Override public boolean isScrollable() { return false; } // Peek chat does not support scrolling
			@Override public boolean isDrawScrollbar() { return false; }
			@Override public boolean isShowPrivateMessages() { return cfg.featurePeek_ShowPrivateMessages(); }
			@Override public boolean isFollowChatBox() { return cfg.featurePeek_FollowChatBox(); }
			@Override public boolean isFadeEnabled() { return cfg.featurePeek_FadeEnabled(); }
			@Override public int getFadeDelay() { return cfg.featurePeek_FadeDelay(); }
			@Override public int getFadeDuration() { return cfg.featurePeek_FadeDuration(); }
			@Override public Point getOffset() { return new Point(cfg.featurePeek_OffsetX(), cfg.featurePeek_OffsetY()); }
			@Override public Margin getMargin() { return new Margin(0, cfg.featurePeek_MarginBottom(), 0, cfg.featurePeek_MarginRight()); }
			@Override public Padding getPadding() { return new Padding(cfg.featurePeek_Padding()); }
			@Override public int getLineSpacing() { return super.getLineSpacing(); }
			@Override public int getScrollStep() { return 0; }
			@Override public int getScrollbarWidth() { return 0; }
			@Override public FontStyle getLineFontStyle() { return cfg.featurePeek_FontStyle(); }
			@Override public int getLineFontSize() { return cfg.featurePeek_FontSize(); }
			@Override public int getTextShadow() { return cfg.featurePeek_TextShadow(); }
			@Override public Color getBackdropColor() { return cfg.featurePeek_BackgroundColor(); }
			@Override public Color getBorderColor() { return cfg.featurePeek_BorderColor(); }
			@Override public Color getShadowColor() { return super.getShadowColor(); }
			@Override public Color getScrollbarTrackColor() { return super.getScrollbarTrackColor(); }
			@Override public Color getScrollbarThumbColor() { return super.getScrollbarThumbColor(); }
			@Override public Color getWelcomeColor() { return cfg.getWelcomeColor(); }
			@Override public Color getPublicColor() { return cfg.getPublicColor(); }
			@Override public Color getPrivateColor() { return cfg.getPrivateColor(); }
			@Override public Color getFriendColor() { return cfg.getFriendColor(); }
			@Override public Color getClanColor() { return cfg.getClanColor(); }
			@Override public Color getSystemColor() { return cfg.getSystemColor(); }
			@Override public Color getTradeColor() { return cfg.getTradeColor(); }
		};
	}

	@Override
	public boolean isEnabled() {
		return config.featurePeek_Enabled();
	}

	@Override
	public void startUp() {
		super.startUp();

		chatPeekOverlay.startUp(partitionConfig(config), ChatMode.PUBLIC, false);

		overlayManager.add(chatPeekOverlay);
	}

	@Override
	public void shutDown(boolean fullShutdown) {
		super.shutDown(fullShutdown);

		chatPeekOverlay.shutDown();

		overlayManager.remove(chatPeekOverlay);

		Widget pmWidget = widgetBucket.getPmWidget();
		if (pmWidget != null) {
			pmWidget.setHidden(false);
		}
	}

	@Subscribe
	public void onChatMenuOpenedEvent(ChatMenuOpenedEvent e) {
		tryAddClearPeekMessagesMenuOption(client.getMenu().getMenuEntries());
	}

	@Subscribe
	public void onMenuOpened(MenuOpened e) {
		MenuEntry[] entries = e.getMenuEntries();
		if (entries.length == 1)
			return;

		tryAddClearPeekMessagesMenuOption(entries);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
		if (!e.getGroup().equals(ModernChatConfig.GROUP))
			return;

		String key = e.getKey();
		if (key == null || !key.startsWith(getConfigGroup() + "_"))
			return;

		if (chatPeekOverlay != null) {
			chatPeekOverlay.dirty();
			chatPeekOverlay.resetFade();
		}
	}

	@Subscribe(priority = -3) // run after ChatMessageManager
	public void onChatMessage(ChatMessage e) {
		// Never show SPAM messages
		if (ChatUtil.isSpamMessage(e.getType())) {
			return;
		}

		// Invoke chat filter check to let other plugins filter (and get collapsed message text)
		String filteredMessage = ChatUtil.invokeChatFilterCheck(client, eventBus, e);
		if (filteredMessage == null) {
			return; // Message blocked by chat filter plugin
		}

        MessageLine line = ChatUtil.createMessageLine(e, client, false, filteredMessage);
        if (line == null) {
            log.error("Failed to parse chat message event: {}", e);
            return; // Ignore empty messages
        }

        // Respect the ShowNpc config
        if (ChatUtil.isNpcMessage(line.getType()) && !config.featureRedesign_ShowNpc()) {
            return;
        }

        log.debug("Chat message received: {}", line);
		chatPeekOverlay.pushLine(line);
	}

	@Subscribe
	public void onPostClientTick(PostClientTick e) {
		Widget pmWidget = widgetBucket.getPmWidget();
		boolean visible = chatPeekOverlay == null || (chatPeekOverlay.canShow() && config.featurePeek_HideSplitPrivateMessages());
		if (pmWidget != null && visible != pmWidget.isHidden()) {
			pmWidget.setHidden(visible);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if (e.getGroupId() == InterfaceID.CHATBOX) {
			if (chatPeekOverlay != null) {
				chatPeekOverlay.clearChatWidget();
			}
		}
	}

	@Subscribe
	public void onModernChatVisibilityChangeEvent(ModernChatVisibilityChangeEvent e) {
		chatPeekOverlay.setHidden(e.isVisible());
		chatPeekOverlay.resetFade();
	}

	private boolean tryAddClearPeekMessagesMenuOption(MenuEntry[] entries) {
		int order = 0;
		int id = -1;
		for (MenuEntry entry : entries) {
			order++;
			String option = entry.getOption();
			if (option == null || (!option.equalsIgnoreCase("Clear history") &&
								   !option.equalsIgnoreCase("Clear messages")))
				continue;
			id = entry.getIdentifier();
			break;
		}

		if (id == -1)
			return false;

		client.getMenu().createMenuEntry(order - 1)
			.setOption("Clear peek messages")
			.setType(MenuAction.RUNELITE_HIGH_PRIORITY)
			.setIdentifier(0)
			.onClick(me -> chatPeekOverlay.clearMessages());
		return true;
	}

	public void unFade() {
		chatPeekOverlay.resetFade();
	}
}
