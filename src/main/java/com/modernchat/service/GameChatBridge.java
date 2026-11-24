package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatMode;
import com.modernchat.common.NotificationService;
import com.modernchat.event.ChatMessageSentEvent;
import com.modernchat.event.ChatPrivateMessageSentEvent;
import com.modernchat.event.SubmitHistoryEvent;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.util.ChatUtil;
import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Sends what the player submits in Modern Chat through the game's own chat scripts.
 * <p>
 * RuneLite only lets a script send a chat packet when the game itself runs it in
 * response to the player's key press; plugins would need the restricted
 * setCanSendPackets API. So Modern Chat never sends a message itself: on Enter it
 * registers a hand-off and lets the player's real Enter reach the game. Right before the
 * game's key script runs, the hand-off loads the submitted text and channel into the
 * game's input, and the game sends it.
 * <ul>
 *   <li>Channel messages go through chatdefault_onkey on the chatbox root. Modern Chat
 *   keeps that root live but off-screen (see {@link ChatUtil#setChatConcealed}) and
 *   neutralises every other key the script would handle, so it never types or sends
 *   anything the player did not submit through Modern Chat.</li>
 *   <li>Private messages go through the game's "Enter message to send to" prompt, which
 *   Modern Chat holds open (concealed) while the player types on a private tab.</li>
 * </ul>
 */
@Slf4j
@Singleton
public class GameChatBridge implements ChatService
{
    // Game scripts this bridge hooks. ScriptID has no constants for them.
    private static final int SCRIPT_CHATDEFAULT_ONKEY = 73;
    private static final int SCRIPT_MESLAYER_ONKEY = 112;
    // Jagex key code the key scripts receive for Enter (^key_return)
    private static final int KEY_RETURN = 84;

    // VarClientID.CHAT_VIEW values that make chatdefault_onkey pick the channel itself
    // (friends chat, clan and group tabs of the game chatbox)
    private static final int CHAT_VIEW_ALL = 0;
    private static final int CHAT_VIEW_FIRST_CHANNEL = 4;
    private static final int CHAT_VIEW_LAST_CHANNEL = 6;

    // How long a hand-off may wait for the game to process the Enter press
    private static final long HANDOFF_TIMEOUT_MS = 1000;
    private static final int UNSET = Integer.MIN_VALUE;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private EventBus eventBus;
    @Inject private NotificationService notificationService;
    @Inject private Provider<CommandsChatFeature> commandsChatFeature;

    // Submitted on the AWT thread, claimed on the client thread by the key script hooks
    private final AtomicReference<Handoff> pending = new AtomicReference<>();

    // Client thread only: the hand-off the running key script is sending, and the game
    // state changed for it that must be put back once the script finishes
    private Handoff active;
    private int restoreChatboxMode = UNSET;
    private int appliedChatboxMode = UNSET;
    private int restoreChatView = UNSET;
    private Widget suppressedChatDisplay;

    // Private message prompt the player is composing in (client thread), and the target it
    // is confirmed open for (read on the AWT thread when the player presses Enter)
    private volatile String readyPromptTarget;
    private String failedPromptTarget;

    @Override
    public void startUp() {
        // The chatbox flags are static and outlive a plugin restart; nothing is concealed
        // or held open until the chat redesign starts again
        ChatUtil.LEGACY_CHAT_CONCEALED.set(false);
        ChatUtil.OWN_PRIVATE_PROMPT_TARGET.set(null);
        readyPromptTarget = null;
        eventBus.register(this);
    }

    @Override
    public void shutDown() {
        eventBus.unregister(this);
        pending.set(null);
        clientThread.invoke(() -> {
            restoreScriptState();
            active = null;
            syncPrivatePrompt(null);
            failedPromptTarget = null;
        });
    }

    /**
     * Hands a channel message to the game. The caller must let the Enter key event that
     * submitted it reach the game. {@code onFailed} receives the text back on the client
     * thread if the game never processes that Enter press.
     */
    public void handOffChannelMessage(String text, ChatMode mode, Consumer<String> onFailed) {
        register(new Handoff(text, mode, null, onFailed));
    }

    /**
     * Hands a private message to the game's private message prompt, which must be open for
     * the target (see {@link #isPrivatePromptReady}). The caller must let the Enter key event
     * that submitted it reach the game.
     */
    public void handOffPrivateMessage(String text, String target, Consumer<String> onFailed) {
        register(new Handoff(text, ChatMode.PRIVATE, target, onFailed));
    }

    /**
     * Whether a private message to the target can be handed off right now.
     */
    public boolean isPrivatePromptReady(String target) {
        String ready = readyPromptTarget;
        return ready != null && target != null && isSamePlayer(ready, target);
    }

    /**
     * Opens, keeps or closes the game's private message prompt so it matches the private
     * tab the player is composing in. Pass null when no private message is being composed.
     * MUST be on client thread, outside any script.
     */
    public void syncPrivatePrompt(@Nullable String target) {
        String own = ChatUtil.OWN_PRIVATE_PROMPT_TARGET.get();
        if (own != null && !ClientUtil.isPrivatePromptOpenFor(client, own)) {
            // Closed by a send, or replaced by a game prompt or dialog
            releaseOwnPrompt(own);
            own = null;
        }

        if (target == null || (failedPromptTarget != null && !isSamePlayer(failedPromptTarget, target)))
            failedPromptTarget = null;

        if (own == null ? target == null : target != null && isSamePlayer(own, target))
            return;

        // Never pull the prompt away from a message that is on its way out
        if (active != null || pending.get() != null)
            return;

        if (own != null)
            closeOwnPrompt(own);
        if (target != null && failedPromptTarget == null)
            openOwnPrompt(target);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired e) {
        int id = e.getScriptId();
        if (id != SCRIPT_CHATDEFAULT_ONKEY && id != SCRIPT_MESLAYER_ONKEY)
            return;

        // Backstop in case a previous run's post hook was missed
        finishScript();

        ScriptEvent event = e.getScriptEvent();
        boolean enter = event != null && event.getTypedKeyCode() == KEY_RETURN;
        if (id == SCRIPT_CHATDEFAULT_ONKEY) {
            beforeChatKey(enter);
        } else if (enter) {
            beforePrivatePromptEnter();
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        int id = e.getScriptId();
        if (id != SCRIPT_CHATDEFAULT_ONKEY && id != SCRIPT_MESLAYER_ONKEY)
            return;

        finishScript();
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent e) {
        Handoff handoff = active;
        if (handoff == null)
            return;

        switch (e.getEventName()) {
            case "chatboxInput": {
                if (handoff.isPrivate())
                    return;
                // Same stack layout RuneLite's ChatInputManager reads
                Object[] objectStack = client.getObjectStack();
                int[] intStack = client.getIntStack();
                int objectStackSize = client.getObjectStackSize();
                int intStackSize = client.getIntStackSize();
                String message = (String) objectStack[objectStackSize - 1];
                int chatType = intStack[intStackSize - 2];
                int clanTarget = intStack[intStackSize - 1];
                handoff.delivered = true;
                eventBus.post(new ChatMessageSentEvent(message, chatType, clanTarget));
                break;
            }
            case "privateMessage":
                if (!handoff.isPrivate())
                    return;
                handoff.delivered = true;
                eventBus.post(new SubmitHistoryEvent(handoff.text));
                eventBus.post(new ChatPrivateMessageSentEvent(handoff.text, handoff.target));
                break;
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        GameState state = e.getGameState();
        if (state != GameState.LOGIN_SCREEN && state != GameState.HOPPING)
            return;

        // The chatbox goes away with the session, and the held prompt with it
        ChatUtil.OWN_PRIVATE_PROMPT_TARGET.set(null);
        readyPromptTarget = null;
        failedPromptTarget = null;
    }

    @Subscribe
    public void onClientTick(ClientTick e) {
        // Key scripts finish within the tick that runs them
        finishScript();

        Handoff handoff = pending.get();
        if (handoff != null && System.currentTimeMillis() > handoff.deadline && pending.compareAndSet(handoff, null))
            fail(handoff);
    }

    /**
     * Puts back what was changed for the key script that just ran. A hand-off it was
     * carrying but did not send (the game's script bailed out early) goes back to the player.
     */
    private void finishScript() {
        restoreScriptState();
        Handoff handoff = active;
        active = null;
        if (handoff != null && !handoff.delivered)
            fail(handoff);
    }

    private void register(Handoff handoff) {
        Handoff previous = pending.getAndSet(handoff);
        if (previous != null)
            clientThread.invoke(() -> fail(previous));
    }

    /**
     * chatdefault_onkey is about to run for a key press.
     */
    private void beforeChatKey(boolean enter) {
        boolean concealed = ChatUtil.LEGACY_CHAT_CONCEALED.get();
        if (enter) {
            Handoff handoff = claim(false);
            if (handoff != null) {
                active = handoff;
                prepareChannelSend(handoff.text, handoff.mode);
                return;
            }

            if (!concealed) {
                // The game chatbox is in use: still honour Modern Chat's group chat aliases
                String body = resolveGroupChatAlias(client.getVarcStrValue(VarClientID.CHATINPUT));
                if (body != null)
                    prepareChannelSend(body, ChatMode.CLAN_GIM);
                return;
            }
        }

        // While Modern Chat owns the chat, the game must not type, reply or send anything
        // the player did not submit through Modern Chat
        if (concealed)
            suppressChatKey();
    }

    /**
     * meslayer_onkey is about to run for Enter.
     */
    private void beforePrivatePromptEnter() {
        String own = ChatUtil.OWN_PRIVATE_PROMPT_TARGET.get();
        if (own == null || !ClientUtil.isPrivatePromptOpenFor(client, own))
            return; // A prompt the game opened itself: leave it alone

        Handoff handoff = claim(true);
        if (handoff != null && isSamePlayer(handoff.target, own)) {
            active = handoff;
            client.setVarcStrValue(VarClientID.MESLAYERINPUT, handoff.text);
            return;
        }

        if (handoff != null)
            fail(handoff);
        // Nothing reaches the server that the player did not submit
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, "");
    }

    private void prepareChannelSend(String text, ChatMode mode) {
        String body = text;
        ChatMode channel = mode;
        String aliasBody = resolveGroupChatAlias(text);
        if (aliasBody != null) {
            body = aliasBody;
            channel = ChatMode.CLAN_GIM;
        }

        client.setVarcStrValue(VarClientID.CHATINPUT, body);

        // An unprefixed message goes to the channel of the game's chatbox mode, unless
        // the game chatbox is showing a channel tab. Point both at the chosen channel.
        int chatboxMode = channel.getChatboxMode();
        if (chatboxMode < 0)
            return;

        int currentMode = client.getVarcIntValue(VarClientID.CHATBOX_MODE);
        if (currentMode != chatboxMode) {
            restoreChatboxMode = currentMode;
            appliedChatboxMode = chatboxMode;
            client.setVarcIntValue(VarClientID.CHATBOX_MODE, chatboxMode);
        }

        int view = client.getVarcIntValue(VarClientID.CHAT_VIEW);
        if (view >= CHAT_VIEW_FIRST_CHANNEL && view <= CHAT_VIEW_LAST_CHANNEL) {
            restoreChatView = view;
            client.setVarcIntValue(VarClientID.CHAT_VIEW, CHAT_VIEW_ALL);
        }
    }

    /**
     * Makes chatdefault_onkey return straight away: it bails out while the chat display
     * is hidden. Restored by {@link #restoreScriptState} once the script finishes.
     */
    private void suppressChatKey() {
        Widget chatDisplay = client.getWidget(InterfaceID.Chatbox.CHATDISPLAY);
        if (chatDisplay == null || chatDisplay.isSelfHidden())
            return;
        chatDisplay.setHidden(true);
        suppressedChatDisplay = chatDisplay;
    }

    private void restoreScriptState() {
        if (suppressedChatDisplay != null) {
            suppressedChatDisplay.setHidden(false);
            suppressedChatDisplay = null;
        }

        if (restoreChatboxMode != UNSET) {
            // A "/@" prefix makes the game set the mode itself; keep the player's choice
            if (client.getVarcIntValue(VarClientID.CHATBOX_MODE) == appliedChatboxMode)
                client.setVarcIntValue(VarClientID.CHATBOX_MODE, restoreChatboxMode);
            restoreChatboxMode = UNSET;
            appliedChatboxMode = UNSET;
        }

        if (restoreChatView != UNSET) {
            if (client.getVarcIntValue(VarClientID.CHAT_VIEW) == CHAT_VIEW_ALL)
                client.setVarcIntValue(VarClientID.CHAT_VIEW, restoreChatView);
            restoreChatView = UNSET;
        }
    }

    private @Nullable Handoff claim(boolean privateMessage) {
        Handoff handoff = pending.get();
        if (handoff == null || handoff.isPrivate() != privateMessage)
            return null;
        return pending.compareAndSet(handoff, null) ? handoff : null;
    }

    private void fail(Handoff handoff) {
        log.debug("Chat hand-off was not processed by the game: private={}", handoff.isPrivate());
        notificationService.pushChatMessage(new ChatMessageBuilder()
            .append("Your message could not be sent. It is back in the chat input."));
        // Deferred: this can run inside a key script's hooks, and restoring the input
        // can show the chat, which may run scripts of its own
        clientThread.invokeLater(() -> handoff.onFailed.accept(handoff.text));
    }

    private void openOwnPrompt(String target) {
        if (client.getGameState() != GameState.LOGGED_IN)
            return;
        // Conditions the game's prompt script would refuse with a chat message; retried
        // on a later tick once they clear
        if (client.getVarbitValue(VarbitID.HAS_DISPLAYNAME_TRANSMITTER) != 1
            || client.getVarcIntValue(VarClientID.SNAPSHOT_OPEN) == 1
            || client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0)
            return;

        // Claimed before the script runs so the hooks it triggers treat the prompt as ours
        ChatUtil.OWN_PRIVATE_PROMPT_TARGET.set(target);
        try {
            client.runScript(ScriptID.OPEN_PRIVATE_MESSAGE_INTERFACE, target);
        } catch (RuntimeException ex) {
            log.warn("Failed to open the private message prompt", ex);
        }

        if (ClientUtil.isPrivatePromptOpenFor(client, target)) {
            readyPromptTarget = target;
        } else {
            ChatUtil.OWN_PRIVATE_PROMPT_TARGET.set(null);
            failedPromptTarget = target; // don't retry every tick
            log.debug("Private message prompt did not open");
        }
    }

    private void closeOwnPrompt(String target) {
        if (client.getGameState() == GameState.LOGGED_IN && ClientUtil.isPrivatePromptOpenFor(client, target)) {
            // Clear the input and restore the chatbox key handling; no dialog to resume
            client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 1, 1, 0);
        }
        releaseOwnPrompt(target);
    }

    private void releaseOwnPrompt(String target) {
        ChatUtil.OWN_PRIVATE_PROMPT_TARGET.set(null);
        readyPromptTarget = null;

        // The game leaves the target set after its prompt closes; clear ours so other
        // checks don't read a private message as still being composed
        if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != ClientUtil.MESLAYER_MODE_PRIVATE_MESSAGE) {
            String current = client.getVarcStrValue(VarClientID.FRIENDTOMESSAGE);
            if (current != null && isSamePlayer(current, target))
                client.setVarcStrValue(VarClientID.FRIENDTOMESSAGE, "");
        }
    }

    private @Nullable String resolveGroupChatAlias(@Nullable String text) {
        if (text == null || text.isEmpty())
            return null;
        return commandsChatFeature.get().resolveGroupChatAlias(text);
    }

    private static boolean isSamePlayer(String a, String b) {
        return Text.toJagexName(a).equalsIgnoreCase(Text.toJagexName(b));
    }

    private static final class Handoff
    {
        final String text;
        final ChatMode mode;
        @Nullable final String target;
        final Consumer<String> onFailed;
        final long deadline;
        // Set on the client thread once the game's send script has taken the message
        boolean delivered;

        Handoff(String text, ChatMode mode, @Nullable String target, Consumer<String> onFailed) {
            this.text = text;
            this.mode = mode;
            this.target = target;
            this.onFailed = onFailed;
            this.deadline = System.currentTimeMillis() + HANDOFF_TIMEOUT_MS;
        }

        boolean isPrivate() {
            return target != null;
        }
    }
}
