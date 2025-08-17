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
 */
public class ResizePanel extends Overlay
{
    public interface ResizeListener
    {
        void onResize(int newWidth, int newHeight);
    }

    private static final int TOP_BAR_INSET_X   = 5;
    private static final int TOP_BAR_X1_OFFSET = 0;
    private static final int TOP_BAR_Y1_OFFSET = 4;
    private static final int TOP_BAR_H         = 1;

    private static final int RIGHT_BAR_INSET_Y   = 6;
    private static final int RIGHT_BAR_X1_OFFSET = 5;
    private static final int RIGHT_BAR_W         = 1;

    private static final int BOTTOM_BAR_INSET_X   = 5;
    private static final int BOTTOM_BAR_X1_OFFSET = 0;
    private static final int BOTTOM_BAR_Y1_OFFSET = -4;
    private static final int BOTTOM_BAR_H         = 1;

    private static final int LEFT_BAR_INSET_Y   = 6;
    private static final int LEFT_BAR_X1_OFFSET = 5;
    private static final int LEFT_BAR_W         = 1;

    private static final Color DEFAULT_GRIP_COLOR       = new Color(0, 0, 0, 0);
    private static final Color DEFAULT_GRIP_HOVER_COLOR = new Color(255, 255, 255, 220);

    // Clickable hot areas
    private static final int DEFAULT_TOP_HOT_H    = 10;
    private static final int DEFAULT_RIGHT_HOT_W  = 10;
    private static final int DEFAULT_BOTTOM_HOT_H = 10; // height, not width
    private static final int DEFAULT_LEFT_HOT_W   = 10;

    // Constraints
    private static final int DEFAULT_MIN_W = 220;
    private static final int DEFAULT_MIN_H = 120;

    @Inject private Client client;
    @Inject private MouseManager mouseManager;

    @Setter private Supplier<Rectangle> baseBoundsProvider;
    @Setter @Nullable private ResizeListener listener;

    // Configurable properties
    @Getter @Setter private Supplier<Boolean> isResizable = () -> true;

    // Enable/disable each side independently
    @Getter @Setter private Supplier<Boolean> enableTop    = () -> true;
    @Getter @Setter private Supplier<Boolean> enableRight  = () -> true;
    @Getter @Setter private Supplier<Boolean> enableBottom = () -> true;
    @Getter @Setter private Supplier<Boolean> enableLeft   = () -> true;

    // Minimum size
    @Getter @Setter private int minWidth  = DEFAULT_MIN_W;
    @Getter @Setter private int minHeight = DEFAULT_MIN_H;

    // Hot zone sizes
    @Getter @Setter private int topHotHeight    = DEFAULT_TOP_HOT_H;
    @Getter @Setter private int rightHotWidth   = DEFAULT_RIGHT_HOT_W;
    @Getter @Setter private int bottomHotHeight = DEFAULT_BOTTOM_HOT_H;
    @Getter @Setter private int leftHotWidth    = DEFAULT_LEFT_HOT_W;

    // Grip colors
    @Getter @Setter private Color gripColor      = DEFAULT_GRIP_COLOR;
    @Getter @Setter private Color gripHoverColor = DEFAULT_GRIP_HOVER_COLOR;

    // Mouse
    private final MouseHandler mouseHandler = new MouseHandler();

    // State
    private Rectangle lastPanel = null;
    private final Rectangle topHot    = new Rectangle();
    private final Rectangle rightHot  = new Rectangle();
    private final Rectangle bottomHot = new Rectangle();
    private final Rectangle leftHot   = new Rectangle();

    @Getter @Setter private Supplier<Boolean> drawGrips = () -> true;

    @Getter private int widthOverride  = -1; // if <0, use base width
    @Getter private int heightOverride = -1; // if <0, use base height

    private boolean resizingTop    = false;
    private boolean resizingRight  = false;
    private boolean resizingBottom = false;
    private boolean resizingLeft   = false;
    private int dragStartX, dragStartY;
    private int startW, startH;

    private int currentCursor = Cursor.DEFAULT_CURSOR;

