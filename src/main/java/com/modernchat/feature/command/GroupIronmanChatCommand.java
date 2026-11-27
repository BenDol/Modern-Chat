package com.modernchat.feature.command;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.NotificationService;
import net.runelite.client.events.ChatboxInput;

import javax.inject.Inject;

/**
 * Holds back /gim and /group. The game only sends to Group Ironman group chat with /g, so
 * the game reads these as its "/" friends chat prefix, and Modern Chat must not rewrite a message the player
 * sends. The player is pointed at /g instead. Modern Chat's own input holds them back before
 * sending (see {@link CommandsChatFeature#holdBackGroupChatAlias}); this catches the game's chatbox.
 */
public class GroupIronmanChatCommand extends AbstractChatCommand {

    @Inject private NotificationService notificationService;

    @Override
    public void handleSubmit(String[] args, ChatboxInput ev) {
        if (ev == null || !feature.getConfig().featureCommands_GroupChatEnabled())
            return;

        ev.consume(); // RuneLite blanks the message so the game does not send it
        notifyHeldBack(false);
    }

    void notifyHeldBack(boolean keptInInput) {
        ChatMessageBuilder notice = new ChatMessageBuilder()
            .append("Not sent: use /g to talk in Group Ironman group chat.");
        if (keptInInput)
            notice.append(" Your message is still in the chat input.");
        notificationService.pushChatMessage(notice);
    }
}
