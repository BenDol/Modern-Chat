package com.modernchat.overlay;

import com.modernchat.ModernChatConfig;
import net.runelite.api.Client;
import net.runelite.client.input.MouseListener;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatOverlayTest
{
    @Test
    public void clickThroughBackdropPreservesInputFocus() throws Exception
    {
        ChatOverlay overlay = new ChatOverlay();
        setField(overlay, "config", new ChatOverlayConfig.Default());
        setField(overlay, "client", proxy(Client.class));
        setField(overlay, "mainConfig", proxy(ModernChatConfig.class));
        setField(overlay, "lastViewport", new Rectangle(0, 0, 100, 100));
        setField(overlay, "inputFocused", true);

        MouseEvent event = new MouseEvent(
            new Canvas(),
            MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            10,
            10,
            1,
            false,
            MouseEvent.BUTTON1);

        ((MouseListener) getField(overlay, "mouse")).mousePressed(event);

        assertTrue((Boolean) getField(overlay, "inputFocused"));
        assertFalse(event.isConsumed());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type)
    {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
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

    private static void setField(Object target, String name, Object value) throws Exception
    {
        Field field = ChatOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception
    {
        Field field = ChatOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}