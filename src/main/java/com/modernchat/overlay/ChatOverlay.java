package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.common.ClanType;
import com.modernchat.common.WidgetBucket;
import com.modernchat.draw.Padding;
import com.modernchat.draw.RowHit;
import com.modernchat.draw.Tab;
import com.modernchat.draw.TextSegment;
import com.modernchat.draw.VisualLine;
import com.modernchat.event.ChatToggleEvent;
import com.modernchat.event.ModernChatVisibilityChangeEvent;
import com.modernchat.event.NavigateHistoryEvent;
import com.modernchat.event.SubmitHistoryEvent;
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
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
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
    @Inject @Getter private ResizePanel resizePanel;
    @Inject private Provider<MessageContainer> messageContainerProvider;

    private ChatOverlayConfig config;
    private final ChatMouse mouse = new ChatMouse();
    private final InputKeys keys = new InputKeys();

    private final List<Tab> tabOrder = new ArrayList<>();
    @Getter private Tab activeTab = null;
    @Getter private final Map<String, Tab> tabsByKey = new ConcurrentHashMap<>();
    @Getter private final Map<ChatMode, String> defaultTabNames = new ConcurrentHashMap<>();
    private final Rectangle tabsBarBounds = new Rectangle();
    private int lastTabBarHeight = 0;

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

    @Getter private boolean hidden = false;

    @Getter private int desiredChatWidth;
    @Getter private int desiredChatHeight;

    // Tab drag state
    private static final int DRAG_THRESHOLD_PX = 3;
    private static final int DRAG_BUFFER_PX = 100;
    private boolean draggingTab = false;
    private boolean didReorder = false;
    private Tab dragTab = null;
    private String pendingSelectTabKey = null;

    private int pressX = 0;             // mouse x at press
    private int dragOffsetX = 0;        // mouseX - tabLeft at press
    private int dragVisualX = 0;        // where we render the dragged tab
    private int dragStartIndex = -1;    // index before drag started
    private int dragTargetIndex = -1;   // predicted drop index (filtered, without the dragged tab)
    private int dragTabWidth = 0;       // cached from tab bounds
    private int dragTabHeight = 0;      // cached from tab bounds

    // Unread flash settings
    private static final int  UNREAD_FLASH_PERIOD_MS = 900;

    public ChatOverlay() {
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public void startUp(ChatOverlayConfig config) {
        startUp(config, config.getMessageContainerConfig());
    }

    public void startUp(ChatOverlayConfig config, MessageContainerConfig containerConfig) {
        this.config = config;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setClearChildren(false);

        defaultTabNames.clear();
        defaultTabNames.put(ChatMode.PUBLIC, "Public");
        defaultTabNames.put(ChatMode.FRIENDS_CHAT, "Friends Chat");
        defaultTabNames.put(ChatMode.CLAN_MAIN, "Clan");
        defaultTabNames.put(ChatMode.CLAN_GUEST, "Clan Guest");

        eventBus.register(this);

        resizePanel.setBaseBoundsProvider(() -> lastViewport);
        resizePanel.setListener(this::setDesiredChatSize);
        resizePanel.startUp();

        registerMouseListener();
        registerKeyboardListener();

        messageContainers.putAll(Map.of(
            ChatMode.PUBLIC.name(), messageContainerProvider.get(),
            ChatMode.FRIENDS_CHAT.name(), messageContainerProvider.get(),
            ChatMode.CLAN_MAIN.name(), messageContainerProvider.get(),
            ChatMode.CLAN_GUEST.name(), messageContainerProvider.get()
        ));

        messageContainers.forEach((mode, container) -> {
            container.setChromeEnabled(true);
            container.startUp(containerConfig, ChatMode.valueOf(mode));
            //container.pushLines(Arrays.asList("Welcome to ModernChat!", "This is a redesigned chatbox with custom features.", "Use the input below; Left/Right move the caret, Enter sends.", "Esc unfocuses the input. Backspace/Delete/Home/End supported.", "Scroll with the mouse wheel, or drag the scrollbar on the right.", "You can customize the appearance in the settings.", "Enjoy your chat experience!", "This is a sample message to fill the chat buffer and demonstrate scrolling.", "Feel free to type here and see how the chat behaves.", "You can also resize the window to see how it adapts.", "Remember, this is just a demo; you can modify the code to suit your needs.", "Have fun exploring the features of ModernChat!", "This is another message to ensure the buffer has enough content for scrolling.", "Keep typing to see how the chatbox handles new messages.", "Here's a longer message to test the wrapping and scrolling behavior of the chatbox.", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.", "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.", "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.", mode));
        });

        refreshTabs();

        if (config.isStartHidden())
            setHidden(true);
    }

    public void shutDown() {
        setHidden(true);
        clientThread.invoke(this::resetChatbox);

        clear();
        unregisterMouseListener();
        unregisterKeyboardListener();

        eventBus.unregister(this);

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
        Font inputFont = FontManager.getRunescapeFont().deriveFont((float) config.getInputFontSize());
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

        Font tabFont = FontManager.getRunescapeFont().deriveFont((float) config.getTabFontSize());
        g.setFont(tabFont);
        FontMetrics tfm = g.getFontMetrics();

        lastTabBarHeight = drawTabBar(g, tfm, left, top, innerW);
        final int msgAreaTop = top + lastTabBarHeight + 3; // gap under tabs
        final int msgBottom = bottom - inputHeight - gapAboveInput;
        final Rectangle msgArea = new Rectangle(left, msgAreaTop, innerW, Math.max(1, msgBottom - msgAreaTop));

        // Inject the msg area into the MessageContainer
        messageContainer.setBoundsProvider(() -> msgArea);
        messageContainer.setHidden(false);
        messageContainer.setAlpha(1f);

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
        final int h = fm.getHeight() + padY * 2;

        // Bar background (subtle)
        g.setColor(config.getTabBarBackgroundColor());
        g.fillRoundRect(x, y, width, h, 8, 8);

        int cx = x + 4; // running x
        final int r = 7; // corner radius

        int filteredIdx = 0; // counts tabs except the dragged one

        for (Tab t : tabOrder) {
            String label = t.getTitle();
            int textW = fm.stringWidth(label);
            int badgeW = (t.getUnread() > 0 && config.isShowNotificationBadge())
                ? (Math.max(15, fm.stringWidth(String.valueOf(t.getUnread())) + 2))
                : 0;
            int closeW = t.isCloseable() ? (fm.getHeight() - 2) : 0;
            int w = padX + textW + (badgeW > 0 ? (2 + badgeW) : 0) + (t.isCloseable() ? (6 + closeW) : 0) + padX;

            // If dragging, insert the placeholder where we predict a drop
            if (draggingTab && dragTab != null && t != dragTab && filteredIdx == dragTargetIndex) {
                cx += (dragTabWidth > 0 ? dragTabWidth : w) + 4; // reserve space for the dragged tab
            }

            // Skip drawing the dragged tab in-flow, we'll draw it floating later
            if (draggingTab && t == dragTab) {
                continue;
            }

            t.getBounds().setBounds(cx, y, w, h);
            boolean selected = isTabSelected(t);

            // Tab background
            Rectangle bounds = t.getBounds();
            g.setColor(selected ? config.getTabSelectedColor() : config.getTabColor());
            g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, r, r);
            g.setColor(selected ? config.getTabBorderSelectedColor() : config.getTabBorderColor());
            g.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, r, r);

            // Text (shadow and label)
            int textBase = y + padY + fm.getAscent() + 1;
            int tx = bounds.x + padX;
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

            // Unread badge
            if (badgeW > 0) {
                int bx = advanceX + 4;
                int by = y + ((h - fm.getHeight()) / 2) + 1;
                int bH = fm.getHeight() - 2;

                g.setColor(config.getTabNotificationColor());
                g.fillRoundRect(bx, by, badgeW, bH, bH, bH);
                g.setColor(config.getTabNotificationTextColor());
                String n = String.valueOf(t.getUnread());
                int nx = bx + (badgeW - fm.stringWidth(n)) / 2;
                int ny = by + fm.getAscent();
                g.drawString(n, nx, ny);

                advanceX = bx + badgeW;
            }

            if (t.isCloseable()) {
                int closeX = advanceX + 6;
                int closeY = (y + (h - fm.getHeight()) / 2) + 1;
                int closeH = fm.getHeight() - 2;

                g.setColor(config.getTabCloseButtonColor());
                g.fillRoundRect(closeX, closeY, closeH, closeH, closeH, closeH);
                g.setColor(config.getTabCloseButtonTextColor());
                int xSize = 4;
                g.drawLine(closeX + xSize - 1, closeY + xSize, closeX + closeH - xSize - 1, closeY + closeH - xSize);
                g.drawLine(closeX + closeH - xSize - 1, closeY + xSize, closeX + xSize - 1, closeY + closeH - xSize);

                advanceX += (closeH + 6);
                t.setCloseBounds(new Rectangle(closeX, closeY, closeH, closeH));
            }

            cx = bounds.x + bounds.width + 4; // spacing between tabs
            if (!(draggingTab && t == dragTab)) {
                filteredIdx++; // count only non-drag tab to align with dragTargetIndex
            }
        }

        // Draw the dragged tab floating on top
        if (draggingTab && dragTab != null) {
            int w = (dragTabWidth > 0 ? dragTabWidth : dragTab.getBounds().width);
            int hTab = (dragTabHeight > 0 ? dragTabHeight : h);

            int minX = x + 2;
            int maxX = x + width - w - 2;
            int drawX = MathUtil.clamp(dragVisualX, minX, maxX);

            // subtle drop shadow for "lifted" effect
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(drawX + 2, y + 2, w, hTab, r, r);

            // draw the tab itself (selected state preserved)
            boolean selected = isTabSelected(dragTab);
            g.setColor(selected ? new Color(60, 60, 60, 240) : new Color(45, 45, 45, 220));
            g.fillRoundRect(drawX, y, w, hTab, r, r);
            g.setColor(new Color(255, 255, 255, selected ? 160 : 90));
            g.drawRoundRect(drawX, y, w, hTab, r, r);

            // label/badge (same layout as above)
            String label = dragTab.getTitle();
            int textW = fm.stringWidth(label);
            int badgeW = (dragTab.getUnread() > 0) ? Math.max(15, fm.stringWidth(String.valueOf(dragTab.getUnread())) + 2) : 0;
            int closeW = dragTab.isCloseable() ? (fm.getHeight() - 2) : 0;
            int tx = drawX + padX;
            int textBase = y + padY + fm.getAscent() + 1;
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString(label, tx + 1, textBase + 1);
            g.setColor(Color.WHITE);
            g.drawString(label, tx, textBase);

            int advanceX = tx + textW;
            if (badgeW > 0) {
                int bx = advanceX + 4;
                int by = y + ((hTab - fm.getHeight()) / 2) + 1;
                int bH = fm.getHeight() - 2;
                g.setColor(new Color(200, 60, 60, 230));
                g.fillRoundRect(bx, by, badgeW, bH, bH, bH);
                g.setColor(Color.WHITE);
                String n = String.valueOf(dragTab.getUnread());
                int nx = bx + (badgeW - fm.stringWidth(n)) / 2;
                int ny = by + fm.getAscent();
                g.drawString(n, nx, ny);
            }
        }

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

        // Shadow
        g.setColor(config.getInputShadowColor());
        g.drawString(prefix, inputInnerLeft + 1, baseline + 1);
        g.drawString(visibleInputText(fm, inputInnerRight - (inputInnerLeft + prefixW), inputScrollPx),
            inputInnerLeft + prefixW + 1, baseline + 1);

        // Text
        g.setColor(config.getInputPrefixColor());
        g.drawString(prefix, inputInnerLeft, baseline);
        g.setColor(config.getInputTextColor());
        g.drawString(visibleInputText(fm, inputInnerRight - (inputInnerLeft + prefixW), inputScrollPx),
            inputInnerLeft + prefixW, baseline);

        // Caret
        long now = System.currentTimeMillis();
        if (now - lastBlinkMs > 500) { caretOn = !caretOn; lastBlinkMs = now; }
        if (inputFocused && caretOn) {
            g.setColor(config.getInputCaretColor());
            int caretTop = baseline - fm.getAscent();
            int caretBottom = baseline + fm.getDescent();
            g.fillRect(caretScreenX, caretTop, 1, caretBottom - caretTop);
        }
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

        messageContainer = privateContainer;
        messageContainer.setHidden(false);
        messageContainer.setAlpha(1f);
        return tabKey;
    }

    private Tab createPrivateTab(String key, String targetName) {
        return new Tab(key, targetName, true);
    }

    private void selectMessageContainer(ChatMode chatMode) {
        if (messageContainer != null) {
            messageContainer.setHidden(true);
        }

        messageContainer = messageContainers.get(chatMode.name());
        if (messageContainer == null) {
            log.debug("No message container found for chat mode: {}", chatMode);
            return;
        }

        messageContainer.setHidden(false);
        messageContainer.setAlpha(1f);
    }

    public void refreshTabs() {
        availableChatModes.forEach((mode) -> {
            if (mode == ChatMode.PRIVATE) return;
            removeTab(mode);
        });

        recomputeAvailableModes();

        availableChatModes.forEach((mode) -> {
            if (mode == ChatMode.PRIVATE) return;

            String tabKey = tabKey(mode);
            if (!tabsByKey.containsKey(tabKey)) {
                String tabName = defaultTabNames.getOrDefault(mode, mode.name());
                addTab(new Tab(tabKey, tabName, false), mode.ordinal());
            }
        });
    }

    private void removeTab(Tab t) {
        removeTab(t, true);
    }

    private void removeTab(Tab t, boolean keepContainer) {
        if (t == null) {
            log.warn("Attempted to remove null tab");
            return;
        }

        if (t.isPrivate()) {
            removePrivateTab(t.getKey(), keepContainer);
        } else {
            removeTab(t.getKey(), keepContainer);
        }
    }

    public void removePrivateTab(String targetName) {
        removePrivateTab(targetName, true);
    }

    public void removePrivateTab(String targetName, boolean keepContainer) {
        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to remove private tab with null or empty target name");
            return;
        }

        String key = targetName.startsWith("private_") ? targetName : "private_" + targetName;
        removeTab(key, keepContainer);
    }

    public void removeTab(ChatMode chatMode) {
        if (chatMode == ChatMode.PRIVATE) {
            log.warn("Attempted to remove tab for private chat mode, use removePrivateTab instead");
            return;
        }

        String key = tabKey(chatMode);
        if (StringUtil.isNullOrEmpty(key)) {
            log.warn("Attempted to remove tab with null or empty key for chat mode: {}", chatMode);
            return;
        }

        removeTab(key, true);
    }

    public void removeTab(String key) {
        removeTab(key, true);
    }

    public void removeTab(String key, boolean keepContainer) {
        if (StringUtil.isNullOrEmpty(key)) {
            log.warn("Attempted to remove tab with null or empty key");
            return;
        }

        Tab nextTab = null;
        Tab tab = tabsByKey.remove(key);
        if (tab != null) {
            int tabIndex = tabOrder.indexOf(tab);
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
        // Clear unread on select
        Tab t = tabsByKey.get(key);
        if (t == null) {
            log.warn("Attempted to select non-existing tab with key: {}", key);
            return;
        }

        activeTab = t;

        t.setUnread(0);
        if (t.isPrivate()) {
            selectPrivateContainer(t.getTargetName());
        } else {
            // Switch containers
            ChatMode mode = ChatMode.valueOf(key);
            selectMessageContainer(mode);
        }

        if (messageContainer != null) {
            messageContainer.setUserScrolled(false);
            messageContainer.setScrollOffsetPx(Integer.MAX_VALUE);
            messageContainer.setAlpha(1f);
        }

        if (!inputFocused)
            focusInput(); // auto-focus input when switching tabs
    }

    public boolean isTabSelected(Tab t) {
        return t.equals(activeTab);
    }

    public static String tabKey(ChatMode mode) {
        return mode.name();
    }

    @Subscribe
    public void onChatToggleEvent(ChatToggleEvent e) {
        if (e.isHidden() != hidden) {
            setHidden(e.isHidden());
        }
    }

    @Subscribe
    public void onClientTick(ClientTick tick) {
        resizeChatbox(desiredChatWidth, desiredChatHeight);
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

        RowHit hit = messageContainer.rowAt(mouse);
        if (hit != null) {
            final String rowText = buildPlainRowText(hit.getVLine());

            /*MenuEntry parent = rootMenu.createMenuEntry(1)
                .setOption("Chat")
                .setTarget("Message")
                .setType(MenuAction.RUNELITE);

            Menu sub = parent.createSubMenu();*/

            rootMenu.createMenuEntry(1)
                .setOption("Copy line")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> copyToClipboard(rowText));

            String targetName = hit.getTargetName();
            if (targetName != null && !targetName.isBlank()) {
                rootMenu.createMenuEntry(1)
                    .setOption("Chat with " + targetName)
                    .setType(MenuAction.RUNELITE)
                    .onClick(me -> {
                        setHidden(false);
                        selectPrivateTab(targetName);
                        focusInput();
                    });
            }
            return;
        }

        // Tab bar menu
        Tab hovered = tabAt(mouse);
        if (hovered != null) {
            MenuEntry parent = client.getMenu().createMenuEntry(1)
                .setOption("Tab:")
                .setTarget(hovered.getTitle())
                .setType(MenuAction.RUNELITE);

            Menu sub = parent.createSubMenu();

            sub.createMenuEntry(0)
                .setOption("Close")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> removeTab(hovered));

            sub.createMenuEntry(1)
                .setOption("Mark all as read")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> hovered.setUnread(0));

            sub.createMenuEntry(2)
                .setOption("Move left")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> moveTab(hovered, -1));

            sub.createMenuEntry(3)
                .setOption("Move right")
                .setType(MenuAction.RUNELITE)
                .onClick(me -> moveTab(hovered, +1));
        }
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
            if (t == skip) continue;
            Rectangle b = t.getBounds();
            int center = b.x + b.width / 2;
            if (mouseX < center) return idx;
            idx++;
        }
        return idx; // end
    }

    private int targetIndexForDrag(Tab skip, int dragLeftX) {
        int w = (dragTabWidth > 0 ? dragTabWidth : skip.getBounds().width);
        int dragCenter = dragLeftX + w / 2;

        int idx = 0; // counts tabs except the dragged one
        for (Tab t : tabOrder) {
            if (t == skip) continue;
            Rectangle b = t.getBounds();
            int center = b.x + b.width / 2;
            if (dragCenter < center) return idx;
            idx++;
        }
        return idx; // end (after last)
    }

    private String visibleInputText(FontMetrics fm, int availWidth, int scrollPx) {
        String full = inputBuf.toString();
        if (full.isEmpty()) return "";
        int start = 0, acc = 0;
        while (start < full.length()) {
            int w = fm.charWidth(full.charAt(start));
            if (acc + w > scrollPx) break;
            acc += w; start++;
        }
        int end = start, used = 0;
        while (end < full.length()) {
            int w = fm.charWidth(full.charAt(end));
            if (used + w > availWidth) break;
            used += w; end++;
        }
        return full.substring(start, end);
    }

    private boolean commitReorder(Tab tab, int filteredIndex) {
        int old = tabOrder.indexOf(tab);
        if (old < 0) return false;

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

        sendPrivateChat(text, targetName);
    }

    public void sendPrivateChat(String text, String targetName) {
        if (StringUtil.isNullOrEmpty(text)) {
            log.warn("Attempted to send empty private chat message");
            return;
        }

        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to send private chat without a target name");
            return;
        }

        clientThread.invoke(() -> {
            client.runScript(ScriptID.PRIVMSG, targetName, text);
            eventBus.post(new SubmitHistoryEvent(text));
        });
    }

    public void sendMessage(String text) {
        sendMessage(text, getCurrentMode());
    }

    public void sendMessage(String text, ChatMode mode) {
        ClanType clanType = ClanType.NORMAL; // default clan type
        ChatMode selectedMode = mode;

        switch (mode) {
            case PUBLIC:
                break;
            case FRIENDS_CHAT:
                break;
            case CLAN_MAIN:
                break;
            case CLAN_GUEST:
                break;

            // Custom
            case CLAN_GIM:
                clanType = ClanType.IRONMAN;
                selectedMode = ChatMode.CLAN_MAIN;
                break;
            case PRIVATE:
                sendPrivateChat(text, getCurrentTarget());
                return; // skip the rest of the logic
        }

        /* - String Message to send
         * - int modes
         * - int (clan type)
         * - int (boolean) use target
         * - int set target
         * */
        final int modeValue = selectedMode.getValue();
        final int clanTypeValue = clanType.getValue();

        int charLimit = getCharacterLimit();
        if (text.length() >= charLimit) {
            // split into multiple messages if too long
            List<String> parts = ChatUtil.chunk(text, charLimit);
            int delay = 0;
            for (String part : parts) {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        clientThread.invoke(() -> {
                            client.runScript(ScriptID.CHAT_SEND, part, modeValue, clanTypeValue, 0, 0);
                        });
                    }
                }, delay);

                delay += 1500;
            }
        } else {
            clientThread.invoke(() -> {
                client.runScript(ScriptID.CHAT_SEND, text, modeValue, clanTypeValue, 0, 0);
            });
        }
    }

    private int getCharacterLimit() {
        switch (getCurrentMode()) {
            case PUBLIC:
                return 80;
            case FRIENDS_CHAT:
            case CLAN_MAIN:
            case CLAN_GUEST:
                return 80;
            case CLAN_GIM:
                return 80; // GIM chat has a lower limit
            case PRIVATE:
                return 80; // private messages have the same limit as public
            default:
                log.warn("Unknown chat mode: {}, using default character limit", getCurrentMode());
                return 80; // fallback limit
        }
    }

    public ChatMode getCurrentMode() {
        return messageContainer != null ? messageContainer.getChatMode() : config.getDefaultChatMode();
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
        MessageContainer container = null;
        ChatMode mode = ChatUtil.toChatMode(type);

        String targetName = type == ChatMessageType.PRIVATECHATOUT
            ? receiverName
            : (type == ChatMessageType.PRIVATECHAT ? senderName : null);

        if (mode == ChatMode.PRIVATE) {
            if (StringUtil.isNullOrEmpty(targetName)) {
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
                container = openPrivateMessageContainer(targetName);
            }

            if (type != ChatMessageType.PRIVATECHATOUT && !isPrivateTabOpen(targetName)) {
                MessageContainer publicContainer = messageContainers.get(ChatMode.PUBLIC.name());
                if (publicContainer != null) {
                    publicContainer.pushLine(line, type, timestamp, senderName, receiverName, targetName, prefix);

                    Tab publicTab = tabsByKey.get(tabKey(ChatMode.PUBLIC));
                    if (activeTab.equals(publicTab) && publicTab.getUnread() < 99)
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
            //selectTabByKey(tabKey); // TODO: config for this
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

    private void setDesiredChatSize(int newW, int newH) {
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

        Widget chatFrame = widgetBucket.getChatBoxArea();
        if (chatFrame == null)
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
        chatFrame.revalidate();
        chatViewport.revalidate();

        client.refreshChat();

        if (messageContainer != null) {
            messageContainer.dirty();
        }
    }

    private void resetChatbox() {
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
        ClientUtil.setChatHidden(client, false);

        if (hideOverlay)
            setHidden(true);
    }

    public void hideLegacyChat() {
        hideLegacyChat(true);
    }

    public void hideLegacyChat(boolean showOverlay) {
        if (ClientUtil.isSystemTextEntryActive(client))
            return;

        ClientUtil.setChatHidden(client, true);
        if (showOverlay)
            setHidden(false);
    }

    public void clear() {
        messageContainer = null;
        messageContainers.forEach((chatMode, container) -> {
            container.clear();
        });
        privateContainers.forEach((targetName, container) -> {
            container.clear();
        });

        inputBuf.setLength(0);
        caret = 0;
        inputScrollPx = 0;
        inputFocused = false;
        caretOn = true;
        lastBlinkMs = 0;
        lastViewport = null;

        activeTab = null;
        tabsByKey.clear();
        tabOrder.clear();
        availableChatModes.clear();

        refreshTabs(); // reinitialize tabs
    }

    public void clearInputText() {
        setInputText("");
    }

    public void setInputText(String value) {
        if (value == null) {
            log.warn("Attempted to set input text to null");
            return;
        }

        if (inputBuf.toString().equals(value))
            return; // no change

        int charLimit = getCharacterLimit();
        if (value.length() > charLimit) {
            log.debug("Input text exceeds character limit of {}: {}", charLimit, value);
            value = value.substring(0, charLimit);
        }

        final String text = value.trim();
        inputBuf.setLength(0);
        inputBuf.append(text);
        caret = inputBuf.length();
        inputScrollPx = 0;
        inputFocused = true;
        caretOn = true;
        lastBlinkMs = System.currentTimeMillis();
        eventBus.post(new VarClientStrChanged(VarClientStr.CHATBOX_TYPED_TEXT));
        clientThread.invokeLater(() -> {
            ClientUtil.setChatInputText(client, text);
        });
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

    private final class ChatMouse implements MouseListener, MouseWheelListener
    {
        @Override public MouseEvent mouseClicked(MouseEvent e) { return e; }
        @Override public MouseEvent mouseEntered(MouseEvent e) { return e; }
        @Override public MouseEvent mouseExited(MouseEvent e) { return e; }
        @Override public MouseEvent mouseMoved(MouseEvent e) { return e; }

        @Override
        public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e) {
            return e;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent e) {
            if (!isEnabled() || isHidden()) return e;
            if (lastViewport == null) return e;

            if (!lastViewport.contains(e.getPoint())) {
                if (config.isClickOutsideToClose()) {
                    setHidden(true);
                }
            }

            if (tabsBarBounds.contains(e.getPoint())) {
                for (Tab t : tabOrder) {
                    if (!t.getBounds().contains(e.getPoint())) continue;

                    // Close button (LMB only)
                    if (t.isCloseable() && t.getCloseBounds().contains(e.getPoint())) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            if (t.getUnread() > 0) t.setUnread(0);
                            removeTab(t);
                            e.consume();
                        }
                        return e;
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
                    }
                    return e; // RMB falls through for RuneLite menu
                }
            }

            // Input focus: LMB only
            if (inputBounds.contains(e.getPoint())) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    inputFocused = true;
                    e.consume();
                }
                return e;
            } else {
                if (e.getButton() == MouseEvent.BUTTON1)
                    inputFocused = false;
            }
            return e;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent e)  {
            if (!isEnabled() || isHidden()) return e;
            if (dragTab == null) return e;

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
            }
            return e;
        }

        @Override
        public MouseEvent mouseReleased(MouseEvent e) {
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
                return e;
            }
            return e;
        }
    }

    private final class InputKeys implements KeyListener
    {
        @Override
        public void keyTyped(KeyEvent e) {
            if (!isEnabled() || isHidden() || !inputFocused)
                return;

            if (inputBuf.length() >= getCharacterLimit())
                return;

            char ch = e.getKeyChar();
            // Accept printable chars (ignore control chars and ENTER here)
            if (ch >= 32 && ch != 127) {
                inputBuf.insert(caret, ch);
                caret++;
                e.consume();
                // keep caret visible
                caretOn = true;
                lastBlinkMs = System.currentTimeMillis();

                clientThread.invoke(() -> {
                    ClientUtil.setChatInputText(client, getInputText());
                    eventBus.post(new VarClientStrChanged(VarClientStr.CHATBOX_TYPED_TEXT));
                });
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (!isEnabled() || isHidden())
                return;

            int code = e.getKeyCode();
            switch (code) {
                case KeyEvent.VK_LEFT:
                    if (!inputFocused) return;
                    if (caret > 0) caret--;
                    e.consume();
                    break;
                case KeyEvent.VK_RIGHT:
                    if (!inputFocused) return;
                    if (caret < inputBuf.length()) caret++;
                    e.consume();
                    break;
                case KeyEvent.VK_UP:
                    if (!inputFocused) return;

                    if (e.isShiftDown()) {
                        eventBus.post(new NavigateHistoryEvent(NavigateHistoryEvent.PREV));
                        e.consume();
                    }
                    break;
                case KeyEvent.VK_DOWN:
                    if (!inputFocused) return;

                    if (e.isShiftDown()) {
                        eventBus.post(new NavigateHistoryEvent(NavigateHistoryEvent.NEXT));
                        e.consume();
                    }
                    break;
                case KeyEvent.VK_HOME:
                    if (!inputFocused) return;
                    caret = 0;
                    e.consume();
                    break;
                case KeyEvent.VK_END:
                    if (!inputFocused) return;
                    caret = inputBuf.length();
                    e.consume();
                    break;
                case KeyEvent.VK_BACK_SPACE:
                    if (!inputFocused) return;
                    if (caret > 0 && inputBuf.length() > 0) {
                        inputBuf.deleteCharAt(caret - 1);
                        caret--;
                    }
                    e.consume();
                    break;
                case KeyEvent.VK_DELETE:
                    if (!inputFocused) return;
                    if (caret < inputBuf.length()) {
                        inputBuf.deleteCharAt(caret);
                    }
                    e.consume();
                    break;
                case KeyEvent.VK_ENTER:
                    if (!inputFocused) {
                        focusInput();
                        return;
                    }
                    commitInput();
                    /*if (config.isHideOnSend())
                        setHidden(true);*/
                    //e.consume();
                    break;
                case KeyEvent.VK_ESCAPE:
                    if (config.isHideOnEscape())
                        setHidden(true);
                    e.consume();
                    break;
                case KeyEvent.VK_TAB:
                    //if (!inputFocused) return;

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
                case KeyEvent.VK_V:
                    if (inputFocused && e.isControlDown()) {
                        // Paste from clipboard
                        Optional<String> clipboardText = ChatUtil.getClipboardText();
                        if (clipboardText.isPresent()) {
                            setInputText(clipboardText.get());
                            e.consume();
                        }
                    }
                    break;
                default:
                    // allow other keys to pass if not handled
                    if (inputFocused) {
                        clientThread.invokeLater(() -> {
                            ClientUtil.setChatInputText(client, getInputText());

                            // Seems this event isn't posted when the chat is hidden
                            eventBus.post(new VarClientStrChanged(VarClientStr.CHATBOX_TYPED_TEXT));
                        });
                    }
                    break;
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