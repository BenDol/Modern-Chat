package com.modernchat.service.filter;

import com.modernchat.ModernChatConfig;
import com.modernchat.service.MessageFilter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.VarbitID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.Set;

/**
 * Honours OSRS's vanilla chat-tab filter dropdown. Modern Chat overlays its own UI, which
 * means vanilla's per-tab "On / Filtered / Off" selector no longer takes effect — flavor-text
 * spam (e.g. "You catch a shrimp.") still renders even when the player has set the Game tab
 * to Filtered. This filter reads the relevant varbits and reproduces the vanilla behavior.
 *
 * Game tab varbit ({@link VarbitID#GAME_FILTER}):
 *   0 = On       — show everything
 *   1 = Filtered — hide {@link ChatMessageType#SPAM} (flavor text)
 *   2 = Off      — hide all game-channel message types
 */
@Slf4j
@Singleton
public class VanillaChatFilter implements MessageFilter {

    private static final Set<ChatMessageType> GAME_TYPES = EnumSet.of(
        ChatMessageType.GAMEMESSAGE,
        ChatMessageType.ENGINE,
        ChatMessageType.ITEM_EXAMINE,
        ChatMessageType.NPC_EXAMINE,
        ChatMessageType.OBJECT_EXAMINE,
        ChatMessageType.SPAM,
        ChatMessageType.NPC_SAY
    );

    @Inject private Client client;
    @Inject private ModernChatConfig config;

    @Override
    public boolean isEnabled() {
        return config.filters_VanillaTabFilterEnabled();
    }

    @Override
    public @Nullable String apply(ChatMessage message, String currentText) {
        ChatMessageType type = message.getType();

        int gameMode = client.getVarbitValue(VarbitID.GAME_FILTER);
        if (gameMode == 2 && GAME_TYPES.contains(type)) {
            return null;
        }
        if (gameMode >= 1 && type == ChatMessageType.SPAM) {
            return null;
        }

        return currentText;
    }
}
