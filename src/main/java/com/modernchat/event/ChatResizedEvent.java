package com.modernchat.event;

import lombok.Value;

@Value
public class ChatResizedEvent
{
    int width;
    int height;
}