    public ResizePanel() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(Overlay.PRIORITY_HIGHEST);
    }

    public void startUp(Supplier<Boolean> isResizable) {
        mouseManager.registerMouseListener(1, mouseHandler);
        this.isResizable = isResizable;
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
     * Returns the effective panel rect using base and overrides.
     */
    public Rectangle getEffectivePanel() {
        if (baseBoundsProvider == null)
            return null;
        Rectangle base = baseBoundsProvider.get();
        if (base == null)
            return null;
        int w = widthOverride  > 0 ? Math.max(minWidth,  widthOverride)  : base.width;
        int h = heightOverride > 0 ? Math.max(minHeight, heightOverride) : base.height;
        return new Rectangle(base.x, base.y, w, h);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!isResizable.get() || baseBoundsProvider == null)
            return null;

        Rectangle base = baseBoundsProvider.get();
        if (base == null || base.width <= 0 || base.height <= 0) {
            lastPanel = null;
            return null;
        }

        // Apply overrides
        int w = widthOverride  > 0 ? Math.max(minWidth,  widthOverride)  : base.width;
        int h = heightOverride > 0 ? Math.max(minHeight, heightOverride) : base.height;
        Rectangle panel = new Rectangle(base.x, base.y, w, h);
        lastPanel = panel;

        // Build hot areas (only if side is enabled; otherwise clear rect)
        if (enableTop.get()) {
            topHot.setBounds(panel.x + TOP_BAR_INSET_X, panel.y, Math.max(0, panel.width - TOP_BAR_INSET_X * 2), topHotHeight);
        } else {
            topHot.setBounds(0, 0, 0, 0);
        }

        if (enableRight.get()) {
            rightHot.setBounds(panel.x + panel.width - rightHotWidth, panel.y + RIGHT_BAR_INSET_Y, rightHotWidth, Math.max(0, panel.height - RIGHT_BAR_INSET_Y * 2));
        } else {
            rightHot.setBounds(0, 0, 0, 0);
        }

        if (enableBottom.get()) {
            bottomHot.setBounds(panel.x + BOTTOM_BAR_INSET_X, panel.y + panel.height - bottomHotHeight, Math.max(0, panel.width - BOTTOM_BAR_INSET_X * 2), bottomHotHeight);
        } else {
            bottomHot.setBounds(0, 0, 0, 0);
        }

        if (enableLeft.get()) {
            leftHot.setBounds(panel.x, panel.y + LEFT_BAR_INSET_Y, leftHotWidth, Math.max(0, panel.height - LEFT_BAR_INSET_Y * 2));
        } else {
            leftHot.setBounds(0, 0, 0, 0);
        }

        if (!drawGrips.get()) return null;

        // Draw grips
        boolean topHover    = isMouseOver(topHot);
        boolean rightHover  = isMouseOver(rightHot);
        boolean bottomHover = isMouseOver(bottomHot);
        boolean leftHover   = isMouseOver(leftHot);

        // Top
        if (enableTop.get()) {
            g.setColor(topHover ? gripHoverColor : gripColor);
            int y1 = panel.y + TOP_BAR_Y1_OFFSET;
            int xL = panel.x + TOP_BAR_INSET_X + TOP_BAR_X1_OFFSET;
            int xR = panel.x + panel.width - TOP_BAR_INSET_X;
            g.fillRect(xL, y1, Math.max(0, xR - xL), TOP_BAR_H);
        }

        // Right
        if (enableRight.get()) {
            g.setColor(rightHover ? gripHoverColor : gripColor);
            int rx1 = panel.x + panel.width - rightHotWidth + RIGHT_BAR_X1_OFFSET;
            int ryT = panel.y + RIGHT_BAR_INSET_Y;
            int ryH = Math.max(0, panel.y + panel.height - RIGHT_BAR_INSET_Y - ryT);
            g.fillRect(rx1, ryT, RIGHT_BAR_W, ryH);
        }

        // Bottom
        if (enableBottom.get()) {
            g.setColor(bottomHover ? gripHoverColor : gripColor);
            int by1 = panel.y + panel.height - BOTTOM_BAR_H + BOTTOM_BAR_Y1_OFFSET;
            int bxL = panel.x + BOTTOM_BAR_INSET_X + BOTTOM_BAR_X1_OFFSET;
            int bxR = panel.x + panel.width - BOTTOM_BAR_INSET_X;
            g.fillRect(bxL, by1, Math.max(0, bxR - bxL), BOTTOM_BAR_H);
        }

        // Left
        if (enableLeft.get()) {
            g.setColor(leftHover ? gripHoverColor : gripColor);
            int lx1 = panel.x + LEFT_BAR_X1_OFFSET;
            int lyT = panel.y + LEFT_BAR_INSET_Y;
            int lyH = Math.max(0, panel.y + panel.height - LEFT_BAR_INSET_Y - lyT);
            g.fillRect(lx1, lyT, LEFT_BAR_W, lyH);
        }

        return null;
    }

    private boolean isMouseOver(Rectangle r) {
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

    private boolean isResizingAny() {
        return resizingTop || resizingRight || resizingBottom || resizingLeft;
    }

    private void updateHoverCursor(Point p) {
        if (p == null || lastPanel == null || !lastPanel.contains(p)) {
            setCanvasCursor(Cursor.DEFAULT_CURSOR);
            return;
        }
        if (enableTop.get() && topHot.contains(p) || enableBottom.get() && bottomHot.contains(p)) {
            setCanvasCursor(Cursor.N_RESIZE_CURSOR);
        } else if (enableRight.get() && rightHot.contains(p) || enableLeft.get() && leftHot.contains(p)) {
            setCanvasCursor(Cursor.E_RESIZE_CURSOR);
        } else {
            setCanvasCursor(Cursor.DEFAULT_CURSOR);
        }
    }

    // Mouse handler
    private final class MouseHandler implements MouseListener
    {
        private Point lastMovePoint = null;

        @Override public MouseEvent mouseClicked(MouseEvent e) { return e; }

        @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }

        @Override
        public MouseEvent mouseExited(MouseEvent e) {
            lastMovePoint = null;
            if (!isResizingAny())
                setCanvasCursor(Cursor.DEFAULT_CURSOR);
            return e;
        }

        @Override
        public MouseEvent mouseMoved(MouseEvent e) {
            if (!isResizable.get()) return e;

            lastMovePoint = e.getPoint();
            if (isResizingAny()) {
                // keep resize cursor consistent while dragging (guarded elsewhere)
                return e;
            }

            updateHoverCursor(lastMovePoint);
            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            if (!isResizable.get()) return e;
            if (lastPanel == null || !lastPanel.contains(e.getPoint())) return e;

            // TOP
            if (enableTop.get() && topHot.contains(e.getPoint())) {
                resizingTop = true;
                dragStartY = e.getY();
                startH = (heightOverride > 0 ? heightOverride : lastPanel.height);
                setCanvasCursor(Cursor.N_RESIZE_CURSOR);
                e.consume();
                return e;
            }

            // RIGHT
            if (enableRight.get() && rightHot.contains(e.getPoint())) {
                resizingRight = true;
                dragStartX = e.getX();
                startW = (widthOverride > 0 ? widthOverride : lastPanel.width);
                setCanvasCursor(Cursor.E_RESIZE_CURSOR);
                e.consume();
                return e;
            }

            // BOTTOM
            if (enableBottom.get() && bottomHot.contains(e.getPoint())) {
                resizingBottom = true;
                dragStartY = e.getY();
                startH = (heightOverride > 0 ? heightOverride : lastPanel.height);
                setCanvasCursor(Cursor.S_RESIZE_CURSOR);
                e.consume();
                return e;
            }

            // LEFT
            if (enableLeft.get() && leftHot.contains(e.getPoint())) {
                resizingLeft = true;
                dragStartX = e.getX();
                startW = (widthOverride > 0 ? widthOverride : lastPanel.width);
                setCanvasCursor(Cursor.W_RESIZE_CURSOR);
                e.consume();
                return e;
            }

            return e;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e) {
            if (!isResizable.get()) return e;
            lastMovePoint = e.getPoint();

            if (!isResizingAny()) return e;

            if (resizingTop) {
                int dy = e.getY() - dragStartY; // drag down -> increase height
                heightOverride = Math.max(minHeight, startH - dy);
            }
            if (resizingRight) {
                int dx = e.getX() - dragStartX; // drag right -> increase width
                widthOverride = Math.max(minWidth, startW + dx);
            }
            if (resizingBottom) {
                int dy = e.getY() - dragStartY; // drag down -> increase height
                heightOverride = Math.max(minHeight, startH + dy);
            }
            if (resizingLeft) {
                int dx = e.getX() - dragStartX; // drag right -> decrease width
                widthOverride = Math.max(minWidth, startW - dx);
            }

            // Notify listener (e.g., to reflow ChatOverlay/message area)
            if (listener != null && baseBoundsProvider != null) {
                Rectangle base = baseBoundsProvider.get();
                if (base != null) {
                    int w = widthOverride  > 0 ? widthOverride  : base.width;
                    int h = heightOverride > 0 ? heightOverride : base.height;
                    listener.onResize(w, h);
                }
            }

            e.consume();
            return e;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e) {
            if (isResizingAny()) {
                // stop any resizing state
                resizingTop = false;
                resizingRight = false;
                resizingBottom = false;
                resizingLeft = false;

                // update cursor based on current hover
                updateHoverCursor(e.getPoint());
                e.consume();
                return e;
            }
            return e;
        }
    }

    public void setSidesEnabled(boolean top, boolean right, boolean bottom, boolean left) {
        this.enableTop   = () -> top;
        this.enableRight = () -> right;
        this.enableBottom= () -> bottom;
        this.enableLeft  = () -> left;
    }

    public void setAllEnabled(boolean enabled) {
        setSidesEnabled(true, true, true, true);
    }

    public void setLeftEnabled(boolean enabled) {
        this.enableLeft = () -> enabled;
    }

    public void setRightEnabled(boolean enabled) {
        this.enableRight = () -> enabled;
    }

    public void setTopEnabled(boolean enabled) {
        this.enableTop = () -> enabled;
    }

    public void setBottomEnabled(boolean enabled) {
        this.enableBottom = () -> enabled;
    }
}
