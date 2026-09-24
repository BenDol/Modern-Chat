package com.modernchat.service;

import com.modernchat.common.ChannelPrefix;
import com.modernchat.common.ChatMode;
import com.modernchat.common.ClanType;
import com.modernchat.event.ChatMessageSentEvent;
import com.modernchat.event.ChatPrivateMessageSentEvent;
import com.modernchat.event.ChatSendLockedEvent;
import com.modernchat.event.SubmitHistoryEvent;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;

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
        final String trimmed = text.trim();
        if (trimmed.startsWith("::")) {
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
            return;
        }

        if (mode == ChatMode.PRIVATE) {
            if (StringUtil.isNullOrEmpty(targetName)) {
                log.warn("Attempted to send private chat message to no target");
                return;
            }
            sendPrivateChat(text, targetName);
            return;
        }

        clientThread.invoke(() -> sendChannelMessage(text, mode));

        lastSendTimestamp = System.currentTimeMillis();
    }

    private void sendChannelMessage(String text, ChatMode mode) {
        String message = text;
        ChatMode channel = mode;
        int chatboxMode = -1; // -1 leaves the player's chatbox mode untouched

        // Honor the vanilla channel prefixes (/g, /@g, ////, /c, //, /f, /p, ...)
        // the same way the game's own chatbox does.
        ChannelPrefix prefix = ChannelPrefix.match(text, ClientUtil.isGroupIronman(client));
        if (prefix != null) {
            message = text.substring(prefix.getPrefix().length());
            channel = prefix.getMode();
            if (prefix.isSetsChatboxMode() || client.getVarbitValue(VarbitID.OPTION_CHATBOX_MODE_AUTOSET) == 1)
                chatboxMode = prefix.getChatboxMode();
        }

        // A bare "/@g" carries no message but must still reach the script so it can
        // switch the chatbox mode; the script itself skips the send when the text is empty.
        if (message.isEmpty() && chatboxMode == -1)
            return;

        // Group chat is sent as clan chat with the ironman clan type, same as the vanilla /g prefix
        final boolean group = channel == ChatMode.CLAN_GIM;
        final int chatType = group ? ChatMode.CLAN_MAIN.getValue() : channel.getValue();
        final int clanType = (group ? ClanType.IRONMAN : ClanType.NORMAL).getValue();

        client.setVarcStrValue(VarClientID.CHATINPUT, text);
        client.runScript(ScriptID.CHAT_SEND, message, chatType, clanType, 0, chatboxMode);
        if (!message.isEmpty())
            eventBus.post(new ChatMessageSentEvent(message, chatType, clanType));
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

        clientThread.invoke(() -> {
            client.runScript(ScriptID.PRIVMSG, targetName, text);
            eventBus.post(new SubmitHistoryEvent(text));
            eventBus.post(new ChatPrivateMessageSentEvent(text, targetName));
        });

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
