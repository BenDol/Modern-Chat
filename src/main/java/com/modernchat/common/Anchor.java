package com.modernchat.common;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.awt.Rectangle;

public abstract class Anchor {

    protected Client client;
    protected Class<?> widgetClass;


    @Getter @Setter
    protected int offsetX = 0;
    @Getter @Setter
    protected int offsetY = 0;

    @Getter boolean isReset = false;

    protected Integer originalX;
    protected Integer originalY;
    protected Integer originalWidth;
    protected Integer originalHeight;
    protected Integer originalXMode;
    protected Integer originalYMode;
    protected Integer originalWidthMode;
    protected Integer originalHeightMode;

    public abstract Integer x(Widget widget, Widget target);
    public abstract Integer y(Widget widget, Widget target);
    public abstract Integer width(Widget widget, Widget target);
    public abstract Integer height(Widget widget, Widget target);
    public abstract Integer xMode(Widget widget, Widget target);
    public abstract Integer yMode(Widget widget, Widget target);
    public abstract Integer widthMode(Widget widget, Widget target);
    public abstract Integer heightMode(Widget widget, Widget target);

    public Anchor(Client client, Widget widget) {
        this.client = client;
        widgetClass = widget.getClass();
        originalXMode = widget.getXPositionMode();
        originalYMode = widget.getYPositionMode();
        originalWidthMode = widget.getWidthMode();
        originalHeightMode = widget.getHeightMode();
        originalY = widget.getOriginalY();
        originalHeight = widget.getOriginalHeight();
        originalX = widget.getOriginalX();
        originalWidth = widget.getOriginalWidth();
    }

    public void apply(Widget widget, Widget target) {
        if (widget.getClass() != widgetClass) {
            throw new IllegalArgumentException("Widget class does not match the original widget class.");
        }

        Rectangle targetBounds = target.getBounds();
        if (targetBounds == null || targetBounds.height <= 0)
            return;

        widget.setXPositionMode(xMode(widget, target));
        widget.setYPositionMode(yMode(widget, target));
        widget.setWidthMode(widthMode(widget, target));
        widget.setHeightMode(heightMode(widget, target));

        widget.setOriginalY(y(widget, target));
        widget.setOriginalHeight(height(widget, target));
        widget.setOriginalX(x(widget, target));
        widget.setOriginalWidth(width(widget, target));

        widget.revalidate();
        Widget parent = widget.getParent();
        if (parent != null) {
            parent.revalidate();
        }

        isReset = false;
    }

    public void reset(Widget widget) {
        if (widget.getClass() != widgetClass) {
            throw new IllegalArgumentException("Widget class does not match the original widget class.");
        }
        widget.setXPositionMode(originalXMode);
        widget.setYPositionMode(originalYMode);
        widget.setWidthMode(originalWidthMode);
        widget.setHeightMode(originalHeightMode);
        widget.setOriginalX(originalX);
        widget.setOriginalY(originalY);
        widget.setOriginalWidth(originalWidth);
        widget.setOriginalHeight(originalHeight);

        widget.revalidate();
        Widget parent = widget.getParent();
        if (parent != null) {
            parent.revalidate();
        }

        isReset = true;
    }
}
