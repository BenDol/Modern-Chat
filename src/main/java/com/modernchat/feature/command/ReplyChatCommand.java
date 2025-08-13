package com.modernchat.feature.command;

import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;
import com.modernchat.service.PrivateChatService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReplyChatCommand extends AbstractChatCommand {

    @Override
    public void handleInput(String[] args) {
        CommandsChatConfig config = feature.getConfig();
        if (!config.featureCommands_ReplyEnabled())
            return;

        PrivateChatService privateChatService = feature.getPrivateChatService();
        privateChatService.replyToLastPm(/*body*/ null);
        feature.getPrivateChatService().clearChatInput();
    }
}
