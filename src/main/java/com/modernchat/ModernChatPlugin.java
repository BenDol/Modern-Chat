package com.modernchat;

import com.google.inject.Provides;
import com.modernchat.common.Anchor;
import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatProxy;
import com.modernchat.common.MessageService;
import com.modernchat.common.PrivateChatAnchor;
import com.modernchat.common.WidgetBucket;
import com.modernchat.event.LegacyChatVisibilityChangeEvent;
import com.modernchat.event.MessageLayerClosedEvent;
import com.modernchat.event.MessageLayerOpenedEvent;
import com.modernchat.feature.ChatFeature;
import com.modernchat.feature.ChatRedesignFeature;
import com.modernchat.feature.MessageHistoryChatFeature;
import com.modernchat.feature.ToggleChatFeature;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.feature.PeekChatFeature;
import com.modernchat.service.PrivateChatService;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.GeometryUtil;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Modern Chat",
	description = "A chat plugin for RuneLite that modernizes the chat experience with additional features.",
	tags = {"chat", "modern", "quality of life"}
)
public class ModernChatPlugin extends Plugin {

	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private EventBus eventBus;
	@Inject private ConfigManager configManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private MessageService messageService;
	@Inject private ModernChatConfig config;
	@Inject private PrivateChatService privateChatService;
	@Inject private WidgetBucket widgetBucket;
	@Inject private ChatProxy chatProxy;

	//@Inject private ExampleChatFeature exampleChatFeature;
	@Inject private ToggleChatFeature toggleChatFeature;
	@Inject private PeekChatFeature peekChatFeature;
	@Inject private CommandsChatFeature commandsChatFeature;
	@Inject private MessageHistoryChatFeature messageHistoryChatFeature;
	@Inject private ChatRedesignFeature chatRedesignFeature;

	private Set<ChatFeature<?>> features;
	private volatile boolean chatVisible = false;
	private volatile Anchor pmAnchor = null;
	private volatile Rectangle lastChatBounds;

	@Provides
	ModernChatConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(ModernChatConfig.class);
	}

	@Override
	protected void startUp() {
		features = new HashSet<>();
		//addFeature(exampleChatFeature);
		addFeature(toggleChatFeature);
		addFeature(peekChatFeature);
		addFeature(commandsChatFeature);
		addFeature(messageHistoryChatFeature);
		addFeature(chatRedesignFeature);

		features.forEach((f) -> {
			f.startUp();

			if (!f.isEnabled())
				f.shutDown(false);
		});

		// Force an initial re-anchor if enabled once widgets are available
		lastChatBounds = null;

		if (!config.featureExample_Enabled()) {
			toggleChatFeature.scheduleDeferredHide();

			Player localPlayer = client.getLocalPlayer();
			if (localPlayer != null) {
				showInstallMessage();
			}
		}
	}

	@Override
	protected void shutDown() {
		if (features != null) {
			features.forEach((feature) -> {
				try {
					feature.shutDown(true);
				} catch (Exception e) {
					log.error("Error shutting down feature: {}", feature.getClass().getSimpleName(), e);
				}
			});
		}
		features = null;
	}

	protected void addFeature(ChatFeature<?> feature) {
		if (features == null) {
			log.warn("Cannot add feature {}: plugin is not started", feature.getClass().getSimpleName());
			return;
		}

		if (features.contains(feature)) {
			log.warn("Feature {} is already registered", feature.getClass().getSimpleName());
			return;
		}

		features.add(feature);
		log.debug("Feature {} added successfully", feature.getClass().getSimpleName());
	}

	@Subscribe
	public void onMenuOpened(MenuOpened e) {
		MenuEntry[] entries = e.getMenuEntries();
		if (entries.length == 1)
			return;

		tryAddPrivateMessageMenuOption(entries);
	}

	private boolean tryAddPrivateMessageMenuOption(MenuEntry[] entries) {
		List<MenuEntry> targetEntries = new ArrayList<>();
		String playerTarget = null;
		int order = 0;

		for (int i = entries.length - 1; i >= 0; --i) {
			MenuEntry entry = entries[i];
			String option = entry.getOption();
			String target = entry.getTarget();

			if (!StringUtil.isNullOrEmpty(target) && ChatUtil.isPlayerType(entry.getType())) {
				playerTarget = entry.getTarget();
			}

			// try find sub-menu entry for private message first
			if (!StringUtil.isNullOrEmpty(target) && StringUtil.isNullOrEmpty(option) && entry.getType() == MenuAction.RUNELITE) {
				targetEntries.add(entry);
			}
			else if (!StringUtil.isNullOrEmpty(option) && option.equalsIgnoreCase("Message")) {
				playerTarget = target;
				order = i; // insert before this entry
			}
		}

		boolean addedToSubMenu = false;

		for (MenuEntry targetEntry : targetEntries) {
			String target = targetEntry.getTarget();
			if (StringUtil.isNullOrEmpty(target)) {
				continue;
			}

			Menu menu = targetEntry.getSubMenu();
			if (menu == null)
				continue;

			menu.createMenuEntry(1)
				.setOption("Chat with")
				.setTarget(target)
				.setType(MenuAction.RUNELITE_PLAYER)
				.setIdentifier(0)
				.onClick(me -> onPrivateMessageRightClick(target));
			addedToSubMenu = true;
		}

		if (!addedToSubMenu && !StringUtil.isNullOrEmpty(playerTarget)) {
			String finalTarget = playerTarget;
			client.getMenu().createMenuEntry(order)
				.setOption("Chat with")
				.setTarget(playerTarget)
				.setType(MenuAction.RUNELITE_PLAYER)
				.setIdentifier(0)
				.onClick(me -> onPrivateMessageRightClick(finalTarget));
		}

		return true;
	}

	private void onPrivateMessageRightClick(String target) {
		log.info("Private message right-clicked: {}", target);
		String cleanedTarget = Text.removeTags(target);
		int index = cleanedTarget.indexOf(" (");
		if (index != -1)
			cleanedTarget = cleanedTarget.substring(0, index - 1);

		privateChatService.setPmTarget(cleanedTarget);
		privateChatService.clearChatInput();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e) {
		if (e.getVarpId() == VarPlayerID.OPTION_PM) {
			if (!ClientUtil.isOnline(client))
				return;

			if (e.getValue() == 0 && config.general_AnchorPrivateChat()) {
				messageService.pushChatMessage("Split PM chat was disabled, resetting anchor.");
				resetSplitPmAnchor();
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
		if (!e.getGroup().equals(ModernChatConfig.GROUP))
			return;

		String key = e.getKey();
		if (key == null)
			return;

		if (key.endsWith("AnchorPrivateChat")) {
			if (Boolean.parseBoolean(e.getNewValue()) && client.getVarpValue(VarPlayerID.OPTION_PM) == 0) {
				messageService.pushChatMessage(new ChatMessageBuilder()
					.append("Please enable ")
					.append(Color.ORANGE, "Split friends private chat")
					.append(" in the OSRS settings for the 'Anchor Private Chat' feature."));
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e) {
		Widget messageWidget = widgetBucket.getMessageLayerWidget();

		switch (e.getScriptId()) {
			case ScriptID.MESSAGE_LAYER_OPEN:
				eventBus.post(new MessageLayerOpenedEvent(messageWidget, widgetBucket.isPmWidget(messageWidget)));
				break;
			case ScriptID.MESSAGE_LAYER_CLOSE:
				eventBus.post(new MessageLayerClosedEvent(messageWidget, widgetBucket.isPmWidget(messageWidget)));
				break;
		}
	}

	@Subscribe
	public void onClientTick(ClientTick e) {
		Widget chatWidget = widgetBucket.getChatWidget();
		boolean visible = chatWidget != null && !chatWidget.isHidden() && !GeometryUtil.isInvalidChatBounds(chatWidget.getBounds());
		if (chatVisible != visible) {
			chatVisible = visible;
			eventBus.post(new LegacyChatVisibilityChangeEvent(chatWidget, chatVisible));
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick e) {
		// Poll once per tick but do nothing unless bounds changed
		maybeReanchor(false);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e) {
		if (e.getGroupId() == InterfaceID.CHATBOX) {
			lastChatBounds = null;

			// force once loaded
			clientThread.invokeAtTickEnd(() -> maybeReanchor(true));
		}
		if (e.getGroupId() == InterfaceID.PM_CHAT) {
			maybeReanchor(true);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if (e.getGroupId() == InterfaceID.CHATBOX) {
			lastChatBounds = null;
		} else if (e.getGroupId() == InterfaceID.PM_CHAT) {
			resetSplitPmAnchor();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e) {
		lastChatBounds = null;

		if (e.getGameState() == GameState.LOGGED_IN) {
			if (!config.featureExample_Enabled()) {
				Player localPlayer = client.getLocalPlayer();
				if (localPlayer != null) {
					showInstallMessage();
				}
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e) {
		if (ChatUtil.isPrivateMessage(e.getType())) {
			clientThread.invoke(() -> maybeReanchor(true));
		}
	}

	private void maybeReanchor(boolean force) {
		if (!config.general_AnchorPrivateChat()) {
			if (pmAnchor != null && !pmAnchor.isReset())
				resetSplitPmAnchor();
			return;
		}

		final Widget chat = widgetBucket.getChatboxViewportWidget();
		if (chat == null)
			return;

		Rectangle cur = chatProxy.getBounds();
		if (cur == null)
			return;

		if (chatProxy.isHidden())
			cur = new Rectangle(cur.x, cur.y, cur.width, 0); // hide height if chat is hidden

		int offsetX = config.general_AnchorPrivateChatOffsetX();
		int offsetY = config.general_AnchorPrivateChatOffsetY();

		if (!force && Objects.equals(cur, lastChatBounds)) {
			if (pmAnchor != null && !pmAnchor.isReset() && pmAnchor.getOffsetX() == offsetX && pmAnchor.getOffsetY() == offsetY) {
				return; // nothing changed
			}
		}

		if (!chatProxy.isHidden() && GeometryUtil.isInvalidChatBounds(cur)) {
			return; // invalid bounds, skip re-anchoring for now
		}

		lastChatBounds = new Rectangle(cur);

		clientThread.invokeLater(() -> anchorSplitPm(offsetX, offsetY));
	}

	private void anchorSplitPm(int offsetX, int offsetY)
	{
		Widget pm = widgetBucket.getPmWidget();
		if (pm == null || pm.isHidden())
			return;

		Widget pmParent = pm.getParent();
		if (pmParent == null || pmParent.isHidden())
			return;

		Widget chat = widgetBucket.getChatboxViewportWidget();
		if (chat == null)
			return;

		if (pmAnchor == null) {
			pmAnchor = new PrivateChatAnchor(client, pmParent);
		}
		pmAnchor.setOffsetX(offsetX);
		pmAnchor.setOffsetY(offsetY);

		pmAnchor.apply(pmParent, chat);
	}

	private void resetSplitPmAnchor() {
		Widget pm = widgetBucket.getPmWidget();
		if (pm == null) {
			return;
		}

		Widget pmParent = pm.getParent();
		if (pmParent == null) {
			return;
		}

		if (pmAnchor != null) {
			pmAnchor.reset(pmParent);
		}
	}

	private void showInstallMessage() {
		clientThread.invokeLater(() -> {
			toggleChatFeature.scheduleDeferredHide();

			ChatMessageBuilder builder = new ChatMessageBuilder()
				.append("Plugin installed! This is the ")
				.append(Color.CYAN, "Peek Overlay ")
				.append("feature for a more subtle chat experience. ")
				.append("You can press \"Enter\" to send messages and to hide/show the chat window. ");

			boolean isSplitPmDisabled = client.getVarpValue(VarPlayerID.OPTION_PM) == 0;
			if (isSplitPmDisabled) {
				builder.append("We recommend turning on ")
					.append(Color.ORANGE, "Split friends private chat")
					.append(" OSRS setting for some private chat features. ")
					.build();
			}

			messageService.pushChatMessage(builder
				.append("To learn more about the features and create custom configurations, check the plugin settings."),
				ChatMessageType.WELCOME);

			configManager.setConfiguration(ModernChatConfig.GROUP, "featureExample_Enabled", true);
		});
	}
}
