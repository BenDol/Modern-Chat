package com.modernchat.common;

import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class WidgetBucket {

    @Inject private Client client;
    @Inject private EventBus eventBus;

    private Widget chatViewportWidget = null;
    private Widget chatParentWidget = null;
    private Widget chatWidget = null;
    private Widget chatBoxArea = null;
    private Widget messageLayerWidget = null;
    private Widget pmWidget = null;
    private Widget dialogLeft = null;
    private Widget dialogRight = null;
    private Widget dialogOptions = null;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    // Runs before other subscribers so nothing reads a widget from the previous
    // interface instance once the group has been reloaded (e.g. after a world hop).
    @Subscribe(priority = 1)
    public void onWidgetLoaded(WidgetLoaded e) {
        invalidateGroup(e.getGroupId());
    }

    // Runs after other subscribers so close handlers can still reach the old widget.
    @Subscribe(priority = -1)
    public void onWidgetClosed(WidgetClosed e) {
        invalidateGroup(e.getGroupId());
    }

    /**
     * Drops every cached widget that lives in the given interface group.
     * Widget events carry plain interface ids (e.g. {@link InterfaceID#CHATBOX}),
     * never packed component ids, so compare against the group each component belongs to.
     */
    private void invalidateGroup(int groupId) {
        if (groupId == InterfaceID.CHATBOX) {
            chatWidget = null;
            chatParentWidget = null;
            chatBoxArea = null;
            messageLayerWidget = null;
        }
        else if (groupId == InterfaceID.PM_CHAT) {
            pmWidget = null;
        }
        else if (groupId == WidgetUtil.componentToInterface(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT)
              || groupId == WidgetUtil.componentToInterface(ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT)) {
            chatViewportWidget = null;
        }
        else if (groupId == InterfaceID.CHAT_LEFT) {
            dialogLeft = null;
        }
        else if (groupId == InterfaceID.CHAT_RIGHT) {
            dialogRight = null;
        }
        else if (groupId == InterfaceID.CHATMENU) {
            dialogOptions = null;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGIN_SCREEN
         || e.getGameState() == GameState.LOGGING_IN
         || e.getGameState() == GameState.HOPPING) {
            chatBoxArea = null;
            chatWidget = null;
            chatParentWidget = null;
            chatViewportWidget = null;
            messageLayerWidget = null;
            pmWidget = null;
            dialogLeft = null;
            dialogRight = null;
            dialogOptions = null;
        }
    }
    public Widget getDialogLeft() {
        if (dialogLeft == null)
            dialogLeft = client.getWidget(InterfaceID.CHAT_LEFT, 0);
        return dialogLeft;
    }

    public void clearDialogLeft() {
        dialogLeft = null;
    }
    public Widget getDialogRight() {
        if (dialogRight == null)
            dialogRight = client.getWidget(InterfaceID.CHAT_RIGHT, 0);
        return dialogRight;
    }

    public void clearDialogRight() {
        dialogRight = null;
    }
    public Widget getDialogOptions() {
        if (dialogOptions == null)
            dialogOptions = client.getWidget(InterfaceID.CHATMENU, 0);
        return dialogOptions;
    }

    public void clearDialogOptions() {
        dialogOptions = null;
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
        if (chatViewportWidget == null) {
            chatViewportWidget = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT);
            if (chatViewportWidget == null) {
                chatViewportWidget = client.getWidget(ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT);
            }
        }
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
