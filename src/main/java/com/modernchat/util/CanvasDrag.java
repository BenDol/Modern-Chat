package com.modernchat.util;

import net.runelite.api.Client;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;

/**
 * Follows a mouse drag in real canvas space.
 * <p>
 * With Stretched Mode on, {@link ClientUtil#getMouseCanvasPoint} reads the client's tracked
 * mouse position, because the event itself can arrive in stretched (window) space. That
 * position only moves when the game sees the event, so it freezes for a drag Modern Chat
 * consumes (resizing, scrollbar, text selection, tab reordering). A drag is therefore anchored
 * at the press, where the tracked position is still right, and moved by the event's own
 * movement, scaled to canvas space when the events arrive in window space.
 * <p>
 * Without Stretched Mode the point is always the event's own, as before.
 * Used from the AWT event thread only.
 */
public final class CanvasDrag
{
    private Point canvasStart;
    private Point eventStart;
    private double scaleX = 1;
    private double scaleY = 1;

    /**
     * Starts following a drag at this press.
     *
     * @return the press position in canvas space
     */
    public Point begin(Client client, MouseEvent e) {
        Point canvas = ClientUtil.getMouseCanvasPoint(client, e);
        if (!client.isStretchedEnabled()) {
            begin(canvas, e.getPoint(), null, null);
        } else {
            begin(canvas, e.getPoint(), client.getRealDimensions(), client.getStretchedDimensions());
        }
        return new Point(canvas);
    }

    /**
     * The event's position in canvas space: relative to the press when a drag was begun,
     * otherwise read the usual way.
     */
    public Point point(Client client, MouseEvent e) {
        if (canvasStart == null)
            return ClientUtil.getMouseCanvasPoint(client, e);
        return point(e.getPoint());
    }

    /** Stops following the drag. */
    public void end() {
        canvasStart = null;
        eventStart = null;
        scaleX = 1;
        scaleY = 1;
    }

    // Visible for tests
    void begin(Point canvas, Point event, Dimension real, Dimension stretched) {
        canvasStart = new Point(canvas);
        eventStart = new Point(event);
        scaleX = 1;
        scaleY = 1;

        if (real == null || stretched == null || stretched.width <= 0 || stretched.height <= 0)
            return;

        double sx = real.width / (double) stretched.width;
        double sy = real.height / (double) stretched.height;
        if (sx == 1 && sy == 1)
            return;

        // The events are already in canvas space when Stretched Mode's translating listener
        // ran ahead of ours, and in window space when it did not (seen after a launch with
        // Stretched Mode on). Whichever reading lands on the tracked position is the one.
        double asIs = event.distance(canvas);
        double scaled = Point.distance(event.x * sx, event.y * sy, canvas.x, canvas.y);
        if (scaled < asIs) {
            scaleX = sx;
            scaleY = sy;
        }
    }

    // Visible for tests
    Point point(Point event) {
        return new Point(
            canvasStart.x + (int) Math.round((event.x - eventStart.x) * scaleX),
            canvasStart.y + (int) Math.round((event.y - eventStart.y) * scaleY));
    }
}
