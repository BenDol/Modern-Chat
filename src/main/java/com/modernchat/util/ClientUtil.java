package com.modernchat.util;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public class ClientUtil {

    /**
     * MUST be on client thread.
     */
    public static boolean isSystemTextEntryActive(Client client)
    {
        // Reliable for "Add Friend", "Enter amount", etc.
        int type = client.getVarbitValue(VarClientInt.INPUT_TYPE);
        if (type != 0)
        {
            return true;
        }

        // Fallback: the system prompts
        Widget full = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        if (full != null && !full.isHidden())
        {
            return true;
        }

        // Fallback: typed text buffer for system inputs
        String s = client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        return s != null && !s.isEmpty();
    }

    public static boolean isPmComposeOpen(Client client)
    {
        try {
            String t = client.getVarcStrValue(VarClientStr.PRIVATE_MESSAGE_TARGET);
            if (t != null && !t.isEmpty()) return true;
        } catch (Throwable ignored) {
            return true; // varc not available on this build, assume it is to avoid getting stuck
        }

        return false;
    }

    public static String getSystemInputText(Client client)
    {
        try {
            return client.getVarcStrValue(VarClientStr.INPUT_TEXT);
        } catch (Throwable ignored) {}

        return null;
    }

    public static boolean isChatHidden(Client client) {
        Widget root = client.getWidget(InterfaceID.CHATBOX, 0);
        if (root != null)
        {
            return root.isHidden();
        }
        return false;
    }
}
