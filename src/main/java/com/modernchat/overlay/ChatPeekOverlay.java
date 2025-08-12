package com.modernchat.overlay;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.RuneFontStyle;
import com.modernchat.feature.ToggleChatFeature.ToggleChatFeatureConfig;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class ChatPeekOverlay extends Overlay
{
    private static final class Seg {
        final String text;
        final Color color;
        Seg(String t, Color c) {
            text=t; color=c;
        }
    }
    private static final class RichLine {
        final List<Seg> segs = new ArrayList<>();
    }
    private static final class VisualLine {
        final List<Seg> segs = new ArrayList<>();
    }

    private static final int PADDING = 8;
    private static final int MAX_LINES = 12;

    private final Client client;
    private final ModernChatConfig config;
    private final Deque<RichLine> lines = new ArrayDeque<>();
    private Font font = null;
    private RuneFontStyle fontStyle = null;

    @Inject
    public ChatPeekOverlay(Client client, ModernChatConfig config)
    {
        this.client = client;
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!config.featureToggle_peekOverlayEnabled())
            return  null;

        Widget chatbox = client.getWidget(InterfaceID.CHATBOX, 0);
        if (chatbox == null || !chatbox.isHidden())
            return null;

        Rectangle r = chatbox.getBounds();
        if (r == null)
            return null;

        int offsetX = config.featureToggle_peekOffsetX();
        int offsetY = config.featureToggle_peekOffsetY();

        // Apply offsets
        Rectangle box = new Rectangle(r.x + offsetX, r.y + offsetY, r.width, r.height);

        // Background
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(config.featureToggle_peekBgColor());
        g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);

        // Border (optional)
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(config.featureToggle_peekBorderColor());
        g.drawRoundRect(box.x, box.y, box.width, box.height, 8, 8);

        if (fontStyle == null || fontStyle != config.featureToggle_peekFontStyle()) {
            fontStyle = config.featureToggle_peekFontStyle();
            switch (fontStyle) {
                case NORMAL:
                    font = FontManager.getRunescapeFont();
                    break;
                case SMALL:
                    font = FontManager.getRunescapeSmallFont();
                    break;
                case BOLD:
                    font = FontManager.getRunescapeBoldFont();
                    break;
            }
        }

        float fontSize = config.featureToggle_peekFontSize();
        if (fontSize > 0)
            font = font.deriveFont(fontSize);

        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        final int lineH = fm.getHeight();
        final int xLeft = box.x + PADDING;
        final int xRight = box.x + box.width - PADDING;
        final int maxW = Math.max(1, xRight - xLeft);
        int y = box.y + box.height - PADDING;

        // Fit-by-height
        final int maxVisualLines = Math.max(1, (box.height - PADDING * 2) / lineH);
        int drawn = 0;

        Deque<RichLine> snapshot = new ArrayDeque<>(lines);
        for (Iterator<RichLine> it = snapshot.descendingIterator(); it.hasNext(); )
        {
            List<VisualLine> wrapped = wrapRichLine(it.next(), fm, maxW);

            for (int i = wrapped.size() - 1; i >= 0; i--)
            {
                y -= lineH;
                if (y < box.y + PADDING)
                    return null;
                if (++drawn > maxVisualLines)
                    return null;

                int dx = xLeft;
                for (Seg seg : wrapped.get(i).segs)
                {
                    if (dx == xLeft && seg.text.isBlank()) continue;

                    // shadow
                    g.setColor(new Color(0,0,0,200));
                    g.drawString(seg.text, dx + 1, y + 1);

                    // colored text
                    g.setColor(seg.color);
                    g.drawString(seg.text, dx, y);

                    dx += fm.stringWidth(seg.text);
                    if (dx > xRight) break;
                }
            }
        }
        return null;
    }

    public void pushLine(String s, Color c)
    {
        pushRich(parseColored(s == null ? "" : s, c == null ? Color.WHITE : c));

    }
    public void pushRuneScapeFormatted(String rsFormatted)
    {
        pushRich(parseColored(rsFormatted, Color.WHITE));
    }

    private void pushRich(RichLine rl)
    {
        if (rl == null || rl.segs.isEmpty()) return;
        lines.addLast(rl);
        while (lines.size() > MAX_LINES) lines.removeFirst();
    }

    // Minimal parser: <col=HEX>, </col>, <br>, <lt>, <gt>
    private RichLine parseColored(String s, Color base)
    {
        RichLine out = new RichLine();
        if (s == null) return out;

        Deque<Color> stack = new ArrayDeque<>();
        Color cur = base;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < s.length(); )
        {
            char ch = s.charAt(i);
            if (ch == '<')
            {
                int j = s.indexOf('>', i + 1);
                if (j < 0) break;

                String tag = s.substring(i + 1, j).toLowerCase(java.util.Locale.ROOT);

                if (tag.equals("lt")) { buf.append('<'); i = j + 1; continue; }
                if (tag.equals("gt")) { buf.append('>'); i = j + 1; continue; }

                if (buf.length() > 0)
                {
                    out.segs.add(new Seg(buf.toString(), cur)); buf.setLength(0);
                }

                if (tag.startsWith("col="))
                {
                    stack.push(cur); cur = parseHexColor(tag.substring(4), cur);
                }
                else if (tag.equals("/col"))
                {
                    cur = stack.isEmpty() ? base : stack.pop();
                }
                else if (tag.equals("br"))
                {
                    if (out.segs.isEmpty())
                        out.segs.add(new Seg("", cur));
                    pushRich(out);
                    out = new RichLine();
                }

                i = j + 1;
            }
            else { buf.append(ch); i++; }
        }
        if (buf.length() > 0)
            out.segs.add(new Seg(buf.toString(), cur));
        return out;
    }

    private static Color parseHexColor(String hex, Color fallback)
    {
        try {
            String h = hex.trim();
            if (h.startsWith("#"))
                h = h.substring(1);

            if (h.length() == 3) {
                char r=h.charAt(0), g=h.charAt(1), b=h.charAt(2);
                h = ""+r+r+g+g+b+b;
            }

            long v = Long.parseLong(h, 16);
            if (h.length() <= 6)
                return new Color(((int)v & 0xFFFFFF) | 0xFF000000, true);

            return new Color((int)v, true);
        }
        catch (Exception ignored) {
            return fallback;
        }
    }

    private List<VisualLine> wrapRichLine(RichLine rl, FontMetrics fm, int maxWidth)
    {
        List<VisualLine> out = new ArrayList<>();
        VisualLine cur = new VisualLine();
        int curW = 0;

        for (Seg s : rl.segs)
        {
            String txt = s.text;
            int i = 0;

            while (i < txt.length())
            {
                int nextSpace = -1;
                for (int k = i; k < txt.length(); k++) {
                    char c = txt.charAt(k);
                    if (c == ' ' || c == '\u00A0') {
                        nextSpace = k; break;
                    }
                }

                int endWord = (nextSpace == -1 ? txt.length() : nextSpace);
                String word = txt.substring(i, endWord);
                String space = (nextSpace == -1 ? "" : txt.substring(endWord, endWord + 1));

                int wordW = fm.stringWidth(word);
                if (wordW > maxWidth)
                {
                    int start = 0;
                    while (start < word.length())
                    {
                        int fit = fitCharsForWidth(fm, word, start, maxWidth - curW);
                        if (fit == 0)
                        {
                            if (!cur.segs.isEmpty()) {
                                out.add(cur);
                                cur = new VisualLine();
                                curW = 0;
                                continue;
                            }
                            fit = Math.max(1, fitCharsForWidth(fm, word, start, maxWidth));
                        }
                        String part = word.substring(start, start + fit);
                        cur.segs.add(new Seg(part, s.color));
                        curW += fm.stringWidth(part);
                        start += fit;

                        if (start < word.length()) {
                            out.add(cur);
                            cur = new VisualLine();
                            curW = 0;
                        }
                    }
                }
                else
                {
                    if (curW + wordW > maxWidth) {
                        out.add(cur);
                        cur = new VisualLine();
                        curW = 0;
                    }

                    if (!word.isEmpty()) {
                        cur.segs.add(new Seg(word, s.color));
                        curW += wordW;
                    }
                }

                if (!space.isEmpty())
                {
                    int spW = fm.stringWidth(space);
                    if (curW + spW > maxWidth) {
                        out.add(cur);
                        cur = new VisualLine();
                        curW = 0;
                    }
                    cur.segs.add(new Seg(space, s.color));
                    curW += spW;
                }

                i = (nextSpace == -1) ? txt.length() : nextSpace + 1;
            }
        }

        if (!cur.segs.isEmpty())
            out.add(cur);
        return out;
    }

    private int fitCharsForWidth(FontMetrics fm, String s, int start, int remainingWidth)
    {
        if (remainingWidth <= 0)
            return 0;

        int lo = start, hi = s.length(), ans = start;
        while (lo <= hi)
        {
            int mid = (lo + hi) >>> 1;
            String sub = s.substring(start, mid);
            int w = fm.stringWidth(sub);
            if (w <= remainingWidth) {
                ans = mid; lo = mid + 1;
            }
            else hi = mid - 1;
        }
        return Math.max(0, ans - start);
    }
}
