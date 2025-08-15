package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.common.ClanType;
import com.modernchat.draw.Padding;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;

@Slf4j
public class ChatOverlay extends OverlayPanel
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private MouseManager mouseManager;
    @Inject private KeyManager keyManager;
    @Inject @Getter private MessageContainer messageContainer;

    private ChatOverlayConfig config;
    private final ChatMouse mouse = new ChatMouse();
    private final InputKeys keys = new InputKeys();

    private Rectangle lastViewport = null;
    @Getter @Setter private ChatMode currentMode = ChatMode.PUBLIC;
    @Getter @Setter private ClanType currentClanType = ClanType.NORMAL;
    @Getter @Setter private String currentTarget = null; // for private messages

    // Input box state
    private final Rectangle inputBounds = new Rectangle();
    private boolean inputFocused = false;
    private final StringBuilder inputBuf = new StringBuilder();
    private int caret = 0;
    private int inputScrollPx = 0;
    private long lastBlinkMs = 0;
    private boolean caretOn = true;

    @Getter @Setter private boolean hidden = false;

    public ChatOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public void startUp(ChatOverlayConfig config) {
        startUp(config, config.getMessageContainerConfig());
    }

    public void startUp(ChatOverlayConfig config, MessageContainerConfig containerConfig) {
        this.config = config;

        registerMouseListener();

        messageContainer.setChromeEnabled(true);
        messageContainer.setCanDrawDecider((mc) -> !ClientUtil.isChatHidden(client));
        messageContainer.startUp(containerConfig);
        messageContainer.pushLines(Arrays.asList("Welcome to ModernChat!", "This is a redesigned chatbox with custom features.", "Use the input below; Left/Right move the caret, Enter sends.", "Esc unfocuses the input. Backspace/Delete/Home/End supported.", "Scroll with the mouse wheel, or drag the scrollbar on the right.", "You can customize the appearance in the settings.", "Enjoy your chat experience!", "This is a sample message to fill the chat buffer and demonstrate scrolling.", "Feel free to type here and see how the chat behaves.", "You can also resize the window to see how it adapts.", "Remember, this is just a demo; you can modify the code to suit your needs.", "Have fun exploring the features of ModernChat!", "This is another message to ensure the buffer has enough content for scrolling.", "Keep typing to see how the chatbox handles new messages.", "Here's a longer message to test the wrapping and scrolling behavior of the chatbox.", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.", "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.", "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur.", "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum." ));
    }

    public void shutDown() {
        clear();
        unregisterMouseListener();
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!isEnabled() || hidden)
            return null;

        Widget chatRoot = client.getWidget(InterfaceID.CHATBOX, 0);
        if (chatRoot == null || chatRoot.isHidden())
            return null;

        Rectangle vp = chatRoot.getBounds();
        if (vp == null || vp.width <= 0 || vp.height <= 0)
            return null;

        lastViewport = new Rectangle(vp);

        // Panel chrome (style only)
        Composite oc = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(config.getBackdropColor());
        g.fillRoundRect(vp.x + 4, vp.y + 4, vp.width - 8, vp.height - 8, 8, 8);

        g.setColor(config.getBorderColor()); // or a border color of your choice
        g.drawRoundRect(vp.x, vp.y, vp.width, vp.height, 8, 8);

        // Layout constants
        final Padding pad = config.getPadding();
        Font font = FontManager.getRunescapeFont().deriveFont((float) config.getInputFontSize());
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        final int lineH = fm.getAscent() + fm.getDescent() + config.getInputLineSpacing();

        final int inputPadX = 8;
        final int inputPadY = 6;
        final int inputHeight = lineH + inputPadY * 2;
        final int gapAboveInput = 3;

        final int left = vp.x + pad.getLeft();
        final int top = vp.y + pad.getTop();
        final int bottom = vp.y + vp.height - pad.getBottom();
        final int innerW = Math.max(1, vp.width - pad.getWidth());

        // Message area gives up space for the input box
        final int msgBottom = bottom - inputHeight - gapAboveInput;
        final Rectangle msgArea = new Rectangle(left, top, innerW, Math.max(1, msgBottom - top));

        // Inject the msg area into the MessageContainer
        messageContainer.setBoundsProvider(() -> msgArea);
        messageContainer.setHidden(false);
        messageContainer.setAlpha(1f);

        // Let MessageContainer paint inside the message area
        Shape oldClip = g.getClip();
        g.setClip(new Rectangle(vp.x, vp.y, vp.width, msgBottom - vp.y));
        messageContainer.render(g);
        g.setClip(oldClip);

        // Draw input box
        drawInputBox(g, fm, left, msgBottom, innerW, inputHeight, inputPadX, inputPadY, gapAboveInput);

        g.setComposite(oc);
        return null;
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

    public void inputTick() {
        if (inputFocused) {
            long now = System.currentTimeMillis();
            if (now - lastBlinkMs > 500) {
                caretOn = !caretOn;
                lastBlinkMs = now;
            }
        }
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

    private String getPlayerPrefix() {
        Player lp = client.getLocalPlayer();
        String name = lp != null && lp.getName() != null ? Text.removeTags(lp.getName()) : "Player";
        return name + ": ";
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

    private void commitInput() {
        final String text = inputBuf.toString().trim();
        if (!text.isEmpty()) {
            Player player = client.getLocalPlayer();
            if (player != null)
                sendChat(text);

            messageContainer.setUserScrolled(false);
            messageContainer.setScrollOffsetPx(Integer.MAX_VALUE); // snap to bottom
        }

        inputBuf.setLength(0);
        caret = 0;
        inputScrollPx = 0;
    }

    public void sendPrivateChat(String text) {
        sendPrivateChat(text, currentTarget);
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

        client.runScript(ScriptID.PRIVMSG, targetName, text);
    }

    public void sendChat(String text) {
        sendChat(text, currentMode, currentClanType);
    }

    public void sendChat(String text, ChatMode mode) {
        sendChat(text, mode, currentClanType);
    }

    public void sendChat(String text, ChatMode mode, ClanType clanType) {
        switch (mode) {
            case PUBLIC:
                break;
            case FRIENDS_CHAT:
                break;
            case CLAN_MAIN:
                break;
            case CLAN_GUEST:
                break;
        }

        /* - String Message to send
         * - int modes
         * - int (clan type)
         * - int (boolean) use target
         * - int set target
         * */
        clientThread.invoke(() -> {
            client.runScript(ScriptID.CHAT_SEND, text, mode.getValue(), clanType.getValue(), 0, 0);
        });
    }

    public void addMessage(String line, ChatMessageType type, long timestamp) {
        messageContainer.pushLine(line, type, timestamp);
    }

    public void clear() {
        messageContainer.clear();
        inputBuf.setLength(0);
        caret = 0;
        inputScrollPx = 0;
        inputFocused = false;
        caretOn = true;
        lastBlinkMs = 0;
        // Reset viewport
        lastViewport = null;
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
            if (!isEnabled()) return e;
            if (lastViewport == null || !lastViewport.contains(e.getPoint()))
                return e;

            // Focus handling: click inside input => focus; outside => unfocus
            if (inputBounds.contains(e.getPoint())) {
                inputFocused = true;
                e.consume();
                return e;
            } else {
                inputFocused = false;
            }
            return e;
        }

        @Override public MouseEvent mouseDragged(MouseEvent e)  { return e; }
        @Override public MouseEvent mouseReleased(MouseEvent e) { return e; }
    }

    private final class InputKeys implements KeyListener
    {
        @Override
        public void keyTyped(KeyEvent e) {
            if (!isEnabled() || !inputFocused) return;
            char ch = e.getKeyChar();
            // Accept printable chars (ignore control chars and ENTER here)
            if (ch >= 32 && ch != 127) {
                inputBuf.insert(caret, ch);
                caret++;
                e.consume();
                // keep caret visible
                caretOn = true;
                lastBlinkMs = System.currentTimeMillis();
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (!isEnabled() || !inputFocused)
                return;

            int code = e.getKeyCode();
            switch (code) {
                case KeyEvent.VK_LEFT:
                    if (caret > 0) caret--;
                    e.consume();
                    break;
                case KeyEvent.VK_RIGHT:
                    if (caret < inputBuf.length()) caret++;
                    e.consume();
                    break;
                case KeyEvent.VK_HOME:
                    caret = 0;
                    e.consume();
                    break;
                case KeyEvent.VK_END:
                    caret = inputBuf.length();
                    e.consume();
                    break;
                case KeyEvent.VK_BACK_SPACE:
                    if (caret > 0 && inputBuf.length() > 0) {
                        inputBuf.deleteCharAt(caret - 1);
                        caret--;
                    }
                    e.consume();
                    break;
                case KeyEvent.VK_DELETE:
                    if (caret < inputBuf.length()) {
                        inputBuf.deleteCharAt(caret);
                    }
                    e.consume();
                    break;
                case KeyEvent.VK_ENTER:
                    commitInput();
                    //e.consume();
                    break;
                case KeyEvent.VK_ESCAPE:
                    inputFocused = false;
                    //e.consume();
                    break;
                default:
                    // allow other keys to pass if not handled
                    break;
            }
            // keep caret visible after nav/edit
            caretOn = true;
            lastBlinkMs = System.currentTimeMillis();
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }
    }
}