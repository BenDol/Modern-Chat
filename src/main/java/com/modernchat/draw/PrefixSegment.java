package com.modernchat.draw;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.awt.Color;

@Data
@EqualsAndHashCode(callSuper = true)
public class PrefixSegment extends TextSegment
{
    public PrefixSegment(String t, Color c) {
        super(t, c);
    }
}
