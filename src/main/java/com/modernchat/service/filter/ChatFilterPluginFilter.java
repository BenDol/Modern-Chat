package com.modernchat.service.filter;

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
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Built-in filter that replicates RuneLite's ChatFilterPlugin filtering logic.
 * Reads configuration from the "chatfilter" config group via ConfigManager.
 */
@Slf4j
@Singleton
public class ChatFilterPluginFilter implements MessageFilter, ChatService {
    private static final String CONFIG_GROUP = "chatfilter";
    private static final String PLUGIN_NAME = "Chat Filter";
    static final String CENSOR_MESSAGE = "Hey, everyone, I just tried to say something very silly!";

    private static final Set<ChatMessageType> PLAYER_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.AUTOTYPER,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.MODPRIVATECHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT
    );

    private static final Set<ChatMessageType> GAME_TYPES = EnumSet.of(
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.ENGINE,
        ChatMessageType.FRIENDSCHATNOTIFICATION,
        ChatMessageType.ITEM_EXAMINE,
        ChatMessageType.NPC_EXAMINE,
        ChatMessageType.OBJECT_EXAMINE,
        ChatMessageType.SPAM,
        ChatMessageType.CLAN_MESSAGE,
        ChatMessageType.CLAN_GUEST_MESSAGE,
        ChatMessageType.CLAN_GIM_MESSAGE,
        ChatMessageType.NPC_SAY
    );

    private static final Set<ChatMessageType> COLLAPSIBLE_MESSAGETYPES = EnumSet.of(
        ChatMessageType.ENGINE,
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.ITEM_EXAMINE,
        ChatMessageType.NPC_EXAMINE,
        ChatMessageType.OBJECT_EXAMINE,
        ChatMessageType.SPAM,
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.NPC_SAY
    );

    @Inject private Client client;
    @Inject private ConfigManager configManager;
    @Inject private PluginManager pluginManager;
    @Inject private EventBus eventBus;
    @Inject private ModernChatConfig modernChatConfig;

    // Cached patterns
    private volatile List<Pattern> filteredPatterns = Collections.emptyList();
    private volatile List<Pattern> filteredNamePatterns = Collections.emptyList();

    // Cached config values
    private volatile String filterType = "CENSOR_WORDS";
    private volatile boolean filterFriends = false;
    private volatile boolean filterFriendsChat = false;
    private volatile boolean filterClanChat = false;
    private volatile boolean filterGameChat = false;
    private volatile boolean collapseGameChat = false;
    private volatile boolean collapsePlayerChat = false;
    private volatile int maxRepeatedPublicChats = 0;
    private volatile boolean stripAccents = false;
    private volatile boolean pluginEnabled = false;

    // Duplicate tracking
    private static class Duplicate {
        int messageId;
        int count;
    }

    private final LinkedHashMap<String, Duplicate> duplicateChatCache = new LinkedHashMap<>() {
        private static final int MAX_ENTRIES = 100;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Duplicate> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    @Override
    public boolean isEnabled() {
        return modernChatConfig.filters_ChatFilterEnabled();
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
        filteredPatterns = Collections.emptyList();
        filteredNamePatterns = Collections.emptyList();
        duplicateChatCache.clear();
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

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        switch (e.getGameState()) {
            case CONNECTION_LOST:
            case HOPPING:
            case LOGGING_IN:
                duplicateChatCache.values().forEach(d -> d.messageId = -1);
                break;
        }
    }

    private void trackDuplicate(ChatMessage chatMessage) {
        if (!pluginEnabled)
            return;

        if (COLLAPSIBLE_MESSAGETYPES.contains(chatMessage.getType())) {
            int messageId = chatMessage.getMessageNode().getId();
            String key = chatMessage.getMessageNode().getName() + ":" + chatMessage.getMessageNode().getValue();
            Duplicate duplicate = duplicateChatCache.get(key);
            if (duplicate != null && duplicate.messageId == messageId) {
                return; // already tracked this message
            }
            duplicateChatCache.remove(key);
            if (duplicate == null) {
                duplicate = new Duplicate();
            }
            duplicate.count++;
            duplicate.messageId = messageId;
            duplicateChatCache.put(key, duplicate);
        }
    }

    @Override
    public @Nullable String apply(ChatMessage message, String currentText) {
        trackDuplicate(message);

        if (!pluginEnabled)
            return currentText;

        ChatMessageType type = message.getType();
        String name = message.getMessageNode().getName();
        int messageId = message.getMessageNode().getId();
        String result = currentText;
        boolean blockMessage = false;

        // Player message filtering
        if (PLAYER_TYPES.contains(type)) {
            if (canFilterPlayer(Text.sanitize(name))) {
                result = censorMessage(name, result);
                blockMessage = result == null;
            }
        }
        // Game message filtering
        else if (GAME_TYPES.contains(type)) {
            if (filterGameChat) {
                result = censorMessage(null, result);
                blockMessage = result == null;
            }
        }

        // Duplicate collapse
        boolean shouldCollapse = (type == ChatMessageType.PUBLICCHAT || type == ChatMessageType.MODCHAT)
            ? collapsePlayerChat
            : COLLAPSIBLE_MESSAGETYPES.contains(type) && collapseGameChat;

        if (!blockMessage && shouldCollapse) {
            Duplicate duplicateCacheEntry = duplicateChatCache.get(name + ":" + result);
            if (duplicateCacheEntry != null && duplicateCacheEntry.messageId != -1) {
                blockMessage = duplicateCacheEntry.messageId != messageId ||
                    ((type == ChatMessageType.PUBLICCHAT || type == ChatMessageType.MODCHAT) &&
                        maxRepeatedPublicChats > 0 && duplicateCacheEntry.count > maxRepeatedPublicChats);

                if (!blockMessage && duplicateCacheEntry.count > 1) {
                    result = result + " (" + duplicateCacheEntry.count + ")";
                }
            }
        }

        return blockMessage ? null : result;
    }

    private void checkPluginEnabled() {
        pluginEnabled = false;
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginEnabled(plugin)) {
                pluginEnabled = true;
                break;
            }
        }
        log.debug("ChatFilter plugin enabled check: {}", pluginEnabled);
    }

    private void refreshConfig() {
        filterType = ConfigUtil.getString(configManager, CONFIG_GROUP, "filterType", "CENSOR_WORDS");
        filterFriends = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterFriends", false);
        filterFriendsChat = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterClan", false);
        filterClanChat = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterClanChat", false);
        filterGameChat = ConfigUtil.getBool(configManager, CONFIG_GROUP, "filterGameChat", false);
        collapseGameChat = ConfigUtil.getBool(configManager, CONFIG_GROUP, "collapseGameChat", false);
        collapsePlayerChat = ConfigUtil.getBool(configManager, CONFIG_GROUP, "collapsePlayerChat", false);
        maxRepeatedPublicChats = ConfigUtil.getInt(configManager, CONFIG_GROUP, "maxRepeatedPublicChats", 0);
        stripAccents = ConfigUtil.getBool(configManager, CONFIG_GROUP, "stripAccents", false);

        updateFilteredPatterns();

        log.debug("ChatFilter config refreshed: filterType={}, filterGameChat={}, collapse={}/{}, patterns={}, namePatterns={}",
            filterType, filterGameChat, collapsePlayerChat, collapseGameChat,
            filteredPatterns.size(), filteredNamePatterns.size());
    }

    private boolean canFilterPlayer(String playerName) {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
            return false;

        boolean isMessageFromSelf = playerName.equals(localPlayer.getName());
        return !isMessageFromSelf &&
            (filterFriends || !client.isFriended(playerName, false)) &&
            (filterFriendsChat || !isFriendsChatMember(playerName)) &&
            (filterClanChat || !isClanChatMember(playerName));
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

    private @Nullable String censorMessage(@Nullable String username, String message) {
        String strippedMessage = Text.JAGEX_PRINTABLE_CHAR_MATCHER.retainFrom(message)
            .replace('\u00A0', ' ')
            .replace("<lt>", "<")
            .replace("<gt>", ">");
        String strippedAccents = stripAccentsIfEnabled(strippedMessage);

        if (username != null && isNameFiltered(username)) {
            switch (filterType) {
                case "CENSOR_WORDS":
                    return StringUtils.repeat('*', strippedMessage.length());
                case "CENSOR_MESSAGE":
                    return CENSOR_MESSAGE;
                case "REMOVE_MESSAGE":
                    return null;
            }
        }

        boolean filtered = false;
        for (Pattern pattern : filteredPatterns) {
            Matcher m = pattern.matcher(strippedAccents);
            StringBuilder sb = new StringBuilder();
            int idx = 0;

            while (m.find()) {
                switch (filterType) {
                    case "CENSOR_WORDS":
                        MatchResult matchResult = m.toMatchResult();
                        sb.append(strippedMessage, idx, matchResult.start())
                            .append(StringUtils.repeat('*', matchResult.group().length()));
                        idx = m.end();
                        filtered = true;
                        break;
                    case "CENSOR_MESSAGE":
                        return CENSOR_MESSAGE;
                    case "REMOVE_MESSAGE":
                        return null;
                }
            }
            sb.append(strippedMessage.substring(idx));

            strippedMessage = sb.toString();
            strippedAccents = stripAccentsIfEnabled(strippedMessage);
        }

        return filtered ? strippedMessage : message;
    }

    private boolean isNameFiltered(String playerName) {
        String sanitizedName = Text.standardize(playerName);
        for (Pattern pattern : filteredNamePatterns) {
            if (pattern.matcher(sanitizedName).find())
                return true;
        }
        return false;
    }

    private void updateFilteredPatterns() {
        List<Pattern> patterns = new ArrayList<>();
        List<Pattern> namePatterns = new ArrayList<>();

        String filteredWords = ConfigUtil.getString(configManager, CONFIG_GROUP, "filteredWords", "");
        String filteredRegex = ConfigUtil.getString(configManager, CONFIG_GROUP, "filteredRegex", "");
        String filteredNames = ConfigUtil.getString(configManager, CONFIG_GROUP, "filteredNames", "");

        // Words: CSV-separated, compiled with Pattern.quote
        Text.fromCSV(filteredWords).stream()
            .map(this::stripAccentsIfEnabled)
            .map(s -> compilePattern(Pattern.quote(s)))
            .filter(Objects::nonNull)
            .forEach(patterns::add);

        // Regex: newline-separated
        splitNewlines(filteredRegex).stream()
            .map(this::stripAccentsIfEnabled)
            .map(ChatFilterPluginFilter::compilePattern)
            .filter(Objects::nonNull)
            .forEach(patterns::add);

        // Names: newline-separated regex patterns
        splitNewlines(filteredNames).stream()
            .map(this::stripAccentsIfEnabled)
            .map(ChatFilterPluginFilter::compilePattern)
            .filter(Objects::nonNull)
            .forEach(namePatterns::add);

        filteredPatterns = patterns;
        filteredNamePatterns = namePatterns;
    }

    private String stripAccentsIfEnabled(String input) {
        return stripAccents ? StringUtils.stripAccents(input) : input;
    }

    private static @Nullable Pattern compilePattern(String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException ex) {
            return null;
        }
    }

    private static List<String> splitNewlines(String input) {
        if (input == null || input.isEmpty())
            return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (String line : input.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

}
