package com.modernchat.overlay;

import com.modernchat.draw.Padding;

import java.awt.Color;

public interface ChatOverlayConfig
{
    boolean isEnabled();

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

    MessageContainerConfig getMessageContainerConfig();

    class Default implements ChatOverlayConfig
    {
        @Override
        public boolean isEnabled() {
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
        public MessageContainerConfig getMessageContainerConfig() {
            return new MessageContainerConfig.Default();
        }
    }
}
