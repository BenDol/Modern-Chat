package com.modernchat.service;

import net.runelite.api.events.ChatMessage;

import javax.annotation.Nullable;

/**
 * Interface for message filters.
 * Filters are applied in order; each receives the possibly-modified text from prior filters.
 */
public interface MessageFilter {

    /**
     * @param message the original ChatMessage (for metadata: type, sender, message node ID)
     * @param currentText the current message text (possibly modified by prior filters)
     * @return filtered text, or {@code null} to block the message entirely
     */
    @Nullable
    String apply(ChatMessage message, String currentText);

    /**
     * @return true if this filter is enabled and should be applied
     */
    default boolean isEnabled() {
        return true;
    }
}
