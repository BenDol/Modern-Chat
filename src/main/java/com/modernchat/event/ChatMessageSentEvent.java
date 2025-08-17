package com.modernchat.event;

import com.modernchat.common.ChatMode;
import com.modernchat.common.ClanType;
import lombok.Value;

@Value
public class ChatMessageSentEvent
{
    String text;
    int mode;
    int clanType;
}
