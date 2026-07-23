package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.common.FontStyle;
import com.modernchat.common.MessageLine;
import com.modernchat.draw.ImageSegment;
import com.modernchat.draw.Margin;
import com.modernchat.draw.Padding;
import com.modernchat.draw.PrefixSegment;
import com.modernchat.draw.RichLine;
import com.modernchat.draw.RowHit;
import com.modernchat.draw.SenderSegment;
import com.modernchat.draw.TextSegment;
import com.modernchat.draw.TimestampSegment;
import com.modernchat.draw.VisualLine;
import com.modernchat.feature.ToggleChatFeature;
import com.modernchat.service.FontService;
import com.modernchat.service.ForceRecolorService;
import com.modernchat.service.ImageService;
import com.modernchat.service.MessageFilterService;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.ColorUtil;
import com.modernchat.util.FormatUtil;
import com.modernchat.util.GeometryUtil;
import com.modernchat.util.MathUtil;
import com.modernchat.util.StringUtil;
import com.modernchat.util.TextDrawUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Point;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
public class MessageContainer extends Overlay
{
    private static final int DEFAULT_MAX_LINES = 20;
    private static final int MIN_THUMB_H = 24;
    private static final int SCROLL_TO_BOTTOM_SENTINEL = Integer.MAX_VALUE;
    /** Max recent lines walked per container when re-checking MessageNodes for edits */
    public static final int MAX_REFRESH_LINES = 50;
    public static final long EDIT_REFRESH_WINDOW_MS = 15_000L;
    /** RuneLite's default game-message highlight (ChatColorConfig #EF1020), used when unconfigured */
    private static final Color RUNELITE_HIGHLIGHT_COLOR = new Color(0xEF, 0x10, 0x20);
    /** parseRich pushes a new line on <br>, which would corrupt an in-place rebuild */
    private static final Pattern BR_TAG_PATTERN = Pattern.compile("(?i)<br>");

    @Getter @Setter private int maxLines = DEFAULT_MAX_LINES;

    @Inject protected Client client;
    @Inject protected MouseManager mouseManager;
    @Inject protected FontService fontService;
    @Inject protected ImageService imageService;
    @Inject protected ChannelFilterState channelFilterState;
    @Inject protected ForceRecolorService forceRecolorService;
    @Inject protected MessageFilterService messageFilterService;

    // Config
    @Getter protected MessageContainerConfig config;
    @Getter protected ChatMode chatMode;
    @Getter @Setter protected boolean chromeEnabled = true;
    @Getter @Setter protected Supplier<Rectangle> boundsProvider;
    @Getter @Setter protected Function<MessageContainer, Boolean> canShowDecider = mc -> true;

    // State
    @Getter @Setter protected volatile boolean hidden = false;
    @Getter @Setter protected volatile boolean isPrivate = false;
    @Getter @Setter protected volatile boolean applyChannelFilters = false;
    @Getter @Setter protected volatile boolean isPeekOverlay = false;
    @Getter @Setter protected volatile float alpha = 1f;
    @Getter private volatile float fadeAlpha = 1f;
    @Getter private volatile long fadeStartAtMs = Long.MAX_VALUE;
    @Getter private volatile boolean fading = false;
    @Getter private volatile long lastFadeResetMs = 0;
    // Per-line fade: reusable per-row alpha scratch so steady-state frames allocate nothing
    private float[] rowAlphas = new float[64];
    // Per-line fade: when non-zero, pushRich stamps this fade clock base on incoming lines
    protected long pendingFadeStartOverrideMs = 0;

    protected final Deque<RichLine> lines = new ArrayDeque<>();
    protected Font lineFont = null;
    protected FontStyle lineFontStyle = null;

    // Counts of node-tracked (and live-updating) lines currently in the deque, kept in
    // step with every add/remove so refresh sweeps can bail out without allocating
    private int trackedLineCount = 0;
    private int liveTrackedLineCount = 0;

    // Viewport and scrolling
    @Getter protected Rectangle lastViewport = null;
    protected final Rectangle msgViewport = new Rectangle();
    @Getter @Setter protected int scrollOffsetPx = 0;
    protected int contentHeightPx = 0;
    @Getter @Setter protected boolean userScrolled = false;
    @Getter protected int lastLineHeight = 16;

    // Scrollbar geometry for hit-tests
    protected final Rectangle thumb = new Rectangle(0, 0, 0, 0);
    @Getter protected int trackTop = 0;
    @Getter protected int trackHeight = 1;
    @Getter protected int maxScroll = 0;

    @Getter protected MouseHandler mouse;

    public MessageContainer() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_HIGH);
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * The container is treated as transparent when chrome is disabled (no backdrop drawn) or
     * when the configured backdrop color is more than half-transparent. ForceRecolor and
     * Chat Colors use this to choose between their opaque and transparent palettes.
     */
    public boolean isTransparentBackdrop() {
        if (!chromeEnabled) return true;
        return isTransparentBackdrop(config != null ? config.getBackdropColor() : null);
    }

    // Assumes chrome is enabled (a backdrop is actually drawn); callers without a container
    // instance cannot apply the chromeEnabled short-circuit of the instance method above.
    public static boolean isTransparentBackdrop(Color backdrop) {
        return backdrop == null || backdrop.getAlpha() < 128;
    }

    public void startUp(MessageContainerConfig config, ChatMode chatMode) {
        startUp(config, chatMode, true);
    }

    public void startUp(MessageContainerConfig config, ChatMode chatMode, boolean registerMouse) {
        this.config = config;
        this.chatMode = chatMode;

        if (registerMouse) {
            this.mouse = new MouseHandler();
            registerMouseListener();
        }
    }

    public void shutDown() {
        if (this.mouse != null) {
            unregisterMouseListener();
            this.mouse = null;
        }
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!isEnabled() || hidden)
            return null;

        if (!canShow()) {
            resetFade();
            return null;
        }

        updateFadeAlpha();

        final boolean perLineFade = isFadePerLine();
        if (!perLineFade && fadeAlpha <= 0.01f)
            return null; // fully faded; nothing to render

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

        // In per-line mode the container-wide fade is bypassed (fading stays false),
        // so only the external alpha applies at container level
        float actualFade = isFading() ? fadeAlpha : alpha;
        final float containerAlpha = Math.max(0f, Math.min(1f, actualFade));
        final Composite containerComposite = AlphaComposite.SrcOver.derive(containerAlpha);

        // Respect external alpha
        final Composite oldComp = g.getComposite();
        g.setComposite(containerComposite);
        try {
            // Font styles
            Font font = getLineFont();
            float fontSize = config.getLineFontSize();
            if (fontSize > 0) font = font.deriveFont(fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            final int lineH = fm.getAscent() + fm.getDescent() + config.getLineSpacing();
            lastLineHeight = Math.max(1, lineH);

            // Per-line fade params hoisted so the single flatten pass below computes each
            // visible line's alpha exactly once per frame
            final long now = perLineFade ? System.currentTimeMillis() : 0L;
            final long fadeDelayMs = perLineFade ? Math.max(0, fadeDelaySeconds() * 1000L) : 0L;
            final int fadeDurMs = perLineFade ? Math.max(1, fadeDurationMs()) : 1;
            float newestLineAlpha = 0f;

            // Flatten wrapped lines (oldest to newest)
            final List<VisualLine> all = new ArrayList<>(64);
            for (RichLine rl : lines) {
                if (!isLineVisible(rl)) {
                    continue;
                }

                if (rl.getLineCache() == null) {
                    rl.setLineCache(wrapRichLine(rl, fm, innerW));
                }
                final List<VisualLine> cache = rl.getLineCache();
                if (cache != null && !cache.isEmpty()) {
                    if (perLineFade) {
                        final float lineAlpha = lineFadeAlpha(rl, now, fadeDelayMs, fadeDurMs);
                        if (lineAlpha > newestLineAlpha)
                            newestLineAlpha = lineAlpha;
                        final int rowBase = all.size();
                        ensureRowAlphaCapacity(rowBase + cache.size());
                        for (int i = 0; i < cache.size(); i++)
                            rowAlphas[rowBase + i] = lineAlpha;
                    }
                    all.addAll(cache);
                }
            }

            // Every visible line fully faded; nothing to render (finally restores composite)
            if (perLineFade && newestLineAlpha <= 0.01f)
                return null;

            if (chromeEnabled) {
                // Backdrop and border; in per-line mode they follow the newest visible line's
                // fade so the box disappears along with the last visible line
                if (perLineFade)
                    g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, containerAlpha * newestLineAlpha))));

                g.setColor(config.getBackdropColor());
                g.fillRoundRect(lastViewport.x, lastViewport.y, lastViewport.width, lastViewport.height, 8, 8);

                g.setColor(config.getBorderColor());
                g.drawRoundRect(lastViewport.x, lastViewport.y, lastViewport.width, lastViewport.height, 8, 8);

                if (perLineFade)
                    g.setComposite(containerComposite);
            }

            // Measure content height and auto-stick to bottom when needed
            contentHeightPx = all.size() * lineH + 5;

            if (scrollOffsetPx == SCROLL_TO_BOTTOM_SENTINEL || (!userScrolled && contentHeightPx <= msgViewport.height)) {
                scrollOffsetPx = Math.max(0, contentHeightPx - msgViewport.height);
            }
            // Clamp scroll
            scrollOffsetPx = MathUtil.clamp(scrollOffsetPx, 0, Math.max(0, contentHeightPx - msgViewport.height));

            // Clip to message viewport and draw from top honoring scroll
            Shape oldClip = g.getClip();
            g.setClip(msgViewport);

            // Hoist override colors out of the per-segment loop; the config proxy is
            // otherwise hit once per segment per frame
            final Color timestampOverride = config.getTimestampColor();
            final Color prefixOverride = config.getTypePrefixColor();
            final Color nameOverride = config.getNameColor();

            int y = msgViewport.y - scrollOffsetPx + fm.getAscent();
            int rowIdx = 0;
            for (VisualLine vl : all) {
                final float rowAlpha = perLineFade ? rowAlphas[rowIdx++] : 1f;
                if (y - fm.getAscent() > msgViewport.y + msgViewport.height)
                    break; // below viewport
                // Fully faded rows keep their slot (no reflow) but are not drawn
                if (y + fm.getDescent() >= msgViewport.y && rowAlpha > 0.01f) {
                    // Rows at full alpha draw under the container composite as-is
                    final boolean rowComposite = perLineFade && rowAlpha < 1f;
                    if (rowComposite)
                        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, containerAlpha * rowAlpha))));

                    int dx = left;
                    for (TextSegment seg : vl.getSegs()) {
                        if (seg instanceof ImageSegment) {
                            ImageSegment imageSeg = (ImageSegment) seg;
                            Image icon = imageSeg.getImageCache();

                            if (icon == null && imageSeg.isAllowRetryImage()) {
                                icon = imageService.getModIcon(imageSeg.getId());
                                if (icon != null) {
                                    imageSeg.setImageCache(icon);
                                } else {
                                    imageSeg.setAllowRetryImage(false);
                                    dx += fm.getHeight();
                                    dirty();
                                    if (dx > right) break;
                                    continue;
                                }
                            }

                            // Draw or reserve fallback width if still missing
                            int iw = icon != null ? icon.getWidth(null) : fm.getHeight();
                            int ih = icon != null ? icon.getHeight(null) : fm.getHeight();
                            int lineTop = y - fm.getAscent();
                            int iconY = lineTop + ((fm.getAscent() + fm.getDescent()) - ih) / 2;

                            if (icon != null) {
                                g.drawImage(icon, dx, iconY, null);
                            }
                            dx += iw;
                            if (dx > right) break;
                            continue;
                        }

                        String segText = seg.getText();
                        if (dx == left && (segText == null || segText.isBlank()))
                            continue;

                        // Determine color: use config override if not transparent, else segment color
                        Color segColor = seg.getColor();
                        if (seg instanceof TimestampSegment) {
                            if (timestampOverride.getAlpha() > 0) {
                                segColor = timestampOverride;
                            }
                        } else if (seg instanceof PrefixSegment) {
                            if (prefixOverride.getAlpha() > 0) {
                                segColor = prefixOverride;
                            }
                        } else if (seg instanceof SenderSegment) {
                            if (nameOverride.getAlpha() > 0) {
                                segColor = nameOverride;
                            }
                        }

                        // Draw text with shadow or outline
                        TextDrawUtil.drawTextWithShadow(g, segText, dx, y,
                            segColor, config.getShadowColor(),
                            config.getTextShadow(), config.getTextOutline());

                        dx += fm.stringWidth(segText);
                        if (dx > right)
                            break;
                    }

                    if (rowComposite)
                        g.setComposite(containerComposite);
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
        if (lineFont == null) {
            lineFont = fontService.getFont(lineFontStyle != null ? lineFontStyle : FontStyle.RUNE);
        }
        if (lineFont == null) {
            log.error("Line font not found, using default Runescape font");
            return FontManager.getRunescapeFont();
        }
        return lineFont;
    }

    protected Rectangle calculateViewPort(Rectangle r) {
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
        trackedLineCount = 0;
        liveTrackedLineCount = 0;
        clearChatWidget();
    }

    /**
     * Returns a copy of the lines for reading.
     * This allows external code to iterate over messages without modifying the internal state.
     */
    public Deque<RichLine> getLines() {
        return new ArrayDeque<>(lines);
    }

    public void clearChatWidget() {
        lastViewport = null;
    }

    public boolean canShow() {
        return canShowDecider.apply(this);
    }

    private @Nullable Color getColor(ChatMode mode) {
        Color c = null;
        switch (mode) {
            case PUBLIC:
                c = config.getPublicColor();
                break;
            case FRIENDS_CHAT:
                c = config.getFriendColor();
                break;
            case CLAN_MAIN:
            case CLAN_GUEST:
            case CLAN_GIM:
                c = config.getClanColor();
                break;
            case PRIVATE:
                c = config.getPrivateColor();
                break;
        }
        return c;
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
                c = config.getWelcomeColor();
                break;
            default:
                c = config.getSystemColor();
        }
        return c == null ? Color.WHITE : c;
    }

    public void pushLine(MessageLine line) {
        ChatMessageType type = line.getType();
        String senderName = line.getSenderName();
        String receiverName = line.getReceiverName();
        String targetName = type == ChatMessageType.PRIVATECHATOUT || type == ChatMessageType.FRIENDNOTIFICATION
            ? receiverName
            : senderName;

        pushLine(line.getText(),
            type,
            line.getTimestamp(),
            senderName,
            receiverName,
            targetName,
            line.getPrefix(),
            line.getDuplicateKey(),
            line.isCollapsed(),
            line);
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
        pushLine(s, type, timestamp, sender, receiver, targetName, prefix, null, false);
    }

    public void pushLine(
        String s,
        ChatMessageType type,
        long timestamp,
        String sender,
        String receiver,
        String targetName,
        String prefix,
        String duplicateKey,
        boolean collapsed
    ) {
        pushLine(s, type, timestamp, sender, receiver, targetName, prefix, duplicateKey, collapsed, null);
    }

    public void pushLine(
        String s,
        ChatMessageType type,
        long timestamp,
        String sender,
        String receiver,
        String targetName,
        String prefix,
        String duplicateKey,
        boolean collapsed,
        @Nullable MessageLine source
    ) {
        type = type == null ? ChatMessageType.GAMEMESSAGE : type;

        // Always use default color as base (for sender name, etc.)
        Color baseColor = getColor(type);

        // Check ForceRecolor for message body color
        String messageToRender = s == null ? "" : s;
        if (forceRecolorService != null) {
            Color forceColor = forceRecolorService.getRecolorForMessage(s, type, isTransparentBackdrop());
            if (forceColor != null) {
                // Apply ForceRecolor only to message body, sender gets base color
                messageToRender = applyForceRecolorToBody(s, sender, baseColor, forceColor);
            }
        }

        RichLine rl = parseRich(messageToRender, baseColor == null ? Color.WHITE : baseColor, type, timestamp, prefix, sender);
        rl.setType(type);
        rl.setSender(sender);
        rl.setReceiver(receiver);
        rl.setTargetName(targetName);
        rl.setDuplicateKey(duplicateKey);
        rl.setCollapsed(collapsed);

        if (source != null && source.getMessageNodeId() != -1) {
            rl.setMessageNodeId(source.getMessageNodeId());
            rl.setNodeValueSnapshot(source.getNodeValueSnapshot());
            rl.setSenderPrefix(source.getSenderPrefix());
            rl.setLiveUpdating(ChatUtil.isLiveUpdatingText(source.getNodeValueSnapshot()));
        }

        // If this is a collapsed message (has count suffix), remove previous messages with same key
        if (collapsed && duplicateKey != null) {
            Iterator<RichLine> it = lines.iterator();
            while (it.hasNext()) {
                RichLine existing = it.next();
                if (duplicateKey.equals(existing.getDuplicateKey())) {
                    it.remove();
                    onLineRemoved(existing);
                }
            }
        }

        pushRich(rl);
    }

    /**
     * Re-read the MessageNodes behind recently captured lines and rebuild any whose text was
     * edited after capture (e.g. Chat Commands rewriting a !kc result). Bounded by
     * MAX_REFRESH_LINES walked per call; when liveOnly is set, only lines matching a
     * live-updating pattern (system update timer) are re-checked.
     */
    public void refreshTrackedLines(boolean liveOnly, IntFunction<MessageNode> nodeLookup) {
        if (!hasTrackedLines(liveOnly))
            return;

        final long now = System.currentTimeMillis();
        int walked = 0;
        Iterator<RichLine> it = lines.descendingIterator();
        while (it.hasNext() && walked < MAX_REFRESH_LINES) {
            RichLine rl = it.next();
            walked++;

            if (rl.getMessageNodeId() == -1)
                continue;
            if (liveOnly && !rl.isLiveUpdating())
                continue;

            // Plugin edits (Chat Commands lookups) land within seconds of the message; once
            // the window expires the line stops being swept so idle ticks cost nothing. The
            // expiring sweep below is still a full check, so edits that happened while this
            // container was hidden are applied on the first sweep after it becomes visible.
            // Live-updating lines (system update timer) never expire.
            final boolean expired = !rl.isLiveUpdating() && now - rl.getTimestamp() > EDIT_REFRESH_WINDOW_MS;

            MessageNode node = nodeLookup.apply(rl.getMessageNodeId());
            if (node == null) {
                // Node evicted from the client buffers; ids are never re-added, so untrack
                // permanently instead of paying for the lookup on every future sweep
                untrackLine(rl);
                continue;
            }

            String rlFormat = node.getRuneLiteFormatMessage();
            String effective = rlFormat != null ? rlFormat : node.getValue();
            if (effective != null && !effective.equals(rl.getNodeValueSnapshot()))
                rebuildTrackedLine(rl, node, effective);

            if (expired)
                untrackLine(rl);
        }
    }

    /** True when the deque holds any node-tracked (liveOnly: live-updating) lines. */
    public boolean hasTrackedLines(boolean liveOnly) {
        return liveOnly ? liveTrackedLineCount > 0 : trackedLineCount > 0;
    }

    private void onLineAdded(RichLine rl) {
        if (rl.getMessageNodeId() != -1) {
            trackedLineCount++;
            if (rl.isLiveUpdating())
                liveTrackedLineCount++;
        }
    }

    private void onLineRemoved(RichLine rl) {
        if (rl.getMessageNodeId() != -1) {
            trackedLineCount--;
            if (rl.isLiveUpdating())
                liveTrackedLineCount--;
        }
    }

    private void untrackLine(RichLine rl) {
        onLineRemoved(rl);
        rl.setMessageNodeId(-1);
        rl.setLiveUpdating(false);
    }

    private void rebuildTrackedLine(RichLine rl, MessageNode node, String effectiveText) {
        ChatMessageType type = rl.getType() == null ? ChatMessageType.GAMEMESSAGE : rl.getType();

        // Re-apply the same filter transform the capture path ran on the original event
        String body = effectiveText;
        if (messageFilterService != null) {
            ChatMessage synthetic = new ChatMessage(node, type, node.getName(), effectiveText,
                node.getSender(), node.getTimestamp());
            body = messageFilterService.filterMessage(synthetic);
            if (body == null) {
                // Filter blocks the edited text; keep the old rendered line but advance the
                // snapshot so the same edit isn't re-filtered on every sweep
                rl.setNodeValueSnapshot(effectiveText);
                return;
            }
        }

        Color baseColor = getColor(type);
        Color highlight = forceRecolorService != null
            ? forceRecolorService.getGameMessageHighlight(isTransparentBackdrop())
            : null;
        if (highlight == null)
            highlight = RUNELITE_HIGHLIGHT_COLOR;

        String translated = ChatUtil.translateRuneLiteColorTags(body, baseColor, highlight);

        // The node text never contains the collapse count; carry the " (n)" suffix captured
        // at collapse time over to the edited body (unless the filter re-appended it)
        if (rl.isCollapsed()) {
            String suffix = findCollapseSuffix(rl);
            if (suffix != null && !translated.endsWith(suffix))
                translated = translated + suffix;
        }

        String rendered = ChatUtil.composeLineText(rl.getSenderPrefix(), translated, type);

        String messageToRender = rendered;
        if (forceRecolorService != null) {
            Color forceColor = forceRecolorService.getRecolorForMessage(rendered, type, isTransparentBackdrop());
            if (forceColor != null) {
                messageToRender = applyForceRecolorToBody(rendered, rl.getSender(), baseColor, forceColor);
            }
        }

        messageToRender = BR_TAG_PATTERN.matcher(messageToRender).replaceAll(" ");

        // Keep the original resolved prefix text; parseRich would otherwise re-derive it
        String prefix = null;
        for (TextSegment seg : rl.getSegs()) {
            if (seg instanceof PrefixSegment) {
                prefix = seg.getText();
                break;
            }
        }

        RichLine parsed = parseRich(messageToRender, baseColor, type, rl.getTimestamp(), prefix, rl.getSender());
        rl.getSegs().clear();
        rl.getSegs().addAll(parsed.getSegs());
        rl.setNodeValueSnapshot(effectiveText);
        rl.resetCache();
    }

    /** Reads the trailing " (n)" collapse suffix from the currently rendered segments. */
    private @Nullable String findCollapseSuffix(RichLine rl) {
        List<TextSegment> segs = rl.getSegs();
        for (int i = segs.size() - 1; i >= 0; i--) {
            TextSegment seg = segs.get(i);
            if (seg instanceof ImageSegment)
                continue;
            String text = seg.getText();
            if (text == null || text.isEmpty())
                continue;
            return ChatUtil.extractCollapseSuffix(text);
        }
        return null;
    }

    /**
     * Applies ForceRecolor color to the message body and base color to the sender name.
     * The message format is typically: "SenderName: message body" or just "message body"
     */
    private String applyForceRecolorToBody(String message, String sender, Color baseColor, Color forceColor) {
        if (message == null || message.isEmpty() || forceColor == null) {
            return message;
        }

        String forceHex = String.format("%06X", forceColor.getRGB() & 0xFFFFFF);
        String forceTag = "<col=" + forceHex + ">";
        String endTag = "</col>";

        // If no sender, color the entire message with ForceRecolor
        if (sender == null || sender.isEmpty()) {
            return forceTag + message + endTag;
        }

        // Find the ": " separator after the sender name
        // The sender might have formatting like "<img=1>PlayerName"
        int separatorIdx = message.indexOf(": ");
        if (separatorIdx > 0) {
            String senderPart = message.substring(0, separatorIdx + 2); // Include ": "
            String bodyPart = message.substring(separatorIdx + 2);

            // Apply base color to sender, ForceRecolor to body
            String baseHex = baseColor != null
                ? String.format("%06X", baseColor.getRGB() & 0xFFFFFF)
                : "FFFFFF";
            String baseTag = "<col=" + baseHex + ">";

            return baseTag + senderPart + endTag + forceTag + bodyPart + endTag;
        }

        // Fallback: color the entire message with ForceRecolor if no separator found
        return forceTag + message + endTag;
    }

    private RichLine parseRich(String s, Color base, ChatMessageType type, long timestamp, String prefix, String sender) {
        RichLine out = new RichLine();
        out.setTimestamp(timestamp);
        if (s == null) return out;

        Deque<Color> stack = new ArrayDeque<>();
        Color cur = base;
        StringBuilder buf = new StringBuilder();

        // Visible sender-name chars still to emit as SenderSegment; 0 disables marking.
        // Skipped entirely while the name color override is transparent (feature off) so the
        // pre-scan does not run for every pushed line. Lines pushed while disabled keep their
        // base colors until re-pushed (dirty() only re-wraps, it does not re-parse), which is
        // an accepted trade-off documented in the config item description.
        int senderRemaining = 0;
        if (config.getNameColor().getAlpha() > 0 && !StringUtil.isNullOrEmpty(sender)) {
            String senderVisible = Text.removeTags(sender);
            if (!senderVisible.isEmpty() && visibleTextStartsWithSender(s, senderVisible))
                senderRemaining = senderVisible.length();
        }

        // Timestamp color: use configured color if not transparent, else use line color
        Color timestampColor = config.getTimestampColor();
        out.getSegs().add(new TimestampSegment("[" + FormatUtil.toHmTime(timestamp) + "] ",
            timestampColor.getAlpha() > 0 ? timestampColor : cur));

        // Prefix color: use configured color if not transparent, else use line color
        Color prefixColor = config.getTypePrefixColor();
        out.getSegs().add(new PrefixSegment(StringUtil.isNullOrEmpty(prefix)
            ? ChatUtil.getPrefix(type)
            : prefix, prefixColor.getAlpha() > 0 ? prefixColor : cur));

        for (int i = 0; i < s.length(); ) {
            char ch = s.charAt(i);
            if (ch == '<') {
                int j = s.indexOf('>', i + 1);
                if (j < 0)
                    break; // unterminated, stop parsing

                // preserve original case for pass-through/img emission
                String tagRaw = s.substring(i + 1, j);
                String tagLower = tagRaw.toLowerCase(Locale.ROOT);

                // handle entities first
                if (tagLower.equals("lt")) {
                    buf.append('<');
                    i = j + 1;
                    continue;
                }
                if (tagLower.equals("gt")) {
                    buf.append('>');
                    i = j + 1;
                    continue;
                }

                if (buf.length() > 0) {
                    senderRemaining = emitText(out, buf.toString(), cur, senderRemaining);
                    buf.setLength(0);
                }

                if (tagLower.startsWith("col")) {
                    stack.push(cur);
                    cur = ColorUtil.parseHexColor(tagRaw.substring(tagRaw.contains("=") ? 4 : 3), cur);
                    i = j + 1;
                    continue;
                } else if (tagLower.equals("/col")) {
                    cur = stack.isEmpty() ? base : stack.pop();
                    i = j + 1;
                    continue;
                } else if (tagLower.equals("br")) {
                    if (out.getSegs().isEmpty())
                        out.getSegs().add(new TextSegment("", cur));
                    pushRich(out);
                    out = new RichLine();
                    // Continuation lines share the first line's timestamp so per-line
                    // fade does not treat them as instantly aged
                    out.setTimestamp(timestamp);
                    i = j + 1;
                    continue;
                } else if (tagLower.startsWith("img")) {
                    try {
                        int id = Integer.parseInt(tagRaw.substring(tagRaw.contains("=") ? 4 : 3));
                        out.getSegs().add(new ImageSegment(id, cur));
                        i = j + 1;
                        continue;
                    } catch (Exception ignored) {
                        // ignore parse errors, treat as unknown tag
                    }
                }

                // Unknown tag: pass it through literally instead of dropping it
                buf.append('<').append(tagRaw).append('>');
                i = j + 1;
            } else {
                buf.append(ch);
                i++;
            }
        }

        if (buf.length() > 0)
            emitText(out, buf.toString(), cur, senderRemaining);

        return out;
    }

    /**
     * Emits a parsed text run, marking the first {@code senderRemaining} chars as a
     * SenderSegment so the sender name can be recolored at render time. The run is
     * split when the sender-name boundary falls inside it. Returns the number of
     * sender chars still pending.
     */
    private int emitText(RichLine out, String text, Color color, int senderRemaining) {
        if (senderRemaining <= 0) {
            out.getSegs().add(new TextSegment(text, color));
            return 0;
        }
        int take = Math.min(senderRemaining, text.length());
        out.getSegs().add(new SenderSegment(text.substring(0, take), color));
        if (take < text.length())
            out.getSegs().add(new TextSegment(text.substring(take), color));
        return senderRemaining - take;
    }

    /**
     * Checks that the visible text of a composed line starts with "sender: ", using the
     * same tag rules as parseRich (col/img/br are invisible, lt/gt decode to chars, and
     * unknown tags render literally so they fail the match - names never contain '<').
     */
    private static boolean visibleTextStartsWithSender(String s, String sender) {
        String target = sender + ": ";
        int n = 0;
        for (int i = 0; i < s.length() && n < target.length(); ) {
            char ch = s.charAt(i);
            if (ch != '<') {
                if (target.charAt(n) != ch)
                    return false;
                n++;
                i++;
                continue;
            }
            int j = s.indexOf('>', i + 1);
            if (j < 0)
                return false;
            String tagLower = s.substring(i + 1, j).toLowerCase(Locale.ROOT);
            if (tagLower.equals("lt") || tagLower.equals("gt")) {
                if (target.charAt(n) != (tagLower.equals("lt") ? '<' : '>'))
                    return false;
                n++;
            } else if (tagLower.startsWith("img")) {
                // mirror parseRich: malformed img tags render literally and fail the match
                try {
                    Integer.parseInt(tagLower.substring(tagLower.contains("=") ? 4 : 3));
                } catch (Exception ignored) {
                    return false;
                }
            } else if (!tagLower.startsWith("col") && !tagLower.equals("/col") && !tagLower.equals("br")) {
                return false;
            }
            i = j + 1;
        }
        return n == target.length();
    }

    private List<VisualLine> wrapRichLine(RichLine rl, FontMetrics fm, int maxWidth)
    {
        List<VisualLine> out = new ArrayList<>();
        VisualLine cur = new VisualLine();
        int curW = 0;

        for (TextSegment s : rl.getSegs()) {
            if (s instanceof TimestampSegment) {
                if (!config.isShowTimestamp())
                    continue; // skip timestamp segments if disabled
            }

            if (s instanceof PrefixSegment) {
                if (!config.isPrefixChatType() && s.getText().startsWith("["))
                    continue; // skip prefix segments if disabled
            }

            // Image segments unbreakable tokens with cached width
            if (s instanceof ImageSegment) {
                ImageSegment img = (ImageSegment) s;

                Image icon = img.getImageCache();
                if (icon == null && img.isAllowRetryImage()) {
                    icon = imageService.getModIcon(img.getId());
                    if (icon != null) {
                        img.setImageCache(icon);
                    } else {
                        img.setAllowRetryImage(false);
                    }
                }

                int iw = (icon != null) ? icon.getWidth(null) : fm.getHeight();

                if (curW + iw > maxWidth && !cur.getSegs().isEmpty()) {
                    out.add(cur);
                    cur = new VisualLine();
                    curW = 0;
                }
                cur.getSegs().add(img); // keep as ImageSegment for renderer
                curW += iw;
                continue;
            }

            // Timestamp and Prefix segments: unbreakable tokens that preserve their type
            if (s instanceof TimestampSegment || s instanceof PrefixSegment) {
                String txt = s.getText();
                if (txt == null || txt.isEmpty())
                    continue;

                int sw = fm.stringWidth(txt);
                if (curW + sw > maxWidth && !cur.getSegs().isEmpty()) {
                    out.add(cur);
                    cur = new VisualLine();
                    curW = 0;
                }
                cur.getSegs().add(s); // preserve original segment type for renderer
                curW += sw;
                continue;
            }

            // Plain text wrapping
            final String txt = s.getTextCache() != null ? s.getTextCache() : s.getText();
            if (txt == null || txt.isEmpty())
                continue;

            int i = 0;
            while (i < txt.length()) {
                // find next space
                int nextSpace = -1;
                for (int k = i; k < txt.length(); k++) {
                    char c = txt.charAt(k);
                    if (c == ' ' || c == '\u00A0') { nextSpace = k; break; }
                }

                int endWord = (nextSpace == -1 ? txt.length() : nextSpace);
                String word  = txt.substring(i, endWord);
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
                        cur.getSegs().add(copyRun(s, part));
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
                        VisualLine next = new VisualLine();
                        int nextW = 0;
                        // Keep the sender name and its colon together: when the ": msg"
                        // run would wrap right after the name, carry the name's trailing
                        // sender run onto the new line instead of breaking after the bare name
                        if (i == 0 && word.equals(":"))
                            nextW = detachTrailingSenderRun(cur, next, fm);
                        if (!cur.getSegs().isEmpty())
                            out.add(cur);
                        cur = next;
                        curW = nextW;
                    }
                    if (!word.isEmpty()) {
                        cur.getSegs().add(copyRun(s, word));
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
                    cur.getSegs().add(copyRun(s, space));
                    curW += spW;
                }

                i = (nextSpace == -1) ? txt.length() : nextSpace + 1;
            }
        }

        if (!cur.getSegs().isEmpty())
            out.add(cur);
        return out;
    }

    /** Copies a wrapped text run, preserving SenderSegment type so render-time recolor survives wrapping. */
    private static TextSegment copyRun(TextSegment src, String text) {
        return src instanceof SenderSegment
            ? new SenderSegment(text, src.getColor())
            : new TextSegment(text, src.getColor());
    }

    /**
     * Moves the trailing non-space SenderSegment run of {@code line} into {@code target}
     * so a leading ":" token stays glued to the sender name across a wrap. Only sender
     * runs are moved, so no other segment sequence is affected. Returns the width of the
     * moved segments.
     */
    private static int detachTrailingSenderRun(VisualLine line, VisualLine target, FontMetrics fm) {
        List<TextSegment> segs = line.getSegs();
        int idx = segs.size();
        while (idx > 0) {
            TextSegment seg = segs.get(idx - 1);
            if (!(seg instanceof SenderSegment))
                break;
            String t = seg.getText();
            if (t == null || t.isEmpty())
                break;
            char last = t.charAt(t.length() - 1);
            if (last == ' ' || last == '\u00A0')
                break; // sender names can contain spaces; break at them as usual
            idx--;
        }
        int moved = 0;
        while (segs.size() > idx) {
            TextSegment seg = segs.remove(idx);
            target.getSegs().add(seg);
            moved += fm.stringWidth(seg.getText());
        }
        return moved;
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

    public void pushLines(List<String> lines) {
        pushLines(lines, ChatMessageType.GAMEMESSAGE);
    }

    public void pushLines(List<String> lines, ChatMessageType type) {
        for (String line : lines) {
            pushLine(line, type, System.currentTimeMillis(), null, null, null, null);
        }
    }

    /**
     * Copy a RichLine to this container by duplicating its segments.
     * This avoids sharing lineCache between containers with different widths.
     */
    public void copyLine(RichLine source) {
        if (source == null || source.getSegs().isEmpty()) return;

        RichLine copy = new RichLine();
        copy.setType(source.getType());
        copy.setTimestamp(source.getTimestamp());
        copy.setSender(source.getSender());
        copy.setReceiver(source.getReceiver());
        copy.setTargetName(source.getTargetName());
        copy.setMessageNodeId(source.getMessageNodeId());
        copy.setNodeValueSnapshot(source.getNodeValueSnapshot());
        copy.setSenderPrefix(source.getSenderPrefix());
        copy.setLiveUpdating(source.isLiveUpdating());

        // Copy segments (they're immutable-ish, safe to share references)
        copy.getSegs().addAll(source.getSegs());

        pushRich(copy);
    }

    /**
     * Make pushRich accessible for internal use.
     */
    protected void pushRich(RichLine rl) {
        if (rl == null || rl.getSegs().isEmpty()) return;
        if (pendingFadeStartOverrideMs != 0)
            rl.setFadeStartOverrideMs(pendingFadeStartOverrideMs);
        lines.addLast(rl);
        onLineAdded(rl);
        while (lines.size() > maxLines) onLineRemoved(lines.removeFirst());

        // If we haven't scrolled up, auto-stick to bottom on next render
        if (!userScrolled) {
            scrollOffsetPx = SCROLL_TO_BOTTOM_SENTINEL;
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
        trackedLineCount = 0;
        liveTrackedLineCount = 0;
    }

    public void registerMouseListener() {
        mouseManager.registerMouseListener(1, mouse);
        mouseManager.registerMouseWheelListener(mouse);
    }

    public void unregisterMouseListener() {
        mouseManager.unregisterMouseListener(mouse);
        mouseManager.unregisterMouseWheelListener(mouse);
    }

    public boolean hitAt(Point mouse) {
        return lastViewport != null && lastViewport.contains(new java.awt.Point(mouse.getX(), mouse.getY()));
    }

    public Color getTextColor() {
        if (isPrivate())
            return config.getPrivateColor();

        return getColor(chatMode);
    }

    public @Nullable Color getTextColor(ChatMode mode) {
        return getColor(mode);
    }

    public int fadeDelaySeconds() {
        return config.getFadeDelay();
    }

    public int fadeDurationMs() {
        return config.getFadeDuration();
    }

    public boolean isFadePerLine() {
        return config.isFadeEnabled() && config.isFadePerLine();
    }

    public void resetFade() {
        fadeAlpha = 1f;
        fading = false;
        lastFadeResetMs = System.currentTimeMillis();
        fadeStartAtMs = lastFadeResetMs + Math.max(0, fadeDelaySeconds() * 1000);
    }

    private void updateFadeAlpha() {
        final long now = System.currentTimeMillis();

        // Per-line mode bypasses the container-wide fade; lines fade individually
        if (!config.isFadeEnabled() || config.isFadePerLine()) {
            fadeAlpha = 1f;
            fading = false;
            return;
        }

        if (now < fadeStartAtMs) {
            fadeAlpha = 1f;
            fading = false;
            return;
        }

        final int dur = Math.max(1, fadeDurationMs());
        final long t = now - fadeStartAtMs;
        if (t <= 0) {
            fadeAlpha = 1f;
            fading = false;
            return;
        }

        fading = true;
        fadeAlpha = easedFadeAlpha(t, dur);
    }

    /** Render visibility filters shared by the flatten pass and per-line fade bookkeeping. */
    private boolean isLineVisible(RichLine rl) {
        if (!config.isShowPrivateMessages() && ChatUtil.isPrivateMessage(rl.getType())) {
            return false;
        }

        if (!config.isShowNpcMessages() && ChatUtil.isNpcMessage(rl.getType())) {
            return false;
        }

        // Channel filter check - only apply to containers with filters enabled (All tab)
        return !applyChannelFilters || channelFilterState == null || channelFilterState.shouldShowMessage(rl.getType());
    }

    /**
     * Per-line fade alpha. The line's fade clock starts at its own timestamp (or its
     * fade-start override for suppressed lines), or at the last global fade reset
     * (chat closed, config change) if that is more recent, so a reset re-reveals every
     * line before each re-ages independently.
     */
    private float lineFadeAlpha(RichLine rl, long now, long delayMs, int durationMs) {
        final long override = rl.getFadeStartOverrideMs();
        final long fadeStart = Math.max(override != 0 ? override : rl.getTimestamp(), lastFadeResetMs) + delayMs;
        if (now <= fadeStart)
            return 1f;
        return easedFadeAlpha(now - fadeStart, durationMs);
    }

    /** Grows the reusable per-row alpha scratch; steady-state frames allocate nothing. */
    private void ensureRowAlphaCapacity(int needed) {
        if (rowAlphas.length < needed)
            rowAlphas = Arrays.copyOf(rowAlphas, Math.max(needed, rowAlphas.length * 2));
    }

    /**
     * Marks lines pushed until {@link #endInheritedFadePush()} with a fade clock inherited
     * from the newest visible line (or one already fully aged when none is visible), so a
     * suppressed line renders at the current overlay alpha instead of reviving the fade.
     */
    protected void beginInheritedFadePush() {
        long newestBase = 0;
        for (RichLine rl : lines) {
            if (!isLineVisible(rl))
                continue;
            final long override = rl.getFadeStartOverrideMs();
            final long base = override != 0 ? override : rl.getTimestamp();
            if (base > newestBase)
                newestBase = base;
        }
        if (newestBase <= 0) {
            // Nothing visible to inherit from: age the incoming line past the full fade
            newestBase = System.currentTimeMillis()
                - Math.max(0, fadeDelaySeconds() * 1000L) - Math.max(1, fadeDurationMs());
        }
        pendingFadeStartOverrideMs = newestBase;
    }

    protected void endInheritedFadePush() {
        pendingFadeStartOverrideMs = 0;
    }

    /** Shared fade curve for container-wide and per-line fades: easeOutCubic from 1 to 0. */
    private static float easedFadeAlpha(long elapsedMs, int durationMs) {
        float p = Math.min(1f, elapsedMs / (float) durationMs);
        p = 1f - (float) Math.pow(1f - p, 3); // easeOutCubic
        return 1f - p;
    }

    protected final class MouseHandler implements MouseListener, MouseWheelListener
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
