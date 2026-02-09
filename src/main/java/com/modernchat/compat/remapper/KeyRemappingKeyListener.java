package com.modernchat.compat.remapper;

import net.runelite.client.config.ModifierlessKeybind;
import net.runelite.client.input.KeyListener;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KeyRemappingKeyListener implements KeyListener {
    private final KeyRemappingService service;
    private final Map<Integer, Integer> modified = new HashMap<>();
    private final Set<Character> blockedChars = new HashSet<>();

    public KeyRemappingKeyListener(KeyRemappingService service) {
        this.service = service;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (!service.isEnabled())
            return;

        boolean typing = service.isTyping();

        // Typing transitions
        if (!typing && e.getKeyCode() == KeyEvent.VK_ENTER) {
            service.unlockChat();
            return;
        }

        if (!typing && e.getKeyCode() == KeyEvent.VK_SLASH) {
            service.unlockChat();
            return;
        }

        if (typing && (e.getKeyCode() == KeyEvent.VK_ESCAPE || e.getKeyCode() == KeyEvent.VK_ENTER)) {
            service.lockChat();
            if (e.getKeyCode() != KeyEvent.VK_ENTER)
                e.consume();
            return;
        }

        // Don't remap keys while typing
        if (typing)
            return;

        // Camera remapping
        if (service.isCameraRemap()) {
            int remapped = matchCameraKey(e);
            if (remapped != -1) {
                blockedChars.add(e.getKeyChar());
                modified.put(e.getKeyCode(), remapped);
                e.setKeyCode(remapped);
                e.setKeyChar(KeyEvent.CHAR_UNDEFINED);
                return;
            }
        }

        // F-key remapping
        if (service.isFkeyRemap()) {
            int remapped = matchFKey(e);
            if (remapped != -1) {
                blockedChars.add(e.getKeyChar());
                modified.put(e.getKeyCode(), remapped);
                e.setKeyCode(remapped);
                e.setKeyChar(KeyEvent.CHAR_UNDEFINED);
                return;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (!service.isEnabled())
            return;

        Integer remapped = modified.remove(e.getKeyCode());
        if (remapped != null) {
            e.setKeyCode(remapped);
            e.setKeyChar(KeyEvent.CHAR_UNDEFINED);
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (!service.isEnabled())
            return;

        if (blockedChars.remove(e.getKeyChar())) {
            e.consume();
        }
    }

    private int matchCameraKey(KeyEvent e) {
        if (matches(e, service.getUp()))
            return KeyEvent.VK_UP;
        if (matches(e, service.getDown()))
            return KeyEvent.VK_DOWN;
        if (matches(e, service.getLeft()))
            return KeyEvent.VK_LEFT;
        if (matches(e, service.getRight()))
            return KeyEvent.VK_RIGHT;
        return -1;
    }

    private int matchFKey(KeyEvent e) {
        if (matches(e, service.getF1()))
            return KeyEvent.VK_F1;
        if (matches(e, service.getF2()))
            return KeyEvent.VK_F2;
        if (matches(e, service.getF3()))
            return KeyEvent.VK_F3;
        if (matches(e, service.getF4()))
            return KeyEvent.VK_F4;
        if (matches(e, service.getF5()))
            return KeyEvent.VK_F5;
        if (matches(e, service.getF6()))
            return KeyEvent.VK_F6;
        if (matches(e, service.getF7()))
            return KeyEvent.VK_F7;
        if (matches(e, service.getF8()))
            return KeyEvent.VK_F8;
        if (matches(e, service.getF9()))
            return KeyEvent.VK_F9;
        if (matches(e, service.getF10()))
            return KeyEvent.VK_F10;
        if (matches(e, service.getF11()))
            return KeyEvent.VK_F11;
        if (matches(e, service.getF12()))
            return KeyEvent.VK_F12;
        if (matches(e, service.getEsc()))
            return KeyEvent.VK_ESCAPE;
        if (matches(e, service.getSpace()))
            return KeyEvent.VK_SPACE;
        if (matches(e, service.getControl()))
            return KeyEvent.VK_CONTROL;
        return -1;
    }

    private static boolean matches(KeyEvent e, ModifierlessKeybind keybind) {
        return keybind != null && keybind.matches(e);
    }
}
