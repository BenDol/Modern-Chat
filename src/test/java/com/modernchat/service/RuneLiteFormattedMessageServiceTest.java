package com.modernchat.service;

import com.modernchat.event.RuneLiteChatMessageUpdatedEvent;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RuneLiteFormattedMessageServiceTest
{
    @Test
    public void emitsAsyncFormattedResultExactlyOnceWithoutMutatingTheNode()
    {
        Fixture fixture = new Fixture();
        NodeState node = new NodeState(41, "!pets");

        assertNull(fixture.service.observe(chatMessage(node, ChatMessageType.PUBLICCHAT, "!pets")));

        node.formattedValue = "<colNORMAL>Pets: (1) <img=321>";
        fixture.service.poll();
        fixture.service.poll();

        assertEquals(1, fixture.updates.size());
        assertEquals(41, fixture.updates.get(0).getMessageId());
        assertEquals(node.formattedValue, fixture.updates.get(0).getFormattedBody());
        assertEquals(0, node.setterCalls);
    }

    @Test
    public void emitsPetsPlainValueFallbackButIgnoresOtherPlainValueChanges()
    {
        Fixture fixture = new Fixture();
        NodeState pets = new NodeState(42, "!pets");
        NodeState level = new NodeState(43, "!lvl ranged");

        fixture.service.observe(chatMessage(pets, ChatMessageType.PUBLICCHAT, "!pets"));
        fixture.service.observe(chatMessage(level, ChatMessageType.PUBLICCHAT, "!lvl ranged"));

        pets.value = "Open the 'All Pets' tab in the Collection Log to update your pet list";
        level.value = "an unrelated MessageNode value edit";
        fixture.service.poll();

        assertEquals(1, fixture.updates.size());
        assertEquals(42, fixture.updates.get(0).getMessageId());
        assertEquals(pets.value, fixture.updates.get(0).getFormattedBody());

        level.formattedValue = "<colNORMAL>Ranged: <colHIGHLIGHT>99";
        fixture.service.poll();
        assertEquals(2, fixture.updates.size());
        assertEquals(43, fixture.updates.get(1).getMessageId());
        assertEquals(0, pets.setterCalls + level.setterCalls);
    }

    @Test
    public void replacesAReusedMessageIdWithTheNewNodeIdentity()
    {
        Fixture fixture = new Fixture();
        NodeState oldNode = new NodeState(44, "!pets");
        NodeState newNode = new NodeState(44, "!pets");

        fixture.service.observe(chatMessage(oldNode, ChatMessageType.PUBLICCHAT, "!pets"));
        fixture.service.observe(chatMessage(newNode, ChatMessageType.PUBLICCHAT, "!pets"));

        oldNode.formattedValue = "old result";
        fixture.service.poll();
        assertTrue(fixture.updates.isEmpty());

        newNode.formattedValue = "new result";
        fixture.service.poll();
        assertEquals(1, fixture.updates.size());
        assertEquals("new result", fixture.updates.get(0).getFormattedBody());
    }

    @Test
    public void immediateResultOnAReusedIdDiscardsTheOldPendingNode()
    {
        Fixture fixture = new Fixture();
        NodeState oldNode = new NodeState(47, "!pets");
        NodeState newNode = new NodeState(47, "!price coal");
        newNode.formattedValue = "Coal: 150 gp";

        fixture.service.observe(chatMessage(oldNode, ChatMessageType.PUBLICCHAT, "!pets"));
        assertEquals(
            newNode.formattedValue,
            fixture.service.observe(chatMessage(newNode, ChatMessageType.PUBLICCHAT, "!price coal")));

        oldNode.formattedValue = "stale pets result";
        fixture.service.poll();
        assertTrue(fixture.updates.isEmpty());
    }

    @Test
    public void returnsAnImmediateResultAndExpiresUnresolvedWatches()
    {
        Fixture fixture = new Fixture();
        NodeState immediate = new NodeState(45, "!price coal");
        immediate.formattedValue = "<colNORMAL>Coal: <colHIGHLIGHT>150 gp";

        assertEquals(
            immediate.formattedValue,
            fixture.service.observe(chatMessage(immediate, ChatMessageType.PUBLICCHAT, "!price coal")));
        fixture.service.poll();
        assertTrue(fixture.updates.isEmpty());

        NodeState expired = new NodeState(46, "!pets");
        fixture.service.observe(chatMessage(expired, ChatMessageType.PUBLICCHAT, "!pets"));
        fixture.clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
        expired.formattedValue = "late result";
        fixture.service.poll();
        assertTrue(fixture.updates.isEmpty());
    }

    @Test
    public void observesOnlyCommandsFromSupportedPlayerChatTypes()
    {
        assertTrue(RuneLiteFormattedMessageService.isSupportedCommand(
            ChatMessageType.PUBLICCHAT, " !pets"));
        assertTrue(RuneLiteFormattedMessageService.isSupportedCommand(
            ChatMessageType.PRIVATECHATOUT, "!lvl ranged"));

        assertFalse(RuneLiteFormattedMessageService.isSupportedCommand(
            ChatMessageType.GAMEMESSAGE, "!pets"));
        assertFalse(RuneLiteFormattedMessageService.isSupportedCommand(
            ChatMessageType.PUBLICCHAT, "not !pets"));
        assertFalse(RuneLiteFormattedMessageService.isSupportedCommand(
            ChatMessageType.PUBLICCHAT, null));
    }

    private static ChatMessage chatMessage(NodeState node, ChatMessageType type, String message)
    {
        return new ChatMessage(node.proxy, type, "Alice", message, "", 0);
    }

    private static final class Fixture
    {
        private final AtomicLong clock = new AtomicLong();
        private final List<RuneLiteChatMessageUpdatedEvent> updates = new ArrayList<>();
        private final RuneLiteFormattedMessageService service;

        private Fixture()
        {
            EventBus eventBus = new EventBus(error ->
            {
                throw new AssertionError(error);
            });
            eventBus.register(RuneLiteChatMessageUpdatedEvent.class, updates::add, 0);
            service = new RuneLiteFormattedMessageService(eventBus, clock::get);
            service.startUp();
        }
    }

    private static final class NodeState implements InvocationHandler
    {
        private final int id;
        private final MessageNode proxy;
        private String value;
        private String formattedValue;
        private int setterCalls;

        private NodeState(int id, String value)
        {
            this.id = id;
            this.value = value;
            this.proxy = (MessageNode) Proxy.newProxyInstance(
                MessageNode.class.getClassLoader(),
                new Class<?>[]{MessageNode.class},
                this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
        {
            switch (method.getName())
            {
                case "getId":
                    return id;
                case "getValue":
                    return value;
                case "getRuneLiteFormatMessage":
                    return formattedValue;
                case "setValue":
                    setterCalls++;
                    value = (String) args[0];
                    return null;
                case "setRuneLiteFormatMessage":
                    setterCalls++;
                    formattedValue = (String) args[0];
                    return null;
                case "toString":
                    return "MessageNode[" + id + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        }
    }

    private static Object defaultValue(Class<?> type)
    {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}