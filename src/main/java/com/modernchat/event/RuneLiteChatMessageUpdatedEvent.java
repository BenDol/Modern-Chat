package com.modernchat.event;

import lombok.Value;

/**
 * Posted when a read-only watch sees RuneLite replace the body of a Modern Chat row.
 */
@Value
public class RuneLiteChatMessageUpdatedEvent
{
    int messageId;
    String formattedBody;
}