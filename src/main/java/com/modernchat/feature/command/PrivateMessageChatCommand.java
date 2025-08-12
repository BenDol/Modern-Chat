package com.modernchat.feature.command;

import com.modernchat.feature.command.CommandsChatFeature.CommandsChatConfig;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ChatboxInput;

import java.awt.event.KeyEvent;

@Slf4j
public class PrivateMessageChatCommand extends AbstractChatCommand {

    @Override
    public void shutDown(CommandsChatFeature feature) {
        feature.cancelPrivateMessageCompose();

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

        feature.setPmTarget(target);
        feature.clearChatInput();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        super.keyPressed(e);
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            feature.cancelPrivateMessageCompose();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            String lastInputText = ClientUtil.getSystemInputText(client);
            if (StringUtil.isNullOrEmpty(lastInputText)) {
                feature.cancelPrivateMessageCompose();
            }
        }
    }
}
