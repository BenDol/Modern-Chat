package com.modernchat.common;

import com.modernchat.overlay.ChatOverlay;
import com.modernchat.util.ClientUtil;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Rectangle;

@Singleton
public class ChatProxy
{
    @Inject private WidgetBucket widgetBucket;
    @Inject private ChatOverlay modernChat;
    @Inject private Client client;
    @Inject private ClientThread clientThread;

    public boolean isHidden() {
        if (modernChat.isEnabled())
            return modernChat.isHidden();

        Widget legacyChat = widgetBucket.getChatWidget();
        return legacyChat != null && legacyChat.isHidden();
    }

    public @Nullable Rectangle getBounds() {
        if (modernChat.isEnabled())
            return modernChat.getLastViewport();

        Widget legacyChatViewport = widgetBucket.getChatboxViewportWidget();
        if (legacyChatViewport != null) {
            return legacyChatViewport.getBounds();
        }

        return null;
    }

    public void clearInput(Runnable callback) {
        if (modernChat.isEnabled()) {
            modernChat.clearInputText(true);
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
                legacyChat.setHidden(hidden);
            }
        }
    }

    public boolean isTabOpen(ChatMessage msg) {
        if (!modernChat.isEnabled())
            return true;

        return modernChat.isTabSelected(msg);
    }
}
