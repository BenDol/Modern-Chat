package com.modernchat.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

@Slf4j
public class ClientUtil {

    /**
     * MUST be on client thread.
     */
    public static boolean isSystemTextEntryActive(Client client) {
        // Reliable for "Add Friend", "Enter amount", etc.
        int type = client.getVarbitValue(VarClientInt.INPUT_TYPE);
        if (type != 0) {
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

    public static boolean isPmComposeOpen(Client client) {
        try {
            String t = client.getVarcStrValue(VarClientStr.PRIVATE_MESSAGE_TARGET);
            if (t != null && !t.isEmpty()) return true;
        } catch (Throwable ignored) {
            return true; // varc not available on this build, assume it is to avoid getting stuck
        }

        return false;
    }

    public static Widget getSplitPmWidget(Client client) {
        Widget pm = client.getWidget(InterfaceID.PM_CHAT, 0);
        if (pm == null || pm.isHidden())
            return null;
        return pm;
    }

    public static Widget getSplitPmParentWidget(Client client) {
        Widget pm = getSplitPmWidget(client);
        if (pm == null || pm.isHidden())
            return null;

        Widget pmParent = pm.getParent();
        if (pmParent == null || pmParent.isHidden())
            return null;
        return pmParent;
    }

    public static String getSystemInputText(Client client) {
        try {
            return client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        } catch (Throwable ignored) {}

        return null;
    }

    public static boolean isChatHidden(Client client) {
        Widget root = client.getWidget(InterfaceID.CHATBOX, 0);
        if (root != null) {
            return root.isHidden();
        }
        return false;
    }

    public static MessageNode findMessageNode(Client client, int id)
    {
        // The identifier on chat menu entries is the MessageNode id
        for (ChatLineBuffer buf : client.getChatLineMap().values())
        {
            if (buf == null) continue;
            for (MessageNode n : buf.getLines())
            {
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
            }
            catch (Throwable ex) {
                log.warn("Failed to open PM to {} via chat command", currentTarget, ex);
            }
        });
    }
}
