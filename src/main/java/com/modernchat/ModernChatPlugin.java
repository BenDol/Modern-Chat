package com.modernchat;

import javax.inject.Inject;

import com.modernchat.feature.ChatFeature;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.feature.MessageHistoryChatFeature;
import com.modernchat.feature.ToggleChatFeature;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Modern Chat",
	description = "A chat plugin for RuneLite that modernizes the chat experience with additional features.",
	tags = {"chat", "modern", "quality of life"}
)
public class ModernChatPlugin extends Plugin
{
	@Inject private ModernChatConfig cfg;

	//@Inject private ExampleChatFeature exampleChatFeature;
	@Inject private ToggleChatFeature toggleChatFeature;
	@Inject private CommandsChatFeature commandsChatFeature;
	@Inject private MessageHistoryChatFeature messageHistoryChatFeature;

	private Set<ChatFeature<?>> features;

	@Provides
	ModernChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ModernChatConfig.class);
	}

	@Override
	protected void startUp()
	{
		features = new HashSet<>();
		//addFeature(exampleChatFeature);
		addFeature(toggleChatFeature);
		addFeature(commandsChatFeature);
		addFeature(messageHistoryChatFeature);

		features.forEach(ChatFeature::startUp);
	}

	@Override
	protected void shutDown()
	{
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
}
