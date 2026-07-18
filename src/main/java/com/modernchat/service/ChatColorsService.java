package com.modernchat.service;

import com.modernchat.ModernChatConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that integrates with RuneLite's Chat Colors settings (the "Chat Color" entry in the
 * configuration panel, backed by ChatColorConfig, config group "textrecolor").
 * <p>
 * Chat Colors is core client configuration registered directly by the config panel rather than
 * a toggleable plugin, so there is no plugin enabled-state or PluginChanged event to track;
 * a key the user never set simply reads as null and callers fall back to Modern Chat's own
 * general_* colors.
 * <p>
 * Only the channels Modern Chat renders are mapped (see {@link Channel}). Unmapped vanilla
 * settings: private message sent (Modern Chat uses one private color, mapped to received),
 * autochat, server messages, examine, filtered, highlights, and the various username/channel
 * name colors. There is no vanilla welcome message color.
 */
@Slf4j
@Singleton
public class ChatColorsService implements ChatService {
    private static final String TEXTRECOLOR_GROUP = "textrecolor";

    /** Chat channels Modern Chat can recolor, with their textrecolor config keys. */
    public enum Channel {
        PUBLIC("opaquePublicChat", "transparentPublicChat"),
        PRIVATE("opaquePrivateMessageReceived", "transparentPrivateMessageReceived"),
        // Friends chat keeps legacy "ClanChat" key names; it predates the modern clan system
        FRIENDS("opaqueClanChatMessage", "transparentClanChatMessage"),
        CLAN("opaqueClanMessage", "transparentClanMessage"),
        SYSTEM("opaqueGameMessage", "transparentGameMessage"),
        TRADE("opaqueTradeChatMessage", "transparentTradeChatMessage");

        private final String opaqueKey;
        private final String transparentKey;

        Channel(String opaqueKey, String transparentKey) {
            this.opaqueKey = opaqueKey;
            this.transparentKey = transparentKey;
        }
    }

    @Inject private ConfigManager configManager;
    @Inject private EventBus eventBus;
    @Inject private ModernChatConfig config;

    private final Map<Channel, Color> opaqueColors = new ConcurrentHashMap<>();
    private final Map<Channel, Color> transparentColors = new ConcurrentHashMap<>();

    @Override
    public void startUp() {
        eventBus.register(this);
        refreshConfig();
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);
        opaqueColors.clear();
        transparentColors.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (TEXTRECOLOR_GROUP.equals(e.getGroup())) {
            refreshConfig();
        }
    }

    /**
     * Returns the user's Chat Colors color for the channel, or null when the
     * general_UseChatColorsPlugin toggle is off or the user never set the key
     * (callers should fall back to Modern Chat's general_* color).
     *
     * @param channel The chat channel to look up
     * @param isTransparentBackdrop True if the rendering surface is transparent (no backdrop or
     *                              a low-alpha backdrop). Selects the transparent palette;
     *                              falls back to opaque if not configured, and vice versa.
     * @return The color to use, or null if unavailable
     */
    public @Nullable Color getColor(Channel channel, boolean isTransparentBackdrop) {
        if (channel == null || !config.general_UseChatColorsPlugin()) {
            return null;
        }

        Color primary = isTransparentBackdrop ? transparentColors.get(channel) : opaqueColors.get(channel);
        if (primary != null) {
            return primary;
        }
        return isTransparentBackdrop ? opaqueColors.get(channel) : transparentColors.get(channel);
    }

    private void refreshConfig() {
        opaqueColors.clear();
        transparentColors.clear();

        for (Channel channel : Channel.values()) {
            Color opaque = configManager.getConfiguration(TEXTRECOLOR_GROUP, channel.opaqueKey, Color.class);
            Color transparent = configManager.getConfiguration(TEXTRECOLOR_GROUP, channel.transparentKey, Color.class);
            if (opaque != null) opaqueColors.put(channel, opaque);
            if (transparent != null) transparentColors.put(channel, transparent);
        }

        log.debug("Chat Colors config refreshed: opaqueColors={}, transparentColors={}",
            opaqueColors.size(), transparentColors.size());
    }
}
