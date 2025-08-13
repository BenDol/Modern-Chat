package com.modernchat.feature.command;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.event.KeyEvent;

@Slf4j
public abstract class AbstractChatCommand implements CommandsChatFeature.ChatCommandHandler {

    public CommandsChatFeature feature;

    @Inject protected Client client;
    @Inject protected EventBus eventBus;
    @Inject protected KeyManager keyManager;

    @Override
    public void startUp(CommandsChatFeature feature) {
        this.feature = feature;

        eventBus.register(this);
        keyManager.registerKeyListener(this);
    }

    @Override
    public void shutDown(CommandsChatFeature feature) {
        this.feature = null;

        eventBus.unregister(this);
        keyManager.unregisterKeyListener(this);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    protected String getTargetNameFromArgs(String[] args) {
        // Example: /w <name> - whisper to a player
        String arg = args[0].trim();
        if (arg.isEmpty()) {
            log.debug("Invalid target name for /w command");
            return null;
        }

        String target = Text.toJagexName(arg.trim());
        if (target.isEmpty()) {
            log.warn("Invalid target name for /w command");
            return null;
        }

        return target;
    }
}
