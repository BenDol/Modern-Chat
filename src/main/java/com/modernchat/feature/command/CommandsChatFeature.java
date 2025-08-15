package com.modernchat.feature.command;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.ChatProxy;
import com.modernchat.feature.AbstractChatFeature;
import com.modernchat.feature.ChatFeatureConfig;
import com.modernchat.service.PrivateChatService;
import com.modernchat.util.ClientUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.VarClientStr;
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
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Singleton
public class CommandsChatFeature extends AbstractChatFeature<CommandsChatFeature.CommandsChatConfig> {

    @Override
    public String getConfigGroup() {
        return "featureCommands";
    }

    public interface CommandsChatConfig extends ChatFeatureConfig {
        boolean featureCommands_Enabled();
        boolean featureCommands_ReplyEnabled();
        boolean featureCommands_WhisperEnabled();
        boolean featureCommands_PrivateMessageEnabled();
    }

    public interface ChatCommandHandler extends KeyListener {
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
    @Inject @Getter private PrivateChatService privateChatService;
    @Inject @Getter private ChatProxy chatProxy;

    @Getter @Setter
    private String lastChatInput;

    // "command" -> handler(args)
    private final Map<String, ChatCommandHandler> commandHandlers = new HashMap<>();

    @Inject
    public CommandsChatFeature(ModernChatConfig rootConfig, EventBus eventBus) {
        super(rootConfig, eventBus);
    }

    @Override
    protected CommandsChatConfig partitionConfig(ModernChatConfig cfg) {
        // Map root config to feature config
        return new CommandsChatConfig() {
            @Override public boolean featureCommands_Enabled() { return cfg.featureCommands_Enabled(); }
            @Override public boolean featureCommands_ReplyEnabled() { return cfg.featureCommands_ReplyEnabled(); }
            @Override public boolean featureCommands_WhisperEnabled() { return cfg.featureCommands_WhisperEnabled(); }
            @Override public boolean featureCommands_PrivateMessageEnabled() { return cfg.featureCommands_PrivateMessageEnabled(); }
        };
    }

    @Override
    public boolean isEnabled() {
        return config.featureCommands_Enabled();
    }

    @Override
    public void startUp() {
        super.startUp();
        privateChatService.startUp();
        registerCommandHandlers();
        commandHandlers.forEach((cmd, handler) -> {
            handler.startUp(this);
            log.debug("Registered chat command: /{}", cmd);
        });
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        super.shutDown(fullShutdown);
        privateChatService.shutDown();
        shutDownCommandHandlers();
        commandHandlers.clear();
    }

    private void registerCommandHandlers() {
        commandHandlers.clear();

        // /r and /reply
        commandHandlers.put("r", replyChatCommand);

        // /w and /whisper
        commandHandlers.put("w", whisperChatCommand);
        commandHandlers.put("whisper", new ChatCommandLink("w"));

        // /pm /private message
        commandHandlers.put("pm", privateMessageChatCommand);
        commandHandlers.put("private", new ChatCommandLink("pm"));
    }

    private void shutDownCommandHandlers() {
        commandHandlers.forEach((cmd, handler) -> {
            handler.shutDown(this);
            log.debug("Shutdown chat command: /{}", cmd);
        });
    }

    @Subscribe
    public void onVarClientStrChanged(VarClientStrChanged e) {
        if (e.getIndex() != VarClientStr.CHATBOX_TYPED_TEXT)
            return;

        if (chatProxy.isHidden())
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
        } catch (Exception ex) {
            log.warn("Chat command '{}' failed", handler.getClass().getSimpleName(), ex);
        }
    }

    /**
     * Intercept commands before they send.
     */
    @Subscribe
    public void onChatboxInput(ChatboxInput ev) {
        if (!isEnabled()) return;

        String raw = ev.getValue();
        if (raw == null || raw.isEmpty()) return;

        if (!raw.startsWith("/") && lastChatInput.startsWith("/"))
            raw = "/" + raw; // Ensure it starts with a slash

        handleChatSubmit(raw, ev);
    }

    private void handleChatSubmit(String raw, ChatboxInput ev) {
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
        } catch (Exception ex) {
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
        if (parts.length < 1) {
            log.debug("Chat command without arguments: {}", typed);
            return null; // No command or no args, ignore
        }

        final String cmd = parts[0].substring(1).toLowerCase(Locale.ROOT);
        final String[] args = parts.length > 1 ? parseArgs(parts[1]) : new String[0];
        ChatCommandHandler handler = commandHandlers.get(cmd);
        if (handler == null) {
            log.debug("Unknown command: {}", cmd);
            return null; // Unknown command, ignore
        } else {
            if (handler instanceof ChatCommandLink) {
                String link = ((ChatCommandLink) handler).getLink();
                handler = commandHandlers.get(link);

                 if (handler == null) {
                    log.debug("Unknown link command: {}", link);
                    return null; // Unknown command, ignore
                }
            }

            log.debug("Chat command /{} with args: {}", cmd, String.join(", ", args));
            return Pair.of(handler, args); // Return handler and parsed args
        }
    }

    private String[] parseArgs(String raw) {
        if (raw == null || raw.isEmpty())
            return new String[0];

        String[] parts = raw.split("[,:+]", -1);

        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    public boolean isCommand(String cmd) {
        Pair<ChatCommandHandler, String[]> handler = getCommandHandler(cmd);
        return handler != null && handler.getLeft() != null;
    }
}
