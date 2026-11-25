package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.NotificationService;
import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Predicate;

@Slf4j
@Singleton
public class MessageService implements ChatService
{
    @Inject private FilterService filterService;
    @Inject private GameChatBridge gameChatBridge;
    @Inject private NotificationService notificationService;

    @Override
    public void startUp() {
    }

    @Override
    public void shutDown() {
    }

    /**
     * Outcome of {@link #submit}: what the caller does with the Enter key event and the input.
     */
    public enum SubmitResult
    {
        /** Handed to the game: let the Enter key event reach the game, clear the input. */
        PASS_TO_GAME,
        /** Dealt with here (empty or filtered): consume, clear. */
        HANDLED,
        /** Not sent and worth retrying: consume and keep the text in the input. */
        RETAINED
    }

    /**
     * Submits what the player typed when they press Enter. Modern Chat never sends a chat
     * message itself: RuneLite only lets the game's own chat scripts send, as a direct
     * result of the player's key press, so a message is handed to {@link GameChatBridge}
     * and goes out when the same Enter press reaches the game.
     * MUST be called from the key listener handling that Enter press.
     *
     * @param onFailed receives the text back on the client thread if the game never
     *                 processes the Enter press, so it can be put back in the input; it
     *                 answers whether it did
     */
    public SubmitResult submit(String text, ChatMode mode, @Nullable String targetName, Predicate<String> onFailed) {
        if (StringUtil.isNullOrEmpty(text))
            return SubmitResult.HANDLED;

        if (filterService.isFiltered(text))
            return SubmitResult.HANDLED;

        final String trimmed = text.trim();
        final boolean isCommand = trimmed.startsWith("::");
        final boolean isPrivate = !isCommand && mode == ChatMode.PRIVATE;
        if (isPrivate) {
            if (StringUtil.isNullOrEmpty(targetName)) {
                log.warn("Attempted to send private chat message to no target");
                return SubmitResult.RETAINED;
            }
            if (!gameChatBridge.isPrivatePromptReady(targetName)) {
                log.debug("Private message prompt is not open, keeping the message");
                notificationService.pushChatMessage(new ChatMessageBuilder()
                    .append("Your private message could not be sent right now. It is still in the chat input."));
                return SubmitResult.RETAINED;
            }
        }

        if (isCommand) {
            // The game's chat script runs "::" commands exactly as the vanilla chatbox does:
            // its own (::toggleroof, ::wiki), RuneLite's (CommandExecuted) and the server's
            gameChatBridge.handOffCommand(text, onFailed);
        } else if (isPrivate) {
            gameChatBridge.handOffPrivateMessage(text, targetName, onFailed);
        } else {
            gameChatBridge.handOffChannelMessage(text, mode, onFailed);
        }

        return SubmitResult.PASS_TO_GAME;
    }
}
