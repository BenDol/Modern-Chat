package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.NotificationService;
import com.modernchat.event.ChatSendLockedEvent;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class MessageService implements ChatService
{
    private static final int SEND_COOLDOWN_MS = 900;
    private static final int SEND_HOT_MESSAGE_MAX = 5;
    private static final int SEND_LOCK_MS = 1250;
    private static final int SEND_LOCK_COUNT_RESET_MS = 60000;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private FilterService filterService;
    @Inject private GameChatBridge gameChatBridge;
    @Inject private NotificationService notificationService;

    @Getter private volatile long lastSendTimestamp = 0L;
    @Getter private volatile long lastSendLock = 0L;
    @Getter private volatile long sendLockedUntil = 0L;

    private final AtomicInteger sendHotMessageCount = new AtomicInteger(0);
    private final AtomicInteger sendLockCount = new AtomicInteger(0);

    @Override
    public void startUp() {

    }

    @Override
    public void shutDown() {
        lastSendTimestamp = 0L;
    }

    protected void resetLocks() {
        lastSendLock = 0L;
        sendLockedUntil = 0L;
        sendLockCount.set(0);
        sendHotMessageCount.set(0);
    }

    /**
     * Outcome of {@link #submit}: what the caller does with the Enter key event and the input.
     */
    public enum SubmitResult
    {
        /** Handed to the game: let the Enter key event reach the game, clear the input. */
        PASS_TO_GAME,
        /** Dealt with here (sent as a command, filtered or rate limited): consume, clear. */
        HANDLED,
        /** Not sent and worth retrying: consume and keep the text in the input. */
        RETAINED
    }

    /**
     * Submits what the player typed when they press Enter. Modern Chat never sends a chat
     * message itself: RuneLite only lets the game's own chat scripts send, as a direct
     * result of the player's key press, so a message is handed to {@link GameChatBridge}
     * and goes out when the same Enter press reaches the game.
     * MUST be called from the key listener handling that Enter press.
     *
     * @param onFailed receives the text back on the client thread if the game never
     *                 processes the Enter press, so it can be put back in the input
     */
    public SubmitResult submit(String text, ChatMode mode, @Nullable String targetName, Consumer<String> onFailed) {
        if (StringUtil.isNullOrEmpty(text))
            return SubmitResult.HANDLED;

        if (filterService.isFiltered(text))
            return SubmitResult.HANDLED;

        final String trimmed = text.trim();
        final boolean isCommand = trimmed.startsWith("::");
        final boolean isPrivate = !isCommand && mode == ChatMode.PRIVATE;
        if (isPrivate) {
            if (StringUtil.isNullOrEmpty(targetName)) {
                log.warn("Attempted to send private chat message to no target");
                return SubmitResult.RETAINED;
            }
            if (!gameChatBridge.isPrivatePromptReady(targetName)) {
                log.debug("Private message prompt is not open, keeping the message");
                notificationService.pushChatMessage(new ChatMessageBuilder()
                    .append("Your private message could not be sent right now. It is still in the chat input."));
                return SubmitResult.RETAINED;
            }
        }

        if (processSendLock()) {
            log.debug("Message send is locked, cannot send message: {}", text);
            notifyLocked(targetName, isPrivate);
            return SubmitResult.HANDLED;
        }

        if (isCommand) {
            clientThread.invoke(() -> {
                client.setVarcStrValue(VarClientID.CHATINPUT, text);
                String typedText = trimmed.substring(2);
                String[] split = typedText.split(" ");
                if (split.length == 0)
                    return;
                String command = split[0];
                String[] args = Arrays.copyOfRange(split, 1, split.length);
                eventBus.post(new CommandExecuted(command, args));
            });
            return SubmitResult.HANDLED;
        }

        if (isPrivate) {
            gameChatBridge.handOffPrivateMessage(text, targetName, onFailed);
        } else {
            gameChatBridge.handOffChannelMessage(text, mode, onFailed);
        }

        lastSendTimestamp = System.currentTimeMillis();
        return SubmitResult.PASS_TO_GAME;
    }

    private void notifyLocked(String targetName, boolean isPrivate) {
        eventBus.post(new ChatSendLockedEvent(targetName, getSendLockedUntil(), isPrivate));
    }

    private boolean processSendLock() {
        if (isSendLocked())
            return true;

        if (!isSendCooldownActive())
            return false;

        sendHotMessageCount.incrementAndGet();
        if (sendHotMessageCount.get() < SEND_HOT_MESSAGE_MAX)
            return false;

        long now = System.currentTimeMillis();
        if (now - lastSendLock >= SEND_LOCK_COUNT_RESET_MS)
            sendLockCount.set(0);

        lastSendLock = now;
        long lockCount = sendLockCount.incrementAndGet();
        long lockDelay = Math.min(lockCount * SEND_LOCK_MS, SEND_LOCK_COUNT_RESET_MS);
        sendLockedUntil = lastSendLock + lockDelay;
        return true;
    }

    public boolean isSendCooldownActive() {
        return System.currentTimeMillis() - lastSendTimestamp < SEND_COOLDOWN_MS;
    }

    public boolean isSendLocked() {
        return System.currentTimeMillis() < sendLockedUntil;
    }
}
