package com.modernchat.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.GlyphVector;

public final class TextDrawUtil
{
    private TextDrawUtil() {}

    /**
     * Draw text with either a diagonal drop shadow or a stroked outline.
     * <p>
     * When {@code outlineThickness > 0} the text shape is stroked with a round-join
     * {@link BasicStroke} of that width, then filled on top — producing a smooth,
     * uniform outline at any thickness.
     * <p>
     * When {@code outlineThickness == 0} the legacy diagonal drop-shadow is used instead.
     *
     * @param g                graphics context
     * @param text             the string to draw
     * @param x                baseline x
     * @param y                baseline y
     * @param textColor        foreground color
     * @param shadowColor      shadow / outline color
     * @param shadowOffset     pixel distance for the diagonal drop shadow
     * @param outlineThickness outline stroke width in pixels; 0 = drop shadow only
     */
    public static void drawTextWithShadow(Graphics2D g, String text, int x, int y,
                                           Color textColor, Color shadowColor,
                                           int shadowOffset, int outlineThickness)
    {
        if (outlineThickness > 0 && shadowColor.getAlpha() > 0)
        {
            GlyphVector gv = g.getFont().createGlyphVector(g.getFontRenderContext(), text);
            Shape textShape = gv.getOutline(x, y);

            Stroke oldStroke = g.getStroke();
            // Stroke is centered on the path, so double the width; the inner half
            // is covered when we fill the text on top.
            g.setStroke(new BasicStroke(outlineThickness * 2f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(shadowColor);
            g.draw(textShape);
            g.setStroke(oldStroke);

            g.setColor(textColor);
            g.fill(textShape);
        }
        else if (shadowOffset > 0 && shadowColor.getAlpha() > 0)
        {
            g.setColor(shadowColor);
            g.drawString(text, x + shadowOffset, y + shadowOffset);
            g.setColor(textColor);
            g.drawString(text, x, y);
        }
        else
        {
            g.setColor(textColor);
            g.drawString(text, x, y);
        }
    }
}
