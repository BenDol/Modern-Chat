package com.modernchat.util;

import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import java.awt.Point;

public class WidgetUtil {

    public static Point getAbsoluteLocation(Widget w) {
        int x = 0, y = 0;
        for (Widget cur = w; cur != null; cur = cur.getParent())
        {
            x += cur.getRelativeX();
            y += cur.getRelativeY();

            Widget p = cur.getParent();
            if (p != null) {           // adjust for scroll containers
                x -= p.getScrollX();
                y -= p.getScrollY();
            }
        }
        return new Point(x, y); // canvas coords
    }

    public static boolean isChatboxWidget(Widget w) {
        return w != null && ((w.getId() >>> 16) == InterfaceID.CHATBOX);
    }
}
