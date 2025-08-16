package com.modernchat.event;

import lombok.Data;

@Data
public class SubmitHistoryEvent
{
    final String text;
}