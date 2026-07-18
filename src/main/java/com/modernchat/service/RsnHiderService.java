package com.modernchat.service;

import lombok.extern.slf4j.Slf4j;
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

/**
 * Service that integrates with the RSN Hider plugin hub plugin.
 * RSN Hider rewrites the legacy chatbox input widget with a fake name, but skips
 * hidden widgets, so Modern Chat's own input prefix would still show the real name.
 * This service exposes RSN Hider's configured custom name so the modern input
 * prefix can match.
 */
@Slf4j
@Singleton
public class RsnHiderService implements ChatService {
    private static final String RSNHIDER_GROUP = "rsnhider";
    private static final String RSNHIDER_PLUGIN_NAME = "RSN Hider";
    private static final String CUSTOM_RSN_KEY = "customRsn";

    @Inject private ConfigManager configManager;
    @Inject private EventBus eventBus;
    @Inject private PluginManager pluginManager;

    private volatile boolean pluginEnabled = false;
    private volatile String customRsn = null;

    @Override
    public void startUp() {
        eventBus.register(this);
        checkPluginEnabled();
        if (pluginEnabled) {
            refreshConfig();
        }
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);
        clearState();
    }

    /**
     * Returns the custom RSN configured in RSN Hider, or null when RSN Hider is
     * disabled or no custom name is set. When unset, RSN Hider substitutes a random
     * name held in a private field we cannot access, so callers should fall back
     * to the real name.
     */
    public @Nullable String getCustomRsn() {
        return pluginEnabled ? customRsn : null;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (RSNHIDER_GROUP.equals(e.getGroup())) {
            refreshConfig();
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged e) {
        if (RSNHIDER_PLUGIN_NAME.equals(e.getPlugin().getName())) {
            if (e.isLoaded()) {
                pluginEnabled = true;
                refreshConfig();
                log.debug("RSN Hider plugin enabled, refreshed config");
            } else {
                clearState();
                log.debug("RSN Hider plugin disabled, cleared state");
            }
        }
    }

    /**
     * Checks if the RSN Hider plugin is currently enabled.
     */
    private void checkPluginEnabled() {
        pluginEnabled = false;
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (RSNHIDER_PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin)) {
                pluginEnabled = true;
                break;
            }
        }
        log.debug("RSN Hider plugin enabled check: {}", pluginEnabled);
    }

    private void refreshConfig() {
        String value = configManager.getConfiguration(RSNHIDER_GROUP, CUSTOM_RSN_KEY);
        // RSN Hider only treats the exact empty string as "unset" (random name mode)
        customRsn = value != null && !value.isEmpty() ? value : null;
        log.debug("RSN Hider config refreshed: customRsn set={}", customRsn != null);
    }

    private void clearState() {
        pluginEnabled = false;
        customRsn = null;
    }
}
