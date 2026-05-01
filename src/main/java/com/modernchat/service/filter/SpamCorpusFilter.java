package com.modernchat.service.filter;

import com.modernchat.ModernChatConfig;
import com.modernchat.service.MessageFilter;
import com.modernchat.service.SpamFilterService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Built-in filter that consults the user-managed spam corpus from {@link SpamFilterService}.
 * Messages whose raw text matches a "Mark spam" entry are blocked from rendering, unless the
 * same text is also present in the ham corpus (acting as an explicit whitelist override).
 *
 * Match is performed against the original {@link ChatMessage#getMessage()} so that earlier
 * filter stages (e.g. censoring) cannot mask a previously-marked message.
 */
@Slf4j
@Singleton
public class SpamCorpusFilter implements MessageFilter {

    @Inject private SpamFilterService spamFilterService;
    @Inject private ModernChatConfig config;

    @Override
    public boolean isEnabled() {
        return config.filters_SpamCorpusEnabled();
    }

    @Override
    public @Nullable String apply(ChatMessage message, String currentText) {
        String rawText = message.getMessage();
        if (rawText == null || rawText.isEmpty())
            return currentText;

        if (spamFilterService.isMarkedHam(rawText))
            return currentText;

        if (spamFilterService.isMarkedSpam(rawText)) {
            log.debug("Blocking message marked as spam: {}", rawText);
            return null;
        }
        return currentText;
    }
}
