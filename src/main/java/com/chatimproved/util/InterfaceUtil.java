package com.chatimproved.util;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

public class InterfaceUtil {

    /**
     * MUST be on client thread.
     */
    public static boolean isSystemTextEntryActiveCT(Client client)
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
}
