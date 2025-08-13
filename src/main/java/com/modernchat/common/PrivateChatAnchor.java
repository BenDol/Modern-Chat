package com.modernchat.common;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;

import java.awt.Rectangle;

public class PrivateChatAnchor extends Anchor {

    public PrivateChatAnchor(Client client, Widget widget) {
        super(client, widget);
    }

    @Override
    public Integer xMode(Widget widget, Widget target) {
        return WidgetPositionMode.ABSOLUTE_LEFT;
    }

    @Override
    public Integer yMode(Widget widget, Widget target) {
        return WidgetPositionMode.ABSOLUTE_TOP;
    }

    @Override
    public Integer widthMode(Widget widget, Widget target) {
        return WidgetSizeMode.ABSOLUTE;
    }

    @Override
    public Integer heightMode(Widget widget, Widget target) {
        return WidgetSizeMode.ABSOLUTE;
    }

    @Override
    public Integer x(Widget widget, Widget target) {
        return Math.max(0, target.getBounds().x + getOffsetX());
    }

    @Override
    public Integer y(Widget widget, Widget target) {
        return -getLiftFromBottom(target);
    }

    @Override
    public Integer width(Widget widget, Widget target) {
        return Math.max(1, client.getCanvasWidth() - Math.max(0, target.getBounds().x + getOffsetX()));
    }

    @Override
    public Integer height(Widget widget, Widget target) {
        return client.getCanvasHeight();
    }

    protected int getLiftFromBottom(Widget targetWidget) {
        int liftPx = getOffsetY();

        Rectangle targetBounds = targetWidget.getBounds();
        if (targetBounds == null || targetBounds.height <= 0)
            return 0;

        final int canvasH = client.getCanvasHeight();

        int targetBottomY = Math.max(0, (targetBounds.y + targetBounds.height) - Math.max(0, liftPx));
        return canvasH - targetBottomY;
    }
}
