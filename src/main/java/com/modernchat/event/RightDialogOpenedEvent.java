package com.modernchat.event;

import lombok.Value;
import net.runelite.api.widgets.Widget;

@Value
public class RightDialogOpenedEvent
{
    Widget widget;
}
