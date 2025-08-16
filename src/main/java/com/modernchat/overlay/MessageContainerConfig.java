package com.modernchat.overlay;

import com.modernchat.common.RuneFontStyle;
import com.modernchat.draw.ChatColors;
import com.modernchat.draw.Margin;
import com.modernchat.draw.Padding;
import net.runelite.api.Point;

import java.awt.Color;

public interface MessageContainerConfig extends ChatColors
{
    boolean isEnabled();

    boolean isPrefixChatType();

    boolean isShowTimestamp();

    boolean isScrollable();

    boolean isDrawScrollbar();

    Point getOffset();

    Margin getMargin();

    Padding getPadding();

    int getMaxLines();

    int getLineSpacing();

    int getScrollStep();

    int getScrollbarWidth();

    RuneFontStyle getLineFontStyle();

    int getLineFontSize();

    int getTextShadow();

    Color getBackdropColor();

    Color getBorderColor();

    Color getDefaultTextColor();

    Color getShadowColor();

    Color getScrollbarTrackColor();

    Color getScrollbarThumbColor();

    class Default implements MessageContainerConfig
    {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean isPrefixChatType() {
            return true;
        }

        @Override
        public boolean isShowTimestamp() {
            return true;
        }

        @Override
        public boolean isScrollable() {
            return true;
        }

        @Override
        public boolean isDrawScrollbar() {
            return true;
        }

        @Override
        public Point getOffset() {
            return new Point(0, 0);
        }

        @Override
        public Margin getMargin() {
            return new Margin(0);
        }

        @Override
        public Padding getPadding() {
            return new Padding(2, 0, 2, 2);
        }

        @Override
        public int getMaxLines() {
            return 40;
        }

        @Override
        public int getLineSpacing() {
            return 0;
        }

        @Override
        public int getScrollStep() {
            return 32;
        }

        @Override
        public int getScrollbarWidth() {
            return 8;
        }

        @Override
        public RuneFontStyle getLineFontStyle() {
            return RuneFontStyle.NORMAL;
        }

        @Override
        public int getLineFontSize() {
            return 16;
        }

        @Override
        public int getTextShadow() {
            return 1;
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
        public Color getDefaultTextColor() {
            return Color.WHITE;
        }

        @Override
        public Color getShadowColor() {
            return new Color(0, 0, 0, 200);
        }

        @Override
        public Color getScrollbarTrackColor() {
            return new Color(255, 255, 255, 32);
        }

        @Override
        public Color getScrollbarThumbColor() {
            return new Color(255, 255, 255, 144);
        }
    }
}