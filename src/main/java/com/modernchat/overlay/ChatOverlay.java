package com.modernchat.overlay;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.FontStyle;
import com.modernchat.common.MessageLine;
import com.modernchat.common.NotificationService;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.Padding;
import com.modernchat.draw.RowHit;
import com.modernchat.draw.Tab;
import com.modernchat.draw.TextSegment;
import com.modernchat.draw.VisualLine;
import com.modernchat.event.ChatMenuOpenedEvent;
import com.modernchat.event.ChatResizedEvent;
import com.modernchat.event.ChatToggleEvent;
import com.modernchat.event.DialogOptionsClosedEvent;
import com.modernchat.event.DialogOptionsOpenedEvent;
import com.modernchat.event.LeftDialogClosedEvent;
import com.modernchat.event.LeftDialogOpenedEvent;
import com.modernchat.event.ModernChatVisibilityChangeEvent;
import com.modernchat.event.NavigateHistoryEvent;
import com.modernchat.event.RightDialogClosedEvent;
import com.modernchat.event.RightDialogOpenedEvent;
import com.modernchat.event.TabChangeEvent;
import com.modernchat.service.FilterService;
import com.modernchat.service.FontService;
import com.modernchat.service.MessageService;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.MathUtil;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientStr;
import net.runelite.api.clan.ClanID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Singleton
public class ChatOverlay extends OverlayPanel
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private OverlayManager overlayManager;
    @Inject private MouseManager mouseManager;
    @Inject private KeyManager keyManager;
    @Inject private WidgetBucket widgetBucket;
    @Inject private FontService fontService;
    @Inject private NotificationService notificationService;
    @Inject private FilterService filterService;
    @Inject private MessageService messageService;
    @Inject @Getter private ResizePanel resizePanel;
    @Inject private Provider<MessageContainer> messageContainerProvider;

    private ChatOverlayConfig config;
    private final ChatMouse mouse = new ChatMouse();
    private final InputKeys keys = new InputKeys();

    @Getter private final Map<String, Integer> suggestedOrder = new ConcurrentHashMap<>();
    @Getter private final List<Tab> tabOrder = new LinkedList<>();
    @Getter private Tab activeTab = null;
    @Getter private final Map<String, Tab> tabsByKey = new ConcurrentHashMap<>();
    @Getter private final Map<ChatMode, String> defaultTabNames = new ConcurrentHashMap<>();
    private final Rectangle tabsBarBounds = new Rectangle();
    @Getter private int lastTabBarHeight = 0;

    @Getter private final Map<String, MessageContainer> messageContainers = new ConcurrentHashMap<>();
    @Getter private final Map<String, MessageContainer> privateContainers = new ConcurrentHashMap<>();
    @Getter @Nullable private MessageContainer messageContainer = null;
    @Getter private EnumSet<ChatMode> availableChatModes = EnumSet.noneOf(ChatMode.class);

    @Getter private Rectangle lastViewport = null;

    // Input box state
    private final Rectangle inputBounds = new Rectangle();
    private boolean inputFocused = false;
    private final StringBuilder inputBuf = new StringBuilder();
    private int caret = 0;
    private int inputScrollPx = 0;
    private long lastBlinkMs = 0;
    private boolean caretOn = true;

    // Selection state
    private int selStart = 0;
    private int selEnd = 0;
    private int selAnchor = -1; // -1 no anchor
    private boolean selectingText = false;

    // Input padding used for hit-testing
    private static final int INPUT_PAD_X = 8;
    private static final int INPUT_PAD_Y = 6;

    // Selection highlight color
    private static final Color INPUT_SELECTION_BG = new Color(0, 120, 215, 120);

    // Badge tuning
    private static final int BADGE_MIN_W = 15;
    private static final int BADGE_MIN_H = 12;
    private static final int BADGE_TEXT_PAD = 8;   // inside-pill left+right
    private static final int BADGE_SHRINK_PX = 4;  // shrink when thin
    private static final int BADGE_THIN_THRESHOLD = 120; // tab content width threshold

    @Getter private boolean hidden = false;
    @Getter private boolean legacyShowing = false;
    @Getter private boolean wasHidden = false;

    @Getter private int desiredChatWidth;
    @Getter private int desiredChatHeight;

    // Tab drag state
    private static final int DRAG_THRESHOLD_PX = 3;
    private boolean draggingTab = false;
    private boolean didReorder = false;
    private Tab dragTab = null;
    private String pendingSelectTabKey = null;

    private int pressX = 0;
    private int dragOffsetX = 0;
    private int dragVisualX = 0;
    private int dragStartIndex = -1;
    private int dragTargetIndex = -1;
    private int dragTabWidth = 0;
    private int dragTabHeight = 0;

    private int tabsScrollPx = 0;
    private int tabsTotalWidth = 0;
    private int tabsMaxScroll = 0;
    private static final int TAB_WHEEL_STEP = 48;

    // Unread flash settings
    private static final int UNREAD_FLASH_PERIOD_MS = 900;

    // Font caching
    private FontStyle fontStyle;
    private Font font;

    private static boolean PASTE_WARNING_SHOWN = false;

    public ChatOverlay() {
    }

    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    public void startUp(ChatOverlayConfig config) {
        startUp(config, config.getMessageContainerConfig());
    }

    public void startUp(ChatOverlayConfig config, MessageContainerConfig containerConfig) {
        this.config = config;

        clientThread.invoke(() -> {
            hideLegacyChat(false);
        });

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setClearChildren(false);

        defaultTabNames.clear();
        defaultTabNames.put(ChatMode.PUBLIC, "Public");
        defaultTabNames.put(ChatMode.FRIENDS_CHAT, "Friends Chat");
        defaultTabNames.put(ChatMode.CLAN_MAIN, "Clan");
        defaultTabNames.put(ChatMode.CLAN_GUEST, "Clan Guest");

        eventBus.register(this);

        registerMouseListener();
        registerKeyboardListener();

        // Note: need to make sure the resize panel is started after the mouse listener is registered,
        // as we will be consuming events in the chat that will stop the resize panel from receiving them otherwise.
        resizePanel.setSidesEnabled(false, true, true, false);
        resizePanel.setBaseBoundsProvider(() -> lastViewport);
        resizePanel.setListener(this::setDesiredChatSize);
        resizePanel.startUp(() -> isResizable() && !isHidden() && !client.isMenuOpen());

        messageContainers.putAll(Map.of(
            ChatMode.PUBLIC.name(), messageContainerProvider.get(),
            ChatMode.FRIENDS_CHAT.name(), messageContainerProvider.get(),
            ChatMode.CLAN_MAIN.name(), messageContainerProvider.get(),
            ChatMode.CLAN_GUEST.name(), messageContainerProvider.get()
        ));

        messageContainers.forEach((mode, container) -> {
            container.setChromeEnabled(true);
            container.startUp(containerConfig, ChatMode.valueOf(mode));
        });

        refreshTabs();

        clientThread.invokeAtTickEnd(() -> selectTab(config.getDefaultChatMode()));

        if (config.isStartHidden())
            setHidden(true);
    }

    public void shutDown() {
        clientThread.invoke(() -> {
            showLegacyChat(true);
            resetChatbox();
        });

        reset();
        unregisterMouseListener();
        unregisterKeyboardListener();

        eventBus.unregister(this);

        activeTab = null;
        messageContainer = null;
        messageContainers.values().forEach(MessageContainer::shutDown);
        messageContainers.clear();
        privateContainers.values().forEach(MessageContainer::shutDown);
        privateContainers.clear();

        lastViewport = null;

        resizePanel.shutDown();
        overlayManager.remove(resizePanel);
        panelComponent.getChildren().clear();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!isEnabled() || hidden)
            return null;

        Widget chatRoot = widgetBucket.getChatboxViewportWidget();
        if (chatRoot == null || chatRoot.isHidden())
            return null;

        Rectangle vp = chatRoot.getBounds();
        if (vp == null || vp.width <= 0 || vp.height <= 0)
            return null;

        lastViewport = new Rectangle(vp);

        if (messageContainer == null) {
            selectTab(config.getDefaultChatMode());

            if (messageContainer == null)
                return null;
        }

        // Panel chrome (style only)
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(config.getBackdropColor());
        g.fillRoundRect(vp.x + 4, vp.y + 4, vp.width - 8, vp.height - 8, 8, 8);

        g.setColor(config.getBorderColor());
        g.drawRoundRect(vp.x + 3, vp.y + 3, vp.width - 7, vp.height - 7, 8, 8);

        // Layout constants
        final Padding pad = config.getPadding();
        final Font font = getFont();
        Font inputFont = font.deriveFont((float) config.getInputFontSize());
        g.setFont(inputFont);
        FontMetrics fm = g.getFontMetrics();
        final int lineH = fm.getAscent() + fm.getDescent() + config.getInputLineSpacing();

        final int inputPadX = 8;
        final int inputPadY = 6;
        final int inputHeight = lineH + inputPadY * 2;
        final int gapAboveInput = 1;

        final int left = vp.x + pad.getLeft();
        final int top = vp.y + pad.getTop();
        final int bottom = vp.y + vp.height - pad.getBottom();
        final int innerW = Math.max(1, vp.width - pad.getWidth());

        Font tabFont = font.deriveFont((float) config.getTabFontSize());
        g.setFont(tabFont);
        FontMetrics tfm = g.getFontMetrics();

        lastTabBarHeight = drawTabBar(g, tfm, left, top, innerW);
        final int msgAreaTop = top + lastTabBarHeight + 3; // gap under tabs
        final int msgBottom = bottom - inputHeight - gapAboveInput;
        final Rectangle msgArea = new Rectangle(left, msgAreaTop, innerW, Math.max(1, msgBottom - msgAreaTop));

        // Inject the msg area into the MessageContainer
        messageContainer.setBoundsProvider(() -> msgArea);
        //messageContainer.setHidden(false);
        //messageContainer.setAlpha(1f);

        // Let MessageContainer paint inside the message area
        Shape oldClip = g.getClip();
        g.setClip(new Rectangle(vp.x, vp.y, vp.width, msgBottom - vp.y));
        messageContainer.render(g);
        g.setClip(oldClip);

        // Reset the font for the input box
        g.setFont(inputFont);

        // Draw input box
        drawInputBox(g, fm, left, msgBottom, innerW, inputHeight, inputPadX, inputPadY, gapAboveInput);

        resizePanel.render(g);

        g.setComposite(oc);
        return super.render(g);
    }

    private Font getFont() {
        if (fontStyle == null || fontStyle != config.getFontStyle()) {
            fontStyle = config.getFontStyle();
            font = null;
        }
        if (font == null) {
            font = fontService.getFont(fontStyle != null ? fontStyle : FontStyle.RUNE);
        }
        return font;
    }

    public void selectDefaultTab() {
        selectTab(config.getDefaultChatMode());
    }

    public void selectTab(ChatMode chatMode) {
        selectTab(chatMode, false);
    }

    public void selectTab(ChatMode chatMode, boolean autoCreate) {
        String key = tabKey(chatMode);
        if (StringUtil.isNullOrEmpty(key)) {
            log.warn("Attempted to select tab with null or empty key for chat mode: {}", chatMode);
            return;
        }

        if (!tabsByKey.containsKey(key)) {
            log.debug("No tab found for chat mode: {}", chatMode);
            if (autoCreate) {
                // Create a new tab if it doesn't exist
                Tab newTab = new Tab(key, defaultTabNames.getOrDefault(chatMode, chatMode.name()), false);
                addTab(newTab);
            } else {
                return;
            }
        }

        selectTabByKey(key);
    }

    private int drawTabBar(Graphics2D g, FontMetrics fm, int x, int y, int width) {
        final int padX = 10;
        final int padY = 3;
        final int h = fm.getAscent() + fm.getDescent() + padY * 2; // compact
        final int r = 7;

        // Tab font & metrics
        final Font tabFont = fm.getFont();

        // Badge font/metrics
        final float notifSize = Math.max(0f, (float) config.getTabBadgeFontSize());
        final Font notifFont = notifSize > 0f ? tabFont.deriveFont(notifSize) : tabFont;
        final FontMetrics nfm = g.getFontMetrics(notifFont);

        int total = 4; // initial left gap
        for (Tab t : tabOrder) {
            final String label = t.getTitle();
            final int textW = fm.stringWidth(label);

            final int closeH = fm.getHeight() - 2;
            final int closeW = t.isCloseable() ? closeH : 0;

            int i = t.isCloseable() ? (6 + closeW) : 0;
            final int wNoBadge = padX + textW + i + padX;

            int badgeW = 0;
            final boolean showBadge = t.getUnread() > 0 && config.isShowNotificationBadge();
            if (showBadge) {
                final String unreadStr = String.valueOf(t.getUnread());
                final boolean thinTab = (wNoBadge <= BADGE_THIN_THRESHOLD);
                badgeW = computeBadgeWidth(nfm, unreadStr, thinTab);
            }

            final int w = padX + textW + (badgeW > 0 ? (2 + badgeW) : 0) + i + padX;

            total += w + 4; // + gap between tabs
        }
        tabsTotalWidth = total;
        clampTabScroll(width);

        // clip everything we draw for the tab bar
        Shape oldClip = g.getClip();
        g.setClip(new Rectangle(x, y, width, h + 1));

        // Bar background
        g.setColor(config.getTabBarBackgroundColor());
        g.fillRoundRect(x, y, width, h, 8, 8);

        int cx = x + 4 - tabsScrollPx;
        int filteredIdx = 0;

        for (Tab t : tabOrder) {
            final String label = t.getTitle();
            final int textW = fm.stringWidth(label);

            final int closeH = fm.getHeight() - 2;
            final int closeW = t.isCloseable() ? closeH : 0;

            int i = t.isCloseable() ? (6 + closeW) : 0;
            final int wNoBadge = padX + textW + i + padX;

            int badgeW = 0;
            final boolean showBadge = t.getUnread() > 0 && config.isShowNotificationBadge();
            final String unreadStr = String.valueOf(t.getUnread());
            if (showBadge) {
                final boolean thinTab = (wNoBadge <= BADGE_THIN_THRESHOLD);
                badgeW = computeBadgeWidth(nfm, unreadStr, thinTab);
            }

            final int w = padX + textW + (badgeW > 0 ? (2 + badgeW) : 0)
                + i + padX;

            if (draggingTab && dragTab != null && t != dragTab && filteredIdx == dragTargetIndex) {
                cx += (dragTabWidth > 0 ? dragTabWidth : w) + 4;
            }

            if (draggingTab && t == dragTab) {
                // we'll draw the dragged one after the loop
                filteredIdx++;
                continue;
            }

            // Layout & draw tab
            t.getBounds().setBounds(cx, y, w, h);
            final boolean selected = isTabSelected(t);
            final Rectangle bounds = t.getBounds();

            // Background + border
            g.setColor(selected ? config.getTabSelectedColor() : config.getTabColor());
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, r, r);
            g.setColor(selected ? config.getTabBorderSelectedColor() : config.getTabBorderColor());
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, r, r);

            // Label (with subtle shadow)
            g.setFont(tabFont);
            final int textBase = y + padY + fm.getAscent();
            final int tx = bounds.x + padX;
            g.setColor(new Color(0, 0, 0, 180));
            g.drawString(label, tx + 1, textBase + 1);

            Color labelColor = config.getTabTextColor();
            if (!selected && t.getUnread() > 0) {
                long now = System.currentTimeMillis();
                int offset = (t.getKey().hashCode() & 0x7fffffff) % UNREAD_FLASH_PERIOD_MS;
                float phase = flashPhase(now, UNREAD_FLASH_PERIOD_MS, offset);
                labelColor = lerpColor(config.getTabUnreadPulseFromColor(), config.getTabUnreadPulseToColor(), phase);
            }
            g.setColor(labelColor);
            g.drawString(label, tx, textBase);

            int advanceX = tx + textW;

            // Badge
            if (badgeW > 0) {
                final int bx = advanceX + 4;
                final boolean thinTab = (wNoBadge <= BADGE_THIN_THRESHOLD);

                final Font old = g.getFont();
                g.setFont(notifFont);
                int used = drawBadge(g, nfm, bx, textBase, unreadStr,
                    config.getTabNotificationColor(),
                    config.getTabNotificationTextColor(),
                    thinTab);
                g.setFont(old);

                advanceX = bx + used;
            }

            // Close button
            if (t.isCloseable()) {
                final int closeX = advanceX + 6;
                final int closeY = (y + (h - fm.getHeight()) / 2) + 1;

                g.setColor(config.getTabCloseButtonColor());
                g.fillRoundRect(closeX, closeY, closeH, closeH, closeH, closeH);
                g.setColor(config.getTabCloseButtonTextColor());
                final int xSize = 4;
                g.drawLine(closeX + xSize - 1, closeY + xSize, closeX + closeH - xSize - 1, closeY + closeH - xSize);
                g.drawLine(closeX + closeH - xSize - 1, closeY + xSize, closeX + xSize - 1, closeY + closeH - xSize);

                t.setCloseBounds(new Rectangle(closeX, closeY, closeH, closeH));
            }

            cx = bounds.x + bounds.width + 4;
            filteredIdx++;
        }

        // Draw the dragged tab on top (also clipped)
        if (draggingTab && dragTab != null) {
            int w = (dragTabWidth > 0 ? dragTabWidth : dragTab.getBounds().width);
            int hTab = (dragTabHeight > 0 ? dragTabHeight : h);

            int minX = x + 2;
            int maxX = x + width - w - 2;
            int drawX = MathUtil.clamp(dragVisualX, minX, maxX);

            // background + border
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(drawX + 2, y + 2, w, hTab, r, r);

            final boolean selected = isTabSelected(dragTab);
            g.setColor(selected ? new Color(60, 60, 60, 240) : new Color(45, 45, 45, 220));
            g.fillRoundRect(drawX, y, w, hTab, r, r);
            g.setColor(new Color(255, 255, 255, selected ? 160 : 90));
            g.drawRoundRect(drawX, y, w, hTab, r, r);

            // label
            final String label = dragTab.getTitle();
            final int textW = fm.stringWidth(label);
            final int tx = drawX + padX;
            final int textBase = y + padY + fm.getAscent();
            g.setFont(tabFont);
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(label, tx + 1, textBase + 1);
            g.setColor(Color.WHITE);
            g.drawString(label, tx, textBase);

            // badge for dragged tab
            if (dragTab.getUnread() > 0) {
                final String unreadStrDrag = String.valueOf(dragTab.getUnread());
                final int bx = tx + textW + 4;
                final boolean thinTab = (w <= BADGE_THIN_THRESHOLD);

                final Font old = g.getFont();
                g.setFont(notifFont);
                drawBadge(g, nfm, bx, textBase, unreadStrDrag,
                    new Color(200, 60, 60, 230),
                    Color.WHITE,
                    thinTab);
                g.setFont(old);
            }
        }

        // restore clip
        g.setClip(oldClip);

        tabsBarBounds.setBounds(x, y, width, h);
        return h;
    }


    @SuppressWarnings({"SameParameterValue", "UnnecessaryLocalVariable"})
    private void drawInputBox(
        Graphics2D g,
        FontMetrics fm,
        int left,
        int top,
        int innerW,
        int inputHeight,
        int inputPadX,
        int inputPadY,
        int marginY)
    {
        final int inputX = left;
        final int inputY = top + marginY;
        final int inputW = innerW;
        final int inputInnerLeft = inputX + inputPadX;
        final int inputInnerRight = inputX + inputW - inputPadX;

        inputBounds.setBounds(inputX, inputY, inputW, inputHeight);

        // Box
        g.setColor(config.getInputBackgroundColor());
        g.fillRoundRect(inputX, inputY, inputW, inputHeight, 8, 8);
        g.setColor(config.getInputBorderColor());
        g.drawRoundRect(inputX, inputY, inputW, inputHeight, 8, 8);

        // Prefix
        String prefix = getPlayerPrefix();
        int prefixW = fm.stringWidth(prefix);
        int baseline = inputY + inputPadY + fm.getAscent();

        // caret layout + horizontal scroll
        int caretPx = fm.stringWidth(inputBuf.substring(0, Math.min(caret, inputBuf.length())));
        int caretScreenX = inputInnerLeft + prefixW + caretPx - inputScrollPx;

        int leftLimit = inputInnerLeft + prefixW;
        int rightLimit = inputInnerRight;
        if (caretScreenX > rightLimit) {
            inputScrollPx += (caretScreenX - rightLimit);
            caretScreenX = rightLimit;
        } else if (caretScreenX < leftLimit) {
            inputScrollPx -= (leftLimit - caretScreenX);
            caretScreenX = leftLimit;
        }
        if (inputScrollPx < 0) inputScrollPx = 0;

        if (hasSelection()) {
            int selL = Math.min(selStart, selEnd);
            int selR = Math.max(selStart, selEnd);

            int selStartX = inputInnerLeft + prefixW + fm.stringWidth(inputBuf.substring(0, selL)) - inputScrollPx;
            int selEndX   = inputInnerLeft + prefixW + fm.stringWidth(inputBuf.substring(0, selR)) - inputScrollPx;

            int sx = Math.max(selStartX, leftLimit);
            int ex = Math.min(selEndX, rightLimit);

            if (ex > sx) {
                g.setColor(INPUT_SELECTION_BG);
                int caretTop = baseline - fm.getAscent();
                int caretBottom = baseline + fm.getDescent();
                g.fillRect(sx, caretTop, ex - sx, caretBottom - caretTop);
            }
        }

        // Shadow
        g.setColor(config.getInputShadowColor());
        g.drawString(prefix, inputInnerLeft + 1, baseline + 1);
        g.drawString(visibleInputText(fm, inputInnerRight - (inputInnerLeft + prefixW), inputScrollPx),
            inputInnerLeft + prefixW + 1, baseline + 1);

        // Text
        g.setColor(getInputPrefixColor());
        g.drawString(prefix, inputInnerLeft, baseline);
        g.setColor(config.getInputTextColor());
        g.drawString(visibleInputText(fm, inputInnerRight - (inputInnerLeft + prefixW), inputScrollPx),
            inputInnerLeft + prefixW, baseline);

        // Caret
        long now = System.currentTimeMillis();
        if (now - lastBlinkMs > 500) { caretOn = !caretOn; lastBlinkMs = now; }
        if (inputFocused && caretOn && !hasSelection()) {
            g.setColor(config.getInputCaretColor());
            int caretTop = baseline - fm.getAscent();
            int caretBottom = baseline + fm.getDescent();
            g.fillRect(caretScreenX, caretTop, 1, caretBottom - caretTop);
        }
    }

    private int drawBadge(Graphics2D g, FontMetrics fm, int bx, int textBase,
                          String text, Color bg, Color fg, boolean thinTab)
    {
        int bH = badgeHeight(fm);
        int bW = computeBadgeWidth(fm, text, thinTab);

        textBase -= 1;
        int labelTop = textBase - fm.getAscent();
        int labelHeight = fm.getAscent() + fm.getDescent();
        int by = labelTop + (labelHeight - bH) / 2;

        g.setColor(bg);
        g.fillRoundRect(bx, by, bW, bH, bH, bH);
        g.setColor(fg);
        int nx = bx + (bW - fm.stringWidth(text)) / 2;
        g.drawString(text, nx, textBase);

        return bW;
    }

    private int badgeHeight(FontMetrics fm) {
        return Math.max(BADGE_MIN_H, fm.getAscent() + fm.getDescent() - 2);
    }

    private int computeBadgeWidth(FontMetrics fm, String text, boolean thinTab) {
        int base = Math.max(BADGE_MIN_W, fm.stringWidth(text) + BADGE_TEXT_PAD);
        int shrunk = thinTab ? base - BADGE_SHRINK_PX : base;
        return Math.max(badgeHeight(fm), shrunk); // keep pill at least as wide as tall
    }

    private String selectPrivateContainer(String targetName) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to select private container with null or empty target name");
            return null;
        }

        if (targetName.startsWith("private_")) {
            log.warn("Attempted to select private container with contained name starting with 'private_'");
        }

        String tabKey = "private_" + targetName;
        // Create a new tab for this private chat
        if (!tabsByKey.containsKey(tabKey)) {
            Tab tab = createPrivateTab(tabKey, targetName);
            addTab(tab);
        }

        MessageContainer privateContainer = privateContainers.get(targetName);
        if (privateContainer == null) {
            privateContainer = messageContainerProvider.get();
            privateContainer.setPrivate(true);
            privateContainer.startUp(config.getMessageContainerConfig(), ChatMode.PRIVATE);
            privateContainers.put(targetName, privateContainer);
        }

        if (messageContainer != null) {
            messageContainer.setHidden(true);
        }

        messageContainer = privateContainer;
        messageContainer.setHidden(false);
        messageContainer.setAlpha(1f);
        return tabKey;
    }

    private Tab createPrivateTab(String key, String targetName) {
        return new Tab(key, targetName, true);
    }

    private void selectMessageContainer(ChatMode chatMode) {
        MessageContainer container = messageContainers.get(chatMode.name());
        if (container == null) {
            log.debug("No message container found for chat mode: {}", chatMode);
            return;
        }

        if (messageContainer == container) {
            log.debug("Already selected message container for chat mode: {}", chatMode);
            return;
        }

        if (messageContainer != null) {
            messageContainer.setHidden(true);
        }

        messageContainer = container;
        messageContainer.setHidden(false);
        messageContainer.setAlpha(1f);
    }

    public void refreshTabs() {
        tabsScrollPx = 0;

        int i = -1;
        Map<ChatMode, Integer> orderMap = new HashMap<>();
        for (Tab tab : tabOrder) {
            suggestedOrder.put(tab.getKey(), ++i);
            if (tab.isPrivate())
                continue;
            try {
                orderMap.put(ChatMode.valueOf(tab.getKey()), i);
                suggestedOrder.put(tab.getKey(), i);
            } catch (Exception ignore) {
                log.debug("Ignoring tab {} because it is unknown", tab);
            }
        }

        availableChatModes.forEach((mode) -> {
            if (mode == ChatMode.PRIVATE) return;
            removeTab(mode);
        });

        recomputeAvailableModes();

        for (ChatMode mode : availableChatModes) {
            if (mode == ChatMode.PRIVATE) return;

            String tabKey = tabKey(mode);
            if (!tabsByKey.containsKey(tabKey)) {
                String tabName = defaultTabNames.getOrDefault(mode, mode.name());
                int index = orderMap.getOrDefault(mode, -1);
                addTab(new Tab(tabKey, tabName, false), index != -1
                    ? index
                    : suggestedOrder.getOrDefault(mode.name(), -1));
            }
        }
    }

    private int removeTab(Tab t) {
        return removeTab(t, true);
    }

    private int removeTab(Tab t, boolean keepContainer) {
        if (t == null) {
            log.warn("Attempted to remove null tab");
            return -1;
        }

        if (t.isPrivate()) {
            return removePrivateTab(t.getKey(), keepContainer);
        } else {
            return removeTab(t.getKey(), keepContainer);
        }
    }

    public int removePrivateTab(String targetName) {
        return removePrivateTab(targetName, true);
    }

    public int removePrivateTab(String targetName, boolean keepContainer) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to remove private tab with null or empty target name");
            return -1;
        }

        String key = targetName.startsWith("private_") ? targetName : "private_" + targetName;
        return removeTab(key, keepContainer);
    }

    public int removeTab(ChatMode chatMode) {
        if (chatMode == ChatMode.PRIVATE) {
            log.warn("Attempted to remove tab for private chat mode, use removePrivateTab instead");
            return -1;
        }

        String key = tabKey(chatMode);
        if (StringUtil.isNullOrEmpty(key)) {
            log.warn("Attempted to remove tab with null or empty key for chat mode: {}", chatMode);
            return -1;
        }

        return removeTab(key, true);
    }

    public int removeTab(String key) {
        return removeTab(key, true);
    }

    public int removeTab(String key, boolean keepContainer) {
        if (StringUtil.isNullOrEmpty(key)) {
            log.warn("Attempted to remove tab with null or empty key");
            return -1;
        }

        int tabIndex = -1;
        Tab nextTab = null;
        Tab tab = tabsByKey.remove(key);
        if (tab != null) {
            tabIndex = tabOrder.indexOf(tab);
            if (tabIndex >= 0 && tabIndex < tabOrder.size() - 1) {
                nextTab = tabOrder.get(tabIndex + 1);
            } else if (tabIndex > 0) {
                nextTab = tabOrder.get(tabIndex - 1);
            }
            tabOrder.remove(tab);
        } else {
            log.warn("Attempted to remove non-existing tab for key {}", key);
        }

        Map<String, MessageContainer> containers = null;
        String containerKey = key;

        if (key.startsWith("private_")) {
            containers = privateContainers;
            containerKey = key.substring("private_".length());
        } else {
            containers = messageContainers;
        }

        if (containers != null) {
            if (!keepContainer) {
                MessageContainer container = containers.remove(containerKey);
                if (container != null) {
                    container.shutDown();
                } else {
                    log.warn("Attempted to remove non-existing container for key: {}", containerKey);
                }
            }
        }

        if (nextTab != null) {
            selectTab(nextTab);
        }

        return tabIndex;
    }

    public void recomputeAvailableModes() {
        EnumSet<ChatMode> set = EnumSet.of(ChatMode.PUBLIC); // always if logged in

        FriendsChatManager friendsChatManager = client.getFriendsChatManager();
        String friendsChatName = friendsChatManager != null ? friendsChatManager.getName() : null;
        if (friendsChatManager != null && friendsChatManager.getName() != null) {
            set.add(ChatMode.FRIENDS_CHAT);
        }

        if (client.getClanChannel() != null)
            set.add(ChatMode.CLAN_MAIN);
        if (client.getGuestClanChannel() != null)
            set.add(ChatMode.CLAN_GUEST);
        if (client.getClanChannel(ClanID.GROUP_IRONMAN) != null)
            set.add(ChatMode.CLAN_GIM);

        availableChatModes = set;
    }

    private void addTab(Tab t) {
        addTab(t, -1);
    }

    private void addTab(Tab t, int index) {
        if (index < 0 || index >= tabOrder.size()) {
            tabOrder.add(t);
        } else {
            // Insert at the specified index
            tabOrder.add(index, t);
        }

        try {
            tabsByKey.put(t.getKey(), t);
        } catch (Exception e) {
            log.error("Failed to add tab for key '{}': {}", t.getKey(), e.getMessage());
        }
    }

    public void selectTabByKey(String key) {
        Tab t = tabsByKey.get(key);
        if (t == null) {
            log.warn("Attempted to select non-existing tab with key: {}", key);
            return;
        }

        t.setUnread(0);
        if (t.isPrivate()) {
            selectPrivateContainer(t.getTargetName());
        } else {
            selectMessageContainer(ChatMode.valueOf(key));
        }

        if (activeTab != null && activeTab.equals(t)) // already selected
            return;

        Tab lastActive = activeTab;
        activeTab = t;
        eventBus.post(new TabChangeEvent(activeTab, lastActive));

        if (messageContainer != null) {
            messageContainer.setUserScrolled(false);
            messageContainer.setScrollOffsetPx(Integer.MAX_VALUE);
            messageContainer.setAlpha(1f);
        }

        if (!inputFocused)
            focusInput(); // autofocus input when switching tabs
    }

    public Color getInputPrefixColor() {
        Color prefixColor = messageContainer != null ? messageContainer.getTextColor() : null;
        return prefixColor != null ? prefixColor : config.getInputPrefixColor();
    }

    public boolean isTabSelected(Tab t) {
        return t.equals(activeTab);
    }

    public static String tabKey(ChatMode mode) {
        return mode.name();
    }

    @Subscribe
    public void onLeftDialogOpenedEvent(LeftDialogOpenedEvent e) {
        clientThread.invoke(() -> showLegacyChat());
    }

    @Subscribe
    public void onLeftDialogClosedEvent(LeftDialogClosedEvent e) {
        clientThread.invokeLater(() -> hideLegacyChat());
    }

    @Subscribe
    public void onRightDialogOpenedEvent(RightDialogOpenedEvent e) {
        clientThread.invoke(() -> showLegacyChat());
    }

    @Subscribe
    public void onRightDialogClosedEvent(RightDialogClosedEvent e) {
        clientThread.invokeLater(() -> hideLegacyChat());
    }

    @Subscribe
    public void onDialogOptionsOpenedEvent(DialogOptionsOpenedEvent e) {
        clientThread.invoke(() -> showLegacyChat());
    }

    @Subscribe
    public void onDialogOptionsClosedEvent(DialogOptionsClosedEvent e) {
        clientThread.invokeLater(() -> hideLegacyChat());
    }

    @Subscribe
    public void onChatToggleEvent(ChatToggleEvent e) {
        if (e.isHidden() != hidden) {
            setHidden(e.isHidden());
        }
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged e) {
        if (e.getIndex() == VarClientStr.CHATBOX_TYPED_TEXT) {
            // keep the legacy chat input in sync, if the text matches it will be ignored
            setInputText(ClientUtil.getChatInputText(client), false);
        }
    }

    @Subscribe
    public void onClientTick(ClientTick tick) {
        resizeChatbox(desiredChatWidth, desiredChatHeight);

        /*clientThread.invokeAtTickEnd(() -> {
            if (!isHidden() && !legacyShowing)
                hideLegacyChat();
        });*/
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        switch (e.getScriptId()) {
            case ScriptID.BUILD_CHATBOX:
            case ScriptID.MESSAGE_LAYER_OPEN:
            case ScriptID.MESSAGE_LAYER_CLOSE:
            case ScriptID.CHAT_TEXT_INPUT_REBUILD:
                resizeChatbox(desiredChatWidth, desiredChatHeight);
                break;
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened e) {
        if (!isEnabled() || hidden || messageContainer == null)
            return;

        Point mp = client.getMouseCanvasPosition();
        Point mouse = new Point(mp.getX(), mp.getY());

        Menu rootMenu = client.getMenu();

        if (messageContainer != null && messageContainer.hitAt(mouse)) {
            rootMenu.createMenuEntry(1)
                .setOption("Clear messages")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> {
                    if (messageContainer != null)
                        messageContainer.clearMessages();
                });
        }

        RowHit hit = messageContainer != null ? messageContainer.rowAt(mouse) : null;
        if (hit != null) {
            final String rowText = buildPlainRowText(hit.getVLine());

            rootMenu.createMenuEntry(1)
                .setOption("Copy line")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> copyToClipboard(rowText));

            String targetName = hit.getTargetName();
            if (targetName != null && !targetName.isBlank() && !targetName.equals(getLocalPlayerName())) {
                rootMenu.createMenuEntry(2)
                    .setOption("Message " + ColorUtil.wrapWithColorTag(targetName, Color.ORANGE))
                    .setType(MenuAction.RUNELITE)
                    .onClick(me -> {
                        setHidden(false);
                        selectPrivateTab(targetName);
                        focusInput();
                    });
            }
        }

        // Tab bar menu
        Tab hovered = tabAt(mouse);
        if (hovered != null) {
            MenuEntry parent = client.getMenu().createMenuEntry(1)
                .setOption("Tab:")
                .setTarget(hovered.getTitle())
                .setType(MenuAction.RUNELITE);

            Menu sub = parent.createSubMenu();

            int index = 0;
            if (hovered.isCloseable()) {
                sub.createMenuEntry(index++)
                    .setOption("Close")
                    .setType(MenuAction.RUNELITE)
                    .onClick(me -> removeTab(hovered));
            }

            sub.createMenuEntry(index++)
                .setOption("Clear messages")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> clear());

            sub.createMenuEntry(index++)
                .setOption("Move left")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> moveTab(hovered, -1));

            sub.createMenuEntry(index++)
                .setOption("Move right")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> moveTab(hovered, +1));

            sub.createMenuEntry(index++)
                .setOption("Mark all as read")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> hovered.setUnread(0));
        }

        eventBus.post(new ChatMenuOpenedEvent());
    }

    private String getLocalPlayerName() {
        Player lp = client.getLocalPlayer();
        return lp != null && lp.getName() != null ? Text.removeTags(lp.getName()) : "Player";
    }

    public void inputTick() {
        if (inputFocused) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkMs > 500) {
                caretOn = !caretOn;
                lastBlinkMs = now;
            }
        }
    }

    private boolean moveTabToIndex(Tab tab, int newIndex) {
        int old = tabOrder.indexOf(tab);
        if (old < 0)
            return false;

        newIndex = Math.max(0, Math.min(newIndex, tabOrder.size() - 1));
        int adjusted = newIndex > old ? newIndex - 1 : newIndex;
        if (adjusted == old)
            return false;

        tabOrder.remove(old);
        tabOrder.add(adjusted, tab);
        return true;
    }

    private int targetIndexForX(int mouseX) {
        int idx = 0;
        for (Tab t : tabOrder) {
            Rectangle b = t.getBounds();
            int center = b.x + b.width / 2;
            if (mouseX < center) return idx;
            idx++;
        }
        return Math.max(0, tabOrder.size() - 1);
    }

    private int targetIndexForXSkipping(Tab skip, int mouseX) {
        int idx = 0;
        for (Tab t : tabOrder) {
            if (t == skip)
                continue;
            Rectangle b = t.getBounds();
            int center = b.x + b.width / 2;
            if (mouseX < center)
                return idx;
            idx++;
        }
        return idx; // end
    }

    private int targetIndexForDrag(Tab skip, int dragLeftX) {
        int w = (dragTabWidth > 0 ? dragTabWidth : skip.getBounds().width);
        int dragCenter = dragLeftX + w / 2;

        int idx = 0; // counts tabs except the dragged one
        for (Tab t : tabOrder) {
            if (t == skip)
                continue;
            Rectangle b = t.getBounds();
            int center = b.x + b.width / 2;
            if (dragCenter < center)
                return idx;
            idx++;
        }
        return idx; // end (after last)
    }

    private String visibleInputText(FontMetrics fm, int availWidth, int scrollPx) {
        String full = inputBuf.toString();
        if (full.isEmpty())
            return "";
        int start = 0, acc = 0;
        while (start < full.length()) {
            int w = fm.charWidth(full.charAt(start));
            if (acc + w > scrollPx)
                break;
            acc += w; start++;
        }
        int end = start, used = 0;
        while (end < full.length()) {
            int w = fm.charWidth(full.charAt(end));
            if (used + w > availWidth)
                break;
            used += w; end++;
        }
        return full.substring(start, end);
    }

    private boolean commitReorder(Tab tab, int filteredIndex) {
        int old = tabOrder.indexOf(tab);
        if (old < 0)
            return false;

        // Remove the dragged tab first, then insert at filtered index (list w/o tab)
        tabOrder.remove(old);
        filteredIndex = MathUtil.clamp(filteredIndex, 0, tabOrder.size());
        tabOrder.add(filteredIndex, tab);
        return filteredIndex != old;
    }

    private String getPlayerPrefix() {
        Player lp = client.getLocalPlayer();
        String name = lp != null && lp.getName() != null ? Text.removeTags(lp.getName()) : "Player";
        return name + ": ";
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden)
            return; // no change

        if (!hidden && legacyShowing) {
            log.warn("Attempted to show ModernChat while legacy chat is showing, hiding legacy chat first");
            return;
        }

        if (!hidden && ClientUtil.isSystemWidgetActive(client))
            return;

        this.hidden = hidden;

        if (hidden)
            unfocusInput();
        else
            focusInput();

        eventBus.post(new ModernChatVisibilityChangeEvent(!this.hidden));
    }

    public void focusInput() {
        if (hidden) return;

        inputFocused = true;
        caret = inputBuf.length(); // place caret at end
        clearSelection();
    }

    public void unfocusInput() {
        inputFocused = false;
        caretOn = false;
        lastBlinkMs = 0;
    }

    public void registerMouseListener() {
        mouseManager.registerMouseListener(1, mouse);
        mouseManager.registerMouseWheelListener(mouse);
    }

    public void unregisterMouseListener() {
        mouseManager.unregisterMouseListener(mouse);
        mouseManager.unregisterMouseWheelListener(mouse);
    }

    public void registerKeyboardListener() {
        keyManager.registerKeyListener(keys);
    }

    public void unregisterKeyboardListener() {
        keyManager.unregisterKeyListener(keys);
    }

    public String getInputText() {
        return inputBuf.toString();
    }

    private void commitInput() {
        final String text = getInputText().trim();
        if (!text.isEmpty()) {
            Player player = client.getLocalPlayer();
            if (player != null)
                sendMessage(text);

            if (messageContainer != null) {
                messageContainer.setUserScrolled(false);
                messageContainer.setScrollOffsetPx(Integer.MAX_VALUE); // snap to bottom
            } else {
                log.warn("Attempted to send chat message to null message container, likely a bug");
            }
        }

        inputBuf.setLength(0);
        caret = 0;
        inputScrollPx = 0;
        clearSelection(); // ensure selection cleared after send
    }

    public @Nullable String getCurrentTarget() {
        return activeTab != null && activeTab.isPrivate() ? activeTab.getTargetName() : null;
    }

    public void sendPrivateChat(String text) {
        String targetName = getCurrentTarget();
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to send private chat without a target name");
            return;
        }

        messageService.sendPrivateChat(text, targetName);
    }

    public void sendMessage(String text) {
        messageService.sendMessage(text, getCurrentMode(), getCurrentTarget());
    }

    private int getCharacterLimit() {
        // Seems the limit is 80 for all chat modes, so I
        // will leave this here in case it changes in the future
        switch (getCurrentMode()) {
            case PUBLIC:
                return 80;
            case FRIENDS_CHAT:
            case CLAN_MAIN:
            case CLAN_GUEST:
                return 80;
            case CLAN_GIM:
                return 80;
            case PRIVATE:
                return 80;
            default:
                log.warn("Unknown chat mode: {}, using default character limit", getCurrentMode());
                return 80; // fallback limit
        }
    }

    public ChatMode getCurrentMode() {
        return messageContainer != null ? messageContainer.getChatMode() : config.getDefaultChatMode();
    }

    public void addMessage(MessageLine line) {
        addMessage(
            line.getText(),
            line.getType(),
            line.getTimestamp(),
            line.getSenderName(),
            line.getReceiverName(),
            line.getPrefix());
    }

    public void addMessage(
        String line,
        ChatMessageType type,
        long timestamp,
        String senderName,
        String receiverName,
        String prefix
    ) {
        Tab tab = null;
        MessageContainer container;
        ChatMode mode = ChatUtil.toChatMode(type);

        String targetName = type == ChatMessageType.PRIVATECHATOUT || type == ChatMessageType.FRIENDNOTIFICATION
            ? receiverName
            : senderName;

        if (mode == ChatMode.PRIVATE) {
            if (StringUtil.isNullOrEmpty(targetName) && !line.startsWith("Unable to send message ")) {
                log.warn("Attempted to add private message without a receiver name");
                return;
            }

            if (config.isOpenTabOnIncomingPM()) {
                Pair<Tab, MessageContainer> pair = openTabForPrivateChat(targetName);
                if (pair == null) {
                    log.warn("Failed to open tab for private chat with target: {}", targetName);
                    return;
                }

                tab = pair.getLeft();
                container = pair.getRight();
            } else {
                if (!targetName.equals(getLocalPlayerName()))
                    container = openPrivateMessageContainer(targetName);
                else
                    container = messageContainer;
            }

            if (type != ChatMessageType.PRIVATECHATOUT && type != ChatMessageType.FRIENDNOTIFICATION && !isPrivateTabOpen(targetName)) {
                MessageContainer publicContainer = messageContainers.get(ChatMode.PUBLIC.name());
                if (publicContainer != null) {
                    publicContainer.pushLine(line, type, timestamp, senderName, receiverName, targetName, prefix);

                    Tab publicTab = tabsByKey.get(tabKey(ChatMode.PUBLIC));
                    if (!activeTab.equals(publicTab) && publicTab.getUnread() < 99)
                        publicTab.incrementUnread();
                }
            }
        } else {
            container = messageContainers.get(mode.name());
            tab = tabsByKey.get(tabKey(mode));
        }

        if (container == null) {
            log.warn("No message container found for chat type: {}", type);
            return;
        }

        container.pushLine(line, type, timestamp, senderName, receiverName, targetName, prefix);

        if (messageContainer != container) {
            if (tab != null) {
                if (tab.getUnread() < 99)
                    tab.incrementUnread();
            } else {
                log.debug("No tab found for chat mode: {}", mode);
            }
        }
    }

    public boolean isPrivateTabOpen(String targetName) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to check private tab with null or empty target name");
            return false;
        }

        String tabKey = "private_" + targetName;
        return tabsByKey.containsKey(tabKey);
    }

    public Tab selectPrivateTab(String targetName) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to select private tab with null or empty target name");
            return null;
        }

        Pair<Tab, MessageContainer> pair = openTabForPrivateChat(targetName);
        Tab tab = pair.getLeft();

        selectTab(tab);
        return tab;
    }

    private Tab getPrivateTab(String targetName) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to get private tab with null or empty target name");
            return null;
        }

        String tabKey = "private_" + targetName;
        Tab tab = tabsByKey.get(tabKey);
        if (tab == null) {
            log.warn("No private tab found for target: {}", targetName);
        }
        return tab;
    }

    public void selectTab(Tab tab) {
        selectTabByKey(tab.getKey());
    }

    public Pair<Tab, MessageContainer> openTabForPrivateChat(String targetName) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to open private chat tab with null or empty target name");
            return null;
        }

        Tab tab;
        String tabKey = "private_" + targetName;
        if (!tabsByKey.containsKey(tabKey)) {
            tab = createPrivateTab(tabKey, targetName);
            addTab(tab);

            if (config.isAutoSelectPrivateTab()) {
                selectTab(tab); // auto-select if configured
            }
        } else {
            tab = tabsByKey.get(tabKey);
        }

        // For private messages we need to create a container if it doesn't exist
        MessageContainer container = openPrivateMessageContainer(targetName);
        return Pair.of(tab, container);
    }

    private MessageContainer openPrivateMessageContainer(String targetName) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to open private message container with null or empty target name");
            return null;
        }

        MessageContainer container = privateContainers.get(targetName);
        if (container == null) {
            container = messageContainerProvider.get();
            container.setPrivate(true);
            container.startUp(config.getMessageContainerConfig(), ChatMode.PRIVATE);
            privateContainers.put(targetName, container);
        }
        return container;
    }

    private String buildPlainRowText(VisualLine vl) {
        StringBuilder sb = new StringBuilder();
        for (TextSegment ts : vl.getSegs()) sb.append(ts.getText());
        return sb.toString();
    }

    private void copyToClipboard(String s) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new StringSelection(s == null ? "" : s), null);
    }

    private @Nullable Tab tabAt(Point p) {
        java.awt.Point point = new java.awt.Point(p.getX(), p.getY());
        if (lastViewport == null || !tabsBarBounds.contains(point))
            return null;
        for (Tab t : tabOrder)
            if (t.getBounds().contains(point))
                return t;
        return null;
    }

    private void moveTab(Tab t, int dir) {
        int i = tabOrder.indexOf(t);
        if (i < 0)
            return;
        int j = Math.max(0, Math.min(tabOrder.size() - 1, i + dir));
        if (i == j)
            return;
        tabOrder.remove(i);
        tabOrder.add(j, t);
    }

    private void clampTabScroll(int viewWidth) {
        tabsMaxScroll = Math.max(0, tabsTotalWidth - viewWidth);
        tabsScrollPx = MathUtil.clamp(tabsScrollPx, 0, tabsMaxScroll);
    }

    @Override
    public boolean isResizable() {
        return config.isResizeable();
    }

    public void setDesiredChatSize(String newW, String newH) {
        if (newW == null || newH == null) {
            log.debug("Attempted to set desired chat size with null width or height");
            return;
        }

        try {
            int width = Integer.parseInt(newW);
            int height = Integer.parseInt(newH);
            setDesiredChatSize(width, height);
        } catch (NumberFormatException e) {
            log.debug("Unable to parse desired chat size: {}x{} - {}", newW, newH, e.getMessage());
        }
    }

    public void setDesiredChatSize(int newW, int newH) {
        if (newW <= 0 || newH <= 0) return;

        desiredChatWidth = newW;
        desiredChatHeight = newH;
    }

    private void resizeChatbox(int width, int height) {
        if (width <= 0 || height <= 0)
            return;

        if (lastViewport == null || (width == lastViewport.width && height == lastViewport.height))
            return;

        Widget chatViewport = widgetBucket.getChatboxViewportWidget();
        if (chatViewport == null)
            return;

        Widget chatboxParent = widgetBucket.getChatParentWidget();
        if (chatboxParent == null)
            return;

        chatViewport.setOriginalHeight(height);
        chatViewport.setOriginalWidth(width);

        chatboxParent.setHeightMode(WidgetSizeMode.ABSOLUTE);
        chatboxParent.setWidthMode(WidgetSizeMode.ABSOLUTE);
        chatboxParent.setOriginalHeight(chatViewport.getOriginalHeight());
        chatboxParent.setOriginalWidth(chatViewport.getOriginalWidth());

        chatboxParent.revalidate();
        chatViewport.revalidate();

        client.refreshChat();

        if (messageContainer != null)
            messageContainer.dirty();

        eventBus.post(new ChatResizedEvent(width, height));
    }

    public void resetChatbox() {
        Widget chatViewport = widgetBucket.getChatboxViewportWidget();
        if (chatViewport != null && !chatViewport.isHidden()) {
            chatViewport.setOriginalHeight(165);
            chatViewport.setOriginalWidth(519);
            chatViewport.revalidate();
        }

        Widget chatboxParent = widgetBucket.getChatParentWidget();
        if (chatboxParent != null) {
            chatboxParent.setOriginalHeight(0);
            chatboxParent.setOriginalWidth(0);
            chatboxParent.setHeightMode(WidgetSizeMode.MINUS);
            chatboxParent.setWidthMode(WidgetSizeMode.MINUS);
            chatboxParent.revalidate();
        }

        client.refreshChat();

        if (messageContainer != null)
            messageContainer.dirty();
    }

    public void showLegacyChat() {
        showLegacyChat(true);
    }

    public void showLegacyChat(boolean hideOverlay) {
        legacyShowing = true;
        wasHidden = hidden; // remember if we were hidden before
        resetChatbox();

        if (ClientUtil.setChatHidden(client, false) && hideOverlay) {
            setHidden(true);
        }
    }

    public void hideLegacyChat() {
        hideLegacyChat(true);
    }

    public void hideLegacyChat(boolean showOverlay) {
        if (ClientUtil.isSystemWidgetActive(client))
            return;

        legacyShowing = false;
        resizeChatbox(desiredChatWidth, desiredChatHeight);

        if (ClientUtil.setChatHidden(client, true) && showOverlay) {
            setHidden(wasHidden);
        }
    }

    public void reset() {
        clear();

        inputBuf.setLength(0);
        caret = 0;
        inputScrollPx = 0;
        inputFocused = false;
        caretOn = true;
        lastBlinkMs = 0;
        lastViewport = null;
        fontStyle = null;
        dragTab = null;
        dragTabWidth = 0;

        selStart = 0;
        selEnd = 0;
        selAnchor = -1;
        selectingText = false;

        activeTab = null;
        tabsScrollPx = 0;
        tabsTotalWidth = 0;
        tabsMaxScroll = 0;
        tabsByKey.clear();
        tabOrder.clear();
        availableChatModes.clear();

        refreshTabs(); // reinitialize tabs
    }

    public void clear() {
        activeTab = null;
        messageContainer = null;
        messageContainers.forEach((chatMode, container) -> {
            container.clear();
        });
        privateContainers.forEach((targetName, container) -> {
            container.clear();
        });
    }

    public void clearInputText(boolean sync) {
        setInputText("", sync);
    }

    public boolean setInputText(String value, boolean sync) {
        if (value == null) {
            log.warn("Attempted to set input text to null");
            return false;
        }

        if (inputBuf.toString().equals(value))
            return false; // no change

        int charLimit = getCharacterLimit();
        if (value.length() > charLimit) {
            log.debug("Input text exceeds character limit of {}: {}", charLimit, value);
            value = value.substring(0, charLimit);
        }

        final String text = value.trim();
        if (filterService.isFiltered(text))
            return false; // don't insert filtered text

        inputBuf.setLength(0);
        inputBuf.append(text);
        caret = inputBuf.length();
        inputScrollPx = 0;
        caretOn = true;
        selStart = selEnd = caret; // clear selection on set
        selAnchor = -1;
        lastBlinkMs = System.currentTimeMillis();
        if (sync)
            syncChatInputLater();
        return true;
    }

    private static float flashPhase(long nowMs, int periodMs, int offsetMs) {
        float p = ((nowMs + offsetMs) % periodMs) / (float) periodMs; // 0..1
        // smoother than on/off ease with sine
        return 0.5f * (1f + (float)Math.sin(p * (float)(Math.PI * 2)));
    }

    private static Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int)(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bch = (int)(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int alpha = (int)(a.getAlpha() + (b.getAlpha() - a.getAlpha())* t);
        return new Color(r, g, bch, alpha);
    }

    public void dirty() {
        for (MessageContainer container : messageContainers.values()) {
            container.dirty();
        }
    }

    private boolean hasSelection() { return selStart != selEnd; }
    private void clearSelection() { selStart = selEnd = caret; selAnchor = -1; }
    private void setSelectionRange(int a, int b) {
        selStart = Math.max(0, Math.min(a, inputBuf.length()));
        selEnd = Math.max(0, Math.min(b, inputBuf.length()));
    }
    private int clampIndex(int i) { return Math.max(0, Math.min(i, inputBuf.length())); }

    private void startAnchorIfNeeded() { if (selAnchor == -1) selAnchor = caret; }
    private void setCaretAndMaybeExtend(int newCaret, boolean extend) {
        newCaret = clampIndex(newCaret);
        if (extend) {
            startAnchorIfNeeded();
            caret = newCaret;
            setSelectionRange(selAnchor, caret);
        } else {
            caret = newCaret;
            clearSelection();
        }
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int lo = Math.min(selStart, selEnd), hi = Math.max(selStart, selEnd);
        inputBuf.delete(lo, hi);
        caret = lo;
        clearSelection();
    }

    private void insertTextAtCaret(String s) {
        if (s == null || s.isEmpty())
            return;

        int charLimit = getCharacterLimit();
        int canTake = Math.max(0, charLimit - inputBuf.length() + (hasSelection() ? Math.abs(selEnd - selStart) : 0));
        if (canTake == 0)
            return;
        if (s.length() > canTake)
            s = s.substring(0, canTake);

        if (filterService.isFiltered(s))
            return; // don't insert filtered text

        if (hasSelection())
            deleteSelection();

        inputBuf.insert(caret, s);
        caret += s.length();
    }

    private int prevWordIndex(int from) {
        int i = clampIndex(from);
        if (i == 0)
            return 0;
        while (i > 0 && Character.isWhitespace(inputBuf.charAt(i - 1))) i--;
        while (i > 0 && !Character.isWhitespace(inputBuf.charAt(i - 1))) i--;
        return i;
    }

    private int nextWordIndex(int from) {
        int i = clampIndex(from), n = inputBuf.length();
        if (i >= n)
            return n;
        while (i < n && !Character.isWhitespace(inputBuf.charAt(i))) i++;
        while (i < n && Character.isWhitespace(inputBuf.charAt(i))) i++;
        return i;
    }

    private void syncChatInputLater() {
        clientThread.invokeLater(() -> {
            ClientUtil.setChatInputText(client, getInputText());
            eventBus.post(new VarClientStrChanged(VarClientStr.CHATBOX_TYPED_TEXT));
        });
    }

    private FontMetrics getInputFontMetrics() {
        Font f = getFont().deriveFont((float) config.getInputFontSize());
        return Toolkit.getDefaultToolkit().getFontMetrics(f);
    }

    private int indexFromMouseX(FontMetrics fm, int mouseX, int inputX, int inputW, int prefixW) {
        int leftLimit  = inputX + INPUT_PAD_X + prefixW;
        int rightLimit = inputX + inputW - INPUT_PAD_X;

        int x = Math.max(leftLimit, Math.min(mouseX, rightLimit));
        int xWithin = (x - (inputX + INPUT_PAD_X + prefixW)) + inputScrollPx;
        if (xWithin <= 0)
            return 0;

        int acc = 0, i = 0, len = inputBuf.length();
        while (i < len) {
            int w = fm.charWidth(inputBuf.charAt(i));
            if (acc + w / 2 >= xWithin)
                break; // snap at half char
            acc += w; i++;
        }
        return i;
    }

    public boolean isTabSelected(ChatMessage msg) {
        ChatMessageType type = msg.getType();

        if (ChatUtil.isPrivateMessage(type)) {
            Pair<String, String> senderReceiver = ChatUtil.getSenderAndReceiver(msg, getLocalPlayerName());
            String senderName = senderReceiver.getLeft();

            if (StringUtil.isNullOrEmpty(senderName)) {
                log.warn("Private message with null or empty sender/receiver name: {}", msg.getMessage());
                return false;
            }

            return isPrivateTabOpen(senderName);
        } else {
            ChatMode mode = ChatUtil.toChatMode(type);
            if (mode == null) {
                log.warn("Unknown chat mode for message type: {}", type);
                return false;
            }
            return messageContainer != null && messageContainer.getChatMode() == mode;
        }
    }

    private final class ChatMouse implements MouseListener, MouseWheelListener
    {
        private boolean clickThroughNotificationSent = false;

        private boolean shouldBlockClickThrough(MouseEvent e) {
            boolean shouldBlock = !config.isAllowClickThrough()
                && e.getButton() == MouseEvent.BUTTON1
                && !isHidden()
                && !e.isAltDown()
                && !client.isMenuOpen()
                && lastViewport != null
                && lastViewport.contains(e.getPoint());
            if (shouldBlock && !clickThroughNotificationSent) {
                notificationService.pushHelperNotification(new ChatMessageBuilder()
                    .append("Left-click did not pass through the chat because ")
                    .append(Color.ORANGE, "Allow click-through")
                    .append(" is disabled. This can be changed in the settings."));
                clickThroughNotificationSent = true;
            }

            return shouldBlock;
        }

        @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
        @Override public MouseEvent mouseExited(MouseEvent e) { return e; }
        @Override public MouseEvent mouseMoved(MouseEvent e) { return e; }

        @Override
        public MouseEvent mouseClicked(MouseEvent e) {
            boolean hit = handleMouseClicked(e);
            if (!e.isConsumed() && !hit && shouldBlockClickThrough(e)) {
                e.consume(); // block underlying handlers
            }
            return e;
        }

        public boolean handleMouseClicked(MouseEvent e) {
            if (!isEnabled() || isHidden())
                return false;

            return true;
        }

        @Override
        public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e) {
            if (!isEnabled() || isHidden())
                return e;

            // Scroll the tab bar when the cursor is over it
            if (tabsBarBounds.contains(e.getPoint())) {
                final double rot = e.getPreciseWheelRotation(); // +ve = wheel down
                final int step = e.isShiftDown() ? TAB_WHEEL_STEP * 2 : TAB_WHEEL_STEP;
                tabsScrollPx += (int) Math.round(rot * step);   // down -> scroll right
                clampTabScroll(tabsBarBounds.width);
                e.consume();
                return e;
            }

            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            boolean hit = handleMousePressed(e);
            if (!e.isConsumed() && !hit && shouldBlockClickThrough(e)) {
                e.consume(); // block underlying handlers
            }
            return e;
        }

        public boolean handleMousePressed(MouseEvent e) {
            if (!isEnabled() || isHidden())
                return false;
            if (lastViewport == null)
                return false;

            if (!lastViewport.contains(e.getPoint())) {
                if (config.isClickOutsideToClose()) {
                    setHidden(true);
                }
                return false;
            }

            if (client.isMenuOpen()) {
                return false;
            }

            if (tabsBarBounds.contains(e.getPoint())) {
                for (Tab t : tabOrder) {
                    if (!t.getBounds().contains(e.getPoint())) continue;

                    // Close button (LMB only)
                    if (t.isCloseable() && t.getCloseBounds().contains(e.getPoint())) {
                        if (e.getButton() != MouseEvent.BUTTON1)
                            return false;

                        if (t.getUnread() > 0) t.setUnread(0);
                        removeTab(t);
                        e.consume();
                        return true;
                    }

                    // Prepare drag/click (LEFT only); don't select yet
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        pressX = e.getX();
                        dragTab = t;
                        draggingTab = false;
                        didReorder = false;
                        pendingSelectTabKey = t.getKey();

                        Rectangle b = t.getBounds();
                        dragOffsetX = e.getX() - b.x;
                        dragVisualX = b.x;
                        dragStartIndex = tabOrder.indexOf(t);
                        dragTargetIndex = dragStartIndex; // initial predicted drop index
                        dragTabWidth = b.width;
                        dragTabHeight = b.height;

                        e.consume();
                        return true;
                    }
                    return false; // RMB falls through for RuneLite menu
                }
            }

            // Input focus + selection: LMB only
            if (inputBounds.contains(e.getPoint())) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    inputFocused = true;

                    // caret/selection update
                    FontMetrics fm = getInputFontMetrics();
                    String prefix = getPlayerPrefix();
                    int prefixW = fm.stringWidth(prefix);

                    int clickedIdx = indexFromMouseX(fm, e.getX(), inputBounds.x, inputBounds.width, prefixW);
                    if (e.isShiftDown()) {
                        if (!hasSelection()) selAnchor = caret;
                        setCaretAndMaybeExtend(clickedIdx, true);
                    } else {
                        caret = clickedIdx;
                        selAnchor = caret;
                        clearSelection();
                    }

                    selectingText = true;
                    e.consume();
                    return true;
                }
                return false;
            } else {
                if (e.getButton() == MouseEvent.BUTTON1)
                    inputFocused = false;
            }
            return false;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e)  {
            boolean hit = handleMouseDragged(e);
            if (!e.isConsumed() && !hit && shouldBlockClickThrough(e)) {
                e.consume(); // block underlying handlers
            }
            return e;
        }

        public boolean handleMouseDragged(MouseEvent e)  {
            if (!isEnabled() || isHidden())
                return false;

            if (client.isMenuOpen())
                return false;

            // Selection drag
            if (selectingText && inputFocused) {
                FontMetrics fm = getInputFontMetrics();
                String prefix = getPlayerPrefix();
                int prefixW = fm.stringWidth(prefix);

                int idx = indexFromMouseX(fm, e.getX(), inputBounds.x, inputBounds.width, prefixW);
                if (selAnchor == -1) selAnchor = caret;
                caret = idx;
                setSelectionRange(selAnchor, caret);
                e.consume();
                return true;
            }

            if (dragTab == null)
                return false;

            if (!draggingTab && Math.abs(e.getX() - pressX) >= DRAG_THRESHOLD_PX) {
                draggingTab = true;
            }

            if (draggingTab) {
                // Tab visually follows the mouse
                dragVisualX = e.getX() - dragOffsetX;

                // Predict drop index from the dragged tabs current position,
                // not the raw mouse X, this makes left/right drags feel symmetric.
                dragTargetIndex = targetIndexForDrag(dragTab, dragVisualX);

                e.consume();
                return true;
            }
            return false;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e) {
            boolean hit = handleMouseReleased(e);
            if (!e.isConsumed() && !hit && shouldBlockClickThrough(e)) {
                e.consume(); // block underlying handlers
            }
            return e;
        }

        public boolean handleMouseReleased(MouseEvent e) {
            if (client.isMenuOpen()) {
                return false;
            }

            // End selection drag
            if (selectingText && e.getButton() == MouseEvent.BUTTON1) {
                selectingText = false;
                e.consume();
                return false;
            }

            if (dragTab != null && e.getButton() == MouseEvent.BUTTON1) {
                if (draggingTab) {
                    // Commit reorder if target differs from original index
                    didReorder = (dragTargetIndex != dragStartIndex) && commitReorder(dragTab, dragTargetIndex);
                } else {
                    didReorder = false;
                }

                // Only select if we did not drop/reorder
                if (!didReorder && pendingSelectTabKey != null) {
                    selectTabByKey(pendingSelectTabKey);
                }

                // reset drag state
                draggingTab = false;
                dragTab = null;
                pendingSelectTabKey = null;
                dragStartIndex = -1;
                dragTargetIndex = -1;
                e.consume();
                return true;
            }
            return false;
        }
    }

    private final class InputKeys implements KeyListener
    {
        @Override
        public void keyTyped(KeyEvent e) {
            if (!isEnabled() || isHidden() || !inputFocused)
                return;
            char ch = e.getKeyChar();
            if (ch < 32 || ch == 127 || ch == '\n' || ch == '\r')
                return; // ignore control chars

            // insert with selection awareness
            insertTextAtCaret(String.valueOf(ch));
            e.consume();

            caretOn = true;
            lastBlinkMs = System.currentTimeMillis();
            syncChatInputLater();
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (!isEnabled() || isHidden())
                return;

            final boolean shift = e.isShiftDown();
            final boolean ctrl = e.isControlDown();
            final boolean alt = e.isAltDown();
            int code = e.getKeyCode();

            switch (code) {
                case KeyEvent.VK_LEFT: {
                    if (!inputFocused) return;
                    int newCaret = ctrl ? prevWordIndex(caret) : Math.max(0, caret - 1);
                    setCaretAndMaybeExtend(newCaret, shift);
                    e.consume();
                    break;
                }
                case KeyEvent.VK_RIGHT: {
                    if (!inputFocused) return;
                    int newCaret = ctrl ? nextWordIndex(caret) : Math.min(inputBuf.length(), caret + 1);
                    setCaretAndMaybeExtend(newCaret, shift);
                    e.consume();
                    break;
                }
                case KeyEvent.VK_UP: {
                    if (!inputFocused) return;
                    if (e.isShiftDown()) {
                        eventBus.post(new NavigateHistoryEvent(NavigateHistoryEvent.PREV));
                        e.consume();
                    }
                    break;
                }
                case KeyEvent.VK_DOWN: {
                    if (!inputFocused) return;
                    if (e.isShiftDown()) {
                        eventBus.post(new NavigateHistoryEvent(NavigateHistoryEvent.NEXT));
                        e.consume();
                    }
                    break;
                }
                case KeyEvent.VK_HOME: {
                    if (!inputFocused) return;
                    setCaretAndMaybeExtend(0, shift);
                    e.consume();
                    break;
                }
                case KeyEvent.VK_END: {
                    if (!inputFocused) return;
                    setCaretAndMaybeExtend(inputBuf.length(), shift);
                    e.consume();
                    break;
                }
                case KeyEvent.VK_BACK_SPACE: {
                    if (!inputFocused) return;
                    if (hasSelection()) {
                        deleteSelection();
                    }
                    // TODO: need to figure out why Ctrl + Backspace isn't heard by the key listeners
                    else if (alt/*ctrl*/) {
                        int ni = prevWordIndex(caret);
                        if (ni < caret) {
                            inputBuf.delete(ni, caret);
                            caret = ni;
                        }
                    } else if (caret > 0) {
                        inputBuf.deleteCharAt(caret - 1);
                        caret--;
                    }
                    e.consume();
                    syncChatInputLater();
                    break;
                }
                case KeyEvent.VK_DELETE: {
                    if (!inputFocused) return;
                    if (hasSelection()) {
                        deleteSelection();
                    } else if (ctrl) {
                        int ni = nextWordIndex(caret);
                        if (ni > caret && ni <= inputBuf.length()) inputBuf.delete(caret, ni);
                    } else if (caret < inputBuf.length()) {
                        inputBuf.deleteCharAt(caret);
                    }
                    e.consume();
                    syncChatInputLater();
                    break;
                }
                case KeyEvent.VK_ENTER: {
                    if (!inputFocused) {
                        focusInput();
                        e.consume();
                        return;
                    }
                    commitInput();
                    // if (config.isHideOnSend()) setHidden(true);
                    // e.consume();
                    break;
                }
                case KeyEvent.VK_ESCAPE: {
                    if (config.isHideOnEscape())
                        setHidden(true);
                    e.consume();
                    break;
                }
                case KeyEvent.VK_TAB: {
                    if (activeTab != null && !tabOrder.isEmpty()) {
                        final int size = tabOrder.size();
                        final int dir = e.isShiftDown() ? -1 : 1;
                        int currentIndex = tabOrder.indexOf(activeTab);
                        if (currentIndex < 0) currentIndex = 0;

                        // wrap properly even when dir is -1
                        int nextIndex = Math.floorMod(currentIndex + dir, size);
                        selectTab(tabOrder.get(nextIndex));
                    }
                    e.consume();
                    break;
                }
                case KeyEvent.VK_A: {
                    if (inputFocused && ctrl) {
                        selAnchor = 0; caret = inputBuf.length(); setSelectionRange(0, caret);
                        e.consume();
                    }
                    break;
                }
                case KeyEvent.VK_C: {
                    if (inputFocused && ctrl && hasSelection()) {
                        int lo = Math.min(selStart, selEnd), hi = Math.max(selStart, selEnd);
                        copyToClipboard(inputBuf.substring(lo, hi));
                        e.consume();
                    }
                    break;
                }
                case KeyEvent.VK_X: {
                    if (inputFocused && ctrl && hasSelection()) {
                        int lo = Math.min(selStart, selEnd), hi = Math.max(selStart, selEnd);
                        copyToClipboard(inputBuf.substring(lo, hi));
                        deleteSelection();
                        e.consume();
                        syncChatInputLater();
                    }
                    break;
                }
                case KeyEvent.VK_V: {
                    if (!PASTE_WARNING_SHOWN) {
                        notificationService.pushChatMessage(new ChatMessageBuilder()
                            .append("Jagex does not allow pasting into the chat input."));
                        PASTE_WARNING_SHOWN = true;
                    }
                    /*if (inputFocused && ctrl) {
                        Optional<String> clipboardText = ChatUtil.getClipboardText();
                        if (clipboardText.isPresent()) {
                            String text = clipboardText.get();
                            insertTextAtCaret(text);
                            e.consume();
                            syncChatInputLater();
                        }
                    }*/
                    break;
                }
            }
            // keep caret visible after nav/edit
            if (inputFocused) {
                caretOn = true;
                lastBlinkMs = System.currentTimeMillis();
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }
    }
}
