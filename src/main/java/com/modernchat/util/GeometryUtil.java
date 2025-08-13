package com.modernchat.util;

import java.awt.Rectangle;

public class GeometryUtil {

    public static boolean isInvalidChatBounds(Rectangle chatBounds) {
        if (chatBounds == null) {
            return true;
        }
        return (chatBounds.x < 1 && chatBounds.y < 1) ||
               (chatBounds.width < 2 || chatBounds.height < 2);
    }
}
