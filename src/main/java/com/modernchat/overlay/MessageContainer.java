package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.common.FontStyle;
import com.modernchat.draw.Margin;
import com.modernchat.draw.Padding;
import com.modernchat.draw.RichLine;
import com.modernchat.draw.RowHit;
import com.modernchat.draw.TextSegment;
import com.modernchat.draw.VisualLine;
import com.modernchat.feature.ToggleChatFeature;
import com.modernchat.service.FontService;
import com.modernchat.util.ColorUtil;
import com.modernchat.util.FormatUtil;
import com.modernchat.util.GeometryUtil;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Point;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class MessageContainer extends Overlay
{
    private static final int MAX_LINES = 20;
    private static final int MIN_THUMB_H = 24;
    private static final int SCROLL_TO_BOTTOM_SENTINEL = Integer.MAX_VALUE;

    @Inject private MouseManager mouseManager;
    @Inject private FontService fontService;

    // Config
    @Getter private MessageContainerConfig config;
    @Getter private ChatMode chatMode;
    @Getter @Setter private boolean chromeEnabled = true;
    @Getter @Setter private Supplier<Rectangle> boundsProvider;
    @Getter @Setter private Function<MessageContainer, Boolean> canDrawDecider = mc -> true;

    // State
    @Getter @Setter private boolean hidden = false;
    @Getter @Setter private boolean isPrivate = false;
    @Getter @Setter private volatile float alpha = 1f;

    private final Deque<RichLine> lines = new ArrayDeque<>();
    private Font lineFont = null;
    private FontStyle lineFontStyle = null;

    // Viewport and scrolling
    private Rectangle lastViewport = null;                 // full container viewport
    private final Rectangle msgViewport = new Rectangle(); // message area (excludes any input, if you add later)
    @Getter @Setter private int scrollOffsetPx = 0;        // top-anchored pixel offset
    private int contentHeightPx = 0;                       // total content height (in pixels)
    @Getter @Setter private boolean userScrolled = false;  // when false, we auto-stick to bottom on new lines
    @Getter private int lastLineHeight = 16;               // cached from render for wheel step

    // Scrollbar geometry for hit-tests
    private final Rectangle thumb = new Rectangle(0, 0, 0, 0);
    @Getter private int trackTop = 0;
    @Getter private int trackHeight = 1;
    @Getter private int maxScroll = 0;

    @Getter private final MouseHandler mouse = new MouseHandler();

    public MessageContainer() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public void startUp(MessageContainerConfig config, ChatMode chatMode) {
        this.config = config;
        this.chatMode = chatMode;

        registerMouseListener();
    }

    public void shutDown() {
        unregisterMouseListener();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!isEnabled() || hidden)
            return null;

        if (!canShow())
            return null;

        Rectangle vp = boundsProvider.get();
        if (vp == null || vp.width <= 0 || vp.height <= 0)
            return null;

        // Cache the viewport for wheel/drag hit-tests
        lastViewport = calculateViewPort(vp);

        // Padding and layout
        final Padding pad = config.getPadding();
        final int sbW = config.getScrollbarWidth();
        final int innerW = Math.max(1, lastViewport.width - pad.getLeft() - pad.getRight() - sbW - 6); // room for scrollbar
        final int left = lastViewport.x + pad.getLeft();
        final int right = lastViewport.x + lastViewport.width - pad.getRight();
        final int top = lastViewport.y + pad.getTop();
        final int bottom = lastViewport.y + lastViewport.height - pad.getBottom();

        // Our message viewport
        msgViewport.setBounds(left, top, innerW, bottom - top);

        // Respect external alpha
        final Composite oldComp = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        try {
            if (chromeEnabled) {
                // Backdrop and border
                g.setColor(config.getBackdropColor());
                g.fillRoundRect(lastViewport.x, lastViewport.y, lastViewport.width, lastViewport.height, 8, 8);

                g.setColor(config.getBorderColor());
                g.drawRoundRect(lastViewport.x, lastViewport.y, lastViewport.width, lastViewport.height, 8, 8);
            }

            // Font styles
            Font font = getLineFont();
            float fontSize = config.getLineFontSize();
            if (fontSize > 0) font = font.deriveFont(fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            final int lineH = fm.getAscent() + fm.getDescent() + config.getLineSpacing();
            lastLineHeight = Math.max(1, lineH);

            // Flatten wrapped lines (oldest to newest)
            final List<VisualLine> all = new ArrayList<>(64);
            for (RichLine rl : lines) {
                if (rl.getLineCache() == null) {
                    rl.setLineCache(wrapRichLine(rl, fm, innerW));
                }
                final List<VisualLine> cache = rl.getLineCache();
                if (cache != null && !cache.isEmpty())
                    all.addAll(cache);
            }

            // Measure content height and auto-stick to bottom when needed
            contentHeightPx = all.size() * lineH + 5;

            if (scrollOffsetPx == SCROLL_TO_BOTTOM_SENTINEL || (!userScrolled && contentHeightPx <= msgViewport.height)) {
                scrollOffsetPx = Math.max(0, contentHeightPx - msgViewport.height);
            }
            // Clamp scroll
            scrollOffsetPx = clamp(scrollOffsetPx, 0, Math.max(0, contentHeightPx - msgViewport.height));

            // Clip to message viewport and draw from top honoring scroll
            Shape oldClip = g.getClip();
            g.setClip(msgViewport);

            int y = msgViewport.y - scrollOffsetPx + fm.getAscent();
            for (VisualLine vl : all) {
                if (y - fm.getAscent() > msgViewport.y + msgViewport.height)
                    break; // below viewport
                if (y + fm.getDescent() >= msgViewport.y) {
                    int dx = left;
                    for (TextSegment seg : vl.getSegs()) {
                        if (dx == left && seg.getText().isBlank()) continue;

                        int shadowOffset = config.getTextShadow();
                        if (shadowOffset > 0) {
                            g.setColor(config.getShadowColor());
                            g.drawString(seg.getText(), dx + shadowOffset, y + shadowOffset);
                        }

                        g.setColor(seg.getColor());
                        g.drawString(seg.getText(), dx, y);

                        dx += fm.stringWidth(seg.getText());
                        if (dx > right) break;
                    }
                }
                y += lineH;
            }

            g.setClip(oldClip);

            drawScrollbar(g, msgViewport, sbW);
        } finally {
            g.setComposite(oldComp);
        }
        return null;
    }

    private void drawScrollbar(Graphics2D g, Rectangle view, int sbW) {
        if (!config.isDrawScrollbar()) {
            return;
        }

        // Track
        final int trackX = view.x + view.width + 7; // a bit inside the right border
        final int height = view.height - 1;

        if (contentHeightPx <= height) {
            // Nothing to scroll, clear thumb hitbox so dragging is disabled
            thumb.setBounds(0, 0, 0, 0);
            trackTop = 0; trackHeight = 1; maxScroll = 0;
            return;
        }

        g.setColor(config.getScrollbarTrackColor());
        g.fillRoundRect(trackX, view.y, sbW, height, sbW, sbW);

        // Thumb
        int thumbH = Math.max(MIN_THUMB_H, (int) (height * (height / (double) contentHeightPx)));
        int maxThumbTravel = height - thumbH;
        int maxScrollLocal = contentHeightPx - height;
        int thumbY = view.y + (maxScrollLocal == 0 ? 0 :
            (int) Math.round(maxThumbTravel * (scrollOffsetPx / (double) maxScrollLocal)));

        g.setColor(config.getScrollbarThumbColor());
        g.fillRoundRect(trackX, thumbY, sbW, thumbH, sbW, sbW);

        // Update drag geometry for mouse handler
        thumb.setBounds(trackX, thumbY, sbW, thumbH);
        trackTop = view.y;
        trackHeight = view.height;
        maxScroll = maxScrollLocal;

        mouse.updateScrollbar(thumb.x, thumb.y, thumb.width, thumb.height, trackTop, trackHeight, maxScroll);
    }

    private Font getLineFont() {
        if (lineFontStyle == null || lineFontStyle != config.getLineFontStyle()) {
            lineFontStyle = config.getLineFontStyle();
            lineFont = null;
        }
        if (lineFont == null)
            lineFont = fontService.getFont(lineFontStyle != null ? lineFontStyle : FontStyle.RUNE);
        return lineFont;
    }

    private Rectangle calculateViewPort(Rectangle r) {
        if (GeometryUtil.isInvalidChatBounds(r)) {
            if (lastViewport == null) {
                if (GeometryUtil.isInvalidChatBounds(ToggleChatFeature.LAST_CHAT_BOUNDS))
                    return null;
                r = ToggleChatFeature.LAST_CHAT_BOUNDS;
            } else {
                r = lastViewport;
            }
        }

        lastViewport = r;

        Margin margin = config.getMargin();
        int width = r.width - margin.getRight();
        int height = r.height - margin.getBottom();
        Point offset = config.getOffset();

        return new Rectangle(r.x + offset.getX(), r.y + offset.getY(), width, height);
    }

    public void clearMessages() {
        lines.clear();
        clearChatWidget();
    }

    public void clearChatWidget() {
        lastViewport = null;
    }

    public boolean canShow() {
        return canDrawDecider.apply(this);
    }

    private Color getColor(ChatMessageType type) {
        Color c;
        switch (type) {
            case PUBLICCHAT:
                c = config.getPublicColor();
                break;
            case FRIENDSCHATNOTIFICATION:
            case FRIENDSCHAT:
                c = config.getFriendColor();
                break;
            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
                c = config.getClanColor();
                break;
            case PRIVATECHATOUT:
            case PRIVATECHAT:
            case FRIENDNOTIFICATION:
                c = config.getPrivateColor();
                break;
            case WELCOME:
                c = Color.WHITE;
                break;
            default:
                c = config.getSystemColor();
        }
        return c == null ? Color.WHITE : c;
    }

    public void pushLine(
        String s,
        ChatMessageType type,
        long timestamp,
        String sender,
        String receiver,
        String targetName,
        String prefix
    ) {
        type = type == null ? ChatMessageType.GAMEMESSAGE : type;
        Color c = getColor(type);
        RichLine rl = parseColored(s == null ? "" : s, c == null ? Color.WHITE : c);
        rl.setType(type);
        rl.setTimestamp(timestamp);
        rl.setSender(sender);
        rl.setReceiver(receiver);
        rl.setTargetName(targetName);
        rl.setPrefixCache(prefix);
        pushRich(rl);
    }

    private void pushRich(RichLine rl) {
        if (rl == null || rl.getSegs().isEmpty()) return;
        lines.addLast(rl);
        while (lines.size() > MAX_LINES) lines.removeFirst();

        // If we haven't scrolled up, auto-stick to bottom on next render
        if (!userScrolled) {
            scrollOffsetPx = SCROLL_TO_BOTTOM_SENTINEL;
        }
    }

    private RichLine parseColored(String s, Color base) {
        RichLine out = new RichLine();
        if (s == null) return out;

        Deque<Color> stack = new ArrayDeque<>();
        Color cur = base;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < s.length(); ) {
            char ch = s.charAt(i);
            if (ch == '<') {
                int j = s.indexOf('>', i + 1);
                if (j < 0) break;

                String tag = s.substring(i + 1, j).toLowerCase(java.util.Locale.ROOT);

                if (tag.equals("lt")) { buf.append('<'); i = j + 1; continue; }
                if (tag.equals("gt")) { buf.append('>'); i = j + 1; continue; }

                if (buf.length() > 0) {
                    out.getSegs().add(new TextSegment(buf.toString(), cur));
                    buf.setLength(0);
                }

                if (tag.startsWith("col=")) {
                    stack.push(cur);
                    cur = ColorUtil.parseHexColor(tag.substring(4), cur);
                } else if (tag.equals("/col")) {
                    cur = stack.isEmpty() ? base : stack.pop();
                } else if (tag.equals("br")) {
                    if (out.getSegs().isEmpty())
                        out.getSegs().add(new TextSegment("", cur));
                    pushRich(out);
                    out = new RichLine();
                }

                i = j + 1;
            } else {
                buf.append(ch);
                i++;
            }
        }
        if (buf.length() > 0)
            out.getSegs().add(new TextSegment(buf.toString(), cur));
        return out;
    }

    private List<VisualLine> wrapRichLine(RichLine rl, FontMetrics fm, int maxWidth) {
        List<VisualLine> out = new ArrayList<>();
        VisualLine cur = new VisualLine();
        int curW = 0;
        int segIndex = 0;
        boolean hasPrefix = false;

        if (StringUtil.isNullOrEmpty(rl.getPrefixCache()))
            rl.setPrefixCache(getPrefix(rl.getType()));

        if (rl.getTimestampCache() == null && rl.getTimestamp() > 0)
            rl.setTimestampCache("[" + FormatUtil.toHmTime(rl.getTimestamp()) + "] ");

        for (TextSegment s : rl.getSegs()) {
            if (s.getTextCache() == null) {
                if (segIndex == 0) hasPrefix = s.getText().startsWith("[");
                if (segIndex == 0) {
                    if (!hasPrefix && config.isPrefixChatType())
                        s.setTextCache(rl.getPrefixCache() + s.getText());
                    else
                        s.setTextCache(s.getText());

                    if (!StringUtil.isNullOrEmpty(rl.getTimestampCache()) && config.isShowTimestamp())
                        s.setTextCache(rl.getTimestampCache() + s.getTextCache());
                }
            }
            int i = 0;
            segIndex++;
            String txt = s.getTextCache() != null ? s.getTextCache() : s.getText();

            while (i < txt.length()) {
                int nextSpace = -1;
                for (int k = i; k < txt.length(); k++) {
                    char c = txt.charAt(k);
                    if (c == ' ' || c == '\u00A0') {
                        nextSpace = k;
                        break;
                    }
                }

                int endWord = (nextSpace == -1 ? txt.length() : nextSpace);
                String word = txt.substring(i, endWord);
                String space = (nextSpace == -1 ? "" : txt.substring(endWord, endWord + 1));

                int wordW = fm.stringWidth(word);
                if (wordW > maxWidth) {
                    int start = 0;
                    while (start < word.length()) {
                        int fit = fitCharsForWidth(fm, word, start, maxWidth - curW);
                        if (fit == 0) {
                            if (!cur.getSegs().isEmpty()) {
                                out.add(cur);
                                cur = new VisualLine();
                                curW = 0;
                                continue;
                            }
                            fit = Math.max(1, fitCharsForWidth(fm, word, start, maxWidth));
                        }
                        String part = word.substring(start, start + fit);
                        cur.getSegs().add(new TextSegment(part, s.getColor()));
                        curW += fm.stringWidth(part);
                        start += fit;

                        if (start < word.length()) {
                            out.add(cur);
                            cur = new VisualLine();
                            curW = 0;
                        }
                    }
                } else {
                    if (curW + wordW > maxWidth) {
                        out.add(cur);
                        cur = new VisualLine();
                        curW = 0;
                    }
                    if (!word.isEmpty()) {
                        cur.getSegs().add(new TextSegment(word, s.getColor()));
                        curW += wordW;
                    }
                }

                if (!space.isEmpty()) {
                    int spW = fm.stringWidth(space);
                    if (curW + spW > maxWidth) {
                        out.add(cur);
                        cur = new VisualLine();
                        curW = 0;
                    }
                    cur.getSegs().add(new TextSegment(space, s.getColor()));
                    curW += spW;
                }
                i = (nextSpace == -1) ? txt.length() : nextSpace + 1;
            }
        }
        if (!cur.getSegs().isEmpty())
            out.add(cur);
        return out;
    }

    private String getPrefix(ChatMessageType type) {
        String prefix = "";
        switch (type) {
            case PUBLICCHAT:
            case PRIVATECHAT:
            case PRIVATECHATOUT:
            case FRIENDSCHAT:
            case FRIENDSCHATNOTIFICATION:
            case FRIENDNOTIFICATION:
                break;
            case CLAN_CHAT:
            case CLAN_GIM_FORM_GROUP:
            case CLAN_GUEST_CHAT:
            case CLAN_GUEST_MESSAGE:
                prefix = "[Clan] ";
                break;
            case NPC_SAY:
                prefix = "[NPC] ";
                break;
            case TRADE_SENT:
            case TRADEREQ:
                prefix = "[Trade] ";
                break;
            case SPAM:
                prefix = "[Spam] ";
                break;
            default:
                prefix = "[System] ";
        }
        return prefix;
    }

    private int fitCharsForWidth(FontMetrics fm, String s, int start, int remainingWidth) {
        if (remainingWidth <= 0)
            return 0;
        int lo = start, hi = s.length(), ans = start;
        while (lo <= hi) {
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

    public void dirty() {
        for (RichLine line : lines) {
            line.resetCache();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public void pushLines(List<String> lines) {
        pushLines(lines, ChatMessageType.GAMEMESSAGE);
    }

    public void pushLines(List<String> lines, ChatMessageType type) {
        for (String line : lines) {
            pushLine(line, type, System.currentTimeMillis(), null, null, null, null);
        }
    }

    public @Nullable RowHit rowAt(Point p) {
        if (hidden || lastViewport == null || msgViewport.isEmpty() || !msgViewport.contains(new java.awt.Point(p.getX(), p.getY())))
            return null;

        // If we haven't measured content yet, bail (render() populates these).
        if (lastLineHeight <= 0 || contentHeightPx <= 0)
            return null;

        // Use current (possibly auto-stick) scroll offset
        final int viewportH = Math.max(1, msgViewport.height);
        final int effectiveScroll = (scrollOffsetPx == SCROLL_TO_BOTTOM_SENTINEL)
            ? Math.max(0, contentHeightPx - viewportH)
            : Math.max(0, Math.min(scrollOffsetPx, Math.max(0, contentHeightPx - viewportH)));

        // Convert mouse Y to global wrapped-row index
        final int relY = p.getY() - msgViewport.y + effectiveScroll; // 0 at content top
        if (relY < 0 || relY >= contentHeightPx) return null;

        final int rowH = lastLineHeight;
        final int visualIndex = relY / rowH;

        int cm = 0;
        for (RichLine rl : lines)
        {
            final List<VisualLine> cache = rl.getLineCache();
            if (cache == null || cache.isEmpty()) continue;

            final int n = cache.size();
            if (visualIndex < cm + n)
            {
                final VisualLine vl = cache.get(visualIndex - cm);

                // Compute this row's on-screen rect on the fly
                final int yTop = msgViewport.y + visualIndex * rowH - effectiveScroll;
                final Rectangle r = new Rectangle(msgViewport.x, yTop, msgViewport.width, rowH);

                return new RowHit(r, rl, vl);
            }
            cm += n;
        }
        return null;
    }

    public void clear() {
        lines.clear();
    }

    public void registerMouseListener() {
        mouseManager.registerMouseListener(1, mouse);
        mouseManager.registerMouseWheelListener(mouse);
    }

    public void unregisterMouseListener() {
        mouseManager.unregisterMouseListener(mouse);
        mouseManager.unregisterMouseWheelListener(mouse);
    }

    private final class MouseHandler implements MouseListener, MouseWheelListener
    {
        private final Rectangle thumb = new Rectangle(0,0,0,0);
        private int trackTop = 0, trackHeight = 1, maxScroll = 0;

        private boolean dragging = false;
        private int dragOffsetY = 0; // pointer offset within the thumb

        void updateScrollbar(int x, int y, int w, int h, int trackTop, int trackHeight, int maxScroll) {
            this.thumb.setBounds(x, y, w, h);
            this.trackTop = trackTop;
            this.trackHeight = Math.max(1, trackHeight);
            this.maxScroll = Math.max(0, maxScroll);
        }

        @Override public MouseEvent mouseClicked(MouseEvent e) { return e; }
        @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
        @Override public MouseEvent mouseExited(MouseEvent e) { return e; }
        @Override public MouseEvent mouseMoved(MouseEvent e) { return e; }

        @Override
        public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e) {
            if (!isEnabled() || isHidden())
                return e;
            if (!config.isScrollable())
                return e;

            if (lastViewport == null || !lastViewport.contains(e.getPoint()))
                return e;
            if (msgViewport.isEmpty() || !msgViewport.contains(e.getPoint()))
                return e;

            final int viewportH = Math.max(1, msgViewport.height);

            final int maxScrollLocal = Math.max(0, contentHeightPx - viewportH);
            if (maxScrollLocal == 0)
                return e;

            // Normalize sentinel to a real bottom offset; also clamp any stale value
            int offset = (scrollOffsetPx == SCROLL_TO_BOTTOM_SENTINEL)
                ? maxScrollLocal
                : clamp(scrollOffsetPx, 0, maxScrollLocal);

            // Use precise rotation
            final double ticks = e.getPreciseWheelRotation();
            final int stepPx = Math.max(1, config.getScrollStep());
            final int deltaPx = (int) Math.round(ticks * stepPx);

            // Apply and clamp
            offset = clamp(offset + deltaPx, 0, maxScrollLocal);
            scrollOffsetPx = offset;

            // Mark whether user is away from the bottom
            userScrolled = (maxScrollLocal - offset) > 2;

            e.consume();
            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            if (!isEnabled() || isHidden())
                return e;
            if (lastViewport == null || !lastViewport.contains(e.getPoint()))
                return e;

            if (thumb.contains(e.getPoint())) {
                dragging = true;
                dragOffsetY = e.getY() - thumb.y;
                e.consume(); // consume press
                return e;
            }
            return e;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e) {
            if (!isEnabled() || isHidden() || !dragging)
                return e;

            int thumbTravel = trackHeight - thumb.height;
            int newThumbY = clamp(e.getY() - dragOffsetY, trackTop, trackTop + thumbTravel);
            double p = thumbTravel == 0 ? 0.0 : (newThumbY - trackTop) / (double) thumbTravel;

            scrollOffsetPx = (int) Math.round(maxScroll * p);
            userScrolled = scrollOffsetPx < maxScroll - 2;

            e.consume();
            return e; // consume drag
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e) {
            dragging = false;
            return e;
        }

        private int clamp(int v, int lo, int hi) {
            return Math.max(lo, Math.min(hi, v));
        }
    }
}
