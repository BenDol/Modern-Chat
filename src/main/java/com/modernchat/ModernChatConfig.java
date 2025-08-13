package com.modernchat;

import com.modernchat.common.RuneFontStyle;
import com.modernchat.feature.ExampleChatFeature;
import com.modernchat.feature.MessageHistoryChatFeature;
import com.modernchat.feature.peek.PeekChatFeature;
import com.modernchat.feature.ToggleChatFeature;
import com.modernchat.feature.command.CommandsChatFeature;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

import java.awt.Color;
import java.awt.event.KeyEvent;

@ConfigGroup(ModernChatConfig.GROUP)
public interface ModernChatConfig extends Config,
    ExampleChatFeature.ExampleChatFeatureConfig,
    ToggleChatFeature.ToggleChatFeatureConfig,
    PeekChatFeature.PeekChatFeatureConfig,
    CommandsChatFeature.CommandsChatConfig,
    MessageHistoryChatFeature.MessageHistoryChatFeatureConfig
{
    String GROUP = "modernchat";
    String HISTORY_KEY = "messageHistory";

    /* ------------ Sections ------------ */

    @ConfigSection(
        name = "General",
        description = "General settings for Modern Chat",
        position = 0,
        closedByDefault = false
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Chat Toggle",
        description = "Show/hide chat with a hotkey",
        position = 1,
        closedByDefault = false
    )
    String toggleChatSection = "toggleChatSection";

    @ConfigSection(
        name = "Peek Overlay",
        description = "Show a peek overlay when chat is hidden",
        position = 2,
        closedByDefault = true
    )
    String peekOverlaySection = "peekOverlaySection";

    @ConfigSection(
        name = "Chat Commands",
        description = "Custom chat commands for quick actions",
        position = 3,
        closedByDefault = true
    )
    String commandsSection = "commandsSection";

    @ConfigSection(
        name = "Message History",
        description = "Cycle through your message history",
        position = 4,
        closedByDefault = true
    )
    String messageHistorySection = "messageHistorySection";

    /* ------------ Feature: Example ------------ */

    @ConfigItem(
        keyName = "featureExample_Enabled",
        name = "Enable",
        description = "Enable the chat toggle feature",
        position = 0,
        hidden = true // This is just an example, not a real feature
    )
    @Override
    default boolean featureExample_Enabled() {
        return false;
    }

    /* ------------ General Settings ------------ */

    @ConfigItem(
        keyName = "general_AnchorPrivateChat",
        name = "Anchor Private Chat",
        description = "Anchor the split private chat window to the top of the chatbox",
        position = 0,
        section = generalSection
    )
    default boolean general_AnchorPrivateChat() {
        return true;
    }

    @ConfigItem(
        keyName = "general_AnchorPrivateChatOffsetX",
        name = "Anchor Offset X",
        description = "Horizontal offset for the private chat anchor",
        position = 1,
        section = generalSection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    default int general_AnchorPrivateChatOffsetX() {
        return 0;
    }

    @ConfigItem(
        keyName = "general_AnchorPrivateChatOffsetY",
        name = "Anchor Offset Y",
        description = "Vertical offset for the private chat anchor",
        position = 2,
        section = generalSection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    default int general_AnchorPrivateChatOffsetY() {
        return 0;
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
    default boolean featureToggle_Enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureToggle_ToggleKey",
        name = "Toggle hotkey",
        description = "Key used to show/hide the chatbox",
        position = 1,
        section = toggleChatSection
    )
    @Override
    default Keybind featureToggle_ToggleKey() {
        return new Keybind(KeyEvent.VK_ENTER, 0);
    }

    @ConfigItem(
        keyName = "featureToggle_AutoHideOnSend",
        name = "Auto-hide on send",
        description = "Hide chat automatically after sending a message",
        position = 2,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_AutoHideOnSend() {
        return true;
    }

    @ConfigItem(
        keyName = "featureToggle_StartHidden",
        name = "Start hidden",
        description = "Hide the chatbox when the plugin starts",
        position = 3,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_StartHidden() {
        return true;
    }

    @ConfigItem(
        keyName = "featureToggle_LockCameraWhenVisible",
        name = "Lock camera keys",
        description = "Lock the camera key arrows when chat is visible",
        position = 4,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_LockCameraWhenVisible() {
        return false;
    }

    /* ------------ Feature: Peek Overlay ------------ */

    @ConfigItem(
        keyName = "featurePeek_Enabled",
        name = "Enable",
        description = "Enable the peek overlay feature",
        position = 0,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_Enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_ShowPrivateMessages",
        name = "Show Private Messages",
        description = "Show private messages in the peek overlay",
        position = 1,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_ShowPrivateMessages() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_HideSplitPrivateMessages",
        name = "Hide Split Private Messages",
        description = "Hide split private messages when peek overlay is visible",
        position = 2,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_HideSplitPrivateMessages() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_PrefixChatTypes",
        name = "Prefix Chat Types",
        description = "Prefix messages with their chat type in the peek overlay",
        position = 3,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_PrefixChatTypes() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_BackgroundColor",
        name = "Background Color",
        description = "Background color for the peek overlay",
        position = 4,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_BackgroundColor() {
        return new Color(12, 12, 12, 0);
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_BorderColor",
        name = "Border Color",
        description = "Border color for the peek overlay",
        position = 5,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_BorderColor() {
        return new Color(12, 12, 12, 0);
    }

    @ConfigItem(
        keyName = "featurePeek_FontStyle",
        name = "Font Style",
        description = "Font style for the peek overlay",
        position = 6,
        section = peekOverlaySection
    )
    @Units(Units.PIXELS)
    @Override
    default RuneFontStyle featurePeek_FontStyle() {
        return RuneFontStyle.NORMAL;
    }

    @ConfigItem(
        keyName = "featurePeek_FontSize",
        name = "Font Size",
        description = "Show an overlay when the chat is hidden to peek at messages",
        position = 7,
        section = peekOverlaySection
    )
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_FontSize() {
        return 16;
    }

    @ConfigItem(
        keyName = "featurePeek_TextShadow",
        name = "Text Shadow",
        description = "Shadow offset for text in the peek overlay",
        position = 8,
        section = peekOverlaySection
    )
    @Range(min = 0, max = 10)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_TextShadow() {
        return 1;
    }

    @ConfigItem(
        keyName = "featurePeek_OffsetX",
        name = "Offset X",
        description = "Horizontal offset for the peek overlay",
        position = 9,
        section = peekOverlaySection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_OffsetX() {
        return 0;
    }

    @ConfigItem(
        keyName = "featurePeek_OffsetY",
        name = "Offset Y",
        description = "Vertical offset for the peek overlay",
        position = 10,
        section = peekOverlaySection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_OffsetY() {
        return -55;
    }

    @ConfigItem(
        keyName = "featurePeek_Padding",
        name = "Padding",
        description = "Padding around the text in the peek overlay",
        position = 11,
        section = peekOverlaySection
    )
    @Range(min = 0, max = 100)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_Padding() {
        return 8;
    }

    @ConfigItem(
        keyName = "featurePeek_MarginRight",
        name = "Margin Right",
        description = "Right margin for the peek overlay (apply a background color to see effect)",
        position = 12,
        section = peekOverlaySection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_MarginRight() {
        return 0;
    }

    @ConfigItem(
        keyName = "featurePeek_MarginBottom",
        name = "Margin Bottom",
        description = "Bottom margin for the peek overlay (apply a background color to see effect)",
        position = 13,
        section = peekOverlaySection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_MarginBottom() {
        return 0;
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_PublicChatColor",
        name = "Public Chat Color",
        description = "Color for public chat messages in the peek overlay",
        position = 14,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_PublicChatColor() {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_FriendsChatColor",
        name = "Friends Chat Color",
        description = "Color for friends chat messages in the peek overlay",
        position = 15,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_FriendsChatColor() {
        return new Color(0x00FF80); // light green
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_ClanChatColor",
        name = "Clan Chat Color",
        description = "Color for clan chat messages in the peek overlay",
        position = 16,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_ClanChatColor() {
        return new Color(0x80C0FF); // light blue
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_PrivateChatColor",
        name = "Private Chat Color",
        description = "Color for private chat messages in the peek overlay",
        position = 17,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_PrivateChatColor() {
        return new Color(0xFF80FF); // light purple
    }

    @Alpha
    @ConfigItem(
        keyName = "featurePeek_SystemChatColor",
        name = "System Chat Color",
        description = "Color for system chat messages in the peek overlay",
        position = 18,
        section = peekOverlaySection
    )
    @Override
    default Color featurePeek_SystemChatColor() {
        return new Color(0xCFCFCF); // light gray
    }

    @ConfigItem(
        keyName = "featurePeek_FadeEnabled",
        name = "Fade Enabled",
        description = "Enable fade-in/out effect for the peek overlay",
        position = 19,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_FadeEnabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_FadeDelay",
        name = "Fade Delay (s)",
        description = "Delay (seconds) of inactivity before fading in/out the peek overlay",
        position = 20,
        section = peekOverlaySection
    )
    @Override
    default int featurePeek_FadeDelay() {
        return 10;
    }

    @ConfigItem(
        keyName = "featurePeek_FadeDuration",
        name = "Fade Duration (ms)",
        description = "Duration (ms) for fade-in/out effect in the peek overlay",
        position = 21,
        section = peekOverlaySection
    )
    @Range(max = 10000)
    @Override
    default int featurePeek_FadeDuration() {
        return 600;
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
    default boolean featureCommands_Enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureCommands_ReplyEnabled",
        name = "Reply Enabled",
        description = "Enable the /r command to quickly respond to the last private message",
        position = 1,
        section = commandsSection
    )
    @Override
    default boolean featureCommands_ReplyEnabled() {
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
    default boolean featureCommands_WhisperEnabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureCommands_PrivateMessageEnabled",
        name = "Private Message Enabled",
        description =
            "Enable the /pm command to quickly private message players holding the " +
            "player's message prompt until cancelled (Esc or empty message). Avoids " +
            "having use commands each message.",
        position = 3,
        section = commandsSection
    )
    @Override
    default boolean featureCommands_PrivateMessageEnabled() {
        return true;
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
    default boolean featureMessageHistory_Enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureMessageHistory_MaxEntries",
        name = "Max Entries",
        description = "Maximum number of entries to keep in message history",
        position = 1,
        section = messageHistorySection
    )
    @Override
    default int featureMessageHistory_MaxEntries() {
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
    default boolean featureMessageHistory_IncludeCommands() {
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
    default boolean featureMessageHistory_SkipDuplicates() {
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
    default Keybind featureMessageHistory_PrevKey() {
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
    default Keybind featureMessageHistory_NextKey() {
        return new Keybind(KeyEvent.VK_PAGE_DOWN, 0);
    }
}
