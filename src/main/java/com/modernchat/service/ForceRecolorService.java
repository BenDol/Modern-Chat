package com.modernchat.service;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

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
    private static final String TEXTRECOLOR_GROUP = "textrecolor";
    private static final String FORCERECOLOR_PLUGIN_NAME = "Force Recolor";

    @Inject private ConfigManager configManager;
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
        // ForceRecolor reads the default group 0 color from the textrecolor group when
        // recolorStyle == CHAT_COLOR_CONFIG, so we have to refresh on either group changing.
        if (FORCERECOLOR_GROUP.equals(e.getGroup()) || TEXTRECOLOR_GROUP.equals(e.getGroup())) {
            refreshConfig();
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged e) {
        if (FORCERECOLOR_PLUGIN_NAME.equals(e.getPlugin().getName())) {
            if (e.isLoaded()) {
                pluginEnabled = true;
                refreshConfig();
                log.debug("ForceRecolor plugin enabled, refreshed config");
            } else {
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
     *
     * @param message The message text to check
     * @param type The chat message type
     * @param isTransparentBackdrop True if the rendering surface is transparent (no backdrop or
     *                              a low-alpha backdrop). Selects the transparent palette;
     *                              falls back to opaque if not configured.
     * @return The color to use, or null if no match
     */
    public @Nullable Color getRecolorForMessage(String message, ChatMessageType type, boolean isTransparentBackdrop) {
        if (!pluginEnabled || "NONE".equals(recolorStyle) || groupPatterns.isEmpty()) {
            return null;
        }

        if (!allMessageTypes && !isGameMessage(type)) {
            return null;
        }

        int matchedGroup = findMatchingGroup(message);
        if (matchedGroup < 0) {
            return null;
        }

        Color primary = isTransparentBackdrop ? transparentColors.get(matchedGroup) : opaqueColors.get(matchedGroup);
        if (primary != null) {
            return primary;
        }
        return isTransparentBackdrop ? opaqueColors.get(matchedGroup) : transparentColors.get(matchedGroup);
    }

    private void refreshConfig() {
        String matchedText = configManager.getConfiguration(FORCERECOLOR_GROUP, "matchedTextString");
        String allTypesStr = configManager.getConfiguration(FORCERECOLOR_GROUP, "allMessageTypes");
        String style = configManager.getConfiguration(FORCERECOLOR_GROUP, "recolorStyle");

        allMessageTypes = "true".equalsIgnoreCase(allTypesStr);
        recolorStyle = style != null ? style : "CHAT_COLOR_CONFIG"; // matches ForceRecolorConfig default

        parseMatchedTextString(matchedText);

        opaqueColors.clear();
        transparentColors.clear();

        // Group 0 (default group) is special: its colors come from a different config
        // depending on the user's recolorStyle setting. THIS_CONFIG reads from forcerecolor's
        // own opaqueRecolor/transparentRecolor; CHAT_COLOR_CONFIG reads from RuneLite's
        // textrecolor (Chat Color) plugin's opaqueGameMessage/transparentGameMessage.
        Color group0Opaque = null;
        Color group0Transparent = null;
        if ("THIS_CONFIG".equals(recolorStyle)) {
            group0Opaque = configManager.getConfiguration(FORCERECOLOR_GROUP, "opaqueRecolor", Color.class);
            group0Transparent = configManager.getConfiguration(FORCERECOLOR_GROUP, "transparentRecolor", Color.class);
        } else if ("CHAT_COLOR_CONFIG".equals(recolorStyle)) {
            group0Opaque = configManager.getConfiguration(TEXTRECOLOR_GROUP, "opaqueGameMessage", Color.class);
            group0Transparent = configManager.getConfiguration(TEXTRECOLOR_GROUP, "transparentGameMessage", Color.class);
        }
        if (group0Opaque != null) opaqueColors.put(0, group0Opaque);
        if (group0Transparent != null) transparentColors.put(0, group0Transparent);

        // Groups 1-9 always come from the forcerecolor group regardless of recolorStyle.
        for (int i = 1; i <= 9; i++) {
            Color opaque = configManager.getConfiguration(FORCERECOLOR_GROUP, "opaqueRecolorGroup" + i, Color.class);
            Color transparent = configManager.getConfiguration(FORCERECOLOR_GROUP, "transparentRecolorGroup" + i, Color.class);
            if (opaque != null) opaqueColors.put(i, opaque);
            if (transparent != null) transparentColors.put(i, transparent);
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

        for (String entry : Text.fromCSV(csv)) {
            if (entry.isEmpty()) {
                continue;
            }

            int group = 0;
            String text = entry;

            // Group suffix is "::N" — split on the last occurrence so patterns containing
            // "::" still work as long as the trailing token is numeric.
            String[] segments = entry.split("::");
            if (segments.length == 2) {
                try {
                    int parsed = Integer.parseInt(segments[1]);
                    if (parsed >= 0 && parsed <= 9) {
                        group = parsed;
                        text = segments[0];
                    }
                } catch (NumberFormatException ignored) {
                    // Keep default group 0 and full text
                }
            }

            // Match Force Recolor's escaping: escape Jagex tags first, then quote for regex.
            groupToPatterns.computeIfAbsent(group, k -> new ArrayList<>())
                .add(Pattern.quote(Text.escapeJagex(text)));
        }

        // Mirror Force Recolor's boundary regex exactly so we don't lose matches at start/end
        // of message or around whitespace that \b alone wouldn't treat as a boundary.
        for (Map.Entry<Integer, List<String>> e : groupToPatterns.entrySet()) {
            String regex = "(?:\\b|(?<=\\s)|\\A)(?:" + String.join("|", e.getValue()) + ")(?:\\b|(?=\\s)|\\z)";
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

    private boolean isGameMessage(ChatMessageType type) {
        // Match ForceRecolor's behavior: GAMEMESSAGE, SPAM, and ENGINE are "game messages".
        return type == ChatMessageType.GAMEMESSAGE
            || type == ChatMessageType.SPAM
            || type == ChatMessageType.ENGINE;
    }
}
