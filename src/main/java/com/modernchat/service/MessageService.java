package com.modernchat.service;

import com.modernchat.common.ChatMode;
import com.modernchat.common.ClanType;
import com.modernchat.event.ChatMessageSentEvent;
import com.modernchat.event.ChatPrivateMessageSentEvent;
import com.modernchat.event.ChatSendLockedEvent;
import com.modernchat.event.SubmitHistoryEvent;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.CommandExecuted;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarClientID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

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

    public void sendMessage(String text, ChatMode mode, @Nullable String targetName) {
        if (StringUtil.isNullOrEmpty(text))
            return;

        if (filterService.isFiltered(text))
            return;

        if (processSendLock()) {
            log.debug("Message send is locked, cannot send message: {}", text);
            notifyLocked(targetName, false);
            return;
        }

        // Handle :: commands
        if (text.trim().startsWith("::")) {
            clientThread.invoke(() -> {
                client.setVarcStrValue(VarClientID.CHATINPUT, text);
                String typedText = text.substring(2);
                String[] split = typedText.split(" ");
                if (split.length == 0)
                    return;
                String command = split[0];
                String[] args = Arrays.copyOfRange(split, 1, split.length);
                eventBus.post(new CommandExecuted(command, args));
            });
            return;
        }

        ClanType clanType = ClanType.NORMAL; // default clan type
        ChatMode selectedMode = mode;

        switch (mode) {
            case PUBLIC:
                break;
            case FRIENDS_CHAT:
                break;
            case CLAN_MAIN:
                break;
            case CLAN_GUEST:
                break;

            // Custom
            case CLAN_GIM:
                clanType = ClanType.IRONMAN;
                selectedMode = ChatMode.CLAN_MAIN;
                break;
            case PRIVATE:
                if (StringUtil.isNullOrEmpty(targetName)) {
                    log.warn("Attempted to send private chat message to no target");
                    return;
                }
                sendPrivateChat(text, targetName);
                return; // skip the rest of the logic
        }

        final int modeValue = selectedMode.getValue();
        final int clanTypeValue = clanType.getValue();

        // Post chatboxInput ScriptCallbackEvent for other plugins to intercept
        // ChatInputManager.handleInput() reads: objectStack[n-1]=text, intStack[n-2]=chatType, intStack[n-1]=clanTarget
        final boolean[] consumed = {false};

        clientThread.invoke(() -> {
            Object[] objectStack = client.getObjectStack();
            int[] intStack = client.getIntStack();
            int origObjectStackSize = client.getObjectStackSize();
            int origIntStackSize = client.getIntStackSize();

            objectStack[origObjectStackSize] = text;
            intStack[origIntStackSize] = modeValue;
            intStack[origIntStackSize + 1] = clanTypeValue;
            client.setObjectStackSize(origObjectStackSize + 1);
            client.setIntStackSize(origIntStackSize + 2);

            log.debug("Posting ScriptCallbackEvent for chatboxInput: {}", text);
            ScriptCallbackEvent chatboxEvent = new ScriptCallbackEvent();
            chatboxEvent.setEventName("chatboxInput");
            eventBus.post(chatboxEvent);

            // Check if consumed (ChatInputManager sets objectStack[n-1] = "" if consumed)
            consumed[0] = "".equals(objectStack[origObjectStackSize]);

            // Restore stack sizes
            client.setObjectStackSize(origObjectStackSize);
            client.setIntStackSize(origIntStackSize);

            if (!consumed[0]) {
                client.setVarcStrValue(VarClientID.CHATINPUT, text);
                client.runScript(ScriptID.CHAT_SEND, text, modeValue, clanTypeValue, 0, 0);
                eventBus.post(new ChatMessageSentEvent(text, modeValue, clanTypeValue));
            }
        });

        if (consumed[0]) {
            log.debug("Message consumed by another plugin: {}", text);
            return;
        }

        lastSendTimestamp = System.currentTimeMillis();
    }

    public void sendPrivateChat(String text, String targetName) {
        if (StringUtil.isNullOrEmpty(text)) {
            log.warn("Attempted to send empty private chat message");
            return;
        }

        if (StringUtil.isNullOrEmpty(targetName)) {
            log.warn("Attempted to send private chat without a target name");
            return;
        }

        if (filterService.isFiltered(text))
            return;

        if (processSendLock()) {
            log.debug("Private message send is locked, cannot send message: {}", text);
            notifyLocked(targetName, true);
            return;
        }

        // Post privateMessage ScriptCallbackEvent for other plugins to intercept
        // ChatInputManager.handlePrivateMessage() reads: objectStack[n-2]=target, objectStack[n-1]=message
        final boolean[] consumed = {false};

        clientThread.invoke(() -> {
            Object[] objectStack = client.getObjectStack();
            int[] intStack = client.getIntStack();
            int origObjectStackSize = client.getObjectStackSize();
            int origIntStackSize = client.getIntStackSize();

            objectStack[origObjectStackSize] = targetName;
            objectStack[origObjectStackSize + 1] = text;
            intStack[origIntStackSize] = 0; // ChatInputManager checks intStack[n-1] for consumed flag
            client.setObjectStackSize(origObjectStackSize + 2);
            client.setIntStackSize(origIntStackSize + 1);

            log.debug("Posting ScriptCallbackEvent for privateMessage: {} -> {}", targetName, text);
            ScriptCallbackEvent privateMessageEvent = new ScriptCallbackEvent();
            privateMessageEvent.setEventName("privateMessage");
            eventBus.post(privateMessageEvent);

            // Check if consumed (ChatInputManager sets intStack[n-1] = 1 if consumed)
            consumed[0] = intStack[origIntStackSize] == 1;

            // Restore stack sizes
            client.setObjectStackSize(origObjectStackSize);
            client.setIntStackSize(origIntStackSize);

            if (!consumed[0]) {
                client.runScript(ScriptID.PRIVMSG, targetName, text);
                eventBus.post(new SubmitHistoryEvent(text));
                eventBus.post(new ChatPrivateMessageSentEvent(text, targetName));
            }
        });

        if (consumed[0]) {
            log.debug("Private message consumed by another plugin: {}", text);
            return;
        }

        lastSendTimestamp = System.currentTimeMillis();
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
