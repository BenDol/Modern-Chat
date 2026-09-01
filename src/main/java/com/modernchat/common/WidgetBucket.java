package com.modernchat.common;

import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        else if (e.getGroupId() == InterfaceID.CHAT_LEFT) {
            dialogLeft = null;
        }
        else if (e.getGroupId() == InterfaceID.CHAT_RIGHT) {
            dialogRight = null;
        }
        else if (e.getGroupId() == InterfaceID.CHATMENU) {
            dialogOptions = null;
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
        }
        else if (e.getGroupId() == InterfaceID.Chatbox.CHATAREA) {
            chatBoxArea = null;
        }
        else if (e.getGroupId() == ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_CHATBOX_PARENT ||
                 e.getGroupId() == ComponentID.RESIZABLE_VIEWPORT_CHATBOX_PARENT) {
            chatViewportWidget = null;
        }
        else if (e.getGroupId() == InterfaceID.CHAT_LEFT) {
            dialogLeft = null;
        }
        else if (e.getGroupId() == InterfaceID.CHAT_RIGHT) {
            dialogRight = null;
        }
        else if (e.getGroupId() == InterfaceID.CHATMENU) {
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

    // Bounds of open modal sub-interfaces (bank, GE, settings, etc.) that draw above
    // overlays on the UNDER_WIDGETS layer. Written on the client thread, read from the
    // AWT mouse thread.
    private volatile List<Rectangle> interfaceOcclusionRects = Collections.emptyList();
    private int lastOcclusionCycle = -1;

    /**
     * Refreshes the cached bounds of the modal sub-interfaces currently open in the
     * component table. Must be called from the client thread (typically during overlay
     * render); the result is cached per game cycle so repeated calls in a frame are free.
     */
    public void refreshInterfaceOcclusion() {
        int cycle = client.getGameCycle();
        if (cycle == lastOcclusionCycle)
            return;
        lastOcclusionCycle = cycle;

        List<Rectangle> rects = null;
        for (WidgetNode node : client.getComponentTable()) {
            // Non-modal sub-interfaces (side panels, the chatbox itself, etc.) either
            // never overlap the chat area or are managed by this plugin; only modal
            // interfaces such as the bank act as occluders.
            if (node.getModalMode() == WidgetModalMode.NON_MODAL)
                continue;

            int groupId = node.getId();
            if (groupId == InterfaceID.CHATBOX || groupId == InterfaceID.PM_CHAT)
                continue;

            Widget parent = client.getWidget((int) node.getHash());
            if (parent == null || parent.isHidden())
                continue;

            Rectangle bounds = parent.getBounds();
            if (bounds == null || bounds.isEmpty())
                continue;

            if (rects == null)
                rects = new ArrayList<>(4);
            rects.add(bounds);
        }
        interfaceOcclusionRects = rects == null ? Collections.emptyList() : rects;
    }

    /**
     * Whether an open modal interface (bank, GE, etc.) covers the given canvas point.
     * Safe to call from any thread; reads the snapshot built by refreshInterfaceOcclusion.
     */
    public boolean isPointCoveredByInterface(Point p) {
        if (p == null)
            return false;
        for (Rectangle r : interfaceOcclusionRects) {
            if (r.contains(p))
                return true;
        }
        return false;
    }
}
