package com.modernchat.util;

import com.modernchat.ModernChatPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ClientUtil
{
    // Unfortunately this hack is required for KeyRemappingPlugin compatibility
    public static final String PRESS_ENTER_TO_CHAT = "Press Enter to Chat...";

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

        Widget dialogOptions2 = client.getWidget(InterfaceID.OBJECTBOX, 0);
        if (dialogOptions2 != null && !dialogOptions2.isHidden()) {
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
            client.setVarcStrValue(VarClientID.CHATINPUT, v);
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

    // Grand Exchange center coordinates and radius
    private static final int GE_CENTER_X = 3165;
    private static final int GE_CENTER_Y = 3472;
    private static final int GE_RADIUS = 35;

    public static boolean isAtGrandExchange(Client client) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return false;
        }

        WorldPoint loc = localPlayer.getWorldLocation();
        if (loc == null) {
            return false;
        }

        int dx = loc.getX() - GE_CENTER_X;
        int dy = loc.getY() - GE_CENTER_Y;
        return (dx * dx + dy * dy) <= (GE_RADIUS * GE_RADIUS);
    }

    public static boolean isChatLocked(Client client) {
        String input = getChatboxWidgetInput(client);
        return input != null && input.endsWith(PRESS_ENTER_TO_CHAT);
    }

    public static String getChatboxWidgetInput(Client client) {
        Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (chatboxInput != null) {
            return chatboxInput.getText();
        }
        return null;
    }

    public static void setChatboxWidgetInput(Client client, String input) {
        Widget chatboxInput = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (chatboxInput != null) {
            setChatboxWidgetInput(chatboxInput, input);
        }
    }

    public static void setChatboxWidgetInput(Widget widget, String input) {
        String text = widget.getText();
        int idx = text.indexOf(':');
        if (idx != -1) {
            String newText = text.substring(0, idx) + ": " + input;
            widget.setText(newText);
        }
    }

    public static boolean isDialogOpen(Client client) {
        return isHidden(client, InterfaceID.Chatbox.MES_LAYER_HIDE)
            || isHidden(client, InterfaceID.Chatbox.CHATDISPLAY);
    }

    public static boolean isOptionsDialogOpen(Client client) {
        return client.getWidget(InterfaceID.Chatmenu.OPTIONS) != null;
    }

    public static boolean isHidden(Client client, int component) {
        Widget w = client.getWidget(component);
        return w == null || w.isSelfHidden();
    }
}
