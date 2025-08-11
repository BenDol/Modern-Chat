package com.chatimproved;

import javax.inject.Inject;

import com.chatimproved.feature.ChatFeature;
import com.chatimproved.feature.ExampleChatFeature;
import com.chatimproved.feature.SlashCommandsFeature;
import com.chatimproved.feature.ToggleChatFeature;
import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@PluginDescriptor(
	name = "Chat Improved",
	description = "An improved chat plugin for RuneLite that enhances the chat experience with additional features.",
	tags = {"chat", "improved", "quality of life"}
)
public class ChatImprovedPlugin extends Plugin
{
	@Inject private ChatImprovedConfig cfg;

	@Inject private ExampleChatFeature exampleChatFeature;
	@Inject private ToggleChatFeature toggleChatFeature;
	@Inject private SlashCommandsFeature slashCommandsFeature;

	private Set<ChatFeature<?>> features;

	@Provides
	ChatImprovedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ChatImprovedConfig.class);
	}

	@Override
	protected void startUp()
	{
		features = new HashSet<>();
		tryAddFeature(exampleChatFeature);
		tryAddFeature(toggleChatFeature);
		tryAddFeature(slashCommandsFeature);

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

	protected boolean tryAddFeature(ChatFeature<?> feature) {
		if (features == null) {
			log.warn("Cannot add feature {}: plugin is not started", feature.getClass().getSimpleName());
			return false;
		}
		if (features.contains(feature)) {
			log.warn("Feature {} is already registered", feature.getClass().getSimpleName());
			return false;
		}

		if (!feature.isEnabled()) {
			log.debug("Feature {} is not enabled, cannot add", feature.getClass().getSimpleName());
			return false;
		}

		features.add(feature);
		log.debug("Feature {} added successfully", feature.getClass().getSimpleName());
		return true;
	}
}
