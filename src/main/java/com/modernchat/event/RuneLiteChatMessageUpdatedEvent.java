package com.modernchat.event;

import lombok.Value;

/** Posted when RuneLite asynchronously updates a Modern Chat command row. */
@Value
public class RuneLiteChatMessageUpdatedEvent
{
    int messageId;
    String formattedBody;
}
