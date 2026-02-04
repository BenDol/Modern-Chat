package com.modernchat.draw;

import lombok.Data;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;

@Data
public class Checkbox {
    private final String label;
    private boolean checked;
    private final Rectangle bounds = new Rectangle();
    private final Rectangle checkboxBounds = new Rectangle();

    private static final int CHECKBOX_SIZE = 12;
    private static final int CHECKBOX_PADDING = 4;

    public Checkbox(String label, boolean checked) {
        this.label = label;
        this.checked = checked;
    }

    public void draw(Graphics2D g, FontMetrics fm, int x, int y, int width, int height,
                     Color bgColor, Color checkColor, Color textColor) {
        bounds.setBounds(x, y, width, height);

        int checkboxY = y + (height - CHECKBOX_SIZE) / 2;
        checkboxBounds.setBounds(x + CHECKBOX_PADDING, checkboxY, CHECKBOX_SIZE, CHECKBOX_SIZE);

        // Draw checkbox background
        g.setColor(new Color(40, 40, 40));
        g.fillRoundRect(checkboxBounds.x, checkboxBounds.y, CHECKBOX_SIZE, CHECKBOX_SIZE, 3, 3);

        // Draw checkbox border
        g.setColor(new Color(100, 100, 100));
        g.drawRoundRect(checkboxBounds.x, checkboxBounds.y, CHECKBOX_SIZE, CHECKBOX_SIZE, 3, 3);

        // Draw checkmark if checked
        if (checked) {
            g.setColor(checkColor);
            int cx = checkboxBounds.x + 3;
            int cy = checkboxBounds.y + CHECKBOX_SIZE / 2;
            // Draw checkmark
            g.drawLine(cx, cy, cx + 2, cy + 3);
            g.drawLine(cx + 2, cy + 3, cx + CHECKBOX_SIZE - 5, cy - 2);
        }

        // Draw label
        g.setColor(textColor);
        int textX = checkboxBounds.x + CHECKBOX_SIZE + CHECKBOX_PADDING + 2;
        int textY = y + (height + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(label, textX, textY);
    }

    public boolean hitTest(Point p) {
        return bounds.contains(p);
    }

    public void toggle() {
        checked = !checked;
    }
}
