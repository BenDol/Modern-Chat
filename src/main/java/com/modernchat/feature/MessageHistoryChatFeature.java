package com.modernchat.feature;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.modernchat.ModernChatConfig;
import com.modernchat.feature.command.CommandsChatFeature;
import com.modernchat.util.ClientUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatboxInput;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.modernchat.feature.MessageHistoryChatFeature.MessageHistoryChatFeatureConfig;

@Slf4j
@Singleton
public class MessageHistoryChatFeature extends AbstractChatFeature<MessageHistoryChatFeatureConfig>
    implements KeyListener {

    @Override
    public String getConfigGroup() {
        return "featureMessageHistory";
    }

    public interface MessageHistoryChatFeatureConfig extends ChatFeatureConfig {
        boolean featureMessageHistory_Enabled();
        int featureMessageHistory_MaxEntries();
        boolean featureMessageHistory_IncludeCommands();
        boolean featureMessageHistory_SkipDuplicates();
        Keybind featureMessageHistory_PrevKey();
        Keybind featureMessageHistory_NextKey();
    }

    private final List<String> history = new ArrayList<>();
    private int navIndex = -1;
    private String stashedDraft = null; // draft before navigation started

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private KeyManager keyManager;
    @Inject private ConfigManager configManager;
    @Inject private Gson gson;
    @Inject private CommandsChatFeature commandsChatFeature;

    @Inject
    public MessageHistoryChatFeature(ModernChatConfig rootCfg, EventBus eventBus) {
        super(rootCfg, eventBus);
    }

    @Override
    protected MessageHistoryChatFeatureConfig partitionConfig(ModernChatConfig cfg) {
        return new MessageHistoryChatFeatureConfig() {
            @Override public boolean featureMessageHistory_Enabled() { return cfg.featureMessageHistory_Enabled(); }
            @Override public int featureMessageHistory_MaxEntries() { return cfg.featureMessageHistory_MaxEntries(); }
            @Override public boolean featureMessageHistory_IncludeCommands() { return cfg.featureMessageHistory_IncludeCommands();}
            @Override public boolean featureMessageHistory_SkipDuplicates() { return cfg.featureMessageHistory_SkipDuplicates();}
            @Override public Keybind featureMessageHistory_PrevKey() { return cfg.featureMessageHistory_PrevKey();}
            @Override public Keybind featureMessageHistory_NextKey() {return cfg.featureMessageHistory_NextKey(); }
        };
    }

    @Override
    public boolean isEnabled() {
        return config.featureMessageHistory_Enabled();
    }

    @Override
    public void startUp() {
        super.startUp();
        keyManager.registerKeyListener(this);
        resetNavState();
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        super.shutDown(fullShutdown);
        keyManager.unregisterKeyListener(this);
        resetNavState();
        saveHistory();
        history.clear();
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        clientThread.invoke(() -> {
            if (ClientUtil.isChatHidden(client))
                return; // don't process keys when chat is hidden

            final Keybind prev = config.featureMessageHistory_PrevKey();
            final Keybind next = config.featureMessageHistory_NextKey();
            final boolean isPrev = prev != null && prev.matches(e);
            final boolean isNext = next != null && next.matches(e);

            if (!isPrev && !isNext)
                return;

            if (!ClientUtil.isChatInputEditable(client))
                return;

            e.consume();

            if (isPrev)
                navigateHistory(-1);
            else
                navigateHistory(+1);
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGGED_IN) {
            // Load history when logging in
            loadHistory();
        } else if (e.getGameState() == GameState.LOGIN_SCREEN) {
            // Clear history when logging out
            history.clear();
        }

        resetNavState(); // reset navigation state
    }

    @Subscribe
    public void onChatboxInput(ChatboxInput ev) {
        if (!isEnabled()) return;

        final String raw = ev.getValue();
        if (raw == null) return;

        String msg = Text.removeTags(raw).trim();
        if (msg.isEmpty()) return;

        if (commandsChatFeature.isEnabled()) {
            int commandIndex = msg.indexOf(' ');
            if (commandIndex != -1) {
                String cmd = msg.substring(0, commandIndex);
                if (commandsChatFeature.isCommand(cmd.startsWith("/") ? cmd : "/" + cmd)) {
                    // Skip slash commands if configured
                    if (!config.featureMessageHistory_IncludeCommands()) {
                        resetNavState(); // still reset after a send
                        return;
                    }

                    // Ensure it starts with a slash (client trims '/' from messages)
                    if (!msg.startsWith("/"))
                        msg = "/" + msg;
                }
            }
        }

        // Avoid consecutive duplicates if desired
        if (config.featureMessageHistory_SkipDuplicates()
            && !history.isEmpty()
            && history.get(history.size() - 1).equals(msg)) {
            resetNavState();
            return;
        }

        history.add(msg);

        // Bound the size
        int max = Math.max(1, config.featureMessageHistory_MaxEntries());
        if (history.size() > max)
            history.remove(0);

        saveHistory();
        // After sending, drop navigation state and draft
        resetNavState();
    }

    private void navigateHistory(int delta) {
        if (history.isEmpty()) return;

        // When starting, stash current draft and set index just past newest
        if (navIndex == -1) {
            stashedDraft = ClientUtil.getChatInputText(client);
            navIndex = history.size();
        }

        if (delta < 0 && navIndex > 0)
            navIndex--;  // older
        else if (delta > 0 && navIndex < history.size())
            navIndex++;  // newer

        if (navIndex >= 0 && navIndex < history.size()) {
            ClientUtil.setChatInputText(client, history.get(navIndex));
        } else {
            // Past newest, restore the original draft
            String value = stashedDraft != null ? stashedDraft : "";
            clientThread.invoke(() -> {
                ClientUtil.setChatInputText(client, value);

                if (commandsChatFeature.isEnabled()) {
                    commandsChatFeature.setLastChatInput(value);
                }
            });
        }
    }

    private void loadHistory() {
        try {
            // Per-account configuration
            String json = configManager.getRSProfileConfiguration(ModernChatConfig.GROUP, ModernChatConfig.HISTORY_KEY);
            if (json == null || json.isEmpty()) return;

            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> saved = gson.fromJson(json, listType);
            if (saved == null) return;

            // apply current cap & sanitize
            int max = Math.max(1, config.featureMessageHistory_MaxEntries());
            List<String> trimmed = saved.stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());

            if (trimmed.size() > max)
                trimmed = trimmed.subList(trimmed.size() - max, trimmed.size());

            history.clear();
            history.addAll(trimmed);
        } catch (Exception ex) {
            log.warn("Failed to load message history", ex);
        }
    }

    private void saveHistory() {
        try {
            int max = Math.max(1, config.featureMessageHistory_MaxEntries());
            List<String> toSave = history;
            if (history.size() > max)
                toSave = history.subList(history.size() - max, history.size());

            String json = gson.toJson(toSave);
            // Per-account configuration
            configManager.setRSProfileConfiguration(ModernChatConfig.GROUP, ModernChatConfig.HISTORY_KEY, json);
        }
        catch (Exception ex) {
            log.warn("Failed to persist message history", ex);
        }
    }

    public void clearHistoryStack() {
        history.clear();
        saveHistory();
    }

    private void resetNavState() {
        navIndex = -1;
        stashedDraft = null;
    }


}
