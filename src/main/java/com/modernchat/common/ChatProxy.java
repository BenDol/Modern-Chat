package com.modernchat.common;

import com.modernchat.overlay.ChatOverlay;
import com.modernchat.service.MessageService;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.ClientUtil;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ChatProxy
{
    @Inject private WidgetBucket widgetBucket;
    @Inject private ChatOverlay modernChat;
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private MessageService messageService;

    private final AtomicBoolean systemWidgetActive = new AtomicBoolean(false);

    @Getter
    @Setter
    private boolean autoHide = false;

    public boolean isHidden() {
        if (modernChat.isEnabled())
            return modernChat.isHidden();

        if (client.isClientThread()) {
            Widget legacyChat = widgetBucket.getChatWidget();
            return legacyChat != null && legacyChat.isHidden();
        } else {
            return ChatUtil.LEGACY_CHAT_HIDDEN.get();
        }
    }

    public boolean isLegacyHidden() {
        Widget legacyChat = widgetBucket.getChatWidget();
        return legacyChat != null && legacyChat.isHidden();
    }

    public boolean isModernHidden() {
        return modernChat.isHidden();
    }

    public boolean isCommandMode() {
        if (modernChat.isEnabled())
            return modernChat.isCommandMode();

        return false;
    }

    public @Nullable Rectangle getBounds() {
        if (modernChat.isEnabled())
            return modernChat.getViewPort();

        Widget legacyChatViewport = widgetBucket.getChatboxViewportWidget();
        if (legacyChatViewport != null) {
            return legacyChatViewport.getBounds();
        }

        return null;
    }

    public void clearInput(Runnable callback) {
        clearInput(callback, true);
    }

    public void clearInput(Runnable callback, boolean sync) {
        if (modernChat.isEnabled()) {
            modernChat.clearInputText(sync);
            callback.run();
        } else {
            ClientUtil.clearChatInput(client, clientThread, callback);
        }
    }

    public boolean startPrivateMessage(String currentTarget, String body, Runnable callback) {
        if (modernChat.isEnabled()) {
            if (!modernChat.isPrivateTabOpen(currentTarget)) {
                modernChat.setHidden(false);
                modernChat.selectPrivateTab(currentTarget);
            }
            return false;
        } else {
            ClientUtil.startPrivateMessage(client, clientThread, currentTarget, body, callback);
            return true;
        }
    }

    public void setInputText(String value) {
        if (modernChat.isEnabled()) {
            if (modernChat.setInputText(value, true))
                modernChat.focusInput();
        } else {
            ClientUtil.setChatInputText(client, value);
        }
    }

    public String getInputText() {
        return modernChat.isEnabled() ? modernChat.getInputText() : ClientUtil.getChatInputText(client);
    }

    public void setHidden(boolean hidden) {
        if (modernChat.isEnabled()) {
            modernChat.setHidden(hidden);
        } else {
            Widget legacyChat = widgetBucket.getChatWidget();
            if (legacyChat != null) {
                ChatUtil.setChatHidden(legacyChat, hidden);
                client.refreshChat();
            }
        }
    }

    public boolean isTabOpen(ChatMessage msg) {
        if (!modernChat.isEnabled())
            return true;

        return modernChat.isTabSelected(msg);
    }

    public void ensureLegacyChatVisible() {
        if (modernChat.isEnabled()) {
            modernChat.showLegacyChat(true);
        } else {
            Widget legacyChat = widgetBucket.getChatWidget();
            if (legacyChat != null) {
                legacyChat.setHidden(false);
            }
        }
    }

    public void ensureLegacyChatHidden() {
        if (modernChat.isEnabled()) {
            modernChat.hideLegacyChat(true);
        } else {
            Widget legacyChat = widgetBucket.getChatWidget();
            if (legacyChat != null) {
                legacyChat.setHidden(true);
            }
        }
    }

    public boolean isLegacy() {
        return modernChat == null || !modernChat.isEnabled() || modernChat.isLegacyShowing();
    }

    public boolean submitInput(KeyEvent keyEvent) {
        if (modernChat.isEnabled()) {
            return modernChat.submitInput(keyEvent);
        } else {
            //messageService.sendMessage(client, keyEvent);
        }
        return false;
    }

    /**
     * Thread-safe snapshot of {@link ClientUtil#isSystemWidgetActive(Client)}.
     * Updated every client tick via {@code refreshSystemWidgetActive()}.
     */
    public boolean isSystemWidgetActive() {
        return systemWidgetActive.get();
    }

    /**
     * Must be called on the client thread (e.g. from onClientTick).
     */
    public void refreshSystemWidgetActive() {
        systemWidgetActive.set(ClientUtil.isSystemWidgetActive(client));
    }
}
