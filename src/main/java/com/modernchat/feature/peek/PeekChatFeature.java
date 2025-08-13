package com.modernchat.feature.peek;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.RuneFontStyle;
import com.modernchat.feature.AbstractChatFeature;
import com.modernchat.feature.ChatFeatureConfig;
import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;

import static com.modernchat.feature.peek.PeekChatFeature.PeekChatFeatureConfig;

@Slf4j
@Singleton
public class PeekChatFeature extends AbstractChatFeature<PeekChatFeatureConfig>
{
	@Override
	public String getConfigGroup() {
		return "featurePeek";
	}

	public interface PeekChatFeatureConfig extends ChatFeatureConfig {
		boolean featurePeek_Enabled();
		boolean featurePeek_ShowPrivateMessages();
		boolean featurePeek_HideSplitPrivateMessages();
		Color featurePeek_BackgroundColor();
		Color featurePeek_BorderColor();
		RuneFontStyle featurePeek_FontStyle();
		int featurePeek_FontSize();
		int featurePeek_TextShadow();
		int featurePeek_Padding();
		int featurePeek_OffsetX();
		int featurePeek_OffsetY();
		int featurePeek_MarginRight();
		int featurePeek_MarginBottom();
		Color featurePeek_PublicChatColor();
		Color featurePeek_FriendsChatColor();
		Color featurePeek_ClanChatColor();
		Color featurePeek_PrivateChatColor();
		Color featurePeek_SystemChatColor();
		boolean featurePeek_PrefixChatTypes();
		boolean featurePeek_FadeEnabled();
		int featurePeek_FadeDelay();
		int featurePeek_FadeDuration();
	}

	@Inject private Client client;
	@Inject private OverlayManager overlayManager;
	@Inject private ChatPeekOverlay chatPeekOverlay;

	private Widget pmWidget = null;

	@Inject
	public PeekChatFeature(ModernChatConfig config, EventBus eventBus) {
		super(config, eventBus);
	}

	@Override
	protected PeekChatFeatureConfig partitionConfig(ModernChatConfig config) {
		return new PeekChatFeatureConfig() {
			@Override public boolean featurePeek_Enabled() { return config.featurePeek_Enabled(); }
			@Override public boolean featurePeek_ShowPrivateMessages() { return config.featurePeek_ShowPrivateMessages(); }
			@Override public boolean featurePeek_HideSplitPrivateMessages() { return config.featurePeek_HideSplitPrivateMessages(); }
			@Override public Color featurePeek_BackgroundColor() { return config.featurePeek_BackgroundColor(); }
			@Override public Color featurePeek_BorderColor() { return config.featurePeek_BorderColor(); }
			@Override public RuneFontStyle featurePeek_FontStyle() { return config.featurePeek_FontStyle(); }
			@Override public int featurePeek_FontSize() { return config.featurePeek_FontSize(); }
			@Override public int featurePeek_TextShadow() { return config.featurePeek_TextShadow(); }
			@Override public int featurePeek_Padding() { return config.featurePeek_Padding(); }
			@Override public int featurePeek_OffsetX() { return config.featurePeek_OffsetX(); }
			@Override public int featurePeek_OffsetY() { return config.featurePeek_OffsetY(); }
			@Override public int featurePeek_MarginRight() { return config.featurePeek_MarginRight(); }
			@Override public int featurePeek_MarginBottom() { return config.featurePeek_MarginBottom(); }
			@Override public Color featurePeek_PublicChatColor() { return config.featurePeek_PublicChatColor(); }
			@Override public Color featurePeek_FriendsChatColor() { return config.featurePeek_FriendsChatColor(); }
			@Override public Color featurePeek_ClanChatColor() { return config.featurePeek_ClanChatColor(); }
			@Override public Color featurePeek_PrivateChatColor() { return config.featurePeek_PrivateChatColor(); }
			@Override public Color featurePeek_SystemChatColor() { return config.featurePeek_SystemChatColor(); }
			@Override public boolean featurePeek_PrefixChatTypes() { return config.featurePeek_PrefixChatTypes(); }
			@Override public boolean featurePeek_FadeEnabled() { return config.featurePeek_FadeEnabled(); }
			@Override public int featurePeek_FadeDelay() { return config.featurePeek_FadeDelay(); }
			@Override public int featurePeek_FadeDuration() { return config.featurePeek_FadeDuration(); }
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

		if (pmWidget != null) {
			pmWidget.setHidden(false);
			pmWidget = null;
		}
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
			if (option == null || !option.equalsIgnoreCase("Clear history"))
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
	public void onChatMessage(ChatMessage e) {
		Player localPlayer = client.getLocalPlayer();
		ChatMessageType type = e.getType();
		String name = (type == ChatMessageType.PRIVATECHATOUT
			? (localPlayer != null ? localPlayer.getName() : "Me")
			: e.getName());
		String msg = e.getMessage();
		String line = (name != null && !name.isEmpty()) ? name + ": " + msg : msg;

		chatPeekOverlay.pushLine(line, type);
	}

	@Subscribe
	public void onPostClientTick(PostClientTick e) {
		pmWidget = pmWidget == null ? ClientUtil.getSplitPmWidget(client) : pmWidget;
		if (pmWidget != null) {
			pmWidget.setHidden(chatPeekOverlay == null ||
				(chatPeekOverlay.canShow() && config.featurePeek_HideSplitPrivateMessages()));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if (e.getGroupId() == InterfaceID.PM_CHAT) {
			pmWidget = null;
		}
		else if (e.getGroupId() == InterfaceID.CHATBOX) {
			if (chatPeekOverlay != null) {
				chatPeekOverlay.clearChatWidget();
			}
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if (e.getGroupId() == InterfaceID.PM_CHAT) {
			pmWidget = null;
		}
	}
}
