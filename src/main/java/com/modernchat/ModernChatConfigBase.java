package com.modernchat;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.modernchat.common.ChatMode;
import com.modernchat.common.ExtendedKeybind;
import com.modernchat.common.FontStyle;
import com.modernchat.common.Sfx;
import com.modernchat.feature.ChatRedesignFeature;
import com.modernchat.feature.ExampleChatFeature;
import com.modernchat.feature.MessageHistoryChatFeature;
import com.modernchat.feature.NotificationChatFeature;
import com.modernchat.feature.PeekChatFeature;
import com.modernchat.feature.ToggleChatFeature;
import com.modernchat.feature.command.CommandsChatFeature;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Base compile-safe schema for ModernChatConfig.
 * <p>
 * A little verbose because we can't use reflection.
 */
public interface ModernChatConfigBase extends
    ExampleChatFeature.ExampleChatFeatureConfig,
    ChatRedesignFeature.ChatRedesignFeatureConfig,
    ToggleChatFeature.ToggleChatFeatureConfig,
    PeekChatFeature.PeekChatFeatureConfig,
    CommandsChatFeature.CommandsChatConfig,
    MessageHistoryChatFeature.MessageHistoryChatFeatureConfig,
    NotificationChatFeature.NotificationChatFeatureConfig
{
    String GROUP = "modernchat";
    String HISTORY_KEY = "messageHistory";
    String CHAT_WIDTH = "chatWidth";
    String CHAT_HEIGHT = "chatHeight";

    /** Default-fallback provider for interface default methods. */
    ModernChatConfigBase DEFAULTS = new ModernChatConfig() {};

    /** Centralized literal keys (optional: use in other places too). */
    interface Keys {
        String featureExample_Enabled = "featureExample_Enabled";

        // Core
        String featureRedesign_Enabled = "featureRedesign_Enabled";
        String featureRedesign_DefaultChatMode = "featureRedesign_DefaultChatMode";
        String featureRedesign_OpenTabOnIncomingPM = "featureRedesign_OpenTabOnIncomingPM";
        String featureRedesign_MessageContainer_PrefixChatType = "featureRedesign_MessageContainer_PrefixChatType";
        String featureRedesign_MessageContainer_ShowTimestamp = "featureRedesign_MessageContainer_ShowTimestamp";
        String featureRedesign_Resizeable = "featureRedesign_Resizeable";
        String featureRedesign_MessageContainer_Scrollable = "featureRedesign_MessageContainer_Scrollable";
        String featureRedesign_ClickOutsideToClose = "featureRedesign_ClickOutsideToClose";
        String featureRedesign_ShowNotificationBadge = "featureRedesign_ShowNotificationBadge";
        String featureRedesign_AllowClickThrough = "featureRedesign_AllowClickThrough";
        String featureRedesign_AutoSelectPrivateTab = "featureRedesign_AutoSelectPrivateTab";
        String featureRedesign_ShowNpc = "featureRedesign_ShowNpc";
        String featureRedesign_ClassicMode = "featureRedesign_ClassicMode";
        String featureRedesign_ClassicMode_AllowPmTabs = "featureRedesign_ClassicMode_AllowPmTabs";
        String featureRedesign_ClassicMode_ShowUnread = "featureRedesign_ClassicMode_ShowUnread";
        String featureRedesign_GameTabEnabled = "featureRedesign_GameTabEnabled";
        String featureRedesign_TradeTabEnabled = "featureRedesign_TradeTabEnabled";

        // Style: fonts & sizes
        String featureRedesign_FontStyle = "featureRedesign_FontStyle";
        String featureRedesign_InputFontSize = "featureRedesign_InputFontSize";
        String featureRedesign_TabFontSize = "featureRedesign_TabFontSize";
        String featureRedesign_TabBadgeFontSize = "featureRedesign_TabBadgeFontSize";
        String featureRedesign_MessageContainer_LineFontStyle = "featureRedesign_MessageContainer_LineFontStyle";
        String featureRedesign_MessageContainer_LineFontSize = "featureRedesign_MessageContainer_LineFontSize";

        // Layout / metrics
        String featureRedesign_Padding = "featureRedesign_Padding";
        String featureRedesign_MessageContainer_DrawScrollbar = "featureRedesign_MessageContainer_DrawScrollbar";
        String featureRedesign_MessageContainer_ScrollbarWidth = "featureRedesign_MessageContainer_ScrollbarWidth";

        // Colors (outer)
        String featureRedesign_BackdropColor = "featureRedesign_BackdropColor";
        String featureRedesign_BorderColor = "featureRedesign_BorderColor";
        String featureRedesign_InputPrefixColor = "featureRedesign_InputPrefixColor";
        String featureRedesign_InputBackgroundColor = "featureRedesign_InputBackgroundColor";
        String featureRedesign_InputBorderColor = "featureRedesign_InputBorderColor";
        String featureRedesign_InputShadowColor = "featureRedesign_InputShadowColor";
        String featureRedesign_InputTextColor = "featureRedesign_InputTextColor";
        String featureRedesign_InputCaretColor = "featureRedesign_InputCaretColor";
        String featureRedesign_TabBarBackgroundColor = "featureRedesign_TabBarBackgroundColor";
        String featureRedesign_TabColor = "featureRedesign_TabColor";
        String featureRedesign_TabSelectedColor = "featureRedesign_TabSelectedColor";
        String featureRedesign_TabBorderColor = "featureRedesign_TabBorderColor";
        String featureRedesign_TabBorderSelectedColor = "featureRedesign_TabBorderSelectedColor";
        String featureRedesign_TabTextColor = "featureRedesign_TabTextColor";
        String featureRedesign_TabUnreadPulseToColor = "featureRedesign_TabUnreadPulseToColor";
        String featureRedesign_TabUnreadPulseFromColor = "featureRedesign_TabUnreadPulseFromColor";
        String featureRedesign_TabNotificationColor = "featureRedesign_TabNotificationColor";
        String featureRedesign_TabNotificationTextColor = "featureRedesign_TabNotificationTextColor";
        String featureRedesign_TabCloseButtonColor = "featureRedesign_TabCloseButtonColor";
        String featureRedesign_TabCloseButtonTextColor = "featureRedesign_TabCloseButtonTextColor";
        String featureRedesign_FilterButtonColor = "featureRedesign_FilterButtonColor";
        String featureRedesign_ReportButtonColor = "featureRedesign_ReportButtonColor";
        String featureRedesign_ReportButtonTextColor = "featureRedesign_ReportButtonTextColor";

        // Message container geometry/colors
        String featureRedesign_MessageContainer_OffsetX = "featureRedesign_MessageContainer_OffsetX";
        String featureRedesign_MessageContainer_OffsetY = "featureRedesign_MessageContainer_OffsetY";
        String featureRedesign_MessageContainer_Margin = "featureRedesign_MessageContainer_Margin";
        String featureRedesign_MessageContainer_PaddingTop = "featureRedesign_MessageContainer_PaddingTop";
        String featureRedesign_MessageContainer_PaddingLeft = "featureRedesign_MessageContainer_PaddingLeft";
        String featureRedesign_MessageContainer_PaddingBottom = "featureRedesign_MessageContainer_PaddingBottom";
        String featureRedesign_MessageContainer_PaddingRight = "featureRedesign_MessageContainer_PaddingRight";
        String featureRedesign_MessageContainer_LineSpacing = "featureRedesign_MessageContainer_LineSpacing";
        String featureRedesign_MessageContainer_ScrollStep = "featureRedesign_MessageContainer_ScrollStep";
        String featureRedesign_MessageContainer_TextShadow = "featureRedesign_MessageContainer_TextShadow";
        String featureRedesign_MessageContainer_BackdropColor = "featureRedesign_MessageContainer_BackdropColor";
        String featureRedesign_MessageContainer_BorderColor = "featureRedesign_MessageContainer_BorderColor";
        String featureRedesign_MessageContainer_ShadowColor = "featureRedesign_MessageContainer_ShadowColor";
        String featureRedesign_MessageContainer_ScrollbarTrackColor = "featureRedesign_MessageContainer_ScrollbarTrackColor";
        String featureRedesign_MessageContainer_ScrollbarThumbColor = "featureRedesign_MessageContainer_ScrollbarThumbColor";

        // Filters
        String filters_Enabled = "filters_Enabled";
        String filters_ChatFilterEnabled = "filters_ChatFilterEnabled";
        String filters_AreaMuteEnabled = "filters_AreaMuteEnabled";

        // General
        String general_AnchorPrivateChat = "general_AnchorPrivateChat";
        String general_AnchorPrivateChatOffsetX = "general_AnchorPrivateChatOffsetX";
        String general_AnchorPrivateChatOffsetY = "general_AnchorPrivateChatOffsetY";
        String general_HelperNotifications = "general_HelperNotifications";
        String general_ShowReportButton = "general_ShowReportButton";
        String general_ShowSessionTimer = "general_ShowSessionTimer";
        String general_PublicChatColor = "general_PublicChatColor";
        String general_FriendsChatColor = "general_FriendsChatColor";
        String general_ClanChatColor = "general_ClanChatColor";
        String general_PrivateChatColor = "general_PrivateChatColor";
        String general_SystemChatColor = "general_SystemChatColor";
        String general_WelcomeChatColor = "general_WelcomeChatColor";
        String general_TradeChatColor = "general_TradeChatColor";

        // Toggle
        String featureToggle_Enabled = "featureToggle_Enabled";
        String featureToggle_ToggleKey = "featureToggle_ToggleKey";
        String featureToggle_ExtendedToggleKey = "featureToggle_ExtendedToggleKey";
        String featureToggle_AutoHideOnSend = "featureToggle_AutoHideOnSend";
        String featureToggle_EscapeHides = "featureToggle_EscapeHides";
        String featureToggle_StartHidden = "featureToggle_StartHidden";
        String featureToggle_LockCameraWhenVisible = "featureToggle_LockCameraWhenVisible";

        // Peek
        String featurePeek_Enabled = "featurePeek_Enabled";
        String featurePeek_FollowChatBox = "featurePeek_FollowChatBox";
        String featurePeek_ShowPrivateMessages = "featurePeek_ShowPrivateMessages";
        String featurePeek_HideSplitPrivateMessages = "featurePeek_HideSplitPrivateMessages";
        String featurePeek_ShowTimestamp = "featurePeek_ShowTimestamp";
        String featurePeek_PrefixChatTypes = "featurePeek_PrefixChatTypes";
        String featurePeek_BackgroundColor = "featurePeek_BackgroundColor";
        String featurePeek_BorderColor = "featurePeek_BorderColor";
        String featurePeek_FontStyle = "featurePeek_FontStyle";
        String featurePeek_FontSize  = "featurePeek_FontSize";
        String featurePeek_TextShadow = "featurePeek_TextShadow";
        String featurePeek_OffsetX = "featurePeek_OffsetX";
        String featurePeek_OffsetY = "featurePeek_OffsetY";
        String featurePeek_Padding = "featurePeek_Padding";
        String featurePeek_MarginRight = "featurePeek_MarginRight";
        String featurePeek_MarginBottom = "featurePeek_MarginBottom";
        String featurePeek_FadeEnabled = "featurePeek_FadeEnabled";
        String featurePeek_FadeDelay = "featurePeek_FadeDelay";
        String featurePeek_FadeDuration = "featurePeek_FadeDuration";
        String featurePeek_SourceTabKey = "featurePeek_SourceTabKey";
        String featurePeek_SuppressFadeAtGE = "featurePeek_SuppressFadeAtGE";
        String featurePeek_TimestampColor = "featurePeek_TimestampColor";
        String featurePeek_TypePrefixColor = "featurePeek_TypePrefixColor";
        String featurePeek_SourceTabIndicatorColor = "featurePeek_SourceTabIndicatorColor";

        // Timestamp and type prefix colors
        String featureRedesign_TimestampColor = "featureRedesign_TimestampColor";
        String featureRedesign_TypePrefixColor = "featureRedesign_TypePrefixColor";

        // Commands
        String featureCommands_Enabled = "featureCommands_Enabled";
        String featureCommands_ReplyEnabled = "featureCommands_ReplyEnabled";
        String featureCommands_WhisperEnabled = "featureCommands_WhisperEnabled";
        String featureCommands_PrivateMessageEnabled = "featureCommands_PrivateMessageEnabled";

        // Message history
        String featureMessageHistory_Enabled = "featureMessageHistory_Enabled";
        String featureMessageHistory_MaxEntries = "featureMessageHistory_MaxEntries";
        String featureMessageHistory_IncludeCommands = "featureMessageHistory_IncludeCommands";
        String featureMessageHistory_SkipDuplicates = "featureMessageHistory_SkipDuplicates";
        String featureMessageHistory_PrevKey = "featureMessageHistory_PrevKey";
        String featureMessageHistory_NextKey = "featureMessageHistory_NextKey";

        // Notify
        String featureNotify_Enabled = "featureNotify_Enabled";
        String featureNotify_SoundEnabled = "featureNotify_SoundEnabled";
        String featureNotify_UseRuneLiteSound = "featureNotify_UseRuneLiteSound";
        String featureNotify_VolumePercent = "featureNotify_VolumePercent";
        String featureNotify_MessageReceivedSfx = "featureNotify_MessageReceivedSfx";
        String featureNotify_OnPublicMessage = "featureNotify_OnPublicMessage";
        String featureNotify_OnPrivateMessage = "featureNotify_OnPrivateMessage";
        String featureNotify_OnFriendsChat = "featureNotify_OnFriendsChat";
        String featureNotify_OnClan = "featureNotify_OnClan";
    }

    /** Value kinds we need for generic JSON/Config writes. */
    enum Kind { BOOL, INT, ENUM, COLOR, KEYBIND }

    /** Field metadata with typed getter (no reflection). */
    enum Field
    {
        FEATURE_EXAMPLE(Keys.featureExample_Enabled, Kind.BOOL, ModernChatConfigBase::featureExample_Enabled),

        // ---- Modern Redesign (core) ----
        FEATURE_REDESIGN_ENABLED(Keys.featureRedesign_Enabled, Kind.BOOL, ModernChatConfigBase::featureRedesign_Enabled),
        FEATURE_REDESIGN_DEFAULT_CHAT_MODE(Keys.featureRedesign_DefaultChatMode, ChatMode.class, ModernChatConfigBase::featureRedesign_DefaultChatMode),
        FEATURE_REDESIGN_OPEN_TAB_ON_INCOMING_PM(Keys.featureRedesign_OpenTabOnIncomingPM, Kind.BOOL, ModernChatConfigBase::featureRedesign_OpenTabOnIncomingPM),
        FEATURE_REDESIGN_PREFIX_CHAT_TYPE(Keys.featureRedesign_MessageContainer_PrefixChatType, Kind.BOOL, ModernChatConfigBase::featureRedesign_MessageContainer_PrefixChatType),
        FEATURE_REDESIGN_SHOW_TIMESTAMP(Keys.featureRedesign_MessageContainer_ShowTimestamp, Kind.BOOL, ModernChatConfigBase::featureRedesign_MessageContainer_ShowTimestamp),
        FEATURE_REDESIGN_RESIZEABLE(Keys.featureRedesign_Resizeable, Kind.BOOL, ModernChatConfigBase::featureRedesign_Resizeable),
        FEATURE_REDESIGN_SCROLLABLE(Keys.featureRedesign_MessageContainer_Scrollable, Kind.BOOL, ModernChatConfigBase::featureRedesign_MessageContainer_Scrollable),
        FEATURE_REDESIGN_CLICK_OUTSIDE_TO_CLOSE(Keys.featureRedesign_ClickOutsideToClose, Kind.BOOL, ModernChatConfigBase::featureRedesign_ClickOutsideToClose),
        FEATURE_REDESIGN_SHOW_BADGE(Keys.featureRedesign_ShowNotificationBadge, Kind.BOOL, ModernChatConfigBase::featureRedesign_ShowNotificationBadge),
        FEATURE_REDESIGN_ALLOW_CLICK_THROUGH(Keys.featureRedesign_AllowClickThrough, Kind.BOOL, ModernChatConfigBase::featureRedesign_AllowClickThrough),
        FEATURE_REDESIGN_AUTO_SELECT_PRIVATE_TAB(Keys.featureRedesign_AutoSelectPrivateTab, Kind.BOOL, ModernChatConfigBase::featureRedesign_AutoSelectPrivateTab),
        FEATURE_REDESIGN_SHOW_NPC(Keys.featureRedesign_ShowNpc, Kind.BOOL, ModernChatConfigBase::featureRedesign_ShowNpc),
        FEATURE_REDESIGN_CLASSIC_MODE(Keys.featureRedesign_ClassicMode, Kind.BOOL, ModernChatConfigBase::featureRedesign_ClassicMode),
        FEATURE_REDESIGN_CLASSIC_MODE_ALLOW_PM_TABS(Keys.featureRedesign_ClassicMode_AllowPmTabs, Kind.BOOL, ModernChatConfigBase::featureRedesign_ClassicMode_AllowPmTabs),
        FEATURE_REDESIGN_CLASSIC_MODE_SHOW_UNREAD(Keys.featureRedesign_ClassicMode_ShowUnread, Kind.BOOL, ModernChatConfigBase::featureRedesign_ClassicMode_ShowUnread),
        FEATURE_REDESIGN_GAME_TAB_ENABLED(Keys.featureRedesign_GameTabEnabled, Kind.BOOL, ModernChatConfigBase::featureRedesign_GameTabEnabled),
        FEATURE_REDESIGN_TRADE_TAB_ENABLED(Keys.featureRedesign_TradeTabEnabled, Kind.BOOL, ModernChatConfigBase::featureRedesign_TradeTabEnabled),

        // ---- Style: fonts & sizes ----
        FEATURE_REDESIGN_FONT_STYLE(Keys.featureRedesign_FontStyle, FontStyle.class, ModernChatConfigBase::featureRedesign_FontStyle),
        FEATURE_REDESIGN_INPUT_FONT_SIZE(Keys.featureRedesign_InputFontSize, Kind.INT, ModernChatConfigBase::featureRedesign_InputFontSize),
        FEATURE_REDESIGN_TAB_FONT_SIZE(Keys.featureRedesign_TabFontSize, Kind.INT, ModernChatConfigBase::featureRedesign_TabFontSize),
        FEATURE_REDESIGN_TAB_BADGE_FONT_SIZE(Keys.featureRedesign_TabBadgeFontSize, Kind.INT, ModernChatConfigBase::featureRedesign_TabBadgeFontSize),
        FEATURE_REDESIGN_MSG_LINE_FONT_STYLE(Keys.featureRedesign_MessageContainer_LineFontStyle, FontStyle.class, ModernChatConfigBase::featureRedesign_MessageContainer_LineFontStyle),
        FEATURE_REDESIGN_MSG_LINE_FONT_SIZE(Keys.featureRedesign_MessageContainer_LineFontSize, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_LineFontSize),

        // ---- Layout / metrics ----
        FEATURE_REDESIGN_PADDING(Keys.featureRedesign_Padding, Kind.INT, ModernChatConfigBase::featureRedesign_Padding),
        FEATURE_REDESIGN_DRAW_SCROLLBAR(Keys.featureRedesign_MessageContainer_DrawScrollbar, Kind.BOOL, ModernChatConfigBase::featureRedesign_MessageContainer_DrawScrollbar),
        FEATURE_REDESIGN_SCROLLBAR_WIDTH(Keys.featureRedesign_MessageContainer_ScrollbarWidth, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_ScrollbarWidth),

        // ---- Colors (outer) ----
        FEATURE_REDESIGN_BACKDROP_COLOR(Keys.featureRedesign_BackdropColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_BackdropColor),
        FEATURE_REDESIGN_BORDER_COLOR(Keys.featureRedesign_BorderColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_BorderColor),
        FEATURE_REDESIGN_INPUT_PREFIX_COLOR(Keys.featureRedesign_InputPrefixColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_InputPrefixColor),
        FEATURE_REDESIGN_INPUT_BG_COLOR(Keys.featureRedesign_InputBackgroundColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_InputBackgroundColor),
        FEATURE_REDESIGN_INPUT_BORDER_COLOR(Keys.featureRedesign_InputBorderColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_InputBorderColor),
        FEATURE_REDESIGN_INPUT_SHADOW_COLOR(Keys.featureRedesign_InputShadowColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_InputShadowColor),
        FEATURE_REDESIGN_INPUT_TEXT_COLOR(Keys.featureRedesign_InputTextColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_InputTextColor),
        FEATURE_REDESIGN_INPUT_CARET_COLOR(Keys.featureRedesign_InputCaretColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_InputCaretColor),
        FEATURE_REDESIGN_TABBAR_BG_COLOR(Keys.featureRedesign_TabBarBackgroundColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabBarBackgroundColor),
        FEATURE_REDESIGN_TAB_COLOR(Keys.featureRedesign_TabColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabColor),
        FEATURE_REDESIGN_TAB_SELECTED_COLOR(Keys.featureRedesign_TabSelectedColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabSelectedColor),
        FEATURE_REDESIGN_TAB_BORDER_COLOR(Keys.featureRedesign_TabBorderColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabBorderColor),
        FEATURE_REDESIGN_TAB_BORDER_SELECTED_COLOR(Keys.featureRedesign_TabBorderSelectedColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabBorderSelectedColor),
        FEATURE_REDESIGN_TAB_TEXT_COLOR(Keys.featureRedesign_TabTextColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabTextColor),
        FEATURE_REDESIGN_TAB_UNREAD_TO_COLOR(Keys.featureRedesign_TabUnreadPulseToColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabUnreadPulseToColor),
        FEATURE_REDESIGN_TAB_UNREAD_FROM_COLOR(Keys.featureRedesign_TabUnreadPulseFromColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabUnreadPulseFromColor),
        FEATURE_REDESIGN_TAB_NOTIF_COLOR(Keys.featureRedesign_TabNotificationColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabNotificationColor),
        FEATURE_REDESIGN_TAB_NOTIF_TEXT_COLOR(Keys.featureRedesign_TabNotificationTextColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabNotificationTextColor),
        FEATURE_REDESIGN_TAB_CLOSE_BTN_COLOR(Keys.featureRedesign_TabCloseButtonColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabCloseButtonColor),
        FEATURE_REDESIGN_TAB_CLOSE_BTN_TEXT_COLOR(Keys.featureRedesign_TabCloseButtonTextColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TabCloseButtonTextColor),
        FEATURE_REDESIGN_FILTER_BTN_COLOR(Keys.featureRedesign_FilterButtonColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_FilterButtonColor),
        FEATURE_REDESIGN_REPORT_BTN_COLOR(Keys.featureRedesign_ReportButtonColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_ReportButtonColor),
        FEATURE_REDESIGN_REPORT_BTN_TEXT_COLOR(Keys.featureRedesign_ReportButtonTextColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_ReportButtonTextColor),

        // ---- Message container geometry ----
        FEATURE_REDESIGN_MSG_OFFSET_X(Keys.featureRedesign_MessageContainer_OffsetX, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_OffsetX),
        FEATURE_REDESIGN_MSG_OFFSET_Y(Keys.featureRedesign_MessageContainer_OffsetY, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_OffsetY),
        FEATURE_REDESIGN_MSG_MARGIN(Keys.featureRedesign_MessageContainer_Margin, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_Margin),
        FEATURE_REDESIGN_MSG_PAD_TOP(Keys.featureRedesign_MessageContainer_PaddingTop, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_PaddingTop),
        FEATURE_REDESIGN_MSG_PAD_LEFT(Keys.featureRedesign_MessageContainer_PaddingLeft, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_PaddingLeft),
        FEATURE_REDESIGN_MSG_PAD_BOTTOM(Keys.featureRedesign_MessageContainer_PaddingBottom, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_PaddingBottom),
        FEATURE_REDESIGN_MSG_PAD_RIGHT(Keys.featureRedesign_MessageContainer_PaddingRight, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_PaddingRight),
        FEATURE_REDESIGN_MSG_LINE_SPACING(Keys.featureRedesign_MessageContainer_LineSpacing, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_LineSpacing),
        FEATURE_REDESIGN_MSG_SCROLL_STEP(Keys.featureRedesign_MessageContainer_ScrollStep, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_ScrollStep),
        FEATURE_REDESIGN_MSG_TEXT_SHADOW(Keys.featureRedesign_MessageContainer_TextShadow, Kind.INT, ModernChatConfigBase::featureRedesign_MessageContainer_TextShadow),

        // ---- Message container colors ----
        FEATURE_REDESIGN_MSG_BACKDROP_COLOR(Keys.featureRedesign_MessageContainer_BackdropColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_MessageContainer_BackdropColor),
        FEATURE_REDESIGN_MSG_BORDER_COLOR(Keys.featureRedesign_MessageContainer_BorderColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_MessageContainer_BorderColor),
        FEATURE_REDESIGN_MSG_SHADOW_COLOR(Keys.featureRedesign_MessageContainer_ShadowColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_MessageContainer_ShadowColor),
        FEATURE_REDESIGN_MSG_SCROLL_TRACK_COLOR(Keys.featureRedesign_MessageContainer_ScrollbarTrackColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_MessageContainer_ScrollbarTrackColor),
        FEATURE_REDESIGN_MSG_SCROLL_THUMB_COLOR(Keys.featureRedesign_MessageContainer_ScrollbarThumbColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_MessageContainer_ScrollbarThumbColor),
        FEATURE_REDESIGN_TIMESTAMP_COLOR(Keys.featureRedesign_TimestampColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TimestampColor),
        FEATURE_REDESIGN_TYPE_PREFIX_COLOR(Keys.featureRedesign_TypePrefixColor, Kind.COLOR, ModernChatConfigBase::featureRedesign_TypePrefixColor),

        // ---- Filters ----
        FILTERS_ENABLED(Keys.filters_Enabled, Kind.BOOL, ModernChatConfigBase::filters_Enabled),
        FILTERS_CHAT_FILTER_ENABLED(Keys.filters_ChatFilterEnabled, Kind.BOOL, ModernChatConfigBase::filters_ChatFilterEnabled),
        FILTERS_AREA_MUTE_ENABLED(Keys.filters_AreaMuteEnabled, Kind.BOOL, ModernChatConfigBase::filters_AreaMuteEnabled),

        // ---- General ----
        GENERAL_ANCHOR_PM(Keys.general_AnchorPrivateChat, Kind.BOOL, ModernChatConfigBase::general_AnchorPrivateChat),
        GENERAL_ANCHOR_PM_OFFSET_X(Keys.general_AnchorPrivateChatOffsetX, Kind.INT, ModernChatConfigBase::general_AnchorPrivateChatOffsetX),
        GENERAL_ANCHOR_PM_OFFSET_Y(Keys.general_AnchorPrivateChatOffsetY, Kind.INT, ModernChatConfigBase::general_AnchorPrivateChatOffsetY),
        GENERAL_HELPER_NOTIFICATIONS(Keys.general_HelperNotifications, Kind.BOOL, ModernChatConfigBase::general_HelperNotifications),
        GENERAL_SHOW_REPORT_BUTTON(Keys.general_ShowReportButton, Kind.BOOL, ModernChatConfigBase::general_ShowReportButton),
        GENERAL_SHOW_SESSION_TIMER(Keys.general_ShowSessionTimer, Kind.BOOL, ModernChatConfigBase::general_ShowSessionTimer),
        GENERAL_PUBLIC_COLOR(Keys.general_PublicChatColor, Kind.COLOR, ModernChatConfigBase::general_PublicChatColor),
        GENERAL_FRIENDS_COLOR(Keys.general_FriendsChatColor, Kind.COLOR, ModernChatConfigBase::general_FriendsChatColor),
        GENERAL_CLAN_COLOR(Keys.general_ClanChatColor, Kind.COLOR, ModernChatConfigBase::general_ClanChatColor),
        GENERAL_PRIVATE_COLOR(Keys.general_PrivateChatColor, Kind.COLOR, ModernChatConfigBase::general_PrivateChatColor),
        GENERAL_SYSTEM_COLOR(Keys.general_SystemChatColor, Kind.COLOR, ModernChatConfigBase::general_SystemChatColor),
        GENERAL_WELCOME_COLOR(Keys.general_WelcomeChatColor, Kind.COLOR, ModernChatConfigBase::general_WelcomeChatColor),
        GENERAL_TRADE_COLOR(Keys.general_TradeChatColor, Kind.COLOR, ModernChatConfigBase::general_TradeChatColor),

        // ---- Toggle ----
        TOGGLE_ENABLED(Keys.featureToggle_Enabled, Kind.BOOL, ModernChatConfigBase::featureToggle_Enabled),
        TOGGLE_KEY(Keys.featureToggle_ToggleKey, Kind.KEYBIND, ModernChatConfigBase::featureToggle_ToggleKey),
        TOGGLE_EXTENDED_KEY(Keys.featureToggle_ExtendedToggleKey, ExtendedKeybind.class, ModernChatConfigBase::featureToggle_ExtendedToggleKey),
        TOGGLE_AUTOHIDE_ON_SEND(Keys.featureToggle_AutoHideOnSend, Kind.BOOL, ModernChatConfigBase::featureToggle_AutoHideOnSend),
        TOGGLE_ESCAPE_HIDES(Keys.featureToggle_EscapeHides, Kind.BOOL, ModernChatConfigBase::featureToggle_EscapeHides),
        TOGGLE_START_HIDDEN(Keys.featureToggle_StartHidden, Kind.BOOL, ModernChatConfigBase::featureToggle_StartHidden),
        TOGGLE_LOCK_CAMERA(Keys.featureToggle_LockCameraWhenVisible, Kind.BOOL, ModernChatConfigBase::featureToggle_LockCameraWhenVisible),

        // ---- Peek ----
        PEEK_ENABLED(Keys.featurePeek_Enabled, Kind.BOOL, ModernChatConfigBase::featurePeek_Enabled),
        PEEK_FOLLOW_CHATBOX(Keys.featurePeek_FollowChatBox, Kind.BOOL, ModernChatConfigBase::featurePeek_FollowChatBox),
        PEEK_SHOW_PM(Keys.featurePeek_ShowPrivateMessages, Kind.BOOL, ModernChatConfigBase::featurePeek_ShowPrivateMessages),
        PEEK_HIDE_SPLIT_PM(Keys.featurePeek_HideSplitPrivateMessages, Kind.BOOL, ModernChatConfigBase::featurePeek_HideSplitPrivateMessages),
        PEEK_SHOW_TIMESTAMP(Keys.featurePeek_ShowTimestamp, Kind.BOOL, ModernChatConfigBase::featurePeek_ShowTimestamp),
        PEEK_PREFIX_TYPES(Keys.featurePeek_PrefixChatTypes, Kind.BOOL, ModernChatConfigBase::featurePeek_PrefixChatTypes),
        PEEK_BG_COLOR(Keys.featurePeek_BackgroundColor, Kind.COLOR, ModernChatConfigBase::featurePeek_BackgroundColor),
        PEEK_BORDER_COLOR(Keys.featurePeek_BorderColor, Kind.COLOR, ModernChatConfigBase::featurePeek_BorderColor),
        PEEK_FONT_STYLE(Keys.featurePeek_FontStyle, FontStyle.class, ModernChatConfigBase::featurePeek_FontStyle),
        PEEK_FONT_SIZE(Keys.featurePeek_FontSize, Kind.INT, ModernChatConfigBase::featurePeek_FontSize),
        PEEK_TEXT_SHADOW(Keys.featurePeek_TextShadow, Kind.INT, ModernChatConfigBase::featurePeek_TextShadow),
        PEEK_OFFSET_X(Keys.featurePeek_OffsetX, Kind.INT, ModernChatConfigBase::featurePeek_OffsetX),
        PEEK_OFFSET_Y(Keys.featurePeek_OffsetY, Kind.INT, ModernChatConfigBase::featurePeek_OffsetY),
        PEEK_PADDING(Keys.featurePeek_Padding, Kind.INT, ModernChatConfigBase::featurePeek_Padding),
        PEEK_MARGIN_RIGHT(Keys.featurePeek_MarginRight, Kind.INT, ModernChatConfigBase::featurePeek_MarginRight),
        PEEK_MARGIN_BOTTOM(Keys.featurePeek_MarginBottom, Kind.INT, ModernChatConfigBase::featurePeek_MarginBottom),
        PEEK_FADE_ENABLED(Keys.featurePeek_FadeEnabled, Kind.BOOL, ModernChatConfigBase::featurePeek_FadeEnabled),
        PEEK_FADE_DELAY(Keys.featurePeek_FadeDelay, Kind.INT, ModernChatConfigBase::featurePeek_FadeDelay),
        PEEK_FADE_DURATION(Keys.featurePeek_FadeDuration, Kind.INT, ModernChatConfigBase::featurePeek_FadeDuration),
        PEEK_TIMESTAMP_COLOR(Keys.featurePeek_TimestampColor, Kind.COLOR, ModernChatConfigBase::featurePeek_TimestampColor),
        PEEK_TYPE_PREFIX_COLOR(Keys.featurePeek_TypePrefixColor, Kind.COLOR, ModernChatConfigBase::featurePeek_TypePrefixColor),

        // ---- Commands ----
        CMD_ENABLED(Keys.featureCommands_Enabled, Kind.BOOL, ModernChatConfigBase::featureCommands_Enabled),
        CMD_REPLY(Keys.featureCommands_ReplyEnabled, Kind.BOOL, ModernChatConfigBase::featureCommands_ReplyEnabled),
        CMD_WHISPER(Keys.featureCommands_WhisperEnabled, Kind.BOOL, ModernChatConfigBase::featureCommands_WhisperEnabled),
        CMD_PM(Keys.featureCommands_PrivateMessageEnabled, Kind.BOOL, ModernChatConfigBase::featureCommands_PrivateMessageEnabled),

        // ---- Message history ----
        HIST_ENABLED(Keys.featureMessageHistory_Enabled, Kind.BOOL, ModernChatConfigBase::featureMessageHistory_Enabled),
        HIST_MAX_ENTRIES(Keys.featureMessageHistory_MaxEntries, Kind.INT, ModernChatConfigBase::featureMessageHistory_MaxEntries),
        HIST_INCLUDE_CMDS(Keys.featureMessageHistory_IncludeCommands, Kind.BOOL, ModernChatConfigBase::featureMessageHistory_IncludeCommands),
        HIST_SKIP_DUPES(Keys.featureMessageHistory_SkipDuplicates, Kind.BOOL, ModernChatConfigBase::featureMessageHistory_SkipDuplicates),
        HIST_PREV_KEY(Keys.featureMessageHistory_PrevKey, Kind.KEYBIND, ModernChatConfigBase::featureMessageHistory_PrevKey),
        HIST_NEXT_KEY(Keys.featureMessageHistory_NextKey, Kind.KEYBIND, ModernChatConfigBase::featureMessageHistory_NextKey),

        // ---- Notify ----
        NOTIFY_ENABLED(Keys.featureNotify_Enabled, Kind.BOOL, ModernChatConfigBase::featureNotify_Enabled),
        NOTIFY_SOUND_ENABLED(Keys.featureNotify_SoundEnabled, Kind.BOOL, ModernChatConfigBase::featureNotify_SoundEnabled),
        NOTIFY_USE_RUNELITE_SOUND(Keys.featureNotify_UseRuneLiteSound, Kind.BOOL, ModernChatConfigBase::featureNotify_UseRuneLiteSound),
        NOTIFY_VOLUME_PERCENT(Keys.featureNotify_VolumePercent, Kind.INT, ModernChatConfigBase::featureNotify_VolumePercent),
        NOTIFY_MESSAGE_RECEIVED_SFX(Keys.featureNotify_MessageReceivedSfx, Sfx.class, ModernChatConfigBase::featureNotify_MessageReceivedSfx),
        NOTIFY_ON_PUBLIC_MESSAGE(Keys.featureNotify_OnPublicMessage, Kind.BOOL, ModernChatConfigBase::featureNotify_OnPublicMessage),
        NOTIFY_ON_PRIVATE_MESSAGE(Keys.featureNotify_OnPrivateMessage, Kind.BOOL, ModernChatConfigBase::featureNotify_OnPrivateMessage),
        NOTIFY_ON_FRIENDS_CHAT(Keys.featureNotify_OnFriendsChat, Kind.BOOL, ModernChatConfigBase::featureNotify_OnFriendsChat),
        NOTIFY_ON_CLAN(Keys.featureNotify_OnClan, Kind.BOOL, ModernChatConfigBase::featureNotify_OnClan);

        public final String key;
        public final Kind kind;
        public final Class<? extends Enum<?>> enumType; // only for ENUM kind
        private final Function<ModernChatConfigBase, ?> getter;

        // Bool/Int/Color/Keybind
        <T> Field(String key, Kind kind, Function<ModernChatConfigBase, T> getter) {
            this.key = key;
            this.kind = kind;
            this.getter = getter;
            this.enumType = null;
            if (kind == Kind.ENUM) throw new IllegalArgumentException("Use ENUM constructor");
        }

        // Enum
        <E extends Enum<E>> Field(String key, Class<E> enumType, Function<ModernChatConfigBase, E> getter) {
            this.key = key;
            this.kind = Kind.ENUM;
            this.enumType = enumType;
            this.getter = getter;
        }

        @SuppressWarnings("unchecked")
        public <T> T read(ModernChatConfigBase c) {
            return (T) getter.apply(c);
        }

        /** Write this field to ConfigManager. */
        public void apply(ConfigManager cm, String group, ModernChatConfigBase cfg) {
            cm.setConfiguration(group, key, read(cfg));
        }
    }

    boolean filters_Enabled();
    boolean filters_ChatFilterEnabled();
    boolean filters_AreaMuteEnabled();
    boolean general_AnchorPrivateChat();
    int general_AnchorPrivateChatOffsetX();
    int general_AnchorPrivateChatOffsetY();
    boolean general_HelperNotifications();
    boolean general_ShowReportButton();
    boolean general_ShowSessionTimer();
    Color general_PublicChatColor();
    Color general_FriendsChatColor();
    Color general_ClanChatColor();
    Color general_PrivateChatColor();
    Color general_SystemChatColor();
    Color general_WelcomeChatColor();
    Color general_TradeChatColor();

    Color featureRedesign_TimestampColor();
    Color featureRedesign_TypePrefixColor();

    Color featurePeek_TimestampColor();
    Color featurePeek_TypePrefixColor();
    Color featurePeek_SourceTabIndicatorColor();

    static JsonObject buildJsonFromConfig(ModernChatConfigBase c) {
        JsonObject j = new JsonObject();

        for (Field value : Field.values()) {
            if (value.key.equals(Field.FEATURE_EXAMPLE.key))
                continue;

            Object v = value.read(c);
            putJson(j, value.key, v);
        }
        return j;
    }

    // Writers
    static void setCfg(ConfigManager cm, String group, String key, Object value) {
        cm.setConfiguration(group, key, value);
    }

    static void putJson(JsonObject j, String key, Object v) {
        if (v == null) { j.add(key, JsonNull.INSTANCE); return; }
        if (v instanceof Boolean) { j.addProperty(key, (Boolean)v); return; }
        if (v instanceof Number)  { j.addProperty(key, (Number)v);  return; }
        if (v instanceof String)  { j.addProperty(key, (String)v);  return; }
        if (v instanceof Enum)    { j.addProperty(key, ((Enum<?>)v).name()); return; }
        if (v instanceof Color)   { j.add(key, toJsonColor((Color)v)); return; }
        if (v instanceof Keybind) { j.add(key, toJsonKeybind((Keybind)v)); return; }
        j.addProperty(key, String.valueOf(v));
    }

    static JsonObject toJsonColor(Color c)
    {
        JsonObject o = new JsonObject();
        o.addProperty("r", c.getRed());
        o.addProperty("g", c.getGreen());
        o.addProperty("b", c.getBlue());
        o.addProperty("a", c.getAlpha());
        return o;
    }

    static JsonObject toJsonKeybind(Keybind k)
    {
        JsonObject o = new JsonObject();
        o.addProperty("keyCode", k.getKeyCode());
        o.addProperty("modifiers", k.getModifiers());
        return o;
    }

    /**
     * Concrete ModernChatConfigBase backed by a case-insensitive JsonObject.
     * Each getter fetches a key equal to the method name (case-insensitive),
     * or falls back to the interface default.
     */
    final class ModernChatProfile implements ModernChatConfigBase {
        private final JsonObject obj;

        public ModernChatProfile(JsonObject src) {
            this.obj = Objects.requireNonNull(src);
        }

        // ModernChatConfigBase overrides using centralized Keys

        @Override public boolean featureExample_Enabled() { return true; }

        // Modern design
        @Override public boolean featureRedesign_Enabled() { return getBool(Keys.featureRedesign_Enabled, DEFAULTS.featureRedesign_Enabled()); }
        @Override public ChatMode featureRedesign_DefaultChatMode() { return getEnum(Keys.featureRedesign_DefaultChatMode, DEFAULTS.featureRedesign_DefaultChatMode(), ChatMode.class); }
        @Override public boolean featureRedesign_OpenTabOnIncomingPM() { return getBool(Keys.featureRedesign_OpenTabOnIncomingPM, DEFAULTS.featureRedesign_OpenTabOnIncomingPM()); }
        @Override public boolean featureRedesign_MessageContainer_PrefixChatType() { return getBool(Keys.featureRedesign_MessageContainer_PrefixChatType, DEFAULTS.featureRedesign_MessageContainer_PrefixChatType()); }
        @Override public boolean featureRedesign_MessageContainer_ShowTimestamp() { return getBool(Keys.featureRedesign_MessageContainer_ShowTimestamp, DEFAULTS.featureRedesign_MessageContainer_ShowTimestamp()); }
        @Override public boolean featureRedesign_Resizeable() { return getBool(Keys.featureRedesign_Resizeable, DEFAULTS.featureRedesign_Resizeable()); }
        @Override public boolean featureRedesign_MessageContainer_Scrollable() { return getBool(Keys.featureRedesign_MessageContainer_Scrollable, DEFAULTS.featureRedesign_MessageContainer_Scrollable()); }
        @Override public boolean featureRedesign_ClickOutsideToClose() { return getBool(Keys.featureRedesign_ClickOutsideToClose, DEFAULTS.featureRedesign_ClickOutsideToClose()); }
        @Override public boolean featureRedesign_ShowNotificationBadge() { return getBool(Keys.featureRedesign_ShowNotificationBadge, DEFAULTS.featureRedesign_ShowNotificationBadge()); }
        @Override public boolean featureRedesign_AllowClickThrough() { return getBool(Keys.featureRedesign_AllowClickThrough, DEFAULTS.featureRedesign_AllowClickThrough()); }
        @Override public boolean featureRedesign_AutoSelectPrivateTab() { return getBool(Keys.featureRedesign_AutoSelectPrivateTab, DEFAULTS.featureRedesign_AutoSelectPrivateTab()); }
        @Override public boolean featureRedesign_ShowNpc() { return getBool(Keys.featureRedesign_ShowNpc, DEFAULTS.featureRedesign_ShowNpc()); }
        @Override public boolean featureRedesign_ClassicMode() { return getBool(Keys.featureRedesign_ClassicMode, DEFAULTS.featureRedesign_ClassicMode()); }
        @Override public boolean featureRedesign_ClassicMode_AllowPmTabs() { return getBool(Keys.featureRedesign_ClassicMode_AllowPmTabs, DEFAULTS.featureRedesign_ClassicMode_AllowPmTabs()); }
        @Override public boolean featureRedesign_ClassicMode_ShowUnread() { return getBool(Keys.featureRedesign_ClassicMode_ShowUnread, DEFAULTS.featureRedesign_ClassicMode_ShowUnread()); }
        @Override public boolean featureRedesign_GameTabEnabled() { return getBool(Keys.featureRedesign_GameTabEnabled, DEFAULTS.featureRedesign_GameTabEnabled()); }
        @Override public boolean featureRedesign_TradeTabEnabled() { return getBool(Keys.featureRedesign_TradeTabEnabled, DEFAULTS.featureRedesign_TradeTabEnabled()); }

        // Style: fonts & sizes
        @Override public FontStyle featureRedesign_FontStyle() { return getEnum(Keys.featureRedesign_FontStyle, DEFAULTS.featureRedesign_FontStyle(), FontStyle.class); }
        @Override public int featureRedesign_InputFontSize() { return getInt(Keys.featureRedesign_InputFontSize, DEFAULTS.featureRedesign_InputFontSize()); }
        @Override public int featureRedesign_TabFontSize() { return getInt(Keys.featureRedesign_TabFontSize, DEFAULTS.featureRedesign_TabFontSize()); }
        @Override public int featureRedesign_TabBadgeFontSize() { return getInt(Keys.featureRedesign_TabBadgeFontSize, DEFAULTS.featureRedesign_TabBadgeFontSize()); }
        @Override public FontStyle featureRedesign_MessageContainer_LineFontStyle() { return getEnum(Keys.featureRedesign_MessageContainer_LineFontStyle, DEFAULTS.featureRedesign_MessageContainer_LineFontStyle(), FontStyle.class); }
        @Override public int featureRedesign_MessageContainer_LineFontSize() { return getInt(Keys.featureRedesign_MessageContainer_LineFontSize, DEFAULTS.featureRedesign_MessageContainer_LineFontSize()); }

        // Layout / metrics
        @Override public int featureRedesign_Padding() { return getInt(Keys.featureRedesign_Padding, DEFAULTS.featureRedesign_Padding()); }
        @Override public boolean featureRedesign_MessageContainer_DrawScrollbar() { return getBool(Keys.featureRedesign_MessageContainer_DrawScrollbar, DEFAULTS.featureRedesign_MessageContainer_DrawScrollbar()); }
        @Override public int featureRedesign_MessageContainer_ScrollbarWidth() { return getInt(Keys.featureRedesign_MessageContainer_ScrollbarWidth, DEFAULTS.featureRedesign_MessageContainer_ScrollbarWidth()); }

        // Colors (outer)
        @Override public Color featureRedesign_BackdropColor() { return getColor(Keys.featureRedesign_BackdropColor, DEFAULTS.featureRedesign_BackdropColor()); }
        @Override public Color featureRedesign_BorderColor() { return getColor(Keys.featureRedesign_BorderColor, DEFAULTS.featureRedesign_BorderColor()); }
        @Override public Color featureRedesign_InputPrefixColor() { return getColor(Keys.featureRedesign_InputPrefixColor, DEFAULTS.featureRedesign_InputPrefixColor()); }
        @Override public Color featureRedesign_InputBackgroundColor() { return getColor(Keys.featureRedesign_InputBackgroundColor, DEFAULTS.featureRedesign_InputBackgroundColor()); }
        @Override public Color featureRedesign_InputBorderColor() { return getColor(Keys.featureRedesign_InputBorderColor, DEFAULTS.featureRedesign_InputBorderColor()); }
        @Override public Color featureRedesign_InputShadowColor() { return getColor(Keys.featureRedesign_InputShadowColor, DEFAULTS.featureRedesign_InputShadowColor()); }
        @Override public Color featureRedesign_InputTextColor() { return getColor(Keys.featureRedesign_InputTextColor, DEFAULTS.featureRedesign_InputTextColor()); }
        @Override public Color featureRedesign_InputCaretColor() { return getColor(Keys.featureRedesign_InputCaretColor, DEFAULTS.featureRedesign_InputCaretColor()); }
        @Override public Color featureRedesign_TabBarBackgroundColor() { return getColor(Keys.featureRedesign_TabBarBackgroundColor, DEFAULTS.featureRedesign_TabBarBackgroundColor()); }
        @Override public Color featureRedesign_TabColor() { return getColor(Keys.featureRedesign_TabColor, DEFAULTS.featureRedesign_TabColor()); }
        @Override public Color featureRedesign_TabSelectedColor() { return getColor(Keys.featureRedesign_TabSelectedColor, DEFAULTS.featureRedesign_TabSelectedColor()); }
        @Override public Color featureRedesign_TabBorderColor() { return getColor(Keys.featureRedesign_TabBorderColor, DEFAULTS.featureRedesign_TabBorderColor()); }
        @Override public Color featureRedesign_TabBorderSelectedColor() { return getColor(Keys.featureRedesign_TabBorderSelectedColor, DEFAULTS.featureRedesign_TabBorderSelectedColor()); }
        @Override public Color featureRedesign_TabTextColor() { return getColor(Keys.featureRedesign_TabTextColor, DEFAULTS.featureRedesign_TabTextColor()); }
        @Override public Color featureRedesign_TabUnreadPulseToColor() { return getColor(Keys.featureRedesign_TabUnreadPulseToColor, DEFAULTS.featureRedesign_TabUnreadPulseToColor()); }
        @Override public Color featureRedesign_TabUnreadPulseFromColor() { return getColor(Keys.featureRedesign_TabUnreadPulseFromColor, DEFAULTS.featureRedesign_TabUnreadPulseFromColor()); }
        @Override public Color featureRedesign_TabNotificationColor() { return getColor(Keys.featureRedesign_TabNotificationColor, DEFAULTS.featureRedesign_TabNotificationColor()); }
        @Override public Color featureRedesign_TabNotificationTextColor() { return getColor(Keys.featureRedesign_TabNotificationTextColor, DEFAULTS.featureRedesign_TabNotificationTextColor()); }
        @Override public Color featureRedesign_TabCloseButtonColor() { return getColor(Keys.featureRedesign_TabCloseButtonColor, DEFAULTS.featureRedesign_TabCloseButtonColor()); }
        @Override public Color featureRedesign_TabCloseButtonTextColor() { return getColor(Keys.featureRedesign_TabCloseButtonTextColor, DEFAULTS.featureRedesign_TabCloseButtonTextColor()); }
        @Override public Color featureRedesign_FilterButtonColor() { return getColor(Keys.featureRedesign_FilterButtonColor, DEFAULTS.featureRedesign_FilterButtonColor()); }
        @Override public Color featureRedesign_ReportButtonColor() { return getColor(Keys.featureRedesign_ReportButtonColor, DEFAULTS.featureRedesign_ReportButtonColor()); }
        @Override public Color featureRedesign_ReportButtonTextColor() { return getColor(Keys.featureRedesign_ReportButtonTextColor, DEFAULTS.featureRedesign_ReportButtonTextColor()); }

        // Message container geometry
        @Override public int featureRedesign_MessageContainer_OffsetX() { return getInt(Keys.featureRedesign_MessageContainer_OffsetX, DEFAULTS.featureRedesign_MessageContainer_OffsetX()); }
        @Override public int featureRedesign_MessageContainer_OffsetY() { return getInt(Keys.featureRedesign_MessageContainer_OffsetY, DEFAULTS.featureRedesign_MessageContainer_OffsetY()); }
        @Override public int featureRedesign_MessageContainer_Margin() { return getInt(Keys.featureRedesign_MessageContainer_Margin, DEFAULTS.featureRedesign_MessageContainer_Margin()); }
        @Override public int featureRedesign_MessageContainer_PaddingTop() { return getInt(Keys.featureRedesign_MessageContainer_PaddingTop, DEFAULTS.featureRedesign_MessageContainer_PaddingTop()); }
        @Override public int featureRedesign_MessageContainer_PaddingLeft() { return getInt(Keys.featureRedesign_MessageContainer_PaddingLeft, DEFAULTS.featureRedesign_MessageContainer_PaddingLeft()); }
        @Override public int featureRedesign_MessageContainer_PaddingBottom() { return getInt(Keys.featureRedesign_MessageContainer_PaddingBottom, DEFAULTS.featureRedesign_MessageContainer_PaddingBottom()); }
        @Override public int featureRedesign_MessageContainer_PaddingRight() { return getInt(Keys.featureRedesign_MessageContainer_PaddingRight, DEFAULTS.featureRedesign_MessageContainer_PaddingRight()); }
        @Override public int featureRedesign_MessageContainer_LineSpacing() { return getInt(Keys.featureRedesign_MessageContainer_LineSpacing, DEFAULTS.featureRedesign_MessageContainer_LineSpacing()); }
        @Override public int featureRedesign_MessageContainer_ScrollStep() { return getInt(Keys.featureRedesign_MessageContainer_ScrollStep, DEFAULTS.featureRedesign_MessageContainer_ScrollStep()); }
        @Override public int featureRedesign_MessageContainer_TextShadow() { return getInt(Keys.featureRedesign_MessageContainer_TextShadow, DEFAULTS.featureRedesign_MessageContainer_TextShadow()); }

        // Message container colors
        @Override public Color featureRedesign_MessageContainer_BackdropColor() { return getColor(Keys.featureRedesign_MessageContainer_BackdropColor, DEFAULTS.featureRedesign_MessageContainer_BackdropColor()); }
        @Override public Color featureRedesign_MessageContainer_BorderColor() { return getColor(Keys.featureRedesign_MessageContainer_BorderColor, DEFAULTS.featureRedesign_MessageContainer_BorderColor()); }
        @Override public Color featureRedesign_MessageContainer_ShadowColor() { return getColor(Keys.featureRedesign_MessageContainer_ShadowColor, DEFAULTS.featureRedesign_MessageContainer_ShadowColor()); }
        @Override public Color featureRedesign_MessageContainer_ScrollbarTrackColor() { return getColor(Keys.featureRedesign_MessageContainer_ScrollbarTrackColor, DEFAULTS.featureRedesign_MessageContainer_ScrollbarTrackColor()); }
        @Override public Color featureRedesign_MessageContainer_ScrollbarThumbColor() { return getColor(Keys.featureRedesign_MessageContainer_ScrollbarThumbColor, DEFAULTS.featureRedesign_MessageContainer_ScrollbarThumbColor()); }
        @Override public Color featureRedesign_TimestampColor() { return getColor(Keys.featureRedesign_TimestampColor, DEFAULTS.featureRedesign_TimestampColor()); }
        @Override public Color featureRedesign_TypePrefixColor() { return getColor(Keys.featureRedesign_TypePrefixColor, DEFAULTS.featureRedesign_TypePrefixColor()); }

        // Filters
        @Override public boolean filters_Enabled() { return getBool(Keys.filters_Enabled, DEFAULTS.filters_Enabled()); }
        @Override public boolean filters_ChatFilterEnabled() { return getBool(Keys.filters_ChatFilterEnabled, DEFAULTS.filters_ChatFilterEnabled()); }
        @Override public boolean filters_AreaMuteEnabled() { return getBool(Keys.filters_AreaMuteEnabled, DEFAULTS.filters_AreaMuteEnabled()); }

        // General colors & options
        @Override public boolean general_AnchorPrivateChat() { return getBool(Keys.general_AnchorPrivateChat, DEFAULTS.general_AnchorPrivateChat()); }
        @Override public int general_AnchorPrivateChatOffsetX() { return getInt(Keys.general_AnchorPrivateChatOffsetX, DEFAULTS.general_AnchorPrivateChatOffsetX()); }
        @Override public int general_AnchorPrivateChatOffsetY() { return getInt(Keys.general_AnchorPrivateChatOffsetY, DEFAULTS.general_AnchorPrivateChatOffsetY()); }
        @Override public boolean general_HelperNotifications() { return getBool(Keys.general_HelperNotifications, DEFAULTS.general_HelperNotifications()); }
        @Override public boolean general_ShowReportButton() { return getBool(Keys.general_ShowReportButton, DEFAULTS.general_ShowReportButton()); }
        @Override public boolean general_ShowSessionTimer() { return getBool(Keys.general_ShowSessionTimer, DEFAULTS.general_ShowSessionTimer()); }
        @Override public Color general_PublicChatColor() { return getColor(Keys.general_PublicChatColor, DEFAULTS.general_PublicChatColor()); }
        @Override public Color general_FriendsChatColor() { return getColor(Keys.general_FriendsChatColor, DEFAULTS.general_FriendsChatColor()); }
        @Override public Color general_ClanChatColor() { return getColor(Keys.general_ClanChatColor, DEFAULTS.general_ClanChatColor()); }
        @Override public Color general_PrivateChatColor() { return getColor(Keys.general_PrivateChatColor, DEFAULTS.general_PrivateChatColor()); }
        @Override public Color general_SystemChatColor() { return getColor(Keys.general_SystemChatColor, DEFAULTS.general_SystemChatColor()); }
        @Override public Color general_WelcomeChatColor() { return getColor(Keys.general_WelcomeChatColor, DEFAULTS.general_WelcomeChatColor()); }
        @Override public Color general_TradeChatColor() { return getColor(Keys.general_TradeChatColor, DEFAULTS.general_TradeChatColor()); }

        // Toggle feature
        @Override public boolean featureToggle_Enabled() { return getBool(Keys.featureToggle_Enabled, DEFAULTS.featureToggle_Enabled()); }
        @Override public Keybind featureToggle_ToggleKey() { return getKeybind(Keys.featureToggle_ToggleKey, DEFAULTS.featureToggle_ToggleKey()); }
        @Override public ExtendedKeybind featureToggle_ExtendedToggleKey() { return getEnum(Keys.featureToggle_ExtendedToggleKey, DEFAULTS.featureToggle_ExtendedToggleKey(), ExtendedKeybind.class); }
        @Override public boolean featureToggle_AutoHideOnSend() { return getBool(Keys.featureToggle_AutoHideOnSend, DEFAULTS.featureToggle_AutoHideOnSend()); }
        @Override public boolean featureToggle_EscapeHides() { return getBool(Keys.featureToggle_EscapeHides, DEFAULTS.featureToggle_EscapeHides()); }
        @Override public boolean featureToggle_StartHidden() { return getBool(Keys.featureToggle_StartHidden, DEFAULTS.featureToggle_StartHidden()); }
        @Override public boolean featureToggle_LockCameraWhenVisible() { return getBool(Keys.featureToggle_LockCameraWhenVisible, DEFAULTS.featureToggle_LockCameraWhenVisible()); }

        // Peek overlay
        @Override public boolean featurePeek_Enabled() { return getBool(Keys.featurePeek_Enabled, DEFAULTS.featurePeek_Enabled()); }
        @Override public boolean featurePeek_FollowChatBox() { return getBool(Keys.featurePeek_FollowChatBox, DEFAULTS.featurePeek_FollowChatBox()); }
        @Override public boolean featurePeek_ShowPrivateMessages() { return getBool(Keys.featurePeek_ShowPrivateMessages, DEFAULTS.featurePeek_ShowPrivateMessages()); }
        @Override public boolean featurePeek_HideSplitPrivateMessages() { return getBool(Keys.featurePeek_HideSplitPrivateMessages, DEFAULTS.featurePeek_HideSplitPrivateMessages()); }
        @Override public boolean featurePeek_ShowTimestamp() { return getBool(Keys.featurePeek_ShowTimestamp, DEFAULTS.featurePeek_ShowTimestamp()); }
        @Override public boolean featurePeek_PrefixChatTypes() { return getBool(Keys.featurePeek_PrefixChatTypes, DEFAULTS.featurePeek_PrefixChatTypes()); }
        @Override public Color featurePeek_BackgroundColor() { return getColor(Keys.featurePeek_BackgroundColor, DEFAULTS.featurePeek_BackgroundColor()); }
        @Override public Color featurePeek_BorderColor() { return getColor(Keys.featurePeek_BorderColor, DEFAULTS.featurePeek_BorderColor()); }
        @Override public FontStyle featurePeek_FontStyle() { return getEnum(Keys.featurePeek_FontStyle, DEFAULTS.featurePeek_FontStyle(), FontStyle.class); }
        @Override public int featurePeek_FontSize() { return getInt(Keys.featurePeek_FontSize, DEFAULTS.featurePeek_FontSize()); }
        @Override public int featurePeek_TextShadow() { return getInt(Keys.featurePeek_TextShadow, DEFAULTS.featurePeek_TextShadow()); }
        @Override public int featurePeek_OffsetX() { return getInt(Keys.featurePeek_OffsetX, DEFAULTS.featurePeek_OffsetX()); }
        @Override public int featurePeek_OffsetY() { return getInt(Keys.featurePeek_OffsetY, DEFAULTS.featurePeek_OffsetY()); }
        @Override public int featurePeek_Padding() { return getInt(Keys.featurePeek_Padding, DEFAULTS.featurePeek_Padding()); }
        @Override public int featurePeek_MarginRight() { return getInt(Keys.featurePeek_MarginRight, DEFAULTS.featurePeek_MarginRight()); }
        @Override public int featurePeek_MarginBottom() { return getInt(Keys.featurePeek_MarginBottom, DEFAULTS.featurePeek_MarginBottom()); }
        @Override public boolean featurePeek_FadeEnabled() { return getBool(Keys.featurePeek_FadeEnabled, DEFAULTS.featurePeek_FadeEnabled()); }
        @Override public int featurePeek_FadeDelay() { return getInt(Keys.featurePeek_FadeDelay, DEFAULTS.featurePeek_FadeDelay()); }
        @Override public int featurePeek_FadeDuration() { return getInt(Keys.featurePeek_FadeDuration, DEFAULTS.featurePeek_FadeDuration()); }
        @Override public String featurePeek_SourceTabKey() { return getString(Keys.featurePeek_SourceTabKey, DEFAULTS.featurePeek_SourceTabKey()); }
        @Override public boolean featurePeek_SuppressFadeAtGE() { return getBool(Keys.featurePeek_SuppressFadeAtGE, DEFAULTS.featurePeek_SuppressFadeAtGE()); }
        @Override public Color featurePeek_TimestampColor() { return getColor(Keys.featurePeek_TimestampColor, DEFAULTS.featurePeek_TimestampColor()); }
        @Override public Color featurePeek_TypePrefixColor() { return getColor(Keys.featurePeek_TypePrefixColor, DEFAULTS.featurePeek_TypePrefixColor()); }
        @Override public Color featurePeek_SourceTabIndicatorColor() { return getColor(Keys.featurePeek_SourceTabIndicatorColor, DEFAULTS.featurePeek_SourceTabIndicatorColor()); }

        // Commands
        @Override public boolean featureCommands_Enabled() { return getBool(Keys.featureCommands_Enabled, DEFAULTS.featureCommands_Enabled()); }
        @Override public boolean featureCommands_ReplyEnabled() { return getBool(Keys.featureCommands_ReplyEnabled, DEFAULTS.featureCommands_ReplyEnabled()); }
        @Override public boolean featureCommands_WhisperEnabled() { return getBool(Keys.featureCommands_WhisperEnabled, DEFAULTS.featureCommands_WhisperEnabled()); }
        @Override public boolean featureCommands_PrivateMessageEnabled() { return getBool(Keys.featureCommands_PrivateMessageEnabled, DEFAULTS.featureCommands_PrivateMessageEnabled()); }

        // Message history
        @Override public boolean featureMessageHistory_Enabled() { return getBool(Keys.featureMessageHistory_Enabled, DEFAULTS.featureMessageHistory_Enabled()); }
        @Override public int featureMessageHistory_MaxEntries() { return getInt(Keys.featureMessageHistory_MaxEntries, DEFAULTS.featureMessageHistory_MaxEntries()); }
        @Override public boolean featureMessageHistory_IncludeCommands() { return getBool(Keys.featureMessageHistory_IncludeCommands, DEFAULTS.featureMessageHistory_IncludeCommands()); }
        @Override public boolean featureMessageHistory_SkipDuplicates() { return getBool(Keys.featureMessageHistory_SkipDuplicates, DEFAULTS.featureMessageHistory_SkipDuplicates()); }
        @Override public Keybind featureMessageHistory_PrevKey() { return getKeybind(Keys.featureMessageHistory_PrevKey, DEFAULTS.featureMessageHistory_PrevKey()); }
        @Override public Keybind featureMessageHistory_NextKey() { return getKeybind(Keys.featureMessageHistory_NextKey, DEFAULTS.featureMessageHistory_NextKey()); }

        // Notify
        @Override public boolean featureNotify_Enabled() { return getBool(Keys.featureNotify_Enabled, DEFAULTS.featureNotify_Enabled()); }
        @Override public boolean featureNotify_SoundEnabled() { return getBool(Keys.featureNotify_SoundEnabled, DEFAULTS.featureNotify_SoundEnabled()); }
        @Override public boolean featureNotify_UseRuneLiteSound() { return getBool(Keys.featureNotify_UseRuneLiteSound, DEFAULTS.featureNotify_UseRuneLiteSound()); }
        @Override public int featureNotify_VolumePercent() { return getInt(Keys.featureNotify_VolumePercent, DEFAULTS.featureNotify_VolumePercent()); }
        @Override public Sfx featureNotify_MessageReceivedSfx() { return getEnum(Keys.featureNotify_MessageReceivedSfx, DEFAULTS.featureNotify_MessageReceivedSfx(), Sfx.class); }
        @Override public boolean featureNotify_OnPublicMessage() { return getBool(Keys.featureNotify_OnPublicMessage, DEFAULTS.featureNotify_OnPublicMessage()); }
        @Override public boolean featureNotify_OnPrivateMessage() { return getBool(Keys.featureNotify_OnPrivateMessage, DEFAULTS.featureNotify_OnPrivateMessage()); }
        @Override public boolean featureNotify_OnFriendsChat() { return getBool(Keys.featureNotify_OnFriendsChat, DEFAULTS.featureNotify_OnFriendsChat()); }
        @Override public boolean featureNotify_OnClan() { return getBool(Keys.featureNotify_OnClan, DEFAULTS.featureNotify_OnClan()); }

        public JsonElement get(String key) {
            String kl = key.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (e.getKey().toLowerCase(Locale.ROOT).equals(kl))
                    return e.getValue();
            }
            return null;
        }

        public boolean getBool(String key, boolean def) {
            JsonElement el = get(key);
            if (el == null || el.isJsonNull())
                return def;
            if (el.isJsonPrimitive()) {
                JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isBoolean())
                    return p.getAsBoolean();
                if (p.isNumber())
                    return p.getAsInt() != 0;
                if (p.isString()) {
                    String s = p.getAsString().trim();
                    return s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equals("1");
                }
            }
            return def;
        }

        public int getInt(String key, int def) {
            JsonElement el = get(key);
            if (el == null || el.isJsonNull())
                return def;
            try {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber())
                    return (int)Math.round(el.getAsDouble());
                return Integer.parseInt(el.getAsString().trim());
            } catch (Exception ignore) {
                return def;
            }
        }

        public String getString(String key, String def) {
            JsonElement el = get(key);
            if (el == null || el.isJsonNull())
                return def;
            try {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString())
                    return el.getAsString();
                return el.toString();
            } catch (Exception ignore) {
                return def;
            }
        }

        public Color getColor(String key, Color def) {
            JsonElement el = get(key);
            if (el == null || el.isJsonNull())
                return def;
            try {
                if (el.isJsonPrimitive()) {
                    JsonPrimitive p = el.getAsJsonPrimitive();
                    if (p.isNumber()) return new Color((int)Math.round(p.getAsDouble()), true);
                    if (p.isString()) {
                        String s = p.getAsString().trim();
                        if (s.startsWith("#")) {
                            String h = s.substring(1);
                            long v = Long.parseLong(h, 16);
                            if (h.length() == 6) return new Color((int)v | 0xFF000000, true);
                            if (h.length() == 8) return new Color((int)v, true);
                        }
                        if (s.toLowerCase(Locale.ROOT).startsWith("rgba")) {
                            String inner = s.substring(s.indexOf('(')+1, s.lastIndexOf(')'));
                            String[] parts = inner.split(",");
                            int r = Integer.parseInt(parts[0].trim());
                            int g = Integer.parseInt(parts[1].trim());
                            int b = Integer.parseInt(parts[2].trim());
                            float af = Float.parseFloat(parts[3].trim());
                            int a = (af <= 1f) ? Math.round(af * 255f) : (int)af;
                            return new Color(r,g,b,a);
                        }
                    }
                } else if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    int r = o.has("r") ? getIntFrom(o.get("r"), 255) : 255;
                    int g = o.has("g") ? getIntFrom(o.get("g"), 255) : 255;
                    int b = o.has("b") ? getIntFrom(o.get("b"), 255) : 255;
                    int a = o.has("a") ? getIntFrom(o.get("a"), 255) : 255;
                    return new Color(r,g,b,a);
                }
            } catch (Exception ignore) {}
            return def;
        }

        public static int getIntFrom(JsonElement el, int def) {
            if (el == null || el.isJsonNull()) return def;
            try {
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber())
                    return (int)Math.round(el.getAsDouble());
                return Integer.parseInt(el.getAsString().trim());
            } catch (Exception ignore) { return def; }
        }

        public <E extends Enum<E>> E getEnum(String key, E def, Class<E> type)
        {
            JsonElement el = get(key);
            if (el == null || el.isJsonNull())
                return def;

            try {
                if (el.isJsonPrimitive()) {
                    JsonPrimitive p = el.getAsJsonPrimitive();
                    if (p.isNumber()) {
                        int ord = (int) Math.round(p.getAsDouble());
                        E[] values = type.getEnumConstants();
                        return (ord >= 0 && ord < values.length) ? values[ord] : def;
                    }
                    if (p.isString()) {
                        String s = p.getAsString().trim();
                        // Support a few common user formats: "rune_reg", "Rune Reg", "rune-reg"
                        String canonical = s
                            .replace('-', '_')
                            .replace(' ', '_')
                            .toUpperCase(java.util.Locale.ROOT);
                        try {
                            return Enum.valueOf(type, canonical);
                        } catch (IllegalArgumentException ignored) {
                            // If someone wrote a friendly label, fall back
                            return def;
                        }
                    }
                }
            } catch (Exception ignored) { }
            return def;
        }

        public Keybind getKeybind(String key, Keybind def) {
            JsonElement el = get(key);
            if (el == null || el.isJsonNull())
                return def;
            try {
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    int keyCode = o.has("keyCode") ? getIntFrom(o.get("keyCode"), def.getKeyCode()) : def.getKeyCode();
                    int modifiers = o.has("modifiers") ? getIntFrom(o.get("modifiers"), def.getModifiers()) : def.getModifiers();
                    return new Keybind(keyCode, modifiers);
                }
                if (el.isJsonPrimitive()) {
                    String s = el.getAsString().trim();
                    if (s.isEmpty() || s.equalsIgnoreCase("none"))
                        return def;
                    Map<String,Integer> keys = new HashMap<>();
                    keys.put("ENTER", KeyEvent.VK_ENTER);
                    keys.put("ESCAPE", KeyEvent.VK_ESCAPE);
                    keys.put("SPACE", KeyEvent.VK_SPACE);
                    keys.put("PAGE_UP", KeyEvent.VK_PAGE_UP);
                    keys.put("PAGE_DOWN", KeyEvent.VK_PAGE_DOWN);
                    Integer vk = keys.get(s.toUpperCase(Locale.ROOT));
                    if (vk != null)
                        return new Keybind(vk, 0);
                }
            } catch (Exception ignore) {}
            return def;
        }
    }
}
