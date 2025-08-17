package com.modernchat.event;

import lombok.Value;

@Value
public class ChatPrivateMessageSentEvent
{
    String text;
    String targetName;
}
