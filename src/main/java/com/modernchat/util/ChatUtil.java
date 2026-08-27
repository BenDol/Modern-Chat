package com.modernchat.util;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.MessageLine;
import com.modernchat.draw.RichLine;
import com.modernchat.draw.TextSegment;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.Widget;
import lombok.Value;
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
import java.util.List;
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

    public static int extractIconId(@Nullable String name) {
        if (name == null || name.isEmpty()) return -1;
        Matcher m = IMG_TAG_PATTERN.matcher(name);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static List<Integer> extractIconIds(@Nullable String name) {
        if (name == null || name.isEmpty()) return List.of();
        List<Integer> ids = null;
        Matcher m = IMG_TAG_PATTERN.matcher(name);
        while (m.find()) {
            if (ids == null) ids = new ArrayList<>();
            ids.add(Integer.parseInt(m.group(1)));
        }
        return ids == null ? List.of() : ids;
    }

    /**
     * Strips formatting tags the way the Force Recolor plugin's removeMostTags does:
     * every tag is removed EXCEPT &lt;lt&gt;, &lt;gt&gt; and &lt;img=N&gt; (case-sensitive,
     * digits only), and NBSP (U+00A0) is then normalized to a regular space - the original
     * plugin appends that replace so multi-word patterns still match NBSP-separated text.
     * Force Recolor patterns are built from Text.escapeJagex'd text, so the surviving
     * lt/gt entities line up with the escaped pattern text when matching.
     */
    public static String removeMostTags(@Nullable String s) {
        String stripped = stripTags(s, false);
        if (stripped == null || stripped.isEmpty()) return stripped;
        return stripped.replace('\u00A0', ' ');
    }

    /**
     * Strips only color tags (&lt;col=...&gt;, &lt;colNORMAL&gt;, &lt;/col&gt;) so an outer
     * color wrapper actually wins; all other tags (&lt;img=N&gt;, &lt;lt&gt;, &lt;gt&gt;,
     * &lt;br&gt;, ...) are preserved.
     */
    public static String removeColorTags(@Nullable String s) {
        return stripTags(s, true);
    }

    private static String stripTags(@Nullable String s, boolean colorTagsOnly) {
        if (s == null || s.isEmpty() || s.indexOf('<') < 0) return s;

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            char ch = s.charAt(i);
            if (ch != '<') {
                out.append(ch);
                i++;
                continue;
            }

            int j = s.indexOf('>', i + 1);
            if (j < 0) {
                // Unterminated tag: keep the remainder as-is. The original Force Recolor
                // plugin strips tags with a regex that requires a closing '>', so
                // unterminated text is never consumed as a tag there either.
                out.append(s, i, s.length());
                break;
            }

            boolean keep;
            if (colorTagsOnly) {
                // Match parseRich's case-insensitive color tag detection:
                // <col=HEX>, <colHEX>, <colNORMAL>, </col>
                keep = !(s.regionMatches(true, i + 1, "col", 0, 3)
                    || (j - i - 1 == 4 && s.regionMatches(true, i + 1, "/col", 0, 4)));
            } else {
                keep = isKeptMatchTag(s, i + 1, j);
            }
            if (keep) {
                out.append(s, i, j + 1);
            }
            i = j + 1;
        }
        return out.toString();
    }

    /**
     * Case-sensitive keep rule matching the original Force Recolor plugin's
     * removeMostTags: only &lt;lt&gt;, &lt;gt&gt; and &lt;img=N&gt; (N = digits) survive.
     * The tag body spans {@code [start, end)} of {@code s} (angle brackets excluded).
     */
    private static boolean isKeptMatchTag(String s, int start, int end) {
        int len = end - start;
        if (len == 2) {
            return s.regionMatches(start, "lt", 0, 2) || s.regionMatches(start, "gt", 0, 2);
        }
        if (len > 4 && s.regionMatches(start, "img=", 0, 4)) {
            for (int k = start + 4; k < end; k++) {
                char c = s.charAt(k);
                if (c < '0' || c > '9') return false;
            }
            return true;
        }
        return false;
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
        return new SenderReceiver(senderName, receiverName, senderIconId, List.copyOf(senderIconIds));
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

    /**
     * Create a MessageLine from a ChatMessage, optionally using a filtered message text.
     *
     * @param e the chat message event
     * @param client the game client
     * @param requireLocalPlayer whether to require local player info
     * @param filteredMessage optional filtered message text (from chat filter plugins), or null to use original
     */
    public static @Nullable MessageLine createMessageLine(ChatMessage e, Client client, boolean requireLocalPlayer, @Nullable String filteredMessage) {
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

        ChatMessageBuilder builder = new ChatMessageBuilder();

        if (!StringUtil.isNullOrEmpty(senderName)) {
            // Render account-type icons (ironman, leagues, etc.) before the sender name.
            // senderName itself stays tag-free - it is used for tab keys and target names.
            for (int iconId : senderReceiver.getSenderIconIds()) {
                builder.img(iconId);
            }
            builder.append(senderName, false).append(": ");
        }

        String message = msg;
        if (params.length > 1) {
            int icon = ChatUtil.getModImageId(params[0]);
            if (icon != -1) {
                builder.img(icon);
            }

            // message should always be last
            message = params[params.length - 1];
        }

        builder.append(message, false);

        // Generate duplicate key from name + original message (for collapse detection)
        String duplicateKey = e.getName() + ":" + originalMsg;

        // Check if the filtered message has a collapse suffix like " (2)"
        // Only detect collapse for COLLAPSIBLE_MESSAGETYPES (game message types)
        boolean collapsed = filteredMessage != null && originalMsg != null
            && COLLAPSIBLE_MESSAGETYPES.contains(type)
            && COLLAPSE_PATTERN.matcher(filteredMessage).find()
            && !originalMsg.equals(filteredMessage); // only if filtered differs from original

        return new MessageLine(builder.build(), type, timestamp, senderName, receiverName, prefix, duplicateKey, collapsed, senderIconId);
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

    public static MessageLine toMessageLine(RichLine rl) {
        StringBuilder textBuilder = new StringBuilder();
        for (TextSegment segment : rl.getSegs()) {
            textBuilder.append(segment.getText());
        }
        // Re-extract sender icon from sender name if available
        int senderIcon = extractIconId(rl.getSender());

        return new MessageLine(
            textBuilder.toString(),
            rl.getType(),
            rl.getTimestamp(),
            rl.getSender(),
            rl.getReceiver(),
            "", // Prefix is not directly stored in RichLine, using empty string for now
            rl.getDuplicateKey(),
            rl.isCollapsed(),
            senderIcon
        );
    }
}