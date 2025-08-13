package com.modernchat;

import com.google.inject.Provides;
import com.modernchat.common.Anchor;
import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.MessageService;
import com.modernchat.common.PrivateChatAnchor;
import com.modernchat.feature.ChatFeature;
import com.modernchat.feature.MessageHistoryChatFeature;
import com.modernchat.feature.ToggleChatFeature;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.feature.peek.PeekChatFeature;
import com.modernchat.service.PrivateChatService;
import com.modernchat.util.GeometryUtil;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.HashSet;
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
	@Inject private MenuManager menuManager;
	@Inject private ConfigManager configManager;
	@Inject private ChatMessageManager chatMessageManager;
	@Inject private MessageService messageService;
	@Inject private ModernChatConfig config;
	@Inject private PrivateChatService privateChatService;

	//@Inject private ExampleChatFeature exampleChatFeature;
	@Inject private ToggleChatFeature toggleChatFeature;
	@Inject private PeekChatFeature peekChatFeature;
	@Inject private CommandsChatFeature commandsChatFeature;
	@Inject private MessageHistoryChatFeature messageHistoryChatFeature;

	private Set<ChatFeature<?>> features;
	private Widget chatWidget = null;
	private Widget pmWidget = null;
	private Anchor pmAnchor = null;
	private Rectangle lastChatBounds;

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

		features.forEach(ChatFeature::startUp);

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
		int order = 0;
		String target = null;
		for (MenuEntry entry : entries) {
			order++;
			String option = entry.getOption();
			if (option == null || !option.equalsIgnoreCase("Message"))
				continue;

			target = entry.getTarget();
			if (!StringUtil.isNullOrEmpty(target)) {
				target = Text.removeTags(target);
				if (!StringUtil.isNullOrEmpty(target)) {
					// If we find a valid target, we can stop looking
					break;
				}
			}
		}

		final String cleanedTarget = target;
		if (StringUtil.isNullOrEmpty(cleanedTarget))
			return false;

		client.getMenu().createMenuEntry(order - 1) // insert at top
			.setOption("Chat with")
			.setTarget(cleanedTarget)
			.setType(MenuAction.RUNELITE_HIGH_PRIORITY)
			.setIdentifier(0)
			.onClick(me -> onPrivateMessageRightClick(cleanedTarget));
		return true;
	}

	private void onPrivateMessageRightClick(String target) {
		log.info("Private message right-clicked: {}", target);
		privateChatService.setPmTarget(target);
		privateChatService.clearChatInput();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e) {
		if (e.getVarpId() == VarPlayerID.OPTION_PM) {
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
				messageService.pushChatMessage(
					"Please enable \"Split friends private chat\" in the OSRS settings for the 'Anchor Private Chat' feature.");
			}
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
			chatWidget = null;
			lastChatBounds = null;
			maybeReanchor(true); // force once loaded
		}
		if (e.getGroupId() == InterfaceID.PM_CHAT) {
			pmWidget = null;
			maybeReanchor(true);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e) {
		if (e.getGroupId() == InterfaceID.CHATBOX) {
			chatWidget = null;
			lastChatBounds = null;
		} else if (e.getGroupId() == InterfaceID.PM_CHAT) {
			resetSplitPmAnchor();
			pmWidget = null;
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

	private void maybeReanchor(boolean force)
	{
		if (!config.general_AnchorPrivateChat()) {
			if (pmAnchor != null) resetSplitPmAnchor();
			return;
		}

		final Widget chat = getChatWidget();
		if (chat == null)
			return;

		final Rectangle cur = chat.getBounds();
		if (cur == null || cur.height <= 0 || cur.width <= 0)
			return;

		int offsetX = config.general_AnchorPrivateChatOffsetX();
		int offsetY = config.general_AnchorPrivateChatOffsetY();

		if (!force && Objects.equals(cur, lastChatBounds)) {
			if (pmAnchor != null && pmAnchor.getOffsetX() == offsetX && pmAnchor.getOffsetY() == offsetY) {
				return; // nothing changed
			}
		}

		if (GeometryUtil.isInvalidChatBounds(cur)) {
			return; // invalid bounds, skip re-anchoring for now
		}

		lastChatBounds = new Rectangle(cur);
		anchorSplitPm(offsetX, offsetY);
	}

	private void anchorSplitPm(int offsetX, int offsetY)
	{
		Widget pm = getPmWidget();
		if (pm == null || pm.isHidden())
			return;

		Widget pmParent = pm.getParent();
		if (pmParent == null || pmParent.isHidden())
			return;

		Widget chat = getChatWidget();
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
		Widget pm = getPmWidget();
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

		// we need to restart the features
		shutDown();
		startUp();
	}

	private Widget getPmWidget() {
		if (pmWidget == null)
			pmWidget = client.getWidget(InterfaceID.PM_CHAT, 0);
		return pmWidget;
	}

	private Widget getChatWidget() {
		if (chatWidget == null)
			chatWidget = client.getWidget(InterfaceID.CHATBOX, 0);
		return chatWidget;
	}

	private void showInstallMessage() {
		clientThread.invokeLater(() -> {
			toggleChatFeature.scheduleDeferredHide();

			ChatMessageBuilder builder = new ChatMessageBuilder()
				.append("Plugin installed! This is the ")
				.append(Color.CYAN, "Peek Overlay ")
				.append("feature for a more subtle chat experience while playing. ")
				.append("You can press \"Enter\" to send messages and hide/show the chat window. ");

			boolean isSplitPmDisabled = client.getVarpValue(VarPlayerID.OPTION_PM) == 0;
			if (isSplitPmDisabled) {
				builder.append("We recommend turning on ")
					.append(Color.ORANGE, "Split friends private chat")
					.append(" OSRS setting for some private chat features. ")
					.build();
			}

			messageService.pushChatMessage(builder
				.append("To learn about more features and create custom configurations, check the plugin settings."),
				ChatMessageType.WELCOME);

			configManager.setConfiguration(ModernChatConfig.GROUP, "featureExample_Enabled", true);
		});
	}
}
