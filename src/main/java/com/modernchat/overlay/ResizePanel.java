package com.modernchat.overlay;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/**
 * Draws resize grips and handles mouse interactions to resize a panel.
 * Intended to sit on top of your chat panel (same bounds provider).
 */
public class ResizePanel extends Overlay
{
    public interface ResizeListener
    {
        void onResize(int newWidth, int newHeight);
    }

    private static final int TOP_BAR_INSET_X = 5;
    private static final int TOP_BAR_X1_OFFSET = 0; // 1st bar x-offset from panel.x
    private static final int TOP_BAR_Y1_OFFSET = 4; // 1st bar y-offset from panel.y
    private static final int TOP_BAR_H = 1;        // each bar height

    private static final int RIGHT_BAR_INSET_Y = 6;
    private static final int RIGHT_BAR_X1_OFFSET = 5;
    private static final int RIGHT_BAR_W = 1;

    private static final Color GRIP_COLOR = new Color(0, 0, 0, 0);
    private static final Color GRIP_HOVER_COLOR = new Color(255, 255, 255, 220);

    // Clickable hot areas
    private static final int TOP_HOT_H = 10;
    private static final int RIGHT_HOT_W = 10;

    // Constraints
    private static final int MIN_W = 220;
    private static final int MIN_H = 120;

    @Inject
    private Client client;
    @Inject
    private MouseManager mouseManager;

    @Setter
    private Supplier<Rectangle> baseBoundsProvider;

    @Setter
    @Nullable
    private ResizeListener listener;

    // Mouse
    private final MouseHandler mouseHandler = new MouseHandler();

    // State
    private Rectangle lastPanel = null;
    private final Rectangle topHot = new Rectangle();
    private final Rectangle rightHot = new Rectangle();

    @Getter
    private int widthOverride = -1;   // if <0, use base width
    @Getter
    private int heightOverride = -1;  // if <0, use base height

    private boolean resizingTop = false;
    private boolean resizingRight = false;
    private int dragStartX, dragStartY;
    private int startW, startH;

    private int currentCursor = Cursor.DEFAULT_CURSOR;

