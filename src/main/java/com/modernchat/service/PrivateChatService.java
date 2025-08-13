package com.modernchat.service;

import com.modernchat.util.ClientUtil;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;

@Slf4j
@Singleton
public class PrivateChatService implements ChatService, KeyListener {

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private KeyManager keyManager;

    // Track last inbound PM sender (sanitized RuneScape name)
    private volatile String lastPmFrom;
    @Getter private String lastChatInput;

    // Queue to execute scripts after the frame (avoids reentrancy)
    @Getter @Setter
    private String pmTarget = null;
    private String pendingPmTarget = null;
    private String pendingPrefill = null;

    @Override
    public void startUp() {
        eventBus.register(this);
        keyManager.registerKeyListener(this);

        lastPmFrom = null;
        lastChatInput = null;
        pmTarget = null;
        pendingPmTarget = null;
        pendingPrefill = null;
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);
        keyManager.unregisterKeyListener(this);

        lastPmFrom = null;
        lastChatInput = null;
        pmTarget = null;
        pendingPmTarget = null;
        pendingPrefill = null;
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            cancelPrivateMessage();
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            String lastInputText = ClientUtil.getSystemInputText(client);
            if (StringUtil.isNullOrEmpty(lastInputText)) {
                cancelPrivateMessage();
            }
        }
    }

    /**
     * Remember the last inbound PM sender.
     */
    @Subscribe
    public void onChatMessage(ChatMessage e) {
        final ChatMessageType t = e.getType();
        if (t == ChatMessageType.PRIVATECHAT || t == ChatMessageType.MODPRIVATECHAT) {
            lastPmFrom = Text.toJagexName(Text.removeTags(e.getName()));
            log.debug("lastPmFrom = {}", lastPmFrom);
        }
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged e) {
        if (e.getIndex() != VarClientStr.CHATBOX_TYPED_TEXT)
            return;

        Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (input == null || input.isHidden())
            return;

        if (ClientUtil.isSystemTextEntryActive(client)) {
            return; // Don't do anything if a system prompt is active
        }

        lastChatInput = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
    }

    @Subscribe
    public void onPostClientTick(PostClientTick e) {
        if (ClientUtil.isSystemTextEntryActive(client)) {
            // If a system prompt is active, don't open PM interface
            return;
        }

        String target = pendingPmTarget;
        if (target == null || target.isEmpty()) {
            target = pmTarget; // Use the current target if no pending
        }

        if (target == null || target.isEmpty()) {
            return;
        }

        final String currentTarget = Text.toJagexName(target);
        final String body = pendingPrefill;
        pendingPmTarget = null;
        pendingPrefill = null;

        ClientUtil.startPrivateMessage(client, clientThread, currentTarget, body, () -> {});
    }

    public void replyTo(String target) {
        if (target == null || target.isEmpty()) {
            log.warn("Reply target is empty or null");
            return;
        }

        // Queue for AFTER the frame to avoid "scripts are not reentrant"
        // If something is already queued, don't enqueue again this frame.
        if (pendingPmTarget == null) {
            pendingPmTarget = target;
            pendingPrefill = null; // currently null; kept for future use
        }
    }

    public void replyToLastPm(String body) {
        final String target = lastPmFrom;
        if (target == null || target.isEmpty()) {
            return;
        }

        // Queue for AFTER the frame to avoid "scripts are not reentrant"
        // If something is already queued, don't enqueue again this frame.
        if (pendingPmTarget == null) {
            pendingPmTarget = target;
            pendingPrefill = body; // currently null; kept for future use
        }
    }

    public void clearChatInput() {
        ClientUtil.clearChatInput(client, clientThread, ()-> {
            lastChatInput = null; // Clear last chat input
        });
    }

    public void cancelPrivateMessage() {
        String lastPmTarget = getPmTarget();
        if (lastPmTarget == null || lastPmTarget.isEmpty()) {
            return;
        }

        setPmTarget(null);

        ClientUtil.cancelPrivateMessage(client, clientThread, ()-> {});
    }
}
