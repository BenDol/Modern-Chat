package com.modernchat.service;

import com.modernchat.ModernChatConfig;
import com.modernchat.service.filter.AreaMutePluginFilter;
import com.modernchat.service.filter.ChatFilterPluginFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrator service that applies message filters in order.
 */
@Slf4j
@Singleton
public class MessageFilterService implements ChatService {

    @Inject private EventBus eventBus;
    @Inject private ModernChatConfig config;

    @Inject private ChatFilterPluginFilter chatFilterPluginFilter;
    @Inject private AreaMutePluginFilter areaMutePluginFilter;

    private final CopyOnWriteArrayList<MessageFilter> filters = new CopyOnWriteArrayList<>();

    @Override
    public void startUp() {
        eventBus.register(this);

        chatFilterPluginFilter.startUp();
        areaMutePluginFilter.startUp();

        registerFilter(chatFilterPluginFilter);
        registerFilter(areaMutePluginFilter);
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);

        chatFilterPluginFilter.shutDown();
        areaMutePluginFilter.shutDown();

        filters.clear();
    }

    /**
     * Run all registered filters on the given message.
     *
     * @param message the ChatMessage event
     * @return the filtered text, or null if the message should be blocked
     */
    public @Nullable String filterMessage(ChatMessage message) {
        if (!config.filters_Enabled())
            return message.getMessage();

        String text = message.getMessage();
        for (MessageFilter filter : filters) {
            if (!filter.isEnabled())
                continue;

            text = filter.apply(message, text);
            if (text == null)
                return null;
        }
        return text;
    }

    public void registerFilter(MessageFilter filter) {
        filters.addIfAbsent(filter);
    }

    public void unregisterFilter(MessageFilter filter) {
        filters.remove(filter);
    }
}
