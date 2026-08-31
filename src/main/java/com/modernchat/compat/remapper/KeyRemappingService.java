package com.modernchat.compat.remapper;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.ChatProxy;
import com.modernchat.common.NotificationService;
import com.modernchat.event.ChatToggleEvent;
import com.modernchat.service.ChatService;
import com.modernchat.util.ClientUtil;
import com.modernchat.util.ConfigUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ModifierlessKeybind;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Built-in service that replicates RuneLite's KeyRemappingPlugin logic.
 * Reads configuration from the "keyremapping" config group via ConfigManager.
 */
@Slf4j
@Singleton
public class KeyRemappingService implements ChatService {
    private static final String CONFIG_GROUP = "keyremapping";
    private static final String PLUGIN_NAME = "Key Remapping";
    // Low-frequency safety net (~500ms at 50 ticks/s) for interfaces that
    // re-enable the chat input without firing the message layer scripts
    private static final int RELOCK_BACKSTOP_TICKS = 25;

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private EventBus eventBus;
    @Inject private KeyManager keyManager;
    @Inject private PluginManager pluginManager;
    @Inject private ChatProxy chatProxy;
    @Inject private NotificationService notificationService;

    private KeyRemappingKeyListener keyListener;
    private volatile boolean typing = false;
    private volatile boolean chatOpenPending = false;
    private boolean enabled = false;

    // Reentrancy guard: our own MESSAGE_LAYER_CLOSE runScript calls fire
    // ScriptPostFired synchronously, which would otherwise recurse back into
    // relockChat() before the lock is observable. Client thread only.
    private boolean relocking = false;
    // Dirty flag gating the ClientTick relock backstop so steady-state ticks
    // exit on a single boolean read. Set from the script/toggle event paths.
    private volatile boolean relockCheckPending = false;
    // Dialog state cached on the client thread so the AWT key listener never
    // walks widgets off the client thread
    private volatile boolean dialogOpen = false;
    private int ticksSinceRelockCheck = 0;

    // Camera remap config
    @Getter private volatile boolean cameraRemap;
    @Getter private volatile ModifierlessKeybind up;
    @Getter private volatile ModifierlessKeybind down;
    @Getter private volatile ModifierlessKeybind left;
    @Getter private volatile ModifierlessKeybind right;

    // F-key remap config
    @Getter private volatile boolean fkeyRemap;
    @Getter private volatile ModifierlessKeybind f1;
    @Getter private volatile ModifierlessKeybind f2;
    @Getter private volatile ModifierlessKeybind f3;
    @Getter private volatile ModifierlessKeybind f4;
    @Getter private volatile ModifierlessKeybind f5;
    @Getter private volatile ModifierlessKeybind f6;
    @Getter private volatile ModifierlessKeybind f7;
    @Getter private volatile ModifierlessKeybind f8;
    @Getter private volatile ModifierlessKeybind f9;
    @Getter private volatile ModifierlessKeybind f10;
    @Getter private volatile ModifierlessKeybind f11;
    @Getter private volatile ModifierlessKeybind f12;
    @Getter private volatile ModifierlessKeybind esc;
    @Getter private volatile ModifierlessKeybind space;
    @Getter private volatile ModifierlessKeybind control;

    @Override
    public void startUp() {
        eventBus.register(this);

        keyListener = new KeyRemappingKeyListener(this);
        keyManager.registerKeyListener(keyListener);

        refreshConfig();
        relocking = false;
        dialogOpen = false;
        ticksSinceRelockCheck = 0;
        relockCheckPending = true;
        enabled = true;

        log.debug("KeyRemappingService started");
    }

    @Override
    public void shutDown() {
        enabled = false;
        eventBus.unregister(this);

        if (keyListener != null) {
            keyManager.unregisterKeyListener(keyListener);
            keyListener = null;
        }

        typing = false;
        chatOpenPending = false;
        relockCheckPending = false;
        dialogOpen = false;

        log.debug("KeyRemappingService shut down");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Single source of truth for whether the vanilla chat input is allowed to
     * be open: true only while the user has explicitly opened chat. The key
     * listener, the script callbacks and the relock logic all consult this.
     */
    public boolean isTyping() {
        return typing;
    }

    public boolean isChatOpenPending() {
        return chatOpenPending;
    }

    /**
     * Set synchronously on the AWT thread while ToggleChatFeature has a chat open
     * queued on the client thread, so remapping can't remap or swallow keystrokes
     * typed in the gap before the chat input takes focus.
     */
    public void setChatOpenPending(boolean pending) {
        chatOpenPending = pending;
    }

    public void lockChat() {
        if (!typing)
            return;

        typing = false;
        clientThread.invoke(() -> {
            // Flag our own MESSAGE_LAYER_CLOSE so the synchronous
            // ScriptPostFired it triggers is ignored by relockChat()
            relocking = true;
            try {
                client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 0, 0, 0);
                ClientUtil.setChatboxWidgetInput(client, ClientUtil.PRESS_ENTER_TO_CHAT);
            } finally {
                relocking = false;
            }
        });
    }

