package com.modernchat.feature.command;

import com.modernchat.ModernChatConfig;
import com.modernchat.feature.AbstractChatFeature;
import com.modernchat.feature.ChatFeatureConfig;
import com.modernchat.util.ClientUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatboxInput;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.Text;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class CommandsChatFeature extends AbstractChatFeature<CommandsChatFeature.CommandsChatConfig>
{
    @Override
    public String getConfigGroup() {
        return "featureCommands";
    }

    public interface CommandsChatConfig extends ChatFeatureConfig
    {
        boolean featureCommands_Enabled();
        boolean featureCommands_ReplyEnabled();
        boolean featureCommands_WhisperEnabled();
        boolean featureCommands_PrivateMessageEnabled();
    }

    public interface ChatCommandHandler extends KeyListener
    {
        void startUp(CommandsChatFeature feature);
        void shutDown(CommandsChatFeature feature);

        default void handleInput(String[] args) {}
        default void handleSubmit(String[] args, ChatboxInput ev) {}
        default void handleInputOrSubmit(String[] args, ChatboxInput ev) {}
    }

    @Inject private ReplyChatCommand replyChatCommand;
    @Inject private WhisperChatCommand whisperChatCommand;
    @Inject private PrivateMessageChatCommand privateMessageChatCommand;

    @Inject @Getter private Client client;
    @Inject @Getter private ClientThread clientThread;

    // Track last inbound PM sender (sanitized RuneScape name)
    private volatile String lastPmFrom;
    @Getter
    private String lastChatInput;

    // "command" -> handler(args)
    private final Map<String, ChatCommandHandler> commandHandlers = new HashMap<>();

    // Queue to execute scripts after the frame (avoids reentrancy)
    @Getter @Setter
    private String pmTarget = null;
    private String pendingPmTarget = null;
    private String pendingPrefill = null;

    @Inject
    public CommandsChatFeature(ModernChatConfig rootConfig, EventBus eventBus)
    {
        super(rootConfig, eventBus);
    }

    @Override
    protected CommandsChatConfig extractConfig(ModernChatConfig cfg)
    {
        // Map root config to feature config
        return new CommandsChatConfig()
        {
            @Override public boolean featureCommands_Enabled()
            {
                return cfg.featureCommands_Enabled();
            }

            @Override public boolean featureCommands_ReplyEnabled()
            {
                return cfg.featureCommands_ReplyEnabled();
            }

            @Override public boolean featureCommands_WhisperEnabled() {
                return cfg.featureCommands_WhisperEnabled();
            }

            @Override public boolean featureCommands_PrivateMessageEnabled() {
                return cfg.featureCommands_PrivateMessageEnabled();
            }
        };
    }

    @Override
    public boolean isEnabled()
    {
        return config.featureCommands_Enabled();
    }

    @Override
    public void startUp()
    {
        super.startUp();
        lastPmFrom = null;
        lastChatInput = null;
        pmTarget = null;
        pendingPmTarget = null;
        pendingPrefill = null;
        registerCommandHandlers();
        commandHandlers.forEach((cmd, handler) -> {
            handler.startUp(this);
            log.debug("Registered chat command: /{}", cmd);
        });
    }

    @Override
    public void shutDown(boolean fullShutdown)
    {
        super.shutDown(fullShutdown);
        lastPmFrom = null;
        lastChatInput = null;
        pmTarget = null;
        pendingPmTarget = null;
        pendingPrefill = null;
        shutDownCommandHandlers();
        commandHandlers.clear();
    }

    private void registerCommandHandlers()
    {
        commandHandlers.clear();

        // /r and /reply
        commandHandlers.put("r", replyChatCommand);
        commandHandlers.put("reply", commandHandlers.get("r"));

        // /w and /whisper
        commandHandlers.put("w", whisperChatCommand);
        commandHandlers.put("whisper", commandHandlers.get("w"));

        // /pm /private message
        commandHandlers.put("pm", privateMessageChatCommand);
        commandHandlers.put("private", commandHandlers.get("pm"));
    }

    private void shutDownCommandHandlers()
    {
        commandHandlers.forEach((cmd, handler) -> {
            handler.shutDown(this);
            log.debug("Shutdown chat command: /{}", cmd);
        });
    }

    /** Remember the last inbound PM sender. */
    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        final ChatMessageType t = e.getType();
        if (t == ChatMessageType.PRIVATECHAT || t == ChatMessageType.MODPRIVATECHAT)
        {
            lastPmFrom = Text.toJagexName(Text.removeTags(e.getName()));
            log.debug("lastPmFrom = {}", lastPmFrom);
        }
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged e)
    {
        if (e.getIndex() != VarClientStr.CHATBOX_TYPED_TEXT)
            return;

        Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
        if (input == null || input.isHidden())
            return;

        if (ClientUtil.isSystemTextEntryActive(client)) {
            return; // Don't do anything if a system prompt is active
        }

        String raw = client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT);
        handleChatInput(raw);
        lastChatInput = raw;
    }

    private void handleChatInput(String raw) {
        final Pair<ChatCommandHandler, String[]> pair = getCommandHandler(raw);
        if (pair == null) {
            // Unknown slash, pass through (let normal chat send it)
            return;
        }

        final ChatCommandHandler handler = pair.getLeft();
        final String[] args = pair.getRight();

        try {
            handler.handleInput(args);
            handler.handleInputOrSubmit(args, null);
        }
        catch (Exception ex)
        {
            log.warn("Chat command '{}' failed", handler.getClass().getSimpleName(), ex);
        }
    }

    /** Intercept commands before they send. */
    @Subscribe
    public void onChatboxInput(ChatboxInput ev)
    {
        if (!isEnabled()) return;

        String raw = ev.getValue();
        if (raw == null || raw.isEmpty()) return;

        if (!raw.startsWith("/") && lastChatInput.startsWith("/"))
            raw = "/" + raw; // Ensure it starts with a slash

        handleChatSubmit(raw, ev);
    }

    private void handleChatSubmit(String raw, ChatboxInput ev)
    {
        final Pair<ChatCommandHandler, String[]> pair = getCommandHandler(raw);
        if (pair == null) {
            // Unknown slash, pass through (let normal chat send it)
            return;
        }

        final ChatCommandHandler handler = pair.getLeft();
        final String[] args = pair.getRight();

        try {
            handler.handleSubmit(args, ev);
            handler.handleInputOrSubmit(args, ev);
        }
        catch (Exception ex)
        {
            log.warn("Chat command '{}' failed", handler.getClass().getSimpleName(), ex);
        }
    }

    private Pair<ChatCommandHandler, String[]> getCommandHandler(String raw) {
        if (raw == null || raw.isEmpty())
            return null; // No input, ignore

        final String typed = Text.removeTags(raw).trim();
        if (!typed.startsWith("/"))
            return null; // Not a command, ignore

        // Parse: "/cmd arg1, arg2, arg3, etc"
        final String[] parts = typed.split("\\s+", 2);
        if (parts.length < 1)
        {
            log.debug("Chat command without arguments: {}", typed);
            return null; // No command or no args, ignore
        }

        final String cmd = parts[0].substring(1).toLowerCase(Locale.ROOT);
        final String[] args = parts.length > 1 ? parseArgs(parts[1]) : new String[0];
        final ChatCommandHandler handler = commandHandlers.get(cmd);
        if (handler == null)
        {
            log.debug("Unknown command: {}", cmd);
            return null; // Unknown command, ignore
        }
        else
        {
            log.debug("Chat command /{} with args: {}", cmd, String.join(", ", args));
            return Pair.of(handler, args); // Return handler and parsed args
        }
    }

    private String[] parseArgs(String raw)
    {
        if (raw == null || raw.isEmpty())
            return new String[0];

        String[] parts = raw.split("[,:+]", -1);

        for (int i = 0; i < parts.length; i++)
        {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    public void replyTo(String target)
    {
        if (target == null || target.isEmpty())
        {
            log.warn("Reply target is empty or null");
            return;
        }

        // Queue for AFTER the frame to avoid "scripts are not reentrant"
        // If something is already queued, don't enqueue again this frame.
        if (pendingPmTarget == null)
        {
            pendingPmTarget = target;
            pendingPrefill  = null; // currently null; kept for future use
        }
    }

    public void replyToLastPm(String body)
    {
        final String target = lastPmFrom;
        if (target == null || target.isEmpty())
        {
            return;
        }

        // Queue for AFTER the frame to avoid "scripts are not reentrant"
        // If something is already queued, don't enqueue again this frame.
        if (pendingPmTarget == null)
        {
            pendingPmTarget = target;
            pendingPrefill  = body; // currently null; kept for future use
        }
    }

    public void clearChatInput()
    {
        clientThread.invokeLater(() -> {
            lastChatInput = null; // Clear last chat input
            client.setVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT, "");
            client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
        });
    }

    public void cancelPrivateMessageCompose()
    {
        String lastPmTarget = getPmTarget();
        if (lastPmTarget == null || lastPmTarget.isEmpty()) {
            return;
        }

        setPmTarget(null);

        clientThread.invokeLater(() -> {
            try {
                client.setVarcStrValue(VarClientStr.PRIVATE_MESSAGE_TARGET, "");
            } catch (Throwable ex) {
                // Some client builds may not have this VarClientStr; safe to ignore
            }

            try {
                client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 1, 1, 1);
            } catch (Throwable ex) {
                log.debug("Failed to close message layer script", ex);
            }

            client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
        });
    }

    @Subscribe
    public void onPostClientTick(PostClientTick e)
    {
        if (ClientUtil.isSystemTextEntryActive(client)) {
            // If a system prompt is active, don't open PM interface
            return;
        }

        String target = pendingPmTarget;
        if (target == null || target.isEmpty())
        {
            target = pmTarget; // Use the current target if no pending
        }

        if (target == null || target.isEmpty())
        {
            return;
        }

        final String currentTarget = Text.toJagexName(target);
        final String body = pendingPrefill;
        pendingPmTarget = null;
        pendingPrefill  = null;

        // Schedule after current client scripts have finished
        clientThread.invokeLater(() -> {
            try
            {
                // Open "To <target>:" compose line
                client.runScript(ScriptID.OPEN_PRIVATE_MESSAGE_INTERFACE, currentTarget);

                // Optional: prefill message body if you start using it
                if (body != null && !body.isEmpty())
                {
                    client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, body);
                }
            }
            catch (Throwable ex)
            {
                log.warn("Failed to open PM to {} via chat command", currentTarget, ex);
            }
        });
    }
}
