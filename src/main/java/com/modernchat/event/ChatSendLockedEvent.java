package com.modernchat.event;

import lombok.Value;

import javax.annotation.Nullable;

@Value
public class ChatSendLockedEvent
{
    @Nullable String targetName;
    long lockedUntil;
    boolean isPrivate;
}
