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
import net.runelite.api.events.ScriptCallbackEvent;
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
    private boolean enabled = false;

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

        log.debug("KeyRemappingService shut down");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTyping() {
        return typing;
    }

    public void lockChat() {
        if (!typing)
            return;

        typing = false;
        clientThread.invoke(() -> {
            client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 0, 0, 0);
            ClientUtil.setChatboxWidgetInput(client, ClientUtil.PRESS_ENTER_TO_CHAT);
        });
    }

    public void unlockChat() {
        if (typing)
            return;

        typing = true;
        clientThread.invoke(() -> client.runScript(ScriptID.MESSAGE_LAYER_CLOSE, 0, 1, 0));
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
                if (!typing) {
                    ClientUtil.setChatboxWidgetInput(client, ClientUtil.PRESS_ENTER_TO_CHAT);
                }
                break;
            case "blockChatInput":
                if (!typing) {
                    int[] intStack = client.getIntStack();
                    intStack[client.getIntStackSize() - 1] = 1;
                }
                break;
        }
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
}
