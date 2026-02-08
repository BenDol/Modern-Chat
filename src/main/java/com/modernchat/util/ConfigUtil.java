package com.modernchat.util;

import net.runelite.client.config.ConfigManager;

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
}
