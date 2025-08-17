package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.FontStyle;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.ChatColors;
import com.modernchat.event.ChatMenuOpenedEvent;
import com.modernchat.event.ModernChatVisibilityChangeEvent;
import com.modernchat.overlay.ChatPeekOverlay;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
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
	}

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatPeekOverlay chatPeekOverlay;
	@Inject private WidgetBucket widgetBucket;

	@Inject
	public PeekChatFeature(ModernChatConfig config, EventBus eventBus) {
		super(config, eventBus);
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

			public Color featurePeek_FriendsChatColor() { return config.general_FriendsChatColor(); }
			public Color featurePeek_ClanChatColor() { return config.general_ClanChatColor(); }
			public Color featurePeek_PrivateChatColor() { return config.general_PrivateChatColor(); }
			public Color featurePeek_SystemChatColor() { return config.general_SystemChatColor(); }
			public Color featurePeek_TradeChatColor() { return config.general_TradeChatColor(); }
			public Color featurePeek_WelcomeChatColor() { return config.general_WelcomeChatColor(); }
		};
	}

	@Override
	public boolean isEnabled() {
		return config.featurePeek_Enabled();
	}

	@Override
	public void startUp() {
		super.startUp();

		overlayManager.add(chatPeekOverlay);
	}

	@Override
	public void shutDown(boolean fullShutdown) {
		super.shutDown(fullShutdown);

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

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
		if (!e.getGroup().equals(ModernChatConfig.GROUP))
			return;

		String key = e.getKey();
		if (key == null || !key.startsWith(getConfigGroup() + "_"))
			return;

		if (chatPeekOverlay != null) {
			chatPeekOverlay.dirty();
			chatPeekOverlay.noteMessageActivity();
		}
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

		if (type == ChatMessageType.DIALOG) {
			msg = msg.replaceFirst("\\|", ": ");
		}

		String line = (senderName != null && !senderName.isEmpty()) ? senderName + ": " + msg : msg;

		log.debug("Chat message received: type={}, sender={}, receiver={}, message={}",
			type, senderName, receiverName, line);

		chatPeekOverlay.pushLine(line, type, timestamp/*, senderName, receiverName, prefix*/);
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
		chatPeekOverlay.noteMessageActivity();
	}

	public void unFade() {
		chatPeekOverlay.noteMessageActivity();
	}
}
