package com.modernchat;

import com.modernchat.common.ChatMode;
import com.modernchat.common.FontStyle;
import com.modernchat.common.Sfx;
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
public interface ModernChatConfig extends Config, ModernChatConfigBase
{
    /* ------------ Sections ------------ */

    @ConfigSection(
        name = "Modern Design (beta)",
        description = "Modern Chat redesign settings",
        position = 0,
        closedByDefault = false
    )
    String modernChatSection = "modernChatSection";

    @ConfigSection(
        name = "Modern Design Style",
        description = "Modern Chat style settings (Modern Design must be enabled)",
        position = 1,
        closedByDefault = true
    )
    String modernChatStyleSection = "modernChatStyleSection";

    @ConfigSection(
        name = "General",
        description = "General settings for Modern Chat",
        position = 2,
        closedByDefault = false
    )
    String generalSection = "generalSection";

    @ConfigSection(
        name = "Chat Toggle",
        description = "Show/hide chat with a hotkey",
        position = 3,
        closedByDefault = false
    )
    String toggleChatSection = "toggleChatSection";

    @ConfigSection(
        name = "Peek Overlay",
        description = "Show a peek overlay when chat is hidden",
        position = 4,
        closedByDefault = true
    )
    String peekOverlaySection = "peekOverlaySection";

    @ConfigSection(
        name = "Chat Commands",
        description = "Custom chat commands for quick actions",
        position = 5,
        closedByDefault = true
    )
    String commandsSection = "commandsSection";

    @ConfigSection(
        name = "Notifications",
        description = "Notification settings for chat events",
        position = 6,
        closedByDefault = true
    )
    String notificationsSection = "notificationsSection";