    public void unlockChat() {
        if (typing)
            return;

        typing = true;
        clientThread.invoke(() -> client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 0, 1, 0));
    }

    /**
     * Re-assert the chat lock when the client re-opens or rebuilds the vanilla chat
     * input while the user has chat closed - dialogs, clue scrolls and vanilla chat
     * tab switches all do this (#43). Fails open whenever the chatbox may be hosting
     * a legitimate prompt. MUST be called on the client thread. The relocking guard
     * makes the ScriptPostFired re-entry caused by our own MESSAGE_LAYER_CLOSE a
     * no-op instead of unbounded recursion.
     */
    public void relockChat() {
        if (relocking || !enabled || isTyping())
            return;

        if (!canRelockChat())
            return;

        relocking = true;
        try {
            client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 0, 0, 0);
            ClientUtil.setChatboxWidgetInput(client, ClientUtil.PRESS_ENTER_TO_CHAT);
        } finally {
            relocking = false;
        }
    }

    private boolean canRelockChat() {
        // Cheapest decisive check first: already locked means nothing to do
        if (ClientUtil.isChatLocked(client))
            return false;
        // Only touch the message layer while the plain chat input line is the live
        // input; prompts (enter amount, searches, name inputs), dialogs and PM
        // compose must keep working, so skip when any of them own the chatbox.
        if (!ClientUtil.isChatInputEditable(client))
            return false;
        if (ClientUtil.isDialogOpen(client) || ClientUtil.isSystemWidgetActive(client))
            return false;
        if (ClientUtil.isPmComposeOpen(client))
            return false;
        if (chatProxy.isCommandMode())
            return false;
        // Covers the world map search, report interface and focused input fields
        return chatboxFocused();
    }

    public void checkConflictingPlugin() {
        for (Plugin plugin : pluginManager.getPlugins()) {
            if (PLUGIN_NAME.equals(plugin.getName())
                && pluginManager.isPluginEnabled(plugin)) {
                notificationService.showWarningMessageBox(
                    "Plugin Conflict",
                    "The RuneLite 'Key Remapping' plugin conflicts with Modern Chat.\n\n" +
                        "Modern Chat now includes built-in key remapping that reads your existing\n" +
                        "Key Remapping settings. Please disable 'Key Remapping' in the RuneLite\n" +
                        "plugin hub to avoid conflicts.\n\n" +
                        "Your key remapping configuration will be preserved."
                );
                return;
            }
        }
    }

    @Subscribe
    public void onPluginChanged(PluginChanged e) {
        if (PLUGIN_NAME.equals(e.getPlugin().getName()) && e.isLoaded()) {
            notificationService.pushHelperNotification(new ChatMessageBuilder()
                .append(Color.YELLOW, "Warning: ")
                .append(Color.ORANGE, "Key Remapping ")
                .append(Color.YELLOW, "plugin was enabled. This conflicts with Modern Chat's built-in key remapping. ")
                .append(Color.YELLOW, "Please disable it to avoid issues."));
        }
    }

    @Subscribe
    public void onChatToggleEvent(ChatToggleEvent e) {
        if (!enabled)
            return;

        relockCheckPending = true;
        if (e.isHidden()) {
            lockChat();
        } else {
            unlockChat();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (CONFIG_GROUP.equals(e.getGroup())) {
            refreshConfig();
        }
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent e) {
        if (!enabled)
            return;

        switch (e.getEventName()) {
            case "setChatboxInput":
                if (!isTyping()) {
                    ClientUtil.setChatboxWidgetInput(client, ClientUtil.PRESS_ENTER_TO_CHAT);
                }
                break;
            case "blockChatInput":
                if (!isTyping()) {
                    int[] intStack = client.getIntStack();
                    intStack[client.getIntStackSize() - 1] = 1;
                }
                break;
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired e) {
        if (!enabled)
            return;

        switch (e.getScriptId()) {
            case ScriptID.MESSAGE_LAYER_CLOSE:
            case ScriptID.BUILD_CHATBOX:
            case ScriptID.CHAT_TEXT_INPUT_REBUILD:
                relockCheckPending = true;
                relockChat();
                break;
        }
    }

    @Subscribe
    public void onClientTick(ClientTick e) {
        if (!enabled)
            return;

        // Refresh the cached dialog state for the AWT key listener
        dialogOpen = computeDialogOpen();

        // Relock backstop for interfaces that re-enable the chat input without
        // running the message layer scripts (e.g. clue scrolls). Gated behind
        // the dirty flag plus a low-frequency interval so steady-state ticks
        // skip the varc/widget checks.
        if (!relockCheckPending && ++ticksSinceRelockCheck < RELOCK_BACKSTOP_TICKS)
            return;

        relockCheckPending = false;
        ticksSinceRelockCheck = 0;
        relockChat();
    }

    private void refreshConfig() {
        cameraRemap = ConfigUtil.getBool(configManager, CONFIG_GROUP, "cameraRemap", false);
        up = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "up",
            new ModifierlessKeybind(KeyEvent.VK_W, 0));
        down = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "down",
            new ModifierlessKeybind(KeyEvent.VK_S, 0));
        left = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "left",
            new ModifierlessKeybind(KeyEvent.VK_A, 0));
        right = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "right",
            new ModifierlessKeybind(KeyEvent.VK_D, 0));

        fkeyRemap = ConfigUtil.getBool(configManager, CONFIG_GROUP, "fkeyRemap", false);
        f1 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f1",
            new ModifierlessKeybind(KeyEvent.VK_1, 0));
        f2 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f2",
            new ModifierlessKeybind(KeyEvent.VK_2, 0));
        f3 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f3",
            new ModifierlessKeybind(KeyEvent.VK_3, 0));
        f4 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f4",
            new ModifierlessKeybind(KeyEvent.VK_4, 0));
        f5 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f5",
            new ModifierlessKeybind(KeyEvent.VK_5, 0));
        f6 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f6",
            new ModifierlessKeybind(KeyEvent.VK_6, 0));
        f7 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f7",
            new ModifierlessKeybind(KeyEvent.VK_7, 0));
        f8 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f8",
            new ModifierlessKeybind(KeyEvent.VK_8, 0));
        f9 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f9",
            new ModifierlessKeybind(KeyEvent.VK_9, 0));
        f10 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f10",
            new ModifierlessKeybind(KeyEvent.VK_0, 0));
        f11 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f11",
            new ModifierlessKeybind(KeyEvent.VK_MINUS, 0));
        f12 = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "f12",
            new ModifierlessKeybind(KeyEvent.VK_EQUALS, 0));
        esc = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "esc",
            new ModifierlessKeybind(KeyEvent.VK_ESCAPE, 0));
        space = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "space",
            new ModifierlessKeybind(KeyEvent.VK_SPACE, 0));
        control = ConfigUtil.getModifierlessKeybind(configManager, CONFIG_GROUP, "control",
            new ModifierlessKeybind(KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK));

        log.debug("KeyRemapping config refreshed: cameraRemap={}, fkeyRemap={}", cameraRemap, fkeyRemap);
    }

    /**
     * Dialog state cached on the client tick so the AWT key listener does not
     * walk widgets off the client thread.
     */
    boolean isDialogOpen() {
        return dialogOpen;
    }

    private boolean computeDialogOpen() {
        // Most chat dialogs with numerical input are added without the chatbox or its key listener being removed,
        // so chatboxFocused() is true. The chatbox onkey script uses the following logic to ignore key presses,
        // so we will use it too to not remap F-keys.
        return ClientUtil.isDialogOpen(client)
            // We want to block F-key remapping in the bank pin interface too, so it does not interfere with the
            // Keyboard Bankpin feature of the Bank plugin
            || !ClientUtil.isHidden(client, InterfaceID.BankpinKeypad.UNIVERSE);
    }

    public boolean isOptionsDialogOpen() {
        return ClientUtil.isOptionsDialogOpen(client);
    }

    boolean chatboxFocused() {
        Widget chatboxParent = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
        if (chatboxParent == null || chatboxParent.getOnKeyListener() == null) {
            return false;
        }

        // If the search box on the world map is open and focused, ~keypress_permit blocks the keypress
        Widget worldMapSearch = client.getWidget(InterfaceID.Worldmap.MAPLIST_DISPLAY);
        if (worldMapSearch != null && client.getVarcIntValue(VarClientID.WORLDMAP_SEARCHING) == 1) {
            return false;
        }

        // The report interface blocks input due to 162:54 being hidden, however player/npc dialog and
        // options do this too, and so we can't disable remapping just due to 162:54 being hidden.
        Widget report = client.getWidget(InterfaceID.Reportabuse.UNIVERSE);
        if (report != null) {
            return false;
        }

        return client.getFocusedInputFieldWidget() == null;
    }
}
