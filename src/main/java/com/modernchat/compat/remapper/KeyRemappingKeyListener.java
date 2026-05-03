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

        if (!service.chatboxFocused()) {
            return;
        }

        if (!service.isTyping()) {
            int mappedKeyCode = KeyEvent.VK_UNDEFINED;

            if (service.isCameraRemap()) {
                if (matches(e, service.getUp()))
                    mappedKeyCode = KeyEvent.VK_UP;
                else if (matches(e, service.getDown()))
                    mappedKeyCode = KeyEvent.VK_DOWN;
                else if (matches(e, service.getLeft()))
                    mappedKeyCode = KeyEvent.VK_LEFT;
                else if (matches(e, service.getRight()))
                    mappedKeyCode = KeyEvent.VK_RIGHT;
            }

            // F-key remap (and ESC remap) is gated by fkeyRemap and dialog state,
            // so it does not steal number keys used to choose dialog options.
            var isDialogOpen = service.isDialogOpen();
            if (!isDialogOpen) {
                if (service.isFkeyRemap()) {
                    if (matches(e, service.getF1()))
                        mappedKeyCode = KeyEvent.VK_F1;
                    else if (matches(e, service.getF2()))
                        mappedKeyCode = KeyEvent.VK_F2;
                    else if (matches(e, service.getF3()))
                        mappedKeyCode = KeyEvent.VK_F3;
                    else if (matches(e, service.getF4()))
                        mappedKeyCode = KeyEvent.VK_F4;
                    else if (matches(e, service.getF5()))
                        mappedKeyCode = KeyEvent.VK_F5;
                    else if (matches(e, service.getF6()))
                        mappedKeyCode = KeyEvent.VK_F6;
                    else if (matches(e, service.getF7()))
                        mappedKeyCode = KeyEvent.VK_F7;
                    else if (matches(e, service.getF8()))
                        mappedKeyCode = KeyEvent.VK_F8;
                    else if (matches(e, service.getF9()))
                        mappedKeyCode = KeyEvent.VK_F9;
                    else if (matches(e, service.getF10()))
                        mappedKeyCode = KeyEvent.VK_F10;
                    else if (matches(e, service.getF11()))
                        mappedKeyCode = KeyEvent.VK_F11;
                    else if (matches(e, service.getF12()))
                        mappedKeyCode = KeyEvent.VK_F12;
                }

                if (matches(e, service.getEsc()))
                    mappedKeyCode = KeyEvent.VK_ESCAPE;
            }

            // Space remap only applies inside dialogs (excluding the options dialog,
            // which doesn't listen for space).
            if (service.isDialogOpen() && !service.isOptionsDialogOpen()
                && matches(e, service.getSpace())) {
                mappedKeyCode = KeyEvent.VK_SPACE;
            }

            if (!service.isOptionsDialogOpen() && matches(e, service.getControl())) {
                mappedKeyCode = KeyEvent.VK_CONTROL;
            }

            if (mappedKeyCode != KeyEvent.VK_UNDEFINED && mappedKeyCode != e.getKeyCode()) {
                final char keyChar = e.getKeyChar();
                modified.put(e.getKeyCode(), mappedKeyCode);
                e.setKeyCode(mappedKeyCode);
                e.setKeyChar(KeyEvent.CHAR_UNDEFINED);
                if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                    blockedChars.add(keyChar);
                }
            }

            switch (e.getKeyCode()) {
                case KeyEvent.VK_ENTER:
                case KeyEvent.VK_SLASH:
                case KeyEvent.VK_COLON:
                    service.unlockChat();
                    break;
            }
        } else {
            switch (e.getKeyCode()) {
                case KeyEvent.VK_ESCAPE:
                    // Block escape so it doesn't trigger in-game hotkeys when exiting typing.
                    e.consume();
                    service.lockChat();
                    break;
                case KeyEvent.VK_ENTER:
                    service.lockChat();
                    break;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (!service.isEnabled())
            return;

        final char keyChar = e.getKeyChar();
        if (keyChar != KeyEvent.CHAR_UNDEFINED) {
            blockedChars.remove(keyChar);
        }

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

        final char keyChar = e.getKeyChar();
        if (keyChar != KeyEvent.CHAR_UNDEFINED
            && blockedChars.contains(keyChar)
            && service.chatboxFocused()) {
            e.consume();
        }
    }

    private static boolean matches(KeyEvent e, ModifierlessKeybind keybind) {
        return keybind != null && keybind.matches(e);
    }
}
