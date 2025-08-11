package com.chatimproved;

import java.awt.event.KeyEvent;

import com.chatimproved.feature.ExampleChatFeature;
import com.chatimproved.feature.SlashCommandsFeature;
import com.chatimproved.feature.ToggleChatFeature;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(ChatImprovedConfig.GROUP)
public interface ChatImprovedConfig extends Config,
    ExampleChatFeature.ExampleChatFeatureConfig,
    ToggleChatFeature.ToggleChatFeatureConfig,
    SlashCommandsFeature.SlashCommandsConfig
{
    String GROUP = "chatimproved";

    /* ------------ Sections ------------ */

    @ConfigSection(
        name = "Chat Toggle",
        description = "Show/hide chat with a hotkey",
        position = 0,
        closedByDefault = false
    )
    String toggleChatSection = "toggleChatSection";

    @ConfigSection(
        name = "Slash Commands",
        description = "Custom slash commands in chat",
        position = 1,
        closedByDefault = false
    )
    String slashCommandsSection = "slashCommandsSection";

    /* ------------ Feature: Example ------------ */

    @ConfigItem(
        keyName = "featureToggle_Enabled",
        name = "Enable",
        description = "Enable the chat toggle feature",
        position = 0,
        hidden = true // This is just an example, not a real feature
    )
    @Override
    default boolean featureExample_Enabled() { return false; }

    /* ------------ Feature: Toggle Chat ------------ */

    @ConfigItem(
        keyName = "featureToggle_Enabled",
        name = "Enable",
        description = "Enable the chat toggle feature",
        position = 0,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_Enabled() { return true; }

    @ConfigItem(
        keyName = "featureToggle_toggleKey",
        name = "Toggle hotkey",
        description = "Key used to show/hide the chatbox",
        position = 1,
        section = toggleChatSection
    )
    @Override
    default Keybind featureToggle_ToggleKey()
    {
        // Default to Enter. Modify if you prefer another default.
        return new Keybind(KeyEvent.VK_ENTER, 0);
    }

    @ConfigItem(
        keyName = "featureToggle_autoHideOnSend",
        name = "Auto-hide on send",
        description = "Hide chat automatically after sending a message",
        position = 2,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_autoHideOnSend()
    {
        return true;
    }

    @ConfigItem(
        keyName = "featureToggle_startHidden",
        name = "Start hidden",
        description = "Hide the chatbox when the plugin starts",
        position = 3,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_StartHidden()
    {
        return false;
    }

    /* ------------ Feature: Slash Commands ------------ */

    @ConfigItem(
        keyName = "featureSlashCommands_Enabled",
        name = "Enable",
        description = "Enable custom slash commands in chat",
        position = 0,
        section = slashCommandsSection
    )
    @Override
    default boolean featureSlashCommands_Enabled()
    {
        return  true;
    }

    @ConfigItem(
        keyName = "featureSlashCommands_ReplyEnabled",
        name = "Reply Enabled",
        description = "Enable the /r command to quickly respond to the last private message",
        position = 1,
        section = slashCommandsSection
    )
    @Override
    default boolean featureSlashCommands_ReplyEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "featureSlashCommands_WhisperEnabled",
        name = "Whisper Enabled",
        description = "Enable the /w command to quickly private message players",
        position = 2,
        section = slashCommandsSection
    )
    @Override
    default boolean featureSlashCommands_WhisperEnabled()
    {
        return true;
    }
}
