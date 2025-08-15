package com.modernchat.event;

import lombok.Value;

@Value
public class ChatOverlayVisibilityChangeEvent
{
    boolean isVisible;
}
