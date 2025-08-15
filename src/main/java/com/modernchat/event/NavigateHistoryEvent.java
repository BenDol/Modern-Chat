package com.modernchat.event;

import lombok.Data;

@Data
public class NavigateHistoryEvent
{
    public static final int PREV = -1;
    public static final int NEXT = 1;

    final int delta;
}