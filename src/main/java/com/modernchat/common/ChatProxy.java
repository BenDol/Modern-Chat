package com.modernchat.common;

import com.modernchat.overlay.ChatOverlay;
import com.modernchat.util.ClientUtil;
import net.runelite.api.Client;
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
            modernChat.clearInputText();
            callback.run();
        } else {
            ClientUtil.clearChatInput(client, clientThread, callback);
        }
    }

    public void startPrivateMessage(String currentTarget, String body, Runnable callback) {
        if (modernChat.isEnabled()) {
            modernChat.selectPrivateTab(currentTarget);
        } else {
            ClientUtil.startPrivateMessage(client, clientThread, currentTarget, body, callback);
        }
    }

    public void setInputText(String value) {
        if (modernChat.isEnabled()) {
            modernChat.setInputText(value);
        } else {
            ClientUtil.setChatInputText(client, value);
        }
    }

    public String getInputText() {
        return modernChat.isEnabled() ? modernChat.getInputText() : ClientUtil.getChatInputText(client);
    }
}
