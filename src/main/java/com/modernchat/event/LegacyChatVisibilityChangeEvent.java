package com.modernchat.event;

import lombok.Value;
import net.runelite.api.widgets.Widget;

@Value
public class LegacyChatVisibilityChangeEvent
{

    Widget chatWidget;
    boolean isVisible;
}
