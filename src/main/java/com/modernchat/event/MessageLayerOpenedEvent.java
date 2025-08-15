package com.modernchat.event;

import lombok.Value;
import net.runelite.api.widgets.Widget;

@Value
public class MessageLayerOpenedEvent {

    Widget messageWidget;
    boolean isPrivateMessage;
}
