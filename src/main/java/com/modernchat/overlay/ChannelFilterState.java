package com.modernchat.overlay;

import com.modernchat.common.ChatMode;
import com.modernchat.draw.ChannelFilterType;
import lombok.Setter;
import net.runelite.api.ChatMessageType;

import javax.annotation.Nullable;
import javax.inject.Singleton;

@Singleton
public class ChannelFilterState {

    @Setter
    private ChatOverlayConfig config;

    @Setter
    @Nullable
    private ChatMode currentChatMode;

    public boolean isEnabled(ChannelFilterType type) {
        if (config == null) {
            return true; // Default to enabled if no config
        }
        int flags = config.getChannelFilterFlags(currentChatMode);
        // If the bit is NOT set, the filter is enabled (shown)
        return !type.isDisabledIn(flags);
    }

    public void setEnabled(ChannelFilterType type, boolean enabled) {
        if (config == null) {
            return;
        }
        int flags = config.getChannelFilterFlags(currentChatMode);
        // enabled=true means show (bit cleared), enabled=false means hide (bit set)
        flags = type.setIn(flags, !enabled);
        config.setChannelFilterFlags(currentChatMode, flags);
    }

    public void toggle(ChannelFilterType type) {
        setEnabled(type, !isEnabled(type));
    }

    public boolean shouldShowMessage(ChatMessageType messageType) {
        ChannelFilterType filterType = mapMessageTypeToFilter(messageType);
        if (filterType == null) {
            return true; // System/game messages always shown
        }
        return isEnabled(filterType);
    }

    /**
     * Check if a message type should be shown for a specific chat mode's filters.
     * This is used by PeekOverlay to respect the source tab's channel filters.
     */
    public boolean shouldShowMessage(ChatMessageType messageType, @Nullable ChatMode chatMode) {
        if (config == null) {
            return true;
        }
        ChannelFilterType filterType = mapMessageTypeToFilter(messageType);
        if (filterType == null) {
            return true;
        }
        int flags = config.getChannelFilterFlags(chatMode);
        return !filterType.isDisabledIn(flags);
    }

    public ChannelFilterType mapMessageTypeToFilter(ChatMessageType messageType) {
        switch (messageType) {
            case PUBLICCHAT:
            case MODCHAT:
                return ChannelFilterType.PUBLIC;

            case AUTOTYPER:
                return ChannelFilterType.AUTO_TYPER;

            case PRIVATECHAT:
            case PRIVATECHATOUT:
            case MODPRIVATECHAT:
            case FRIENDNOTIFICATION:
                return ChannelFilterType.PRIVATE;

            case FRIENDSCHAT:
            case FRIENDSCHATNOTIFICATION:
                return ChannelFilterType.FRIENDS_CHAT;

            case CLAN_CHAT:
            case CLAN_MESSAGE:
            case CLAN_GUEST_CHAT:
            case CLAN_GUEST_MESSAGE:
            case CLAN_GIM_CHAT:
            case CLAN_GIM_MESSAGE:
            case CLAN_GIM_FORM_GROUP:
            case CLAN_GIM_GROUP_WITH:
                return ChannelFilterType.CLAN;

            case TRADE_SENT:
            case TRADEREQ:
                return ChannelFilterType.TRADE;

            case GAMEMESSAGE:
            case ITEM_EXAMINE:
            case NPC_EXAMINE:
            case OBJECT_EXAMINE:
            case DIALOG:
            case NPC_SAY:
            case PLAYERRELATED:
            case ENGINE:
            case MESBOX:
            case SPAM:
                return ChannelFilterType.GAME;

            case BROADCAST:
            case CONSOLE:
            case WELCOME:
            default:
                return ChannelFilterType.SYSTEM;
        }
    }

    public boolean hasActiveFilters() {
        if (config == null) {
            return false;
        }
        // If any bit is set, there are active filters (something is disabled)
        return config.getChannelFilterFlags(currentChatMode) != 0;
    }
}
