package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.NotificationService;
import com.modernchat.common.TutorialState;
import com.modernchat.event.ChatMessageSentEvent;
import com.modernchat.event.ChatPrivateMessageSentEvent;
import com.modernchat.event.ChatToggleEvent;
import com.modernchat.event.FeatureStoppedEvent;
import com.modernchat.event.TabChangeEvent;
import com.modernchat.feature.ChatRedesignFeature;
import com.modernchat.overlay.ChatOverlay;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Singleton
public class TutorialService implements ChatService
{
    @Inject private EventBus eventBus;
    @Inject private NotificationService notificationService;
    @Inject private ChatOverlay chatOverlay;

    @Getter private TutorialState state = TutorialState.NOT_STARTED;

    @Override
    public void startUp() {
        eventBus.register(this);

        if (state == TutorialState.NOT_STARTED) {
            log.debug("Tutorial awaiting toggle event received, starting tutorial");
            notificationService.pushChatMessage(new ChatMessageBuilder()
                .append(
                    "This will guide you through some new chat features. " +
                    "To start, please make sure your chat is visible, press ")
                .append(Color.ORANGE, "Enter").
                append(" to open the chat."));

            state = TutorialState.AWAITING_TOGGLE;
        } else {
            log.warn("Unexpected tutorial state: {}", state);
        }
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);

        state = TutorialState.NOT_STARTED;
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!chatOverlay.isEnabled())
            shutDown();
    }

    @Subscribe
    public void onChatToggleEvent(ChatToggleEvent e) {
        if (e.isHidden())
            return; // Ignore if chat is hidden, tutorial requires chat to be visible

        if (state == TutorialState.AWAITING_TOGGLE) {
            log.debug("Chat toggle event received, proceeding with tutorial");
            if (chatOverlay.getTabOrder().size() > 1) {
                notificationService.pushChatMessage(new ChatMessageBuilder()
                    .append("Try selecting a ")
                    .append(Color.ORANGE, "chat tab at the top")
                    .append(" to switch to chat mode channels."));

                state = TutorialState.AWAITING_TAB_SWITCH;
            } else {
                notificationService.pushChatMessage(new ChatMessageBuilder()
                    .append("At the ")
                    .append(Color.ORANGE, "top of the chat")
                    .append(" you will see chat mode tabs open when you join them. Now lets try ")
                    .append(Color.ORANGE, "sending a message in chat"));
            }
        } else {
            log.warn("Unexpected tutorial state: {}", state);
        }
    }

    @Subscribe
    public void onTabChangeEvent(TabChangeEvent e) {
        if (state == TutorialState.AWAITING_TAB_SWITCH) {
            log.debug("Chat tab switched, proceeding with tutorial");
            new Timer().schedule(new TimerTask()
            {
                @Override
                public void run() {
                    notificationService.pushChatMessage(new ChatMessageBuilder()
                        .append("Nice! Now try ")
                        .append(Color.ORANGE, "sending a message")
                        .append(" in a chat mode channel by typing into the input box and pressing ")
                        .append(Color.ORANGE, "Enter"));

                    state = TutorialState.AWAITING_CHAT_SEND;
                }
            }, 400);
        } else if (state == TutorialState.AWAITING_PRIVATE_CHAT && e.getNewTab().isPrivate()) {
            log.debug("Private chat sent, completing tutorial");
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    notificationService.pushChatMessage(new ChatMessageBuilder()
                        .append("Congratulations! You have completed the Modern Chat tutorial. " +
                            "Check out the settings to customize your chat experience even more! Some features to note are: ")
                        .append(Color.ORANGE, "resizeable chat").append(", ")
                        .append(Color.ORANGE, "moveable tabs").append(", ")
                        .append(Color.ORANGE, "input selection").append(", ")
                        .append(Color.ORANGE, "input navigation").append(", ")
                        .append(Color.ORANGE, "chat history (shift up/down)")
                        .append(" and much more!"));

                    state = TutorialState.COMPLETED;
                    shutDown();
                }
            }, 600);
        } else {
            log.warn("Unexpected tutorial state: {}", state);
        }
    }

    @Subscribe
    public void onChatMessageSentEvent(ChatMessageSentEvent e) {
        if (state == TutorialState.AWAITING_CHAT_SEND) {
            log.debug("Chat message sent, proceeding with tutorial");
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    notificationService.pushChatMessage(new ChatMessageBuilder()
                        .append("Great! Now try ")
                        .append(Color.ORANGE, "opening a private channel")
                        .append(" to message another player, by ")
                        .append(Color.ORANGE, "right-clicking")
                        .append(" on their name in chat, name in friends list or character in the world, you will see the ")
                        .append(Color.ORANGE, "Chat with")
                        .append(" or ")
                        .append(Color.ORANGE, "Message")
                        .append(" option appear."));

                    state = TutorialState.AWAITING_PRIVATE_CHAT;
                }
            }, 600);
        } else {
            log.warn("Unexpected tutorial state: {}", state);
        }
    }

    @Subscribe
    public void onChatPrivateMessageSentEvent(ChatPrivateMessageSentEvent e) {

    }

    @Subscribe
    public void onFeatureStoppedEvent(FeatureStoppedEvent e) {
        if (!e.getFeature().getClass().equals(ChatRedesignFeature.class))
            return;

        log.debug("Feature stopped, resetting tutorial state");
        state = TutorialState.NOT_STARTED;
        shutDown();
        notificationService.pushChatMessage(new ChatMessageBuilder()
            .append("Tutorial has ended."));
    }
}