    @ConfigSection(
        name = "Message History",
        description = "Cycle through your message history",
        position = 7,
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

    /* ------------ Modern Chat ------------ */

    @ConfigItem(
        keyName = "featureRedesign_Enabled",
        name = "Enable",
        description = "Enable Modern Chat redesign",
        warning = "This is a beta feature and may not work as expected for all features. " +
                  "Feel free to give it a go, but be aware that some features may not be fully functional yet.",
        position = 0,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_Enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_DefaultChatMode",
        name = "Default Chat Mode",
        description = "Default chat mode when opening a new tab",
        position = 1,
        section = modernChatSection
    )
    @Override
    default ChatMode featureRedesign_DefaultChatMode() {
        return ChatMode.PUBLIC;
    }

    @ConfigItem(
        keyName = "featureRedesign_OpenTabOnIncomingPM",
        name = "Open Tab on Incoming PM",
        description = "Open a new tab when receiving a private message",
        position = 2,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_OpenTabOnIncomingPM() {
        return false;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_PrefixChatType",
        name = "Show Type",
        description = "Prefix messages with their chat type (e.g. [Clan], [System], etc.)",
        position = 3,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_MessageContainer_PrefixChatType() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_ShowTimestamp",
        name = "Show Timestamp",
        description = "Show timestamps in the message container",
        position = 4,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_MessageContainer_ShowTimestamp() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_Resizeable",
        name = "Resizeable",
        description = "Allow resizing the chat window",
        position = 5,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_Resizeable() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_Scrollable",
        name = "Scrollable",
        description = "Allow scrolling in the message container",
        position = 6,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_MessageContainer_Scrollable() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_ClickOutsideToClose",
        name = "Click Outside Closes",
        description = "Close chat by clicking outside the chat area",
        position = 7,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_ClickOutsideToClose() {
        return false;
    }

    @ConfigItem(
        keyName = "featureRedesign_ShowNotificationBadge",
        name = "Show Notification Badge",
        description = "Show a notification badge on the tab button when there are unread messages",
        position = 8,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_ShowNotificationBadge() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_AllowClickThrough",
        name = "Allow Click-Through",
        description = "Allow clicking through the chat overlay to interact with game elements",
        position = 9,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_AllowClickThrough() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_AutoSelectPrivateTab",
        name = "Auto Select Private Tab",
        description = "Automatically select the private chat tab when receiving a private message",
        position = 10,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_AutoSelectPrivateTab() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_ShowNpc",
        name = "Show NPC Messages",
        description = "Show NPC messages in the chat",
        position = 11,
        section = modernChatSection
    )
    @Override
    default boolean featureRedesign_ShowNpc() {
        return false;
    }

    /* ------------ Modern Chat Style ------------ */

    @ConfigItem(
        keyName = "featureRedesign_FontStyle",
        name = "Font Style",
        description = "Font style for the outer chat text like input, tabs, etc (see Line Font Style for message text)",
        position = 1,
        section = modernChatStyleSection
    )
    @Override
    default FontStyle featureRedesign_FontStyle() {
        return FontStyle.RUNE;
    }

    @ConfigItem(
        keyName = "featureRedesign_InputFontSize",
        name = "Input Font Size",
        description = "Font size for the input field",
        position = 2,
        section = modernChatStyleSection
    )
    @Units(Units.PIXELS)
    @Override
    default int featureRedesign_InputFontSize() {
        return 16;
    }

    @ConfigItem(
        keyName = "featureRedesign_TabFontSize",
        name = "Tab Font Size",
        description = "Font size for tabs in the tab bar",
        position = 3,
        section = modernChatStyleSection
    )
    @Units(Units.PIXELS)
    @Override
    default int featureRedesign_TabFontSize() {
        return 16;
    }

    @ConfigItem(
        keyName = "featureRedesign_TabBadgeFontSize",
        name = "Badge Font Size",
        description = "Font size for the notification badge on tabs",
        position = 4,
        section = modernChatStyleSection
    )
    @Units(Units.PIXELS)
    @Override
    default int featureRedesign_TabBadgeFontSize() {
        return 12;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_LineFontStyle",
        name = "Message Font Style",
        description = "Font style for messages in the message container",
        position = 5,
        section = modernChatStyleSection
    )
    @Override
    default FontStyle featureRedesign_MessageContainer_LineFontStyle() {
        return FontStyle.RUNE;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_LineFontSize",
        name = "Message Font Size",
        description = "Font size for messages in the message container",
        position = 6,
        section = modernChatStyleSection
    )
    @Units(Units.PIXELS)
    @Override
    default int featureRedesign_MessageContainer_LineFontSize() {
        return 16;
    }

    @ConfigItem(
        keyName = "featureRedesign_Padding",
        name = "Padding",
        description = "Padding around the chat view port",
        position = 7,
        section = modernChatStyleSection
    )
    @Range(max = 200)
    @Units(Units.PIXELS)
    @Override
    default int featureRedesign_Padding() {
        return 8;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_DrawScrollbar",
        name = "Draw Scrollbar",
        description = "Draw a scrollbar in the message container",
        position = 8,
        section = modernChatStyleSection
    )
    @Override
    default boolean featureRedesign_MessageContainer_DrawScrollbar() {
        return true;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_ScrollbarWidth",
        name = "Scrollbar Width",
        description = "Width of the scrollbar in the message container",
        position = 9,
        section = modernChatStyleSection
    )
    @Range(max = 100)
    @Units(Units.PIXELS)
    @Override
    default int featureRedesign_MessageContainer_ScrollbarWidth() {
        return 8;
    }

    @ConfigItem(
        keyName = "featureRedesign_BackdropColor",
        name = "Backdrop Color",
        description = "Color for the chat backdrop",
        position = 10,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_BackdropColor() {
        return new Color(0, 0, 0, 100);
    }

    @ConfigItem(
        keyName = "featureRedesign_BorderColor",
        name = "Border Color",
        description = "Border color for the chat view",
        position = 11,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_BorderColor() {
        return new Color(12, 12, 12, 0);
    }

    @ConfigItem(
        keyName = "featureRedesign_InputPrefixColor",
        name = "Input Prefix Color",
        description = "Color for the input prefix name",
        position = 12,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_InputPrefixColor() {
        return new Color(160, 200, 255);
    }

    @ConfigItem(
        keyName = "featureRedesign_InputBackgroundColor",
        name = "Input Background Color",
        description = "Background color for the input field",
        position = 13,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_InputBackgroundColor() {
        return new Color(0, 0, 0, 145);
    }

    @ConfigItem(
        keyName = "featureRedesign_InputBorderColor",
        name = "Input Border Color",
        description = "Border color for the input field",
        position = 14,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_InputBorderColor() {
        return new Color(255, 255, 255, 40);
    }

    @ConfigItem(
        keyName = "featureRedesign_InputShadowColor",
        name = "Input Shadow Color",
        description = "Shadow color for the input field",
        position = 15,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_InputShadowColor() {
        return new Color(0, 0, 0, 200);
    }

    @ConfigItem(
        keyName = "featureRedesign_InputTextColor",
        name = "Input Text Color",
        description = "Text color for the input field",
        position = 16,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_InputTextColor() {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "featureRedesign_InputCaretColor",
        name = "Input Caret Color",
        description = "Caret (cursor) color for the input field",
        position = 17,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_InputCaretColor() {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "featureRedesign_TabBarBackgroundColor",
        name = "Tab Bar Background Color",
        description = "Background color for the tab bar",
        position = 18,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabBarBackgroundColor() {
        return new Color(0, 0, 0, 50);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabColor",
        name = "Tab Color",
        description = "Color for inactive tabs in the tab bar",
        position = 19,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabColor() {
        return new Color(35, 35, 35, 180);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabSelectedColor",
        name = "Tab Selected Color",
        description = "Color for the selected tab in the tab bar",
        position = 20,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabSelectedColor() {
        return new Color(60, 60, 60, 220);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabBorderColor",
        name = "Tab Border Color",
        description = "Border color for the tab bar",
        position = 21,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabBorderColor() {
        return new Color(255, 255, 255, 70);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabBorderSelectedColor",
        name = "Tab Border Selected Color",
        description = "Border color for the selected tab in the tab bar",
        position = 22,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabBorderSelectedColor() {
        return new Color(255, 255, 255, 140);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabTextColor",
        name = "Tab Text Color",
        description = "Text color for tabs in the tab bar",
        position = 23,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabTextColor() {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "featureRedesign_TabUnreadPulseToColor",
        name = "Unread Pulse To Color",
        description = "Color to pulse to when a tab has unread messages",
        position = 24,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabUnreadPulseToColor() {
        return new Color(255,180,60);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabUnreadPulseFromColor",
        name = "Unread Pulse From Color",
        description = "Color to pulse from when a tab has unread messages",
        position = 25,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabUnreadPulseFromColor() {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "featureRedesign_TabNotificationColor",
        name = "Tab Notification Color",
        description = "Color for the tab notification (e.g. when a new message arrives)",
        position = 26,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabNotificationColor() {
        return new Color(200, 60, 60, 230);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabNotificationTextColor",
        name = "Tab Notification Text Color",
        description = "Text color for the tab notification",
        position = 27,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabNotificationTextColor() {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "featureRedesign_TabCloseButtonColor",
        name = "Tab Close Button Color",
        description = "Color for the tab close button",
        position = 28,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabCloseButtonColor() {
        return new Color(200, 60, 60, 230);
    }

    @ConfigItem(
        keyName = "featureRedesign_TabCloseButtonTextColor",
        name = "Tab Close Text Color",
        description = "Text color for the tab close button",
        position = 29,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_TabCloseButtonTextColor() {
        return Color.WHITE;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_OffsetX",
        name = "Message Offset X",
        description = "Horizontal offset for the message container",
        position = 30,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_OffsetX() {
        return 0;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_OffsetY",
        name = "Message Offset Y",
        description = "Vertical offset for the message container",
        position = 31,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_OffsetY() {
        return 0;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_Margin",
        name = "Message Margin",
        description = "Margin around the message container",
        position = 32,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_Margin() {
        return 0;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_PaddingTop",
        name = "Message Padding Top",
        description = "Padding at the top of the message container",
        position = 33,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_PaddingTop() {
        return 4;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_PaddingLeft",
        name = "Message Padding Left",
        description = "Padding at the left of the message container",
        position = 34,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_PaddingLeft() {
        return 5;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_PaddingBottom",
        name = "Message Padding Bottom",
        description = "Padding at the bottom of the message container",
        position = 35,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_PaddingBottom() {
        return 0;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_PaddingRight",
        name = "Message Padding Right",
        description = "Padding at the right of the message container",
        position = 36,
        section = modernChatStyleSection
    )
    @Range(min = -500, max = 500)
    @Override
    default int featureRedesign_MessageContainer_PaddingRight() {
        return 2;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_LineSpacing",
        name = "Message Spacing",
        description = "Spacing between lines in the message container",
        position = 37,
        section = modernChatStyleSection
    )
    @Range(max = 100)
    @Override
    default int featureRedesign_MessageContainer_LineSpacing() {
        return 0;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_ScrollStep",
        name = "Message Scroll Step",
        description = "Number of lines to scroll when using the mouse wheel",
        position = 38,
        section = modernChatStyleSection
    )
    @Range(max = 120)
    @Override
    default int featureRedesign_MessageContainer_ScrollStep() {
        return 32;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_TextShadow",
        name = "Message Text Shadow",
        description = "Shadow effect for text in the message container",
        position = 39,
        section = modernChatStyleSection
    )
    @Range(max = 32)
    @Override
    default int featureRedesign_MessageContainer_TextShadow() {
        return 1;
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_BackdropColor",
        name = "Message Backdrop Color",
        description = "Color for the message container backdrop",
        position = 40,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_MessageContainer_BackdropColor() {
        return new Color(0, 0, 0, 150);
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_BorderColor",
        name = "Message Border Color",
        description = "Color for the message container border",
        position = 41,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_MessageContainer_BorderColor() {
        return new Color(12, 12, 12, 0);
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_ShadowColor",
        name = "Message Shadow Color",
        description = "Shadow color for the message container",
        position = 42,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_MessageContainer_ShadowColor() {
        return new Color(0, 0, 0, 200);
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_ScrollbarTrackColor",
        name = "Scrollbar Track Color",
        description = "Color for the scrollbar track in the message container",
        position = 43,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_MessageContainer_ScrollbarTrackColor() {
        return new Color(255, 255, 255, 32);
    }

    @ConfigItem(
        keyName = "featureRedesign_MessageContainer_ScrollbarThumbColor",
        name = "Scrollbar Thumb Color",
        description = "Color for the scrollbar thumb in the message container",
        position = 44,
        section = modernChatStyleSection
    )
    @Alpha
    @Override
    default Color featureRedesign_MessageContainer_ScrollbarThumbColor() {
        return new Color(255, 255, 255, 144);
    }

    /* ------------ General Settings ------------ */

    @Override default Color getWelcomeColor() { return general_WelcomeChatColor(); }
    @Override default Color getPublicColor() { return general_PublicChatColor(); }
    @Override default Color getPrivateColor() { return general_PrivateChatColor(); }
    @Override default Color getFriendColor() { return general_FriendsChatColor(); }
    @Override default Color getClanColor() { return general_ClanChatColor(); }
    @Override default Color getSystemColor() { return general_SystemChatColor(); }
    @Override default Color getTradeColor() { return general_TradeChatColor(); }

    @ConfigItem(
        keyName = "general_AnchorPrivateChat",
        name = "Anchor Private Chat",
        description = "Anchor the split private chat window to the top of the chatbox",
        position = 0,
        section = generalSection
    )
    @Override
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
    @Override
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
    @Override
    default int general_AnchorPrivateChatOffsetY() {
        return 0;
    }

    @ConfigItem(
        keyName = "general_HelperNotifications",
        name = "Helper Notifications",
        description = "Show notifications for helper messages in the chat",
        position = 3,
        section = generalSection
    )
    @Override
    default boolean general_HelperNotifications() {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "general_PublicChatColor",
        name = "Public Chat Color",
        description = "Color for public chat messages in the peek overlay",
        position = 4,
        section = generalSection
    )
    @Override
    default Color general_PublicChatColor() {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(
        keyName = "general_FriendsChatColor",
        name = "Friends Chat Color",
        description = "Color for friends chat messages in the peek overlay",
        position = 5,
        section = generalSection
    )
    @Override
    default Color general_FriendsChatColor() {
        return new Color(0x00FF80); // light green
    }

    @Alpha
    @ConfigItem(
        keyName = "general_ClanChatColor",
        name = "Clan Chat Color",
        description = "Color for clan chat messages in the peek overlay",
        position = 6,
        section = generalSection
    )
    @Override
    default Color general_ClanChatColor() {
        return new Color(0x80C0FF); // light blue
    }

    @Alpha
    @ConfigItem(
        keyName = "general_PrivateChatColor",
        name = "Private Chat Color",
        description = "Color for private chat messages in the peek overlay",
        position = 7,
        section = generalSection
    )
    @Override
    default Color general_PrivateChatColor() {
        return new Color(0xFF80FF); // light purple
    }

    @Alpha
    @ConfigItem(
        keyName = "general_SystemChatColor",
        name = "System Chat Color",
        description = "Color for system chat messages in the peek overlay",
        position = 8,
        section = generalSection
    )
    @Override
    default Color general_SystemChatColor() {
        return new Color(0xCFCFCF); // light gray
    }

    @Alpha
    @ConfigItem(
        keyName = "general_WelcomeChatColor",
        name = "Welcome Chat Color",
        description = "Color for welcome chat messages in the peek overlay",
        position = 9,
        section = generalSection
    )
    @Override
    default Color general_WelcomeChatColor() {
        return Color.WHITE;
    }

    @Alpha
    @ConfigItem(
        keyName = "general_TradeChatColor",
        name = "Trade Chat Color",
        description = "Color for trade chat messages in the peek overlay",
        position = 10,
        section = generalSection
    )
    @Override
    default Color general_TradeChatColor() {
        return Color.ORANGE;
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
        keyName = "featureToggle_EscapeHides",
        name = "Escape hides",
        description = "Hide the chatbox when pressing Escape",
        position = 3,
        section = toggleChatSection
    )
    @Override
    default boolean featureToggle_EscapeHides() {
        return true;
    }

    @ConfigItem(
        keyName = "featureToggle_StartHidden",
        name = "Start hidden",
        description = "Hide the chatbox when the plugin starts",
        position = 4,
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
        position = 5,
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
        keyName = "featurePeek_FollowChatBox",
        name = "Follow Chat Box",
        description = "Follow the Chat Box position",
        position = 1,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_FollowChatBox() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_ShowPrivateMessages",
        name = "Show Private Messages",
        description = "Show private messages in the peek overlay",
        position = 2,
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
        position = 3,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_HideSplitPrivateMessages() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_ShowTimestamp",
        name = "Show Timestamp",
        description = "Show timestamps in the peek overlay",
        position = 4,
        section = peekOverlaySection
    )
    @Override
    default boolean featurePeek_ShowTimestamp() {
        return true;
    }

    @ConfigItem(
        keyName = "featurePeek_PrefixChatTypes",
        name = "Show Type",
        description = "Prefix messages with their chat type in the peek overlay",
        position = 5,
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
        position = 6,
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
        position = 7,
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
        position = 8,
        section = peekOverlaySection
    )
    @Override
    default FontStyle featurePeek_FontStyle() {
        return FontStyle.RUNE;
    }

    @ConfigItem(
        keyName = "featurePeek_FontSize",
        name = "Font Size",
        description = "Show an overlay when the chat is hidden to peek at messages",
        position = 9,
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
        position = 10,
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
        position = 11,
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
        position = 12,
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
        position = 13,
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
        position = 14,
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
        position = 15,
        section = peekOverlaySection
    )
    @Range(min = -500, max = 500)
    @Units(Units.PIXELS)
    @Override
    default int featurePeek_MarginBottom() {
        return 0;
    }

    @ConfigItem(
        keyName = "featurePeek_FadeEnabled",
        name = "Fade Enabled",
        description = "Enable fade-in/out effect for the peek overlay (overlay will automatically reappear when a message is received)",
        position = 16,
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
        position = 17,
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
        position = 18,
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

    /* ------------ Notification Settings ------------ */

    @ConfigItem(
        keyName = "featureNotify_Enabled",
        name = "Enable",
        description = "Enable chat notifications for new messages",
        position = 0,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_Enabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureNotify_SoundEnabled",
        name = "Enable Sounds",
        description = "Play a sound when a new message arrives",
        position = 1,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_SoundEnabled() {
        return true;
    }

    @ConfigItem(
        keyName = "featureNotify_UseRuneLiteSound",
        name = "Use RuneLite Sound",
        description = "Use the default RuneLite notification sound for new messages",
        position = 2,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_UseRuneLiteSound() {
        return false;
    }

    @ConfigItem(
        keyName = "featureNotify_VolumePercent",
        name = "Volume Percent",
        description = "Volume percentage for notification sounds (0-100)",
        position = 3,
        section = notificationsSection
    )
    @Range(min = 0, max = 100)
    @Override
    default int featureNotify_VolumePercent() {
        return 50;
    }

    @ConfigItem(
        keyName = "featureNotify_MessageReceivedSfx",
        name = "Message Received Sound",
        description = "Sound to play when a new message is received",
        position = 4,
        section = notificationsSection
    )
    @Override
    default Sfx featureNotify_MessageReceivedSfx() {
        return Sfx.MSG_RECEIVED_1;
    }

    @ConfigItem(
        keyName = "featureNotify_OnPublicMessage",
        name = "Public Messages",
        description = "Notify when a new public message arrives",
        position = 5,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_OnPublicMessage() {
        return false;
    }

    @ConfigItem(
        keyName = "featureNotify_OnPrivateMessage",
        name = "Private Messages",
        description = "Notify when a new private message arrives",
        position = 6,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_OnPrivateMessage() {
        return true;
    }

    @ConfigItem(
        keyName = "featureNotify_OnFriendsChat",
        name = "Friends Messages",
        description = "Notify when a new friends message arrives",
        position = 7,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_OnFriendsChat() {
        return false;
    }

    @ConfigItem(
        keyName = "featureNotify_OnClan",
        name = "Clan Chat",
        description = "Notify when a new clan chat message arrives",
        position = 8,
        section = notificationsSection
    )
    @Override
    default boolean featureNotify_OnClan() {
        return false;
    }
}
