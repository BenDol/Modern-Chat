package com.modernchat.draw;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public final class VisualLine
{
    final List<TextSegment> segs = new ArrayList<>();
}