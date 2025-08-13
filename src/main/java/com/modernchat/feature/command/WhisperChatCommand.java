package com.modernchat.feature.command;

import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;
import com.modernchat.service.PrivateChatService;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ChatboxInput;

@Slf4j
public class WhisperChatCommand extends AbstractChatCommand {

    @Override
    public void handleInputOrSubmit(String[] args, ChatboxInput ev) {
        CommandsChatConfig config = feature.getConfig();
        if (!config.featureCommands_WhisperEnabled())
            return;

        if (args == null || (ev == null && args.length < 2) ||
            (ev != null && args.length < 1)) {
            return;
        }

        String target = getTargetNameFromArgs(args);
        if (StringUtil.isNullOrEmpty(target)) {
            return;
        }

        if (ev != null) {
            ev.consume(); // Prevent default chat submission
        }

        PrivateChatService privateChatService = feature.getPrivateChatService();
        privateChatService.replyTo(target);
        privateChatService.clearChatInput();
    }
}
