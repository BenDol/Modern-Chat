package com.modernchat.util;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.ModifierlessKeybind;

public class ConfigUtil {

    public static String getString(ConfigManager configManager, String group, String key, String defaultValue) {
        String value = configManager.getConfiguration(group, key);
        return value != null ? value : defaultValue;
    }

    public static boolean getBool(ConfigManager configManager, String group, String key, boolean defaultValue) {
        String value = configManager.getConfiguration(group, key);
        return value != null ? "true".equalsIgnoreCase(value) : defaultValue;
    }

    public static int getInt(ConfigManager configManager, String group, String key, int defaultValue) {
        String value = configManager.getConfiguration(group, key);
        if (value == null)
            return defaultValue;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static Keybind getKeybind(ConfigManager configManager, String group, String key, Keybind defaultValue) {
        String value = configManager.getConfiguration(group, key);
        if (value == null)
            return defaultValue;

        String[] parts = value.split(":");
        if (parts.length != 2)
            return defaultValue;

        try {
            int keyCode = Integer.parseInt(parts[0]);
            int modifiers = Integer.parseInt(parts[1]);
            return new Keybind(keyCode, modifiers);
        } catch (NumberFormatException | AssertionError e) {
            return defaultValue;
        }
    }

    public static <T extends Enum<T>> T getEnum(ConfigManager configManager, String group, String key, T defaultValue, Class<T> enumClass) {
        String value = configManager.getConfiguration(group, key);
        if (value == null)
            return defaultValue;

        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    public static ModifierlessKeybind getModifierlessKeybind(ConfigManager configManager, String group, String key, ModifierlessKeybind defaultValue) {
        String value = configManager.getConfiguration(group, key);
        if (value == null)
            return defaultValue;

        String[] parts = value.split(":");
        if (parts.length != 2)
            return defaultValue;

        try {
            int keyCode = Integer.parseInt(parts[0]);
            int modifiers = Integer.parseInt(parts[1]);
            return new ModifierlessKeybind(keyCode, modifiers);
        } catch (NumberFormatException | AssertionError e) {
            return defaultValue;
        }
    }
}