    public ResizePanel() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP); // keep grips above your chat
        setPriority(Overlay.PRIORITY_HIGHEST);
    }

    public void startUp() {
        mouseManager.registerMouseListener(mouseHandler);
    }

    public void shutDown() {
        mouseManager.unregisterMouseListener(mouseHandler);
        setCanvasCursor(Cursor.DEFAULT_CURSOR);
    }

    public void clearOverrides() {
        widthOverride = -1;
        heightOverride = -1;
    }

    /**
     * Returns the effective panel rect using base + overrides.
     */
    public Rectangle getEffectivePanel() {
        Rectangle base = baseBoundsProvider.get();
        if (base == null) return null;
        int w = widthOverride > 0 ? Math.max(MIN_W, widthOverride) : base.width;
        int h = heightOverride > 0 ? Math.max(MIN_H, heightOverride) : base.height;
        return new Rectangle(base.x, base.y, w, h);
    }

    @Override
    public Dimension render(Graphics2D g) {
        Rectangle base = baseBoundsProvider.get();
        if (base == null || base.width <= 0 || base.height <= 0) {
            lastPanel = null;
            return null;
        }

        // Apply overrides
        int w = widthOverride > 0 ? Math.max(MIN_W, widthOverride) : base.width;
        int h = heightOverride > 0 ? Math.max(MIN_H, heightOverride) : base.height;
        Rectangle panel = new Rectangle(base.x, base.y, w, h);
        lastPanel = panel;

        // Build hot areas
        topHot.setBounds(panel.x + TOP_BAR_INSET_X, panel.y, panel.width - TOP_BAR_INSET_X * 2, TOP_HOT_H);
        rightHot.setBounds(panel.x + panel.width - RIGHT_HOT_W, panel.y + 6, RIGHT_HOT_W, panel.height - 12);

        // Draw grips (just the bars; your main panel draws its own chrome)
        boolean topHover = isMouseOver(topHot);
        boolean rightHover = isMouseOver(rightHot);

        Color grip = topHover ? GRIP_HOVER_COLOR : GRIP_COLOR;
        g.setColor(grip);
        int y1 = panel.y + TOP_BAR_Y1_OFFSET;
        int xL = panel.x + TOP_BAR_INSET_X + TOP_BAR_X1_OFFSET;
        int xR = panel.x + panel.width - TOP_BAR_INSET_X;
        g.fillRect(xL, y1, xR - xL, TOP_BAR_H);

        grip = rightHover ? GRIP_HOVER_COLOR : GRIP_COLOR;
        g.setColor(grip);
        int rx1 = panel.x + panel.width - RIGHT_HOT_W + RIGHT_BAR_X1_OFFSET;
        int ryT = panel.y + RIGHT_BAR_INSET_Y;
        int ryH = Math.max(0, panel.y + panel.height - RIGHT_BAR_INSET_Y - ryT);
        g.fillRect(rx1, ryT, RIGHT_BAR_W, ryH);

        return null;
    }

    private boolean isMouseOver(Rectangle r) {
        // Mouse position is only available via events; we infer hover by cursor-set state in handler,
        // but for drawing highlight we approximate using lastPanel!=null and handler.lastMovePoint.
        Point p = mouseHandler.lastMovePoint;
        return p != null && r.contains(p);
    }

    private void setCanvasCursor(int cursorType) {
        if (currentCursor == cursorType)
            return;
        currentCursor = cursorType;
        try {
            Component canvas = client.getCanvas();
            if (canvas != null) {
                canvas.setCursor(Cursor.getPredefinedCursor(cursorType));
            }
        } catch (Exception ignored) { /* no-op */ }
    }

    // Mouse handler

    private final class MouseHandler implements MouseListener
    {
        private Point lastMovePoint = null;

        @Override
        public MouseEvent mouseClicked(MouseEvent e) {
            return e;
        }

        @Override
        public MouseEvent mouseEntered(MouseEvent e) {
            return e;
        }

        @Override
        public MouseEvent mouseExited(MouseEvent e) {
            lastMovePoint = null;
            if (!resizingTop && !resizingRight)
                setCanvasCursor(Cursor.DEFAULT_CURSOR);
            return e;
        }

        @Override
        public MouseEvent mouseMoved(MouseEvent e) {
            lastMovePoint = e.getPoint();
            if (lastPanel == null || !lastPanel.contains(lastMovePoint)) {
                if (!resizingTop && !resizingRight)
                    setCanvasCursor(Cursor.DEFAULT_CURSOR);
                return e;
            }

            // Hover cursor feedback
            if (topHot.contains(lastMovePoint)) {
                setCanvasCursor(Cursor.N_RESIZE_CURSOR);
            } else if (rightHot.contains(lastMovePoint)) {
                setCanvasCursor(Cursor.E_RESIZE_CURSOR);
            } else if (!resizingTop && !resizingRight) {
                setCanvasCursor(Cursor.DEFAULT_CURSOR);
            }

            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            if (lastPanel == null || !lastPanel.contains(e.getPoint()))
                return e;

            if (topHot.contains(e.getPoint())) {
                resizingTop = true;
                dragStartY = e.getY();
                startH = (heightOverride > 0 ? heightOverride : lastPanel.height);
                setCanvasCursor(Cursor.N_RESIZE_CURSOR);
                e.consume();
                return e;
            }

            if (rightHot.contains(e.getPoint())) {
                resizingRight = true;
                dragStartX = e.getX();
                startW = (widthOverride > 0 ? widthOverride : lastPanel.width);
                setCanvasCursor(Cursor.E_RESIZE_CURSOR);
                e.consume();
                return e;
            }

            return e;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e) {
            if (!resizingTop && !resizingRight)
                return e;

            if (resizingTop) {
                int dy = e.getY() - dragStartY;       // drag down -> increase height
                int newH = Math.max(MIN_H, startH - dy);
                heightOverride = newH;
            }
            if (resizingRight) {
                int dx = e.getX() - dragStartX;       // drag right -> increase width
                int newW = Math.max(MIN_W, startW + dx);
                widthOverride = newW;
            }

            // Notify listener (e.g., to reflow ChatOverlay/message area)
            if (listener != null) {
                Rectangle base = baseBoundsProvider.get();
                if (base != null) {
                    int w = widthOverride > 0 ? widthOverride : base.width;
                    int h = heightOverride > 0 ? heightOverride : base.height;
                    listener.onResize(w, h);
                }
            }

            e.consume();
            return e;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e) {
            if (resizingTop || resizingRight) {
                resizingTop = false;
                resizingRight = false;
                // Keep hover cursor if still over a grip; otherwise reset
                if (topHot.contains(e.getPoint()))
                    setCanvasCursor(Cursor.N_RESIZE_CURSOR);
                else if (rightHot.contains(e.getPoint()))
                    setCanvasCursor(Cursor.E_RESIZE_CURSOR);
                else
                    setCanvasCursor(Cursor.DEFAULT_CURSOR);
                e.consume();
                return e;
            }
            return e;
        }
    }
}
