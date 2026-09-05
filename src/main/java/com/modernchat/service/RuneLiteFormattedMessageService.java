package com.modernchat.service;

import com.modernchat.event.RuneLiteChatMessageUpdatedEvent;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Passively watches message nodes that are already displayed by Modern Chat.
 *
 * <p>RuneLite Chat Commands resolves most commands asynchronously by setting
 * {@link MessageNode#getRuneLiteFormatMessage()} on the original node. Modern Chat cannot rely on
 * the legacy {@code chatMessageBuilding} callback to observe that change because its redesigned
 * chat hides the legacy message widgets. This service reads only nodes explicitly observed by an
 * enabled Modern Chat feature and posts an internal row-replacement event when a result appears.
 * It never mutates a message node, refreshes chat, or participates in the default chat renderer.</p>
 */
@Singleton
public class RuneLiteFormattedMessageService implements ChatService
{
    private static final int MAX_PENDING_MESSAGES = 200;
    private static final long MAX_PENDING_AGE_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final String PETS_COMMAND = "!pets";
    private static final Set<ChatMessageType> COMMAND_MESSAGE_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.MODPRIVATECHAT,
        ChatMessageType.PRIVATECHATOUT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT);

    private final EventBus eventBus;
    private final LongSupplier nanoTime;
    private final Map<Integer, PendingMessage> pendingMessages = new LinkedHashMap<>();

    @Inject
    RuneLiteFormattedMessageService(EventBus eventBus)
    {
        this(eventBus, System::nanoTime);
    }

    RuneLiteFormattedMessageService(EventBus eventBus, LongSupplier nanoTime)
    {
        this.eventBus = eventBus;
        this.nanoTime = nanoTime;
    }

    @Override
    public void startUp()
    {
        clear();
    }

    @Override
    public void shutDown()
    {
        clear();
    }

    /**
     * Starts watching a command row that an enabled Modern Chat feature has accepted.
     *
     * @return a result already present on the node, otherwise {@code null}
     */
    public synchronized @Nullable String observe(ChatMessage event)
    {
        if (event == null || event.getMessageNode() == null
            || !isSupportedCommand(event.getType(), event.getMessage()))
        {
            return null;
        }

        MessageNode node = event.getMessageNode();
        int messageId = node.getId();
        if (messageId < 0)
        {
            return null;
        }

        PendingMessage existing = pendingMessages.get(messageId);
        String formattedBody = node.getRuneLiteFormatMessage();
        if (formattedBody != null)
        {
            if (existing != null && existing.node != node)
            {
                // The id now belongs to a different chat row. Do not let the stale watch update it.
                pendingMessages.remove(messageId);
            }
            // Keep a same-node watch until poll(): another Modern Chat consumer may already have
            // rendered the literal command immediately before this asynchronous result appeared.
            return formattedBody;
        }

        if (existing == null || existing.node != node)
        {
            existing = new PendingMessage(
                node,
                node.getValue(),
                isPetsCommand(event.getMessage()),
                nanoTime.getAsLong());
            pendingMessages.put(messageId, existing);
            trimToMaxSize();
        }

        // A second enabled Modern Chat consumer can observe the same node after the asynchronous
        // !pets fallback has changed its plain value. Keep the watch pending so the first consumer
        // also receives the replacement event on the next poll.
        String currentValue = node.getValue();
        return existing.acceptPlainValueChange(currentValue) ? currentValue : null;
    }

    /**
     * Polls pending Modern Chat command rows. This is called from ModernChatPlugin's existing
     * PostClientTick subscriber, so update events are delivered on the client thread.
     */
    public synchronized void poll()
    {
        if (pendingMessages.isEmpty())
        {
            return;
        }

        long now = nanoTime.getAsLong();
        List<RuneLiteChatMessageUpdatedEvent> updates = new ArrayList<>();
        Iterator<Map.Entry<Integer, PendingMessage>> iterator = pendingMessages.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<Integer, PendingMessage> entry = iterator.next();
            PendingMessage pending = entry.getValue();

            if (now - pending.observedAtNanos >= MAX_PENDING_AGE_NANOS)
            {
                iterator.remove();
                continue;
            }

            String formattedBody = pending.node.getRuneLiteFormatMessage();
            if (formattedBody == null)
            {
                String currentValue = pending.node.getValue();
                if (pending.acceptPlainValueChange(currentValue))
                {
                    formattedBody = currentValue;
                }
            }

            if (formattedBody != null)
            {
                iterator.remove();
                updates.add(new RuneLiteChatMessageUpdatedEvent(entry.getKey(), formattedBody));
            }
        }

        for (RuneLiteChatMessageUpdatedEvent update : updates)
        {
            eventBus.post(update);
        }
    }

    public synchronized void clear()
    {
        pendingMessages.clear();
    }

    static boolean isSupportedCommand(ChatMessageType type, @Nullable String message)
    {
        if (type == null || message == null || !COMMAND_MESSAGE_TYPES.contains(type))
        {
            return false;
        }

        String trimmed = message.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '!';
    }

    private static boolean isPetsCommand(@Nullable String message)
    {
        if (message == null)
        {
            return false;
        }

        String trimmed = message.trim();
        int separator = trimmed.indexOf(' ');
        String command = separator == -1 ? trimmed : trimmed.substring(0, separator);
        return PETS_COMMAND.equalsIgnoreCase(command);
    }

    private void trimToMaxSize()
    {
        while (pendingMessages.size() > MAX_PENDING_MESSAGES)
        {
            Iterator<Integer> iterator = pendingMessages.keySet().iterator();
            if (!iterator.hasNext())
            {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static final class PendingMessage
    {
        private final MessageNode node;
        private final String originalValue;
        private final boolean acceptsPlainValueChange;
        private final long observedAtNanos;

        private PendingMessage(
            MessageNode node,
            @Nullable String originalValue,
            boolean acceptsPlainValueChange,
            long observedAtNanos)
        {
            this.node = node;
            this.originalValue = originalValue;
            this.acceptsPlainValueChange = acceptsPlainValueChange;
            this.observedAtNanos = observedAtNanos;
        }

        private boolean acceptPlainValueChange(@Nullable String currentValue)
        {
            return acceptsPlainValueChange
                && currentValue != null
                && !Objects.equals(originalValue, currentValue);
        }
    }
}