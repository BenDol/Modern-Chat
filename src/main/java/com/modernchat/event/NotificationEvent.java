package com.modernchat.event;

import com.modernchat.common.NotifyType;
import lombok.Value;

@Value
public class NotificationEvent
{
    NotifyType type;
    Object key;
    String message;
    boolean allowSound;
    boolean isPrivate;
    Object sender;
}
