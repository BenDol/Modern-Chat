package com.modernchat.util;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.MessageLine;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.MenuAction;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.Widget;
import lombok.Value;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtil
{
    public static final AtomicBoolean LEGACY_CHAT_HIDDEN = new AtomicBoolean(false);
    public static final String MODERN_CHAT_TAG = "[ModernChat]";
    public static final String COMMAND_MODE_MESSAGE = "Command Mode (Modern chat will be restored once you send or cancel the command)";

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img=(\\d+)>");

    @Value
    public static class SenderReceiver {
        String senderName;
        String receiverName;
        int senderIconId; // -1 if none (first icon when multiple)
        List<Integer> senderIconIds; // all icons in order, empty if none
    }

    @Value
    public static class ChannelPrefix {
        ChatMode mode;
        boolean sticky; // sticky variants also set the persistent input channel
        String message;
    }

    // Vanilla chat-input channel aliases (single-slash word prefixes). Private message
    // aliases (/w, /pm, ...) are intentionally absent - CommandsChatFeature owns them.
    private static final Map<String, ChatMode> VANILLA_CHANNEL_PREFIX_ALIASES = buildVanillaChannelPrefixAliases();

    // Extra word aliases (issue #32). Vanilla sends these verbatim to the friends
    // channel, so they only apply when the extended-prefixes option is enabled.
    private static final Map<String, ChatMode> EXTENDED_CHANNEL_PREFIX_ALIASES = buildExtendedChannelPrefixAliases();

    private static Map<String, ChatMode> buildVanillaChannelPrefixAliases() {
        Map<String, ChatMode> aliases = new HashMap<>();
        aliases.put("p", ChatMode.PUBLIC);
        aliases.put("f", ChatMode.FRIENDS_CHAT);
        aliases.put("c", ChatMode.CLAN_MAIN);
        aliases.put("gc", ChatMode.CLAN_GUEST);
        aliases.put("g", ChatMode.CLAN_GIM);
        return aliases;
    }

    private static Map<String, ChatMode> buildExtendedChannelPrefixAliases() {
        Map<String, ChatMode> aliases = new HashMap<>();
        aliases.put("s", ChatMode.PUBLIC);
        aliases.put("say", ChatMode.PUBLIC);
        aliases.put("public", ChatMode.PUBLIC);
        aliases.put("cc", ChatMode.FRIENDS_CHAT);
        aliases.put("fc", ChatMode.FRIENDS_CHAT);
        aliases.put("clan", ChatMode.CLAN_MAIN);
        aliases.put("guest", ChatMode.CLAN_GUEST);
        aliases.put("gim", ChatMode.CLAN_GIM);
        aliases.put("group", ChatMode.CLAN_GIM);
        return aliases;
    }

    /** Parses with vanilla aliases only; see {@link #parseChannelPrefix(String, boolean)}. */
    public static @Nullable ChannelPrefix parseChannelPrefix(String text) {
        return parseChannelPrefix(text, false);
    }

    /**
     * Parse a vanilla chat-input channel prefix (e.g. "/p hi", "//hi", "///@ hi", "/@c").
     * Only a '/' at position 0 counts (leading whitespace makes it a normal message).
     * Slash runs (//, ///, ////) may be followed directly by text; word aliases must be
     * followed by a space or end the input. A '@' after a slash run is a sticky marker
     * only when it ends the input or precedes a space; otherwise it is message text.
     * Bare "/@" is the vanilla shorthand for sticky clan ("/@c"). Anything else after a
     * single slash falls through to the friends channel, mirroring vanilla. Returns null
     * when the text is not a channel prefix and should be sent as-is.
     *
     * @param extendedAliases also accept the non-vanilla word aliases (/say, /clan, ...)
     */
    public static @Nullable ChannelPrefix parseChannelPrefix(String text, boolean extendedAliases) {
        if (text == null || text.isEmpty() || text.charAt(0) != '/')
            return null;

        int slashes = 0;
        while (slashes < text.length() && text.charAt(slashes) == '/')
            slashes++;

        if (slashes >= 2) {
            if (slashes > 4)
                return null;

            ChatMode mode = slashes == 2 ? ChatMode.CLAN_MAIN
                : slashes == 3 ? ChatMode.CLAN_GUEST
                : ChatMode.CLAN_GIM;

            int start = slashes;
            boolean sticky = start < text.length() && text.charAt(start) == '@'
                && (start + 1 >= text.length() || text.charAt(start + 1) == ' ');
            if (sticky)
                start++;

            return new ChannelPrefix(mode, sticky, stripSeparatorSpace(text.substring(start)));
        }

        int space = text.indexOf(' ');
        String token = (space < 0 ? text.substring(1) : text.substring(1, space)).toLowerCase(Locale.ROOT);
        boolean sticky = token.startsWith("@");
        String word = sticky ? token.substring(1) : token;

        if (sticky && word.isEmpty()) // bare "/@" means sticky clan, same as "/@c"
            return new ChannelPrefix(ChatMode.CLAN_MAIN, true, space < 0 ? "" : text.substring(space + 1));

        ChatMode mode = word.isEmpty() ? null : lookupChannelAlias(word, extendedAliases);
        if (mode != null)
            return new ChannelPrefix(mode, sticky, space < 0 ? "" : text.substring(space + 1));

        if (sticky)
            return null; // "/@unknown" is not an alias, send as a plain message

        // Bare "/" routes everything after it to the friends channel (vanilla behavior)
        return new ChannelPrefix(ChatMode.FRIENDS_CHAT, false, stripSeparatorSpace(text.substring(1)));
    }

    private static @Nullable ChatMode lookupChannelAlias(String word, boolean extendedAliases) {
        ChatMode mode = VANILLA_CHANNEL_PREFIX_ALIASES.get(word);
        if (mode == null && extendedAliases)
            mode = EXTENDED_CHANNEL_PREFIX_ALIASES.get(word);
        return mode;
    }

    private static String stripSeparatorSpace(String s) {
        return s.startsWith(" ") ? s.substring(1) : s;
    }

    public static int extractIconId(@Nullable String name) {
        if (name == null || name.isEmpty()) return -1;
        Matcher m = IMG_TAG_PATTERN.matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static List<Integer> extractIconIds(@Nullable String name) {
        if (name == null || name.isEmpty()) return List.of();
        List<Integer> ids = new ArrayList<>();
        Matcher m = IMG_TAG_PATTERN.matcher(name);
        while (m.find()) ids.add(Integer.parseInt(m.group(1)));
        return ids;
    }

    public static boolean isPrivateMessage(ChatMessageType t) {
        return t == ChatMessageType.PRIVATECHAT
            || t == ChatMessageType.PRIVATECHATOUT
            || t == ChatMessageType.MODPRIVATECHAT;
    }

    public static boolean isPlayerType(MenuAction t) {
        switch (t) {
            case PLAYER_FIRST_OPTION:
            case PLAYER_SECOND_OPTION:
            case PLAYER_THIRD_OPTION:
            case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION:
            case PLAYER_SIXTH_OPTION:
            case PLAYER_SEVENTH_OPTION:
            case PLAYER_EIGHTH_OPTION:
            case RUNELITE_PLAYER: // when a RL player-targeted entry is present
                return true;
            default:
                return false;
        }
    }

    public static ChatMode toChatMode(ChatMessageType t) {
        switch (t) {
            case PRIVATECHAT:
            case PRIVATECHATOUT:
            case MODPRIVATECHAT:
            case FRIENDNOTIFICATION:
                return ChatMode.PRIVATE;
            case CLAN_CHAT:
            case CLAN_MESSAGE:
                return ChatMode.CLAN_MAIN;
            case CLAN_GUEST_CHAT:
            case CLAN_GUEST_MESSAGE:
                return ChatMode.CLAN_GUEST;
            case CLAN_GIM_CHAT:
            case CLAN_GIM_FORM_GROUP:
            case CLAN_GIM_MESSAGE:
            case CLAN_GIM_GROUP_WITH:
                return ChatMode.CLAN_GIM;
            case FRIENDSCHAT:
            case FRIENDSCHATNOTIFICATION:
                return ChatMode.FRIENDS_CHAT;
            default:
                return ChatMode.PUBLIC;
        }
    }

    public static String extractNameFromMessage(String line) {
        return extractNameFromMessage(line, null);
    }

    public static String extractNameFromMessage(String line, String orDefault) {
        if (line == null || line.isEmpty()) {
            return orDefault;
        }

        int idx = line.indexOf(':');
        if (idx < 0) {
            return orDefault; // No colon found, cannot extract name
        }

        String name = line.substring(0, idx).trim();
        if (name.isEmpty()) {
            return orDefault; // Empty name
        }

        return name;
    }

    public static List<String> chunk(String s, int limit) {
        if (limit <= 0 || s == null || s.isEmpty()) return List.of(s == null ? "" : s);
        List<String> out = new ArrayList<>((s.length() + limit - 1) / limit);
        Matcher m = Pattern.compile("(?s).+" + limit + "}").matcher(s);
        while (m.find()) out.add(m.group());
        return out;
    }

    public static Optional<String> getClipboardText() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable t = cb.getContents(null);
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return Optional.of((String) t.getTransferData(DataFlavor.stringFlavor));
            }
        } catch (Exception ex) {
            // UnsupportedFlavorException | IOException | IllegalStateException (clipboard busy)
        }
        return Optional.empty();
    }

    public static boolean isClanMessage(ChatMessageType type) {
        return type == ChatMessageType.CLAN_CHAT
            || type == ChatMessageType.CLAN_MESSAGE
            || type == ChatMessageType.CLAN_GUEST_CHAT
            || type == ChatMessageType.CLAN_GUEST_MESSAGE
            || type == ChatMessageType.CLAN_GIM_CHAT
            || type == ChatMessageType.CLAN_GIM_FORM_GROUP
            || type == ChatMessageType.CLAN_GIM_MESSAGE
            || type == ChatMessageType.CLAN_GIM_GROUP_WITH;
    }

    public static boolean isFriendsChatMessage(ChatMessageType type) {
        return type == ChatMessageType.FRIENDSCHAT
            || type == ChatMessageType.FRIENDSCHATNOTIFICATION;
    }

    public static SenderReceiver getSenderAndReceiver(ChatMessage msg, String localPlayerName) {
        String receiverName = null;
        String senderName = msg.getSender();
        String name = msg.getName();
        ChatMessageType type = msg.getType();

        // Extract icon IDs from the raw name *before* stripping tags
        List<Integer> senderIconIds = List.of();

        if (type == ChatMessageType.PRIVATECHATOUT) {
            // For outgoing PMs, the "name" is the receiver - no sender icon
            receiverName = name != null ? Text.removeTags(name) : null;
            senderName = "You";
        }
        else if (type == ChatMessageType.PRIVATECHAT) {
            // For incoming PMs, the "name" is the sender - extract their icons
            senderIconIds = extractIconIds(name);
            receiverName = localPlayerName;
            senderName = name != null ? Text.removeTags(name) : null;
        }
        else if (ChatUtil.isClanMessage(type) || ChatUtil.isFriendsChatMessage(type)) {
            senderIconIds = extractIconIds(name);
            senderName = name != null ? Text.removeTags(name) : null;
        }
        else if (senderName == null) {
            senderIconIds = extractIconIds(name);
            senderName = name != null ? Text.removeTags(name) : null;
        }
        else {
            senderIconIds = extractIconIds(senderName);
            senderName = Text.removeTags(senderName);
        }

        if (receiverName == null) {
            receiverName = localPlayerName;
        }

        int senderIconId = senderIconIds.isEmpty() ? -1 : senderIconIds.get(0);
        return new SenderReceiver(senderName, receiverName, senderIconId, senderIconIds);
    }

    public static String getCustomPrefix(ChatMessage msg) {
        ChatMessageType type = msg.getType();
        if (type == ChatMessageType.PRIVATECHATOUT) {
            return "";
        }
        else if (type == ChatMessageType.PRIVATECHAT) {
            return "";
        }
        else if (ChatUtil.isClanMessage(type) || ChatUtil.isFriendsChatMessage(type)) {
            return msg.getSender() != null ? "(" + msg.getSender() + ") " : "";
        }
        return "";
    }

    /**
     * Resolve the clan/friends chat rank icon for a message sender, or -1 if none.
     * The game injects rank icons at widget-build time, so they never arrive in the
     * ChatMessage name - we have to look the rank up from the relevant channel.
     */
    public static int getRankIconId(ChatMessageType type, @Nullable String senderName, Client client, ChatIconManager chatIconManager) {
        // Only friends chat and clan messages carry rank icons - bail out on cheap
        // enum compares before doing any name normalization work
        boolean friendsChat = isFriendsChatMessage(type);
        boolean clanChat = isClanMessage(type);
        if (!friendsChat && !clanChat)
            return -1;

        if (StringUtil.isNullOrEmpty(senderName))
            return -1;

        // Chat names carry non-breaking spaces - normalize before member lookups
        String cleanName = Text.toJagexName(senderName);
        if (cleanName.isEmpty())
            return -1;

        if (friendsChat) {
            FriendsChatManager friendsChatManager = client.getFriendsChatManager();
            if (friendsChatManager == null)
                return -1;
            FriendsChatMember member = friendsChatManager.findByName(cleanName);
            if (member == null)
                return -1;
            FriendsChatRank rank = member.getRank();
            if (rank == null || rank == FriendsChatRank.UNRANKED)
                return -1;
            return chatIconManager.getIconNumber(rank);
        }

        ClanChannel channel;
        ClanSettings settings;
        switch (toChatMode(type)) {
            case CLAN_MAIN:
                channel = client.getClanChannel();
                settings = client.getClanSettings();
                break;
            case CLAN_GUEST:
                channel = client.getGuestClanChannel();
                settings = client.getGuestClanSettings();
                break;
            case CLAN_GIM:
                channel = client.getClanChannel(ClanID.GROUP_IRONMAN);
                settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
                break;
            default:
                return -1;
        }
        if (channel == null || settings == null)
            return -1;
        ClanChannelMember member = channel.findMember(cleanName);
        if (member == null)
            return -1;
        ClanRank rank = member.getRank();
        if (rank == null)
            return -1;
        ClanTitle title = settings.titleForRank(rank);
        if (title == null)
            return -1;
        return chatIconManager.getIconNumber(title);
    }

    public static int getModImageId(String msg) {
        if (msg == null || msg.isEmpty())
            return -1;
        String idStr = msg.replace("IMG:", "");
        try {
            return Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            // Ignore and return default
        }
        return -1; // Default icon ID if not found
    }

    public static @Nullable MessageLine createMessageLine(ChatMessage e, Client client) {
        return createMessageLine(e, client, true);
    }

    public static @Nullable MessageLine createMessageLine(ChatMessage e, Client client, boolean requireLocalPlayer) {
        return createMessageLine(e, client, requireLocalPlayer, null);
    }

    /** Pattern to detect ChatFilterPlugin's collapse suffix like " (2)", " (15)" etc. */
    private static final Pattern COLLAPSE_PATTERN = Pattern.compile(" \\(\\d+\\)$");

    /** Returns the trailing collapse suffix (e.g. " (2)") of the given text, or null when absent. */
    public static @Nullable String extractCollapseSuffix(@Nullable String s) {
        if (s == null || s.isEmpty()) return null;
        Matcher m = COLLAPSE_PATTERN.matcher(s);
        return m.find() ? m.group() : null;
    }

    /** Message types that ChatFilterPlugin considers collapsible (game message types) */
    private static final Set<ChatMessageType> COLLAPSIBLE_MESSAGETYPES = EnumSet.of(
        ChatMessageType.ENGINE,
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.ITEM_EXAMINE,
        ChatMessageType.NPC_EXAMINE,
        ChatMessageType.OBJECT_EXAMINE,
        ChatMessageType.SPAM,
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.NPC_SAY
    );

    public static @Nullable MessageLine createMessageLine(ChatMessage e, Client client, boolean requireLocalPlayer, @Nullable String filteredMessage) {
        return createMessageLine(e, client, requireLocalPlayer, filteredMessage, null);
    }

    /**
     * Create a MessageLine from a ChatMessage, optionally using a filtered message text.
     *
     * @param e the chat message event
     * @param client the game client
     * @param requireLocalPlayer whether to require local player info
     * @param filteredMessage optional filtered message text (from chat filter plugins), or null to use original
     * @param chatIconManager optional icon manager used to render clan/friends chat rank icons, or null to skip
     */
    public static @Nullable MessageLine createMessageLine(ChatMessage e, Client client, boolean requireLocalPlayer,
                                                          @Nullable String filteredMessage, @Nullable ChatIconManager chatIconManager) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null && requireLocalPlayer)
            return null;

        String localPlayerName = "";
        if (localPlayer != null) {
            localPlayerName = localPlayer.getName();
            if (StringUtil.isNullOrEmpty(localPlayerName) && requireLocalPlayer)
                return null;
        }

        // Use local system time so timestamps reflect the player's clock, not the server's
        long timestamp = System.currentTimeMillis();

        SenderReceiver senderReceiver = ChatUtil.getSenderAndReceiver(e, localPlayerName);

        ChatMessageType type = e.getType();
        String originalMsg = e.getMessage();
        // Use filtered message if provided, otherwise use original
        String msg = filteredMessage != null ? filteredMessage : originalMsg;
        String[] params = msg.split("\\|", 3);
        String receiverName = senderReceiver.getReceiverName();
        String senderName = senderReceiver.getSenderName();
        int senderIconId = senderReceiver.getSenderIconId();
        String prefix = ChatUtil.getCustomPrefix(e);

        if (type == ChatMessageType.DIALOG) {
            senderName = ColorUtil.wrapWithColorTag(params.length > 1 ? params[params.length - 2] : senderName, Color.CYAN);
        }

        ChatMessageBuilder senderBuilder = new ChatMessageBuilder();

        if (!StringUtil.isNullOrEmpty(senderName)) {
            // Rank icon (clan/friends chat) renders first, before account-type icons.
            if (chatIconManager != null) {
                int rankIconId = getRankIconId(type, senderName, client, chatIconManager);
                if (rankIconId > -1)
                    senderBuilder.img(rankIconId);
            }
            // Render account-type icons (ironman, leagues, etc.) before the sender name.
            // senderName itself stays tag-free - it is used for tab keys and target names.
            for (int iconId : senderReceiver.getSenderIconIds()) {
                senderBuilder.img(iconId);
            }
            senderBuilder.append(senderName, false).append(": ");
        }

        String senderPrefix = senderBuilder.build();
        String text = composeLineText(senderPrefix, msg, type);

        // Generate duplicate key from name + original message (for collapse detection)
        String duplicateKey = e.getName() + ":" + originalMsg;

        // Check if the filtered message has a collapse suffix like " (2)"
        // Only detect collapse for COLLAPSIBLE_MESSAGETYPES (game message types)
        boolean collapsed = filteredMessage != null && originalMsg != null
            && COLLAPSIBLE_MESSAGETYPES.contains(type)
            && COLLAPSE_PATTERN.matcher(filteredMessage).find()
            && !originalMsg.equals(filteredMessage); // only if filtered differs from original

        // Track the backing MessageNode so lines can be rebuilt when other
        // plugins edit the node after the fact (e.g. Chat Commands, issue #20)
        MessageNode node = e.getMessageNode();
        int messageNodeId = node != null ? node.getId() : -1;
        String nodeValueSnapshot = null;
        if (node != null) {
            String rlFormat = node.getRuneLiteFormatMessage();
            nodeValueSnapshot = rlFormat != null ? rlFormat : node.getValue();
        }

        return new MessageLine(text, type, timestamp, senderName, receiverName, prefix, duplicateKey, collapsed,
            senderIconId, messageNodeId, nodeValueSnapshot, senderPrefix);
    }

    /**
     * Compose the rendered line text from an already-composed sender prefix (icons + name + ": ",
     * may be null or empty) and the raw message body. Splits off mod icon params ("IMG:x|message")
     * the same way message capture does, so refreshed lines render identically to captured ones.
     * Broadcast values instead carry a trailing newspost url code ("display text|c") which is
     * dropped from the rendered text.
     */
    public static String composeLineText(@Nullable String senderPrefix, @Nullable String msg, @Nullable ChatMessageType type) {
        if (msg == null)
            msg = "";

        ChatMessageBuilder builder = new ChatMessageBuilder();
        if (!StringUtil.isNullOrEmpty(senderPrefix)) {
            builder.append(senderPrefix, false);
        }

        if (type == ChatMessageType.BROADCAST) {
            String display = getBroadcastDisplayText(msg);
            if (display != null) {
                builder.append(display, false);
                return builder.build();
            }
        }

        String message = msg;
        String[] params = msg.split("\\|", 3);
        if (params.length > 1) {
            int icon = ChatUtil.getModImageId(params[0]);
            if (icon != -1) {
                builder.img(icon);
            }

            // message should always be last
            message = params[params.length - 1];
        }

        builder.append(message, false);
        return builder.build();
    }

    /**
     * Clickable broadcast values are "display text|c" (cs2 proc chat_broadcast_parseurl):
     * the char after the trailing pipe is looked up in this alphabet and the resulting index
     * keys the newspost URL game enum (BROADCAST_URL_ENUM_ID, which has no EnumID constant).
     * We parse end-anchored on purpose - see getBroadcastUrlIndex.
     */
    public static final String BROADCAST_URL_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    public static final int BROADCAST_URL_ENUM_ID = 63;

    /**
     * Returns the newspost url-code index of a broadcast value, or -1 when it carries none.
     * The code is a single trailing char after a '|' ("display text|c"); anchoring on the last
     * pipe keeps display texts that themselves contain a '|' intact.
     */
    public static int getBroadcastUrlIndex(@Nullable String value) {
        if (value == null || value.length() < 2 || value.charAt(value.length() - 2) != '|')
            return -1;
        return BROADCAST_URL_ALPHABET.indexOf(value.charAt(value.length() - 1));
    }

    /** Returns the display text of a broadcast value carrying a url code, or null otherwise. */
    public static @Nullable String getBroadcastDisplayText(@Nullable String value) {
        int index = getBroadcastUrlIndex(value);
        return index >= 0 ? value.substring(0, value.length() - 2) : null;
    }

    /**
     * Translate RuneLite-format color tags (<colNORMAL>, <colHIGHLIGHT>) - as emitted by
     * ChatMessageBuilder and RuneLite's Chat Commands plugin - into concrete <col=RRGGBB>
     * tags that the rich-text parser understands.
     */
    public static String translateRuneLiteColorTags(String s, Color normal, Color highlight) {
        if (s == null || s.indexOf('<') < 0)
            return s;

        String out = s;
        if (normal != null)
            out = out.replace("<col" + ChatColorType.NORMAL.name() + ">", ColorUtil.colorTag(normal));
        if (highlight != null)
            out = out.replace("<col" + ChatColorType.HIGHLIGHT.name() + ">", ColorUtil.colorTag(highlight));
        return out;
    }

    /** Chat lines the client rewrites in place each tick start with this (issue #14) */
    public static final String SYSTEM_UPDATE_PREFIX = "System update";

    public static boolean isLiveUpdatingText(@Nullable String s) {
        return s != null && Text.removeTags(s).startsWith(SYSTEM_UPDATE_PREFIX);
    }

    public static String getPrefix(ChatMessageType type) {
        String prefix = "";
        switch (type) {
            case PUBLICCHAT:
            case PRIVATECHAT:
            case PRIVATECHATOUT:
            case FRIENDSCHAT:
            case FRIENDSCHATNOTIFICATION:
            case FRIENDNOTIFICATION:
            case AUTOTYPER:
                break;
            case CLAN_CHAT:
            case CLAN_GIM_FORM_GROUP:
            case CLAN_GUEST_CHAT:
            case CLAN_GUEST_MESSAGE:
                prefix = "[Clan] ";
                break;
            case NPC_SAY:
            case DIALOG:
                prefix = "[NPC] ";
                break;
            case TRADE_SENT:
            case TRADEREQ:
                prefix = "[Trade] ";
                break;
            case SPAM:
                prefix = "[Spam] ";
                break;
            default:
                prefix = "[System] ";
        }
        return prefix;
    }

    public static void setChatHidden(Widget chat, boolean hidden) {
        chat.setHidden(hidden);
        LEGACY_CHAT_HIDDEN.set(hidden);
    }

    public static boolean isNpcMessage(ChatMessage e) {
        return isNpcMessage(e.getType());
    }

    public static boolean isNpcMessage(ChatMessageType type) {
        return type == ChatMessageType.NPC_SAY || type == ChatMessageType.DIALOG;
    }

    public static boolean isSpamMessage(ChatMessageType type) {
        return type == ChatMessageType.SPAM;
    }

    public static boolean isModernChatMessage(String message) {
        return message != null && Text.removeTags(message).startsWith(MODERN_CHAT_TAG);
    }

    public static boolean isIgnoredMessage(String line, ChatMessageType type) {
        return line.endsWith(ChatUtil.COMMAND_MODE_MESSAGE) && ChatUtil.isModernChatMessage(line);
    }
}