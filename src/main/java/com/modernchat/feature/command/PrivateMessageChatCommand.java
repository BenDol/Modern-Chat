package com.modernchat.feature.command;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.MessageService;
import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;
import com.modernchat.service.PrivateChatService;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatboxInput;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;

@Slf4j
public class PrivateMessageChatCommand extends AbstractChatCommand {

    @Inject private MessageService messageService;

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

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().equals(ModernChatConfig.GROUP))
            return;

        String key = e.getKey();
        if (key == null)
            return;

        if (key.endsWith("PrivateMessageEnabled")) {
            if (Boolean.parseBoolean(e.getNewValue()) && client.getVarpValue(VarPlayerID.OPTION_PM) == 0) {
                messageService.showWarningMessageBox("Private Message Command",
                    "We recommend using \"Split friends private chat\" setting in the OSRS settings\n" +
                    "to see responses above the chat window when using the /pm command.");
            }
        }
    }
}
