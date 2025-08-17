package com.modernchat.event;

import com.modernchat.draw.Tab;
import lombok.Value;

@Value
public class TabChangeEvent
{
    Tab newTab;
    Tab oldTab;
}
