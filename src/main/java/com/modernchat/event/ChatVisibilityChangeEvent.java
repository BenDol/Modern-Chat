package com.modernchat.event;

import lombok.Value;
import net.runelite.api.widgets.Widget;

@Value
public class ChatVisibilityChangeEvent {

    Widget chatWidget;
    boolean isVisible;
}
