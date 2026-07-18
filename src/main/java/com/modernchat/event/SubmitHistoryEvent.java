package com.modernchat.event;

import lombok.Data;

@Data
public class SubmitHistoryEvent
{
    final String text;
    // When the recorded send also fires a ChatboxInput echoing this value
    // (e.g. a channel prefix send), history skips that echo
    final String suppressedInput;

    public SubmitHistoryEvent(String text) {
        this(text, null);
    }

    public SubmitHistoryEvent(String text, String suppressedInput) {
        this.text = text;
        this.suppressedInput = suppressedInput;
    }
}
