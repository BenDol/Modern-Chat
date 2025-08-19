package com.modernchat.draw;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.awt.Color;

@Data
@EqualsAndHashCode(callSuper = true)
public class TimestampSegment extends TextSegment
{
    public TimestampSegment(String t, Color c) {
        super(t, c);
    }
}
