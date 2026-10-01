package com.modernchat.service;

import com.modernchat.common.NotificationService;
import com.modernchat.draw.UsernameHit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IconID;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.hiscore.HiscorePlugin;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Adds the standard player actions to usernames rendered by Modern Chat. */
@Slf4j
@Singleton
public class PlayerMenuService
{
    private static final String ADD_FRIEND = "Add friend";
    private static final String ADD_IGNORE = "Add ignore";
    private static final String LOOK_UP = "Look up";
    private static final String COPY_TO_CLIPBOARD = "Copy to clipboard";
    private static final int OP_ADD_IGNORE = 2;
    private static final int OP_ADD_FRIEND = 3;

    private final Client client;
    private final ClientThread clientThread;
    private final PluginManager pluginManager;
    private final NotificationService notificationService;

    @Inject
    public PlayerMenuService(
        Client client,
        ClientThread clientThread,
        PluginManager pluginManager,
        NotificationService notificationService) {
        this.client = client;
        this.clientThread = clientThread;
        this.pluginManager = pluginManager;
        this.notificationService = notificationService;
    }

    public void addMenuEntries(UsernameHit hit) {
        if (hit == null || hit.getUsername().trim().isEmpty()) {
            return;
        }

        String username = hit.getUsername();
        client.getMenu().createMenuEntry(1)
            .setOption(ADD_FRIEND)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> invokeNativeChatAction(username, ADD_FRIEND));

