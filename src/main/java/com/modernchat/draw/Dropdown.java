package com.modernchat.draw;

import lombok.Data;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

@Data
public class Dropdown<T> {
    private final List<DropdownItem<T>> items = new ArrayList<>();
    private final Rectangle bounds = new Rectangle();
    private boolean visible = false;

    public enum Position { ABOVE, BELOW }
    private Position position = Position.BELOW;

    private static final int ITEM_HEIGHT = 24;
    private static final int PADDING = 4;
    private static final int CHECKBOX_SIZE = 12;
    private static final int CHECKBOX_PADDING = 4;

    public void calculatePosition(int anchorY, int anchorHeight, int screenTop, int screenBottom) {
        int dropdownHeight = items.size() * ITEM_HEIGHT + PADDING * 2;
        int spaceBelow = screenBottom - (anchorY + anchorHeight);
        int spaceAbove = anchorY - screenTop;

        // Prefer below, but use above if not enough space
        if (spaceBelow >= dropdownHeight || spaceBelow >= spaceAbove) {
            position = Position.BELOW;
        } else {
            position = Position.ABOVE;
        }
    }

    public void draw(Graphics2D g, FontMetrics fm, int anchorX, int anchorY,
                     int anchorWidth, int anchorHeight, int screenTop, int screenBottom,
                     Color bgColor, Color borderColor, Color textColor, Color checkboxColor, Color checkColor) {
        if (!visible || items.isEmpty()) {
            return;
        }

        calculatePosition(anchorY, anchorHeight, screenTop, screenBottom);

        int dropdownHeight = items.size() * ITEM_HEIGHT + PADDING * 2;
        int dropdownWidth = calculateWidth(fm) + PADDING * 2;

        int dropdownX = anchorX + anchorWidth - dropdownWidth;
        int dropdownY;

        if (position == Position.BELOW) {
            dropdownY = anchorY + anchorHeight + 2;
        } else {
            dropdownY = anchorY - dropdownHeight - 2;
        }

        bounds.setBounds(dropdownX, dropdownY, dropdownWidth, dropdownHeight);

        // Draw background
        g.setColor(bgColor);
        g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

        // Draw border
        g.setColor(borderColor);
        g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6, 6);

        // Draw items
        int itemY = bounds.y + PADDING;
        for (DropdownItem<T> item : items) {
            item.getBounds().setBounds(bounds.x + PADDING, itemY, bounds.width - PADDING * 2, ITEM_HEIGHT);

            // Checkbox background
            int checkboxX = item.getBounds().x + CHECKBOX_PADDING;
            int checkboxY = itemY + (ITEM_HEIGHT - CHECKBOX_SIZE) / 2;

            g.setColor(checkboxColor);
            g.fillRoundRect(checkboxX, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE, 3, 3);

            g.setColor(borderColor);
            g.drawRoundRect(checkboxX, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE, 3, 3);

            // Checkmark
            if (item.isSelected()) {
                g.setColor(checkColor);
                int cx = checkboxX + 3;
                int cy = checkboxY + CHECKBOX_SIZE / 2;
                g.drawLine(cx, cy, cx + 2, cy + 3);
                g.drawLine(cx + 2, cy + 3, cx + CHECKBOX_SIZE - 5, cy - 2);
            }

            // Label
            g.setColor(textColor);
            int textX = checkboxX + CHECKBOX_SIZE + CHECKBOX_PADDING + 2;
            int textY = itemY + (ITEM_HEIGHT + fm.getAscent() - fm.getDescent()) / 2;
            g.drawString(item.getLabel(), textX, textY);

            itemY += ITEM_HEIGHT;
        }
    }

    private int calculateWidth(FontMetrics fm) {
        int maxLabelWidth = 0;
        for (DropdownItem<T> item : items) {
            int labelWidth = fm.stringWidth(item.getLabel());
            maxLabelWidth = Math.max(maxLabelWidth, labelWidth);
        }
        return CHECKBOX_PADDING + CHECKBOX_SIZE + CHECKBOX_PADDING + 2 + maxLabelWidth + CHECKBOX_PADDING;
    }

    public DropdownItem<T> itemAt(Point p) {
        if (!visible || !bounds.contains(p)) {
            return null;
        }

        for (DropdownItem<T> item : items) {
            if (item.getBounds().contains(p)) {
                return item;
            }
        }
        return null;
    }

    public boolean hitTest(Point p) {
        return visible && bounds.contains(p);
    }

    public void toggle() {
        visible = !visible;
    }

    public void close() {
        visible = false;
    }

    public void open() {
        visible = true;
    }
}
