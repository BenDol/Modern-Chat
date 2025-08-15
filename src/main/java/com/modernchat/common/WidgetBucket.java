package com.modernchat.common;

import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class WidgetBucket {

    @Inject private Client client;

    private Widget chatViewportWidget = null;
    private Widget chatParentWidget = null;
    private Widget chatWidget = null;
    private Widget chatBoxArea = null;
    private Widget messageLayerWidget = null;
    private Widget pmWidget = null;

    @Subscribe(priority = -1)
    public void onWidgetLoaded(WidgetLoaded e) {
        if (e.getGroupId() == InterfaceID.CHATBOX) {
            chatWidget = null;
        }
        else if (e.getGroupId() == ComponentID.CHATBOX_PARENT) {
            chatParentWidget = null;
        }
        else if (e.getGroupId() == InterfaceID.PM_CHAT) {
            pmWidget = null;
        }
        else if (e.getGroupId() == InterfaceID.Chatbox.MES_TEXT2) {
            messageLayerWidget = null;
        }
        else if (e.getGroupId() == InterfaceID.Chatbox.CHATAREA) {
            chatBoxArea = null;
        }
        else if (e.getGroupId() == ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT ||
                 e.getGroupId() == ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT) {
            chatViewportWidget = null;
        }
    }

    @Subscribe(priority = -1)
    public void onWidgetClosed(WidgetClosed e) {
        if (e.getGroupId() == InterfaceID.CHATBOX) {
            chatWidget = null;
        }
        else if (e.getGroupId() == ComponentID.CHATBOX_PARENT) {
            chatParentWidget = null;
        }
        else if (e.getGroupId() == InterfaceID.PM_CHAT) {
            pmWidget = null;
        }
        else if (e.getGroupId() == InterfaceID.Chatbox.MES_TEXT2) {
            messageLayerWidget = null;
        } else if (e.getGroupId() == InterfaceID.Chatbox.CHATAREA) {
            chatBoxArea = null;
        } else if (e.getGroupId() == ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT ||
                   e.getGroupId() == ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT) {
            chatViewportWidget = null;
        }
    }

    public Widget getPmWidget() {
        if (pmWidget == null)
            pmWidget = client.getWidget(InterfaceID.PM_CHAT, 0);
        return pmWidget;
    }

    public void clearPmWidget() {
        pmWidget = null;
    }

    public Widget getChatParentWidget() {
        if (chatParentWidget == null)
            chatParentWidget = client.getWidget(ComponentID.CHATBOX_PARENT);
        return chatParentWidget;
    }

    public void clearChatParentWidget() {
        chatParentWidget = null;
    }

    public Widget getChatboxViewportWidget() {
        Widget viewportWidget = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT);
        if (viewportWidget == null) {
            viewportWidget = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT);
        }

        if (viewportWidget == null) {
            return null;
        }

        chatViewportWidget = viewportWidget;
        return chatViewportWidget;
    }

    public void clearChatboxViewportWidget() {
        chatViewportWidget = null;
    }

    public Widget getChatWidget() {
        if (chatWidget == null)
            chatWidget = ClientUtil.getChatWidget(client);
        return chatWidget;
    }

    public void clearChatWidget() {
        chatWidget = null;
    }

    public Widget getChatBoxArea() {
        if (chatBoxArea == null)
            chatBoxArea = client.getWidget(InterfaceID.Chatbox.CHATAREA);
        return chatBoxArea;
    }

    public void clearChatBoxArea() {
        chatBoxArea = null;
    }

    public Widget getMessageLayerWidget() {
        if (messageLayerWidget == null)
            messageLayerWidget = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
        return messageLayerWidget;
    }

    public void clearMessageLayerWidget() {
        messageLayerWidget = null;
    }

    public Widget getSplitPmParentIfVisible() {
        Widget pm = getPmWidget();
        if (pm == null || pm.isHidden())
            return null;

        Widget pmParent = pm.getParent();
        if (pmParent == null || pmParent.isHidden())
            return null;
        return pmParent;
    }

    public boolean isPmWidget(Widget widget) {
        return widget != null && widget == getPmWidget();
    }
}
