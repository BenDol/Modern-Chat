package com.modernchat.feature.command;

import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;
import com.modernchat.service.PrivateChatService;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ChatboxInput;

import java.awt.event.KeyEvent;

@Slf4j
public class PrivateMessageChatCommand extends AbstractChatCommand {

    @Override
    public void startUp(CommandsChatFeature feature) {
        super.startUp(feature);
    }

    @Override
    public void shutDown(CommandsChatFeature feature) {
        feature.getPrivateChatService().cancelPrivateMessage();

        super.shutDown(feature);
    }

    @Override
    public void handleInputOrSubmit(String[] args, ChatboxInput ev) {
        CommandsChatConfig config = feature.getConfig();
        if (!config.featureCommands_PrivateMessageEnabled())
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
        privateChatService.setPmTarget(target);
        privateChatService.clearChatInput();
    }
}
