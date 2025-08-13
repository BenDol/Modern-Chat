package com.modernchat.feature.command;

import lombok.Getter;

import java.awt.event.KeyEvent;

import static com.modernchat.feature.command.CommandsChatFeature.*;

public class ChatCommandLink implements ChatCommandHandler {

    @Getter
    private String link;

    public ChatCommandLink(String link) {
        this.link = link;
    }

    @Override
    public void startUp(CommandsChatFeature feature) {}

    @Override
    public void shutDown(CommandsChatFeature feature) {}

    @Override
    public void keyTyped(KeyEvent e) {
        throw new UnsupportedOperationException("CommandLink does not support keyTyped events");
    }

    @Override
    public void keyPressed(KeyEvent e) {
        throw new UnsupportedOperationException("CommandLink does not support keyPressed events");
    }

    @Override
    public void keyReleased(KeyEvent e) {
        throw new UnsupportedOperationException("CommandLink does not support keyReleased events");
    }
}
