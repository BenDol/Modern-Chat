package com.modernchat.service;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Service that integrates with the ForceRecolor RuneLite plugin.
 * When ForceRecolor is active with patterns configured, this service
 * will provide matching colors for messages.
 */
@Slf4j
@Singleton
public class ForceRecolorService implements ChatService {
    private static final String FORCERECOLOR_GROUP = "forcerecolor";
    private static final String FORCERECOLOR_PLUGIN_NAME = "Force Recolor";
    private static final int TRANSPARENT_CHATBOX_VARBIT = 4608;

    @Inject private ConfigManager configManager;
    @Inject private Client client;
    @Inject private EventBus eventBus;
    @Inject private PluginManager pluginManager;

    // Cached patterns per group (group number -> compiled Pattern)
    private final Map<Integer, Pattern> groupPatterns = new ConcurrentHashMap<>();

    // Cached colors per group
    private final Map<Integer, Color> opaqueColors = new ConcurrentHashMap<>();
    private final Map<Integer, Color> transparentColors = new ConcurrentHashMap<>();

    // Config state
    private volatile boolean pluginEnabled = false;
    private volatile boolean allMessageTypes = false;
    private volatile String recolorStyle = "NONE";

    @Override
    public void startUp() {
        eventBus.register(this);
        checkPluginEnabled();
        if (pluginEnabled) {
            refreshConfig();
        }
    }

