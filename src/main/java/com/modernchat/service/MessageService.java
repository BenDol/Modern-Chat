package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.NotificationService;
import com.modernchat.event.ChatSendLockedEvent;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Slf4j
@Singleton
public class MessageService implements ChatService
{
    private static final int SEND_COOLDOWN_MS = 900;
    private static final int SEND_HOT_MESSAGE_MAX = 5;
    private static final int SEND_LOCK_MS = 1250;
    private static final int SEND_LOCK_COUNT_RESET_MS = 60000;

    @Inject private EventBus eventBus;
    @Inject private FilterService filterService;
    @Inject private GameChatBridge gameChatBridge;
    @Inject private NotificationService notificationService;
    // Provider: the commands feature depends on services that depend on this one
    @Inject private Provider<CommandsChatFeature> commandsChatFeature;

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
        /** Dealt with here (empty or filtered): consume, clear. */
        HANDLED,
        /** Not sent and worth retrying (send locked, PM prompt not ready): consume, keep the text. */
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
     *                 processes the Enter press, so it can be put back in the input; it
     *                 answers whether it did
     */
    public SubmitResult submit(String text, ChatMode mode, @Nullable String targetName, Predicate<String> onFailed) {
        if (StringUtil.isNullOrEmpty(text))
            return SubmitResult.HANDLED;

        if (filterService.isFiltered(text))
            return SubmitResult.HANDLED;

        final String trimmed = text.trim();
        final boolean isCommand = trimmed.startsWith("::");
        final boolean isPrivate = !isCommand && mode == ChatMode.PRIVATE;
        // Sent as is, /gim and /group would go out as public chat
        if (!isCommand && !isPrivate && commandsChatFeature.get().holdBackGroupChatAlias(trimmed))
            return SubmitResult.RETAINED;
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

        if (isSendLocked()) {
            log.debug("Message send is locked, keeping the message");
            notifyLocked(targetName, isPrivate);
            return SubmitResult.RETAINED;
        }

        if (isCommand) {
            // The game's chat script runs "::" commands exactly as the vanilla chatbox does:
            // its own (::toggleroof, ::wiki), RuneLite's (CommandExecuted) and the server's
            gameChatBridge.handOffCommand(text, onFailed, this::recordSend);
        } else if (isPrivate) {
            gameChatBridge.handOffPrivateMessage(text, targetName, onFailed, this::recordSend);
        } else {
            gameChatBridge.handOffChannelMessage(text, mode, onFailed, this::recordSend);
        }
        return SubmitResult.PASS_TO_GAME;
    }

    private void notifyLocked(String targetName, boolean isPrivate) {
        eventBus.post(new ChatSendLockedEvent(targetName, getSendLockedUntil(), isPrivate));
    }

    /**
     * Counts a message the game has sent toward the send lock. Only confirmed sends count,
     * so a message the game did not take can be retried without tripping the lock.
     * Called on the client thread.
     */
    private void recordSend() {
        long now = System.currentTimeMillis();
        if (isSendCooldownActive() && sendHotMessageCount.incrementAndGet() >= SEND_HOT_MESSAGE_MAX) {
            if (now - lastSendLock >= SEND_LOCK_COUNT_RESET_MS)
                sendLockCount.set(0);
            lastSendLock = now;
            sendLockedUntil = now + Math.min(sendLockCount.incrementAndGet() * SEND_LOCK_MS, SEND_LOCK_COUNT_RESET_MS);
        }
        lastSendTimestamp = now;
    }

    public boolean isSendCooldownActive() {
        return System.currentTimeMillis() - lastSendTimestamp < SEND_COOLDOWN_MS;
    }

    public boolean isSendLocked() {
        return System.currentTimeMillis() < sendLockedUntil;
    }
}
