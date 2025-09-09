package com.modernchat.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ClientUtil
{
    /**
     * MUST be on client thread.
     */
    public static boolean isSystemTextEntryActive(Client client) {
        int type = client.getVarbitValue(VarClientInt.INPUT_TYPE);
        if (type != 0 && type != 1) {
            return true;
        }

        // Fallback: the system prompts
        Widget full = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        if (full != null && !full.isHidden()) {
            return true;
        }

        // Fallback: typed text buffer for system inputs
        String s = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        return s != null && !s.isEmpty();
    }

    public static boolean isSystemWidgetActive(Client client) {
        if (isSystemTextEntryActive(client)) {
            return true;
        }

        Widget dialogLeft = client.getWidget(InterfaceID.CHAT_LEFT, 0);
        if (dialogLeft != null && !dialogLeft.isHidden()) {
            return true;
        }

        Widget dialogRight = client.getWidget(InterfaceID.CHAT_RIGHT, 0);
        if (dialogRight != null && !dialogRight.isHidden()) {
            return true;
        }

        Widget dialogOptions = client.getWidget(InterfaceID.CHATMENU, 0);
        if (dialogOptions != null && !dialogOptions.isHidden()) {
            return true;
        }

        return false;
    }

    public static boolean isPmComposeOpen(Client client) {
        try {
            String t = client.getVarcStrValue(VarClientStr.PRIVATE_MESSAGE_TARGET);
            if (t != null && !t.isEmpty()) return true;
        } catch (Throwable ignored) {
            return true; // varc not available on this build, assume it is to avoid getting stuck
        }

        return false;
    }

    public static String getSystemInputText(Client client) {
        try {
            return client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static Widget getChatWidget(Client client) {
        return client.getWidget(InterfaceID.CHATBOX, 0);
    }

    public static Widget getChatInputWidget(Client client) {
        return client.getWidget(InterfaceID.Chatbox.INPUT);
    }

    public static boolean isChatHidden(Client client) {
        Widget root = getChatWidget(client);
        if (root != null) {
            return root.isHidden();
        }
        return false;
    }

    public static boolean setChatHidden(Client client, boolean hidden) {
        Widget chatWidget = getChatWidget(client);
        if (chatWidget != null) {
            ChatUtil.setChatHidden(chatWidget, hidden);
            client.refreshChat();
            return true;
        }
        return false;
    }

    public static MessageNode findMessageNode(Client client, int id) {
        // The identifier on chat menu entries is the MessageNode id
        for (ChatLineBuffer buf : client.getChatLineMap().values()) {
            if (buf == null) continue;
            for (MessageNode n : buf.getLines()) {
                if (n != null && n.getId() == id)
                    return n;
            }
        }
        return null;
    }

    public static void clearChatInput(Client client, ClientThread clientThread, Runnable callback) {
        clientThread.invokeLater(() -> {
            callback.run();
            client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, "");
            client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
        });
    }

    public static void cancelPrivateMessage(Client client, ClientThread clientThread, Runnable callback) {
        clientThread.invokeLater(() -> {
            try {
                client.setVarcStrValue(VarClientStr.PRIVATE_MESSAGE_TARGET, "");
            } catch (Throwable ex) {
                // Some client builds may not have this VarClientStr; safe to ignore
            }

            try {
                client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 1, 1, 1);
            } catch (Throwable ex) {
                log.debug("Failed to close message layer script", ex);
            }

            client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
            callback.run();
        });
    }

    public static void startPrivateMessage(
        Client client,
        ClientThread clientThread,
        String currentTarget,
        String body,
        Runnable callback
    ) {
        // Schedule after current client scripts have finished
        clientThread.invokeLater(() -> {
            try {
                // Open "To <target>:" compose line
                client.runScript(ScriptID.OPEN_PRIVATE_MESSAGE_INTERFACE, currentTarget);

                // Optional: prefill message body if you start using it
                if (body != null && !body.isEmpty()) {
                    client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, body);
                }

                callback.run();
            } catch (Throwable ex) {
                log.warn("Failed to open PM to {} via chat command", currentTarget, ex);
            }
        });
    }

    public static boolean isOnline(Client client) {
        Player player = client.getLocalPlayer();
        return player != null &&
            player.getWorldLocation() != null &&
            player.getWorldLocation().getRegionID() != 0;
    }

    public static void setChatInputText(Client client, String value) {
        final String v = value == null ? "" : value;
        try {
            client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, v);
            client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
        } catch (Throwable ex) {
            log.debug("setChatInputText failed", ex);
        }
    }

    public static boolean isChatInputEditable(Client client) {
        if (client.getVarbitValue(VarClientInt.INPUT_TYPE) != 0)
            return false;

        Widget w = ClientUtil.getChatInputWidget(client);
        return w != null && !w.isHidden();
    }

    public static String getChatInputText(Client client) {
        try {
            String s = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
            return s != null ? Text.removeTags(s) : "";
        } catch (Throwable t) {
            return "";
        }
    }
}
