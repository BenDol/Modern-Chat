package com.modernchat.util;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.MessageLine;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.ColorUtil;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtil
{
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
                return ChatMode.CLAN_MAIN;
            case CLAN_GUEST_CHAT:
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
        Matcher m = Pattern.compile("(?s).{1," + limit + "}").matcher(s);
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
            || type == ChatMessageType.CLAN_GUEST_CHAT
            || type == ChatMessageType.CLAN_GIM_CHAT
            || type == ChatMessageType.CLAN_GIM_FORM_GROUP
            || type == ChatMessageType.CLAN_GIM_MESSAGE
            || type == ChatMessageType.CLAN_GIM_GROUP_WITH;
    }

    public static boolean isFriendsChatMessage(ChatMessageType type) {
        return type == ChatMessageType.FRIENDSCHAT
            || type == ChatMessageType.FRIENDSCHATNOTIFICATION;
    }

    public static Pair<String, String> getSenderAndReceiver(ChatMessage msg, String localPlayerName) {
        String receiverName = null;
        String senderName = msg.getSender();
        String name = msg.getName();
        ChatMessageType type = msg.getType();

        if (type == ChatMessageType.PRIVATECHATOUT) {
            receiverName = name;
            senderName = "You";
        }
        else if (type == ChatMessageType.PRIVATECHAT) {
            receiverName = localPlayerName;
            senderName = name;
        }
        else if (ChatUtil.isClanMessage(type) || ChatUtil.isFriendsChatMessage(type)) {
            senderName = name;
        }
        else if (senderName == null) {
            senderName = name;
        }

        if (receiverName == null) {
            receiverName = localPlayerName;
        }

        return Pair.of(senderName, receiverName);
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
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
            return null;

        String localPlayerName = localPlayer.getName();
        if (StringUtil.isNullOrEmpty(localPlayerName))
            return null;

        long timestamp = e.getTimestamp() > 0 ? e.getTimestamp() : System.currentTimeMillis();

        Pair<String, String> senderReceiver = ChatUtil.getSenderAndReceiver(e, localPlayerName);

        ChatMessageType type = e.getType();
        String msg = e.getMessage();
        String receiverName = senderReceiver.getRight();
        String senderName = senderReceiver.getLeft();
        String prefix = ChatUtil.getCustomPrefix(e);

        if (type == ChatMessageType.DIALOG) {
            msg = msg.replaceFirst("\\|", " ");
            senderName = ColorUtil.wrapWithColorTag(senderName, Color.CYAN);
        }

        ChatMessageBuilder builder = new ChatMessageBuilder();

        if (!StringUtil.isNullOrEmpty(senderName))
            builder.append(senderName, false).append(": ");

        String[] params = msg.split("\\|", 2);
        if (params.length > 1) {
            int icon = ChatUtil.getModImageId(params[0]);
            if (icon != -1) {
                builder.img(icon);
            }

            // message should always be last
            builder.append(params[params.length - 1], false);
        } else {
            builder.append(msg, false);
        }

        return new MessageLine(builder.build(), type, timestamp, senderName, receiverName, prefix);
    }
}