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
    /** Key for duplicate detection: name + original message (before filter modification) */
    @Nullable String duplicateKey;
    /** True if this message has a collapse count suffix like " (2)" */
    boolean collapsed;
    /** Icon ID from the sender's name tag (e.g. ironman icon), or -1 if none */
    int senderIconId;
}