    /**
     * Checks if the ForceRecolor plugin is currently enabled.
     */
    private void checkPluginEnabled() {
        pluginEnabled = false;
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (FORCERECOLOR_PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin)) {
                pluginEnabled = true;
                break;
            }
        }
        log.debug("ForceRecolor plugin enabled check: {}", pluginEnabled);
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);
        groupPatterns.clear();
        opaqueColors.clear();
        transparentColors.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (FORCERECOLOR_GROUP.equals(e.getGroup())) {
            refreshConfig();
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged e) {
        if (FORCERECOLOR_PLUGIN_NAME.equals(e.getPlugin().getName())) {
            if (e.isLoaded()) {
                // ForceRecolor was enabled, refresh config
                pluginEnabled = true;
                refreshConfig();
                log.debug("ForceRecolor plugin enabled, refreshed config");
            } else {
                // ForceRecolor was disabled, clear cached state
                pluginEnabled = false;
                clearState();
                log.debug("ForceRecolor plugin disabled, cleared state");
            }
        }
    }

    /**
     * Clears all cached state when ForceRecolor is disabled.
     */
    private void clearState() {
        pluginEnabled = false;
        groupPatterns.clear();
        opaqueColors.clear();
        transparentColors.clear();
        allMessageTypes = false;
        recolorStyle = "NONE";
    }

    /**
     * Main entry point - returns a Color for the message if it matches a ForceRecolor pattern,
     * or null if no match or ForceRecolor is not active.
     *
     * @param message The message text to check
     * @param type The chat message type
     * @return The color to use, or null if no match
     */
    public @Nullable Color getRecolorForMessage(String message, ChatMessageType type) {
        return getRecolorForMessage(message, type, false);
    }

    /**
     * Returns a Color for the message if it matches a ForceRecolor pattern.
     * For peek overlay, uses transparent colors with fallback to opaque.
     * For main chat overlay, uses opaque colors.
     *
     * @param message The message text to check
     * @param type The chat message type
     * @param isPeekOverlay True if this is for the peek overlay
     * @return The color to use, or null if no match
     */
    public @Nullable Color getRecolorForMessage(String message, ChatMessageType type, boolean isPeekOverlay) {
        if (!pluginEnabled || "NONE".equals(recolorStyle) || groupPatterns.isEmpty()) {
            return null;
        }

        // Check message type filter
        if (!allMessageTypes && !isGameMessage(type)) {
            return null;
        }

        // Find lowest matching group (0-9)
        int matchedGroup = findMatchingGroup(message);
        if (matchedGroup < 0) {
            return null;
        }

        if (isPeekOverlay) {
            // For peek overlay: use transparent colors, fall back to opaque if not defined
            Color transparentColor = transparentColors.get(matchedGroup);
            return transparentColor != null ? transparentColor : opaqueColors.get(matchedGroup);
        } else {
            // For main chat overlay: use opaque colors
            return opaqueColors.get(matchedGroup);
        }
    }

    private void refreshConfig() {
        // Read main settings
        String matchedText = configManager.getConfiguration(FORCERECOLOR_GROUP, "matchedTextString");
        String allTypesStr = configManager.getConfiguration(FORCERECOLOR_GROUP, "allMessageTypes");
        String style = configManager.getConfiguration(FORCERECOLOR_GROUP, "recolorStyle");

        allMessageTypes = "true".equalsIgnoreCase(allTypesStr);
        recolorStyle = style != null ? style : "NONE";

        // Parse patterns
        parseMatchedTextString(matchedText);

        // Read colors for each group (0-9)
        opaqueColors.clear();
        transparentColors.clear();

        for (int i = 0; i <= 9; i++) {
            String opaqueKey = i == 0 ? "opaqueRecolor" : "opaqueRecolorGroup" + i;
            String transparentKey = i == 0 ? "transparentRecolor" : "transparentRecolorGroup" + i;

            Color opaque = parseColor(configManager.getConfiguration(FORCERECOLOR_GROUP, opaqueKey));
            Color transparent = parseColor(configManager.getConfiguration(FORCERECOLOR_GROUP, transparentKey));

            if (opaque != null) {
                opaqueColors.put(i, opaque);
            }
            if (transparent != null) {
                transparentColors.put(i, transparent);
            }
        }

        log.debug("ForceRecolor config refreshed: style={}, allTypes={}, patterns={}, opaqueColors={}, transparentColors={}",
            recolorStyle, allMessageTypes, groupPatterns.size(), opaqueColors.size(), transparentColors.size());
    }

    private void parseMatchedTextString(String csv) {
        groupPatterns.clear();
        if (csv == null || csv.isEmpty()) {
            return;
        }

        Map<Integer, List<String>> groupToPatterns = new HashMap<>();

        for (String entry : csv.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            int group = 0;
            String text = entry;

            // Check for group suffix (::N)
            int colonIdx = entry.lastIndexOf("::");
            if (colonIdx > 0) {
                try {
                    group = Integer.parseInt(entry.substring(colonIdx + 2));
                    text = entry.substring(0, colonIdx);
                    group = Math.max(0, Math.min(9, group)); // Clamp to 0-9
                } catch (NumberFormatException ignored) {
                    // Keep default group 0 and full text
                }
            }

            groupToPatterns.computeIfAbsent(group, k -> new ArrayList<>()).add(Pattern.quote(text));
        }

        // Build regex with word boundaries for each group
        for (Map.Entry<Integer, List<String>> e : groupToPatterns.entrySet()) {
            String regex = "\\b(" + String.join("|", e.getValue()) + ")\\b";
            try {
                groupPatterns.put(e.getKey(), Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            } catch (Exception ex) {
                log.warn("Failed to compile ForceRecolor pattern for group {}: {}", e.getKey(), regex, ex);
            }
        }
    }

    private int findMatchingGroup(String message) {
        if (message == null || message.isEmpty()) {
            return -1;
        }

        // Find lowest matching group number
        int lowestMatch = -1;
        for (Map.Entry<Integer, Pattern> entry : groupPatterns.entrySet()) {
            int group = entry.getKey();
            Pattern pattern = entry.getValue();

            if (pattern.matcher(message).find()) {
                if (lowestMatch < 0 || group < lowestMatch) {
                    lowestMatch = group;
                }
            }
        }
        return lowestMatch;
    }

    private @Nullable Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return null;
        }

        try {
            // RuneLite stores Color configs as integer ARGB values
            int argb = Integer.parseInt(colorStr);
            return new Color(argb, true);
        } catch (NumberFormatException e) {
            // Try hex format as fallback
            if (colorStr.startsWith("#")) {
                try {
                    String hex = colorStr.substring(1);
                    long v = Long.parseLong(hex, 16);
                    if (hex.length() <= 6) {
                        return new Color(((int) v & 0xFFFFFF) | 0xFF000000, true);
                    }
                    return new Color((int) v, true);
                } catch (NumberFormatException ignored) {
                    // Fall through to return null
                }
            }
        }
        return null;
    }

    private boolean isGameMessage(ChatMessageType type) {
        // Match ForceRecolor's behavior: only GAMEMESSAGE and SPAM are "game messages"
        return type == ChatMessageType.GAMEMESSAGE || type == ChatMessageType.SPAM;
    }
}
