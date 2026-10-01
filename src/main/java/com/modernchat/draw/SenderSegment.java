package com.modernchat.draw;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.awt.Color;

@Data
@EqualsAndHashCode(callSuper = true)
public class SenderSegment extends TextSegment
{
    public SenderSegment(String t, Color c) {
        super(t, c);
    }
}
