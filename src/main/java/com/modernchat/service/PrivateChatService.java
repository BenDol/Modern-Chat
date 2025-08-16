package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatProxy;
import com.modernchat.common.MessageService;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Singleton
public class PrivateChatService implements ChatService, KeyListener {

    private static final int PM_COOLDOWN_MS = 1000;
    private static final int PM_HOT_MESSAGE_MAX = 2;
    private static final int PM_LOCK_MS = 1500;
    private static final int PM_LOCK_COUNT_RESET_MS = 60000;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private KeyManager keyManager;
    @Inject private MessageService messageService;
    @Inject private ChatProxy chatProxy;

    private volatile String lastPmFrom = null;
    private volatile long lastPmTimestamp = 0L;
    private volatile long lastPmLock = 0L;
    private volatile long pmLockedUntil = 0L;
    private volatile boolean canShowLockMessage = true;
    @Getter private volatile String lastChatInput;

    private final AtomicInteger pmHotMessageCount = new AtomicInteger(0);
    private final AtomicInteger pmLockCount = new AtomicInteger(0);

    // Queue to execute scripts after the frame (avoids reentrancy)
    @Getter
    private String pmTarget = null;
    private String pendingPmTarget = null;
    private String pendingPrefill = null;

    @Override
    public void startUp() {
        eventBus.register(this);
        keyManager.registerKeyListener(this);

        reset();
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);
        keyManager.unregisterKeyListener(this);

        reset();
    }

    protected void reset() {
        lastPmFrom = null;
        lastChatInput = null;
        pmTarget = null;
        pendingPmTarget = null;
        pendingPrefill = null;
        lastPmTimestamp = 0L;
        canShowLockMessage = true;
    }

    protected void resetLocks() {
        lastPmLock = 0L;
        pmLockedUntil = 0L;
        pmLockCount.set(0);
        pmHotMessageCount.set(0);
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
            clientThread.invoke(() -> {
                if (ClientUtil.isSystemTextEntryActive(client)) {
                    String lastInputText = ClientUtil.getSystemInputText(client);
                    if (StringUtil.isNullOrEmpty(lastInputText)) {
                        cancelPrivateMessage();
                    }
                }
            });
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage e) {
        final ChatMessageType t = e.getType();
        if (t == ChatMessageType.PRIVATECHAT || t == ChatMessageType.MODPRIVATECHAT) {
            lastPmFrom = Text.toJagexName(Text.removeTags(e.getName()));
            log.debug("lastPmFrom = {}", lastPmFrom);
        } else if (t == ChatMessageType.PRIVATECHATOUT) {
            if (isCooldownActive()) {
                pmHotMessageCount.incrementAndGet();
                if (pmHotMessageCount.get() < PM_HOT_MESSAGE_MAX) {
                    return;
                }

                if (System.currentTimeMillis() - lastPmLock >= PM_LOCK_COUNT_RESET_MS) {
                    pmLockCount.set(0);
                }
                lastPmLock = System.currentTimeMillis();
                long lockCount = pmLockCount.incrementAndGet();
                long lockDelay = Math.min(lockCount * PM_LOCK_MS, PM_LOCK_COUNT_RESET_MS);
                pmLockedUntil = lastPmLock + lockDelay;

                String lastPmTarget = getPmTarget();
                cancelPrivateMessage();

                ChatMessageBuilder messageBuilder = new ChatMessageBuilder()
                    .append("You are sending PMs too quickly. Please wait ")
                    .append(Color.RED, String.valueOf((lockDelay / 1000.0)))
                    .append(Color.RED, " seconds");

                if (lockDelay < 10000) {
                    messageBuilder.append(" (reopening chat momentarily)");
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            if (StringUtil.isNullOrEmpty(getPmTarget()))
                                setPmTarget(lastPmTarget);
                        }
                    }, lockDelay);
                }

                messageService.pushChatMessage(messageBuilder);
            } else {
                pmHotMessageCount.set(0); // Reset hot message count
            }

            lastPmTimestamp = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged e) {
        if (e.getIndex() != VarClientStr.CHATBOX_TYPED_TEXT)
            return;

        if (chatProxy.isHidden())
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

         if (isLocked()) {
            if (canShowLockMessage) {
                long remainingLock = pmLockedUntil - System.currentTimeMillis();
                canShowLockMessage = false;
                messageService.pushChatMessage(new ChatMessageBuilder()
                    .append("You are sending PMs too quickly. Please wait ")
                    .append(Color.RED, String.valueOf((remainingLock / 1000.0)))
                    .append(Color.RED, " seconds."));
            }
            return;
        }

        canShowLockMessage = true;

        final String currentTarget = Text.toJagexName(target);
        final String body = pendingPrefill;
        pendingPmTarget = null;
        pendingPrefill = null;

        if (!chatProxy.startPrivateMessage(currentTarget, body, () -> {}))
            setPmTarget(null);
    }

    public void setPmTarget(String pmTarget) {
        this.pmTarget = pmTarget;
        canShowLockMessage = true; // Reset lock message state
    }

    public void cancelPrivateMessage() {
        String lastPmTarget = getPmTarget();
        if (lastPmTarget == null || lastPmTarget.isEmpty()) {
            return;
        }

        setPmTarget(null);

        ClientUtil.cancelPrivateMessage(client, clientThread, ()-> {});
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
        chatProxy.clearInput(()-> {
            lastChatInput = null; // Clear last chat input
        });
    }

    public boolean isCooldownActive() {
        return System.currentTimeMillis() - lastPmTimestamp < PM_COOLDOWN_MS;
    }

    public boolean isLocked() {
        return System.currentTimeMillis() < pmLockedUntil;
    }
}
