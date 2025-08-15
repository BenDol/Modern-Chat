package com.modernchat.draw;

import java.awt.Color;

public interface ChatColors
{
    default Color getWelcomeColor() {
        return Color.WHITE;
    }

    default Color getPublicColor() {
        return Color.WHITE;
    }

    default Color getPrivateColor() {
        return new Color(0xFF80FF);
    }

    default Color getFriendColor() {
        return new Color(0x00FF80);
    }

    default Color getClanColor() {
        return new Color(0x80C0FF);
    }

    default Color getSystemColor() {
        return new Color(0xCFCFCF);
    }

    default Color getTradeColor() {
        return Color.ORANGE;
    }
}
