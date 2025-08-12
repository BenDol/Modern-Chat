package com.modernchat;

import java.awt.Color;
import java.awt.event.KeyEvent;

import com.modernchat.common.RuneFontStyle;
import com.modernchat.feature.ExampleChatFeature;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.feature.MessageHistoryChatFeature;
import com.modernchat.feature.ToggleChatFeature;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ModernChatConfig.GROUP)
public interface ModernChatConfig extends Config,
    ExampleChatFeature.ExampleChatFeatureConfig,
    ToggleChatFeature.ToggleChatFeatureConfig,
    CommandsChatFeature.CommandsChatConfig,
    MessageHistoryChatFeature.MessageHistoryChatFeatureConfig
{
    String GROUP = "modernchat";
    String HISTORY_KEY = "messageHistory";

    /* ------------ Sections ------------ */

    @ConfigSection(
        name = "Chat Toggle",
        description = "Show/hide chat with a hotkey",
        position = 0,
        closedByDefault = false
    )
    String toggleChatSection = "toggleChatSection";

    @ConfigSection(
        name = "Chat Commands",
        description = "Custom chat commands for quick actions",
        position = 1,
        closedByDefault = false
    )
    String commandsSection = "commandsSection";

    @ConfigSection(
        name = "Message History",
        description = "Cycle through your message history",
        position = 2,
        closedByDefault = false
    )
    String messageHistorySection = "messageHistorySection";

    /* ------------ Feature: Example ------------ */

    @ConfigItem(
        keyName = "featureToggle_Enabled",
        name = "Enable",
        description = "Enable the chat toggle feature",
        position = 0,
        hidden = true // This is just an example, not a real feature
    )
    @Override
    default boolean featureExample_Enabled()
    {
        return false;
    }

    /* ------------ Feature: Toggle Chat ------------ */

    @ConfigItem(
        keyName = "featureToggle_Enabled",
        name = "Enable",
        description = "Enable the chat toggle feature",
        position = 0,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_Enabled()
    {
        return true;
    }

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

    @ConfigItem(
        keyName = "featureToggle_lockCameraWhenVisible",
        name = "Lock camera keys",
        description = "Lock the camera key arrows when chat is visible",
        position = 4,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_lockCameraWhenVisible()
    {
        return false;
    }

    @ConfigItem(
        keyName = "featureToggle_peekOverlayEnabled",
        name = "Peek Enabled",
        description = "Show the peek overlay when the chat is hidden to see messages",
        position = 5,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_peekOverlayEnabled()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "featureToggle_peekBgColor",
        name = "Peek Background Color",
        description = "Background color for the peek overlay",
        position = 6,
        section = toggleChatSection
    )
    @Override
    default Color featureToggle_peekBgColor()
    {
        return new Color(12, 12, 12, 0);
    }

    @Alpha
    @ConfigItem(
        keyName = "featureToggle_peekBorderColor",
        name = "Peek Border Color",
        description = "Border color for the peek overlay",
        position = 7,
        section = toggleChatSection
    )
    @Override
    default Color featureToggle_peekBorderColor()
    {
        return new Color(12, 12, 12, 0);
    }

    @ConfigItem(
        keyName = "featureToggle_peekFontStyle",
        name = "Peek Font Style",
        description = "Font style for the peek overlay",
        position = 9,
        section = toggleChatSection
    )
    @Units(Units.PIXELS)
    @Override
    default RuneFontStyle featureToggle_peekFontStyle()
    {
        return RuneFontStyle.NORMAL;
    }

    @ConfigItem(
        keyName = "featureToggle_peekFontSize",
        name = "Peek Font Size",
        description = "Show an overlay when the chat is hidden to peek at messages",
        position = 10,
        section = toggleChatSection
    )
    @Units(Units.PIXELS)
    @Override
    default int featureToggle_peekFontSize()
    {
        return 14;
    }

    @ConfigItem(
        keyName = "featureToggle_peekOffsetX",
        name = "Peek Offset X",
        description = "Horizontal offset for the peek overlay",
        position = 11,
        section = toggleChatSection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featureToggle_peekOffsetX()
    {
        return 0;
    }

    @ConfigItem(
        keyName = "featureToggle_peekOffsetY",
        name = "Peek Offset Y",
        description = "Vertical offset for the peek overlay",
        position = 12,
        section = toggleChatSection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featureToggle_peekOffsetY()
    {
        return -40;
    }

    /* ------------ Feature: Commands ------------ */

    @ConfigItem(
        keyName = "featureCommands_Enabled",
        name = "Enable",
        description = "Enable custom commands in chat",
        position = 0,
        section = commandsSection
    )
    @Override
    default boolean featureCommands_Enabled()
    {
        return  true;
    }

    @ConfigItem(
        keyName = "featureCommands_ReplyEnabled",
        name = "Reply Enabled",
        description = "Enable the /r command to quickly respond to the last private message",
        position = 1,
        section = commandsSection
    )
    @Override
    default boolean featureCommands_ReplyEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "featureCommands_WhisperEnabled",
        name = "Whisper Enabled",
        description = "Enable the /w command to quickly private message players",
        position = 2,
        section = commandsSection
    )
    @Override
    default boolean featureCommands_WhisperEnabled()
    {
        return true;
    }

    @ConfigItem(
        keyName = "featureCommands_PrivateMessageEnabled",
        name = "Private Message Enabled",
        description =
            "Enable the /pm command to quickly private message players holding the " +
            "player's message prompt until cancelled (Esc or empty message). Avoids " +
            "having use commands each message.",
        warning =
            "We recommend using \"Split friends private chat\" setting in the OSRS settings\n" +
            "to see responses above the chat window when using the /pm command.",
        position = 3,
        section = commandsSection
    )
    @Override
    default boolean featureCommands_PrivateMessageEnabled()
    {
        return false;
    }

    /* ------------ Feature: Message History ------------ */

    @ConfigItem(
        keyName = "featureMessageHistory_Enabled",
        name = "Enable",
        description = "Enable message history to cycle using Shift + Up/Down arrows",
        position = 0,
        section = messageHistorySection
    )
    @Override
    default boolean featureMessageHistory_Enabled()
    {
        return  true;
    }

    @ConfigItem(
        keyName = "featureMessageHistory_MaxEntries",
        name = "Max Entries",
        description = "Maximum number of entries to keep in message history",
        position = 1,
        section = messageHistorySection
    )
    @Override
    default int featureMessageHistory_MaxEntries()
    {
        return 50;
    }

    @ConfigItem(
        keyName = "featureMessageHistory_IncludeCommands",
        name = "Include Commands",
        description = "Include commands in message history",
        position = 2,
        section = messageHistorySection
    )
    @Override
    default boolean featureMessageHistory_IncludeCommands()
    {
        return true;
    }

    @ConfigItem(
        keyName = "featureMessageHistory_SkipDuplicates",
        name = "Skip Duplicates",
        description = "Skip duplicate messages in history",
        position = 3,
        section = messageHistorySection
    )
    @Override
    default boolean featureMessageHistory_SkipDuplicates()
    {
        return true;
    }

    @ConfigItem(
        keyName = "featureMessageHistory_PrevKey",
        name = "Previous Key",
        description = "Key to cycle to the previous message in history",
        position = 4,
        section = messageHistorySection
    )
    @Override
    default Keybind featureMessageHistory_PrevKey()
    {
        return new Keybind(KeyEvent.VK_PAGE_UP, 0);
    }

    @ConfigItem(
        keyName = "featureMessageHistory_NextKey",
        name = "Next Key",
        description = "Key to cycle to the next message in history",
        position = 5,
        section = messageHistorySection
    )
    @Override
    default Keybind featureMessageHistory_NextKey()
    {
        return new Keybind(KeyEvent.VK_PAGE_DOWN, 0);
    }
}
