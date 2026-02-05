package com.modernchat.common;

import lombok.Getter;

import java.awt.event.KeyEvent;

/**
 * Extended keybinds for mouse buttons 4/5 and F13-F24 keys.
 * These are inputs that RuneLite's standard Keybind system cannot capture.
 */
@Getter
public enum ExtendedKeybind {
    NONE("None", -1, false),
    MOUSE_4("Mouse Button 4", 4, true),
    MOUSE_5("Mouse Button 5", 5, true),
    F13("F13", KeyEvent.VK_F13, false),
    F14("F14", KeyEvent.VK_F14, false),
    F15("F15", KeyEvent.VK_F15, false),
    F16("F16", KeyEvent.VK_F16, false),
    F17("F17", KeyEvent.VK_F17, false),
    F18("F18", KeyEvent.VK_F18, false),
    F19("F19", KeyEvent.VK_F19, false),
    F20("F20", KeyEvent.VK_F20, false),
    F21("F21", KeyEvent.VK_F21, false),
    F22("F22", KeyEvent.VK_F22, false),
    F23("F23", KeyEvent.VK_F23, false),
    F24("F24", KeyEvent.VK_F24, false);

    private final String displayName;
    private final int code;
    private final boolean mouse;

    ExtendedKeybind(String displayName, int code, boolean mouse) {
        this.displayName = displayName;
        this.code = code;
        this.mouse = mouse;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Find an ExtendedKeybind by its code.
     * @param code The button number (for mouse) or VK_* code (for keys)
     * @param isMouse Whether to search for mouse or key bindings
     * @return The matching ExtendedKeybind, or NONE if not found
     */
    public static ExtendedKeybind fromCode(int code, boolean isMouse) {
        for (ExtendedKeybind kb : values()) {
            if (kb.code == code && kb.mouse == isMouse) {
                return kb;
            }
        }
        return NONE;
    }
}
