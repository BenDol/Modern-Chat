package com.modernchat.common;

import net.runelite.client.chat.ChatColorType;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

import java.awt.Color;

public class ChatMessageBuilder
{
    private final StringBuilder builder = new StringBuilder();

    public ChatMessageBuilder append(final ChatColorType type)
    {
        builder.append("<col").append(type.name()).append('>');
        return this;
    }

    public ChatMessageBuilder append(final Color color, final String message)
    {
        builder.append(ColorUtil.wrapWithColorTag(message, color));
        return this;
    }

    public ChatMessageBuilder append(final String message, boolean escape)
    {
        if (escape)
        {
            builder.append(Text.escapeJagex(message));
        }
        else
        {
            builder.append(message);
        }
        return this;
    }

    public ChatMessageBuilder append(final String message)
    {
        builder.append(Text.escapeJagex(message));
        return this;
    }

    public ChatMessageBuilder img(int imageId)
    {
        builder.append("<img=").append(imageId).append('>');
        return this;
    }

    public String build()
    {
        return builder.toString();
    }
}
