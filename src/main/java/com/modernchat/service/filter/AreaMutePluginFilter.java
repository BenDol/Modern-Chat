package com.modernchat.service.filter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.modernchat.ModernChatConfig;
import com.modernchat.service.ChatService;
import com.modernchat.service.MessageFilter;
import com.modernchat.util.ConfigUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.events.ChatMessage;
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
import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Built-in filter that replicates the Area Mute plugin's filtering logic.
 * Blocks public/autotyper messages from players in muted regions.
 * Reads configuration from the "areamute" config group via ConfigManager.
 */
@Slf4j
@Singleton
public class AreaMutePluginFilter implements MessageFilter, ChatService {
    private static final String CONFIG_GROUP = "areamute";
    private static final String PLUGIN_NAME = "Area Mute";
    private static final Type REGIONS_TOKEN = new TypeToken<HashSet<Integer>>() {}.getType();

    private static final Set<ChatMessageType> FILTERED_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.AUTOTYPER
    );

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private PluginManager pluginManager;
    @Inject private EventBus eventBus;
    @Inject private Gson gson;
    @Inject private ModernChatConfig modernChatConfig;

    private volatile boolean pluginEnabled = false;
    private volatile boolean filterSelf = false;
    private volatile boolean filterFriends = false;
    private volatile boolean filterClanMates = false;
    private volatile boolean filterFriendChat = false;
    private final Set<Integer> mutedRegions = new HashSet<>();

    // Cache of message ID -> should block
    private final LinkedHashMap<Integer, Boolean> chatCache = new LinkedHashMap<>() {
        private static final int MAX_ENTRIES = 2000;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    @Override
    public boolean isEnabled() {
        return modernChatConfig.filters_AreaMuteEnabled();
    }

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
        chatCache.clear();
        mutedRegions.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (CONFIG_GROUP.equals(e.getGroup())) {
            refreshConfig();
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged e) {
        if (PLUGIN_NAME.equals(e.getPlugin().getName())) {
            checkPluginEnabled();
            if (e.isLoaded()) {
                refreshConfig();
            }
        }
    }

    @Override
    public @Nullable String apply(ChatMessage message, String currentText) {
        if (!pluginEnabled || mutedRegions.isEmpty())
            return currentText;

        ChatMessageType type = message.getType();
        if (!FILTERED_TYPES.contains(type))
            return currentText;

        String name = Text.removeTags(message.getName());
        int messageId = message.getMessageNode().getId();

        // Find the player actor to get their region
        Player actor = findPlayer(name);

        if (shouldFilter(actor, name)) {
            chatCache.put(messageId, true);
            return null;
        }

        return currentText;
    }

    private @Nullable Player findPlayer(String name) {
        for (Player p : client.getTopLevelWorldView().players()) {
            if (name.equalsIgnoreCase(p.getName()))
                return p;
        }

        Player local = client.getLocalPlayer();
        if (local != null && name.equalsIgnoreCase(local.getName()))
            return local;

        return null;
    }

    private boolean shouldFilter(@Nullable Player actor, String name) {
        Player local = client.getLocalPlayer();
        if (local == null)
            return false;

        int region = local.getWorldLocation().getRegionID();
        if (actor != null) {
            region = actor.getWorldLocation().getRegionID();
        }

        if (actor == local && !filterSelf)
            return false;

        if (!filterFriends && client.isFriended(name, false))
            return false;

        if (!filterFriendChat && isFriendsChatMember(name))
            return false;

        if (!filterClanMates && isClanChatMember(name))
            return false;

        return mutedRegions.contains(region);
    }

    private boolean isFriendsChatMember(String name) {
        FriendsChatManager friendsChatManager = client.getFriendsChatManager();
        return friendsChatManager != null && friendsChatManager.findByName(name) != null;
    }

    private boolean isClanChatMember(String name) {
        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel != null && clanChannel.findMember(name) != null)
            return true;

        clanChannel = client.getGuestClanChannel();
        return clanChannel != null && clanChannel.findMember(name) != null;
    }

    private void checkPluginEnabled() {
        pluginEnabled = false;
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin)) {
                pluginEnabled = true;
                break;
            }
        }
        log.debug("AreaMute plugin enabled check: {}", pluginEnabled);
    }

    private void refreshConfig() {
        filterSelf = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterSelf", false);
        filterFriends = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterFriends", false);
        filterClanMates = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterClanMates", false);
        filterFriendChat = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterCC", false);
        loadRegions();

        log.debug("AreaMute config refreshed: filterSelf={}, regions={}", filterSelf, mutedRegions.size());
    }

    private void loadRegions() {
        mutedRegions.clear();
        String regionsJson = ConfigUtil.getString(configManager, CONFIG_GROUP, "regions", "[]");
        try {
            Set<Integer> parsed = gson.fromJson(regionsJson, REGIONS_TOKEN);
            if (parsed != null) {
                mutedRegions.addAll(parsed);
            }
        } catch (Exception e) {
            log.warn("Failed to parse areamute regions: {}", regionsJson, e);
        }
    }
}
