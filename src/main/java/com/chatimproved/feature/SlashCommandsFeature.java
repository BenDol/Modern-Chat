package com.chatimproved.feature;

import com.chatimproved.ChatImprovedConfig;
import com.chatimproved.util.InterfaceUtil;
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
import net.runelite.client.util.Text;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class SlashCommandsFeature extends AbstractChatFeature<SlashCommandsFeature.SlashCommandsConfig>
{
    public interface SlashCommandsConfig extends ChatFeatureConfig
    {
        boolean featureSlashCommands_Enabled();
        boolean featureSlashCommands_ReplyEnabled();
        boolean featureSlashCommands_WhisperEnabled();
    }

    @Override
    public String getConfigGroup() {
        return "featureSlashCommands";
    }

    public interface ChatCommandHandler
    {
        default void handleInput(String[] args) {}
        default void handleSubmit(String[] args, ChatboxInput ev) {}
        default void handleInputOrSubmit(String[] args, ChatboxInput ev) {}
    }

    @Inject private Client client;
    @Inject private ClientThread clientThread;

    // Track last inbound PM sender (sanitized RuneScape name)
    private volatile String lastPmFrom;
    private volatile String lastChatInput;

    // registry: "command" -> handler(args)
    private final Map<String, ChatCommandHandler> commandHandlers = new HashMap<>();

    // Queue to execute scripts after the frame (avoids reentrancy)
    private String pendingPmTarget = null;
    private String pendingPrefill  = null;

    @Inject
    public SlashCommandsFeature(ChatImprovedConfig rootConfig, EventBus eventBus)
    {
        super(rootConfig, eventBus);
    }

    @Override
    protected SlashCommandsConfig extractConfig(ChatImprovedConfig cfg)
    {
        // Map root config to feature config
        return new SlashCommandsConfig()
        {
            @Override public boolean featureSlashCommands_Enabled()
            {
                return cfg.featureSlashCommands_Enabled();
            }

            @Override public boolean featureSlashCommands_ReplyEnabled()
            {
                return cfg.featureSlashCommands_ReplyEnabled();
            }

            @Override public boolean featureSlashCommands_WhisperEnabled() {
                return cfg.featureSlashCommands_WhisperEnabled();
            }
        };
    }

    @Override
    public boolean isEnabled()
    {
        return config.featureSlashCommands_Enabled();
    }

    @Override
    public void startUp()
    {
        super.startUp();
        lastPmFrom = null;
        lastChatInput = null;
        pendingPmTarget = null;
        pendingPrefill = null;
        buildRegistry();
    }

    @Override
    public void shutDown(boolean fullShutdown)
    {
        super.shutDown(fullShutdown);
        commandHandlers.clear();
        lastPmFrom = null;
        lastChatInput = null;
        pendingPmTarget = null;
        pendingPrefill = null;
    }

    private void buildRegistry()
    {
        commandHandlers.clear();

        // /r and /reply
        commandHandlers.put("r", new ChatCommandHandler() {
            @Override
            public void handleInput(String[] args) {
                if (!config.featureSlashCommands_ReplyEnabled())
                    return;
                replyToLastPm(/*body*/ null);
                clearChatInput();
            }
        });
        commandHandlers.put("reply", commandHandlers.get("r"));

        // Add more commands later, e.g.:
        commandHandlers.put("w", new ChatCommandHandler() {
            @Override
            public void handleInputOrSubmit(String[] args, ChatboxInput ev) {
                if (!config.featureSlashCommands_WhisperEnabled())
                    return;

                // Example: /w <name> - whisper to a player
                if (args == null || (ev == null && args.length < 2) ||
                                    (ev != null && args.length < 1)) {
                    return;
                }

                String arg = args[0].trim();
                if (arg.isEmpty()) {
                    log.debug("Invalid target name for /w command");
                    return;
                }

                String target = Text.toJagexName(arg.trim());
                if (target.isEmpty()) {
                    log.warn("Invalid target name for /w command");
                    return;
                }

                if (ev != null) {
                    ev.consume(); // Prevent default chat submission
                }

                replyTo(target);
                clearChatInput();
            }
        });
        commandHandlers.put("whisper", commandHandlers.get("w"));
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

        if (InterfaceUtil.isSystemTextEntryActiveCT(client)) {
            return; // Don't do anything if a system prompt is active
        }

        // When the player starts typing into a system prompt, ensure chat is shown
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
            log.warn("Slash command '{}' failed", handler.getClass().getSimpleName(), ex);
        }
    }

    /** Intercept slash commands before they send. */
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
            log.warn("Slash command '{}' failed", handler.getClass().getSimpleName(), ex);
        }
    }

    private Pair<ChatCommandHandler, String[]> getCommandHandler(String raw) {
        if (raw == null || raw.isEmpty())
            return null; // No input, ignore

        final String typed = Text.removeTags(raw).trim();
        if (!typed.startsWith("/"))
            return null; // Not a slash command, ignore

        // Parse: "/cmd arg1, arg2, arg3, etc"
        final String[] parts = typed.split("\\s+", 2);
        if (parts.length < 1)
        {
            log.debug("Slash command without arguments: {}", typed);
            return null; // No command or no args, ignore
        }

        final String cmd = parts[0].substring(1).toLowerCase(Locale.ROOT);
        final String[] args = parts.length > 1 ? parseArgs(parts[1]) : new String[0];
        final ChatCommandHandler handler = commandHandlers.get(cmd);
        if (handler == null)
        {
            log.debug("Unknown slash command: {}", cmd);
            return null; // Unknown command, ignore
        }
        else
        {
            log.debug("Slash command /{} with args: {}", cmd, String.join(", ", args));
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

    private void replyTo(String target)
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

    private void replyToLastPm(String body)
    {
        final String target = lastPmFrom;
        if (target == null || target.isEmpty())
        {
            // No one to reply to; quietly do nothing (or post a game message if you want)
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

    @Subscribe
    public void onPostClientTick(PostClientTick e)
    {
        if (pendingPmTarget == null)
            return;

        final String target = pendingPmTarget;
        final String body = pendingPrefill;
        pendingPmTarget = null;
        pendingPrefill  = null;

        // Schedule after current client scripts have finished
        clientThread.invokeLater(() -> {
            try
            {
                // Open "To <target>:" compose line (also clears the slash text)
                client.runScript(ScriptID.OPEN_PRIVATE_MESSAGE_INTERFACE, target);

                // Optional: prefill message body if you start using it
                if (body != null && !body.isEmpty())
                {
                    client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, body);
                }
            }
            catch (Throwable ex)
            {
                log.warn("Failed to open PM to {} via slash command", target, ex);
            }
        });
    }
}
