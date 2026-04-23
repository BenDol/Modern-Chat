package com.modernchat.feature.command;

import com.modernchat.common.ChatMode;
import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;
import com.modernchat.service.MessageService;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ChatboxInput;
import net.runelite.client.util.Text;

import javax.inject.Inject;

@Slf4j
public class GroupIronmanChatCommand extends AbstractChatCommand {

    @Inject private MessageService messageService;

    @Override
    public void handleSubmit(String[] args, ChatboxInput ev) {
        CommandsChatConfig config = feature.getConfig();
        if (!config.featureCommands_GroupChatEnabled())
            return;

        if (ev == null)
            return;

        String raw = ev.getValue();
        if (StringUtil.isNullOrEmpty(raw))
            return;

        String stripped = Text.removeTags(raw).trim();
        int space = stripped.indexOf(' ');
        if (space < 0)
            return;

        String text = stripped.substring(space + 1).trim();
        if (text.isEmpty())
            return;

        ev.consume();
        messageService.sendMessage(text, ChatMode.CLAN_GIM, null);
    }
}
