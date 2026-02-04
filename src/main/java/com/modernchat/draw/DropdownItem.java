package com.modernchat.draw;

import lombok.Data;

import java.awt.Rectangle;

@Data
public class DropdownItem<T> {
    private final T value;
    private final String label;
    private final Rectangle bounds = new Rectangle();
    private boolean selected;

    public DropdownItem(T value, String label, boolean selected) {
        this.value = value;
        this.label = label;
        this.selected = selected;
    }
}
