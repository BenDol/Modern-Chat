package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.draw.Padding;

import java.awt.Color;

public interface ChatOverlayConfig
{
    boolean isEnabled();

    boolean isStartHidden();

    boolean isHideOnSend();

    boolean isHideOnEscape();

    boolean isOpenTabOnIncomingPM();

    boolean isClickOutsideToClose();

    boolean isShowNotificationBadge();

    Padding getPadding();

    int getInputLineSpacing();

    int getInputFontSize();

    Color getBackdropColor();

    Color getBorderColor();

    Color getInputBackgroundColor();

    Color getInputBorderColor();

    Color getInputShadowColor();

    Color getInputPrefixColor();

    Color getInputTextColor();

    Color getInputCaretColor();

    Color getTabBarBackgroundColor();

    int getTabFontSize();

    Color getTabColor();

    Color getTabSelectedColor();

    Color getTabBorderColor();

    Color getTabBorderSelectedColor();

    Color getTabTextColor();

    Color getTabUnreadPulseToColor();

    Color getTabUnreadPulseFromColor();

    Color getTabNotificationColor();

    Color getTabNotificationTextColor();

    Color getTabCloseButtonColor();

    Color getTabCloseButtonTextColor();

    ChatMode getDefaultChatMode();

    MessageContainerConfig getMessageContainerConfig();

    class Default implements ChatOverlayConfig
    {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isStartHidden() {
            return true;
        }

        @Override
        public boolean isHideOnSend() {
            return true;
        }

        @Override
        public boolean isHideOnEscape() {
            return true;
        }

        @Override
        public boolean isOpenTabOnIncomingPM() {
            return false;
        }

        @Override
        public boolean isClickOutsideToClose() {
            return false;
        }

        @Override
        public boolean isShowNotificationBadge() {
            return true;
        }

        @Override
        public Padding getPadding() {
            return new Padding(8);
        }

        @Override
        public int getInputLineSpacing() {
            return 0;
        }

        @Override
        public int getInputFontSize() {
            return 16;
        }

        @Override
        public Color getBackdropColor() {
            return new Color(0, 0, 0, 160);
        }

        @Override
        public Color getBorderColor() {
            return new Color(12, 12, 12, 0);
        }

        @Override
        public Color getInputPrefixColor() {
            return new Color(160, 200, 255);
        }

        @Override
        public Color getInputBackgroundColor() {
            return new Color(0, 0, 0, 200);
        }

        @Override
        public Color getInputBorderColor() {
            return new Color(255, 255, 255, 40);
        }

        @Override
        public Color getInputShadowColor() {
            return new Color(0, 0, 0, 200);
        }

        @Override
        public Color getInputTextColor() {
            return Color.WHITE;
        }

        @Override
        public Color getInputCaretColor() {
            return Color.WHITE;
        }

        @Override
        public int getTabFontSize() {
            return 16;
        }

        @Override
        public Color getTabBarBackgroundColor() {
            return new Color(0, 0, 0, 80);
        }

        @Override
        public Color getTabColor() {
            return new Color(35, 35, 35, 180);
        }

        @Override
        public Color getTabSelectedColor() {
            return new Color(60, 60, 60, 220);
        }

        @Override
        public Color getTabBorderColor() {
            return new Color(255, 255, 255, 70);
        }

        @Override
        public Color getTabBorderSelectedColor() {
            return new Color(255, 255, 255, 140);
        }

        @Override
        public Color getTabTextColor() {
            return Color.WHITE;
        }

        @Override
        public Color getTabUnreadPulseToColor() {
            return new Color(255,180,60);
        }

        @Override
        public Color getTabUnreadPulseFromColor() {
            return Color.WHITE;
        }

        @Override
        public Color getTabNotificationColor() {
            return new Color(200, 60, 60, 230);
        }

        @Override
        public Color getTabNotificationTextColor() {
            return Color.WHITE;
        }

        @Override
        public Color getTabCloseButtonColor() {
            return new Color(200, 60, 60, 230);
        }

        @Override
        public Color getTabCloseButtonTextColor() {
            return Color.WHITE;
        }

        @Override
        public ChatMode getDefaultChatMode() {
            return ChatMode.PUBLIC;
        }

        @Override
        public MessageContainerConfig getMessageContainerConfig() {
            return new MessageContainerConfig.Default();
        }
    }
}