        client.getMenu().createMenuEntry(1)
            .setOption(ADD_IGNORE)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> invokeNativeChatAction(username, ADD_IGNORE));

        client.getMenu().createMenuEntry(1)
            .setOption(LOOK_UP)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> lookUpPlayer(username, hit.getRawName()));

        client.getMenu().createMenuEntry(1)
            .setOption(COPY_TO_CLIPBOARD)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> copyUsername(username));
    }

    private void invokeNativeChatAction(String username, String action) {
        clientThread.invokeLater(() -> dispatchNativeChatAction(username, action));
    }

    /** Execute the game's own chat-line op so friend/ignore requests use the native client logic. */
    private void dispatchNativeChatAction(String username, String action) {
        try {
            Widget line = findChatLineWidget(username, action);
            if (line == null) {
                notifyUnavailable(action + " is unavailable for " + username + ".");
                return;
            }

            int op = actionOp(line.getActions(), action);
            if (op <= 0) {
                op = ADD_IGNORE.equals(action) ? OP_ADD_IGNORE : OP_ADD_FRIEND;
            }

            MenuAction type = op >= 6 ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
            client.menuAction(-1, line.getId(), type, op, -1, action, Text.removeTags(username));
        } catch (RuntimeException ex) {
            log.warn("Unable to execute chat action {} for {}", action, username, ex);
            notifyUnavailable(action + " is unavailable for " + username + ".");
        }
    }

    private Widget findChatLineWidget(String username, String action) {
        String normalizedUsername = normalize(username);
        List<Widget> candidates = new ArrayList<>();
        collectWidget(client.getWidget(InterfaceID.Chatbox.SCROLLAREA), candidates, 0);
        collectWidget(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLAREA), candidates, 0);
        collectWidget(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS), candidates, 0);
        collectWidget(client.getWidget(InterfaceID.Chatbox.CHATDISPLAY), candidates, 0);
        for (int id = InterfaceID.Chatbox.LINE0; id <= InterfaceID.Chatbox.LINE99; id++) {
            collectWidget(client.getWidget(id), candidates, 0);
        }

        Set<Integer> seen = new HashSet<>();
        Widget best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Widget widget : candidates) {
            if (widget == null || !seen.add(widget.getId())) {
                continue;
            }

            boolean matchesName = matchesUsername(widget, normalizedUsername);
            if (!matchesName) {
                continue;
            }

            int op = actionOp(widget.getActions(), action);
            Object[] listener = widget.getOnOpListener();
            if (op < 0 && listener == null) {
                continue;
            }

            int score = op > 0 ? 4 : 0;
            if (listener != null) {
                score += 2;
            }
            if (normalizeSenderText(widget.getText()).equals(normalizedUsername)) {
                score++;
            }
            if (score > bestScore) {
                best = widget;
                bestScore = score;
            }
        }
        return best;
    }

    private static void collectWidget(Widget widget, List<Widget> out, int depth) {
        if (widget == null || depth > 3) {
            return;
        }
        out.add(widget);
        Widget[] children = widget.getChildren();
        if (children != null) {
            for (Widget child : children) {
                collectWidget(child, out, depth + 1);
            }
        }
    }

    private static boolean matchesUsername(Widget widget, String username) {
        if (username.isEmpty()) {
            return false;
        }
        if (normalize(widget.getName()).equals(username)) {
            return true;
        }

        String text = normalizeSenderText(widget.getText());
        if (text.equals(username)) {
            return true;
        }
        return text.startsWith(username) && text.length() > username.length()
            && ": -".indexOf(text.charAt(username.length())) >= 0;
    }

    private static int actionOp(String[] actions, String action) {
        if (actions == null) {
            return -1;
        }
        String wanted = normalize(action);
        for (int i = 0; i < actions.length; i++) {
            if (actions[i] != null && normalize(actions[i]).equals(wanted)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String normalizeSenderText(String value) {
        String text = normalize(value);
        while (text.endsWith(":")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        return text;
    }

    private static String normalize(String value) {
        return value == null ? "" : Text.standardize(Text.removeTags(value)).trim().toLowerCase(Locale.ROOT);
    }

    private void lookUpPlayer(String username, String rawName) {
        HiscorePlugin plugin = findActiveHiscorePlugin();
        if (plugin == null) {
            notifyUnavailable("Enable the HiScore plugin to look up " + username + ".");
            return;
        }

        try {
            Method getWorldEndpoint = HiscorePlugin.class.getDeclaredMethod("getWorldEndpoint");
            Method lookupPlayer = HiscorePlugin.class.getDeclaredMethod(
                "lookupPlayer", String.class, HiscoreEndpoint.class);
            getWorldEndpoint.setAccessible(true);
            lookupPlayer.setAccessible(true);
            HiscoreEndpoint endpoint = (HiscoreEndpoint) getWorldEndpoint.invoke(plugin);
            if (endpoint == null) {
                notifyUnavailable("HiScore lookup is unavailable right now.");
                return;
            }
            lookupPlayer.invoke(plugin, Text.removeTags(username), resolveChatHiscoreEndpoint(rawName, endpoint));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null
                ? ex.getCause()
                : ex;
            log.warn("Unable to invoke HiScore lookup for {}", username, cause);
            notifyUnavailable("HiScore lookup is unavailable right now.");
        }
    }

    private static HiscoreEndpoint resolveChatHiscoreEndpoint(String rawName, HiscoreEndpoint worldEndpoint) {
        HiscoreEndpoint chatEndpoint = HiscoreEndpoint.NORMAL;
        if (rawName.contains(IconID.IRONMAN.toString())) {
            chatEndpoint = HiscoreEndpoint.IRONMAN;
        } else if (rawName.contains(IconID.ULTIMATE_IRONMAN.toString())) {
            chatEndpoint = HiscoreEndpoint.ULTIMATE_IRONMAN;
        } else if (rawName.contains(IconID.HARDCORE_IRONMAN.toString())) {
            chatEndpoint = HiscoreEndpoint.HARDCORE_IRONMAN;
        } else if (rawName.contains(IconID.LEAGUE.toString())) {
            chatEndpoint = HiscoreEndpoint.SEASONAL;
        }
        return chatEndpoint != HiscoreEndpoint.NORMAL || worldEndpoint == HiscoreEndpoint.SEASONAL
            ? chatEndpoint
            : worldEndpoint;
    }

    private HiscorePlugin findActiveHiscorePlugin() {
        Collection<Plugin> plugins = pluginManager.getPlugins();
        if (plugins == null) {
            return null;
        }
        for (Plugin plugin : plugins) {
            if (plugin instanceof HiscorePlugin && pluginManager.isPluginActive(plugin)) {
                return (HiscorePlugin) plugin;
            }
        }
        return null;
    }

    private void copyUsername(String username) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(Text.removeTags(username)), null);
        } catch (RuntimeException ex) {
            log.warn("Unable to copy username {} to the clipboard", username, ex);
            notifyUnavailable("Unable to copy " + username + " to the clipboard.");
        }
    }

    private void notifyUnavailable(String message) {
        notificationService.pushChatMessage(message);
    }
}
