package com.modernchat.common;

import lombok.ToString;
import lombok.Value;
import net.runelite.api.ChatMessageType;

import javax.annotation.Nullable;

@Value
@ToString
public class MessageLine
{
    String text;
    ChatMessageType type;
    long timestamp;
    String senderName;
    String receiverName;
    String prefix;
}
