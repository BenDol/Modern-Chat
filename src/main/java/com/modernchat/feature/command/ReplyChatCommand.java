package com.modernchat.feature.command;

import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;

public class ReplyChatCommand extends AbstractChatCommand {

    @Override
    public void handleInput(String[] args) {
        CommandsChatConfig config = feature.getConfig();
        if (!config.featureCommands_ReplyEnabled())
            return;

        feature.replyToLastPm(/*body*/ null);
        feature.clearChatInput();
    }
}
