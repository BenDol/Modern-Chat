package com.modernchat.service;

import com.modernchat.common.NotificationService;
import com.modernchat.draw.UsernameHit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IconID;
import net.runelite.api.MenuAction;
import net.runelite.api.MessageNode;
import net.runelite.api.ScriptEvent;
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

/**
 * Recreates the player-name menu exposed by the legacy chatbox for Modern Chat usernames.
 */
@Slf4j
@Singleton
public class PlayerMenuService
{
    private static final String ADD_FRIEND = "Add friend";
    private static final String ADD_IGNORE = "Add ignore";
    private static final String LOOKUP = "Look up";
    private static final String REPORT = "Report";
    private static final String COPY_TO_CLIPBOARD = "Copy to clipboard";

    // The game generates chat-line menus dynamically at menu-open time and stores no
    // static actions on the line widgets in this revision, so the menu ops are fixed:
    // 1 = Message, 2 = Add ignore, 3 = Add friend, 4 = Report abuse.
    private static final int OP_MESSAGE = 1;
    private static final int OP_ADD_IGNORE = 2;
    private static final int OP_ADD_FRIEND = 3;
    private static final int OP_REPORT = 4;

    private final Client client;
    private final ClientThread clientThread;
    private final PluginManager pluginManager;
    private final NotificationService notificationService;

    @Inject
    public PlayerMenuService(
        Client client,
        ClientThread clientThread,
        PluginManager pluginManager,
        NotificationService notificationService)
    {
        this.client = client;
        this.clientThread = clientThread;
        this.pluginManager = pluginManager;
        this.notificationService = notificationService;
    }

    /**
     * Adds the standard username actions. Inserting every entry at index {@code 1} in this order
     * produces the same top-to-bottom order as the legacy RuneLite chat menu.
     */
    public void addMenuEntries(UsernameHit hit)
    {
        if (hit == null)
        {
            return;
        }

        final String username = hit.getUsername();
        if (username == null || username.trim().isEmpty())
        {
            return;
        }

        final int messageId = hit.getMessageId();

        client.getMenu().createMenuEntry(1)
            .setOption(ADD_FRIEND)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> invokeLegacyChatAction(username, messageId, ADD_FRIEND));

        client.getMenu().createMenuEntry(1)
            .setOption(ADD_IGNORE)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> invokeLegacyChatAction(username, messageId, ADD_IGNORE));

        client.getMenu().createMenuEntry(1)
            .setOption(LOOKUP)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> lookupPlayer(username, messageId));

        client.getMenu().createMenuEntry(1)
            .setOption(REPORT)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> invokeLegacyChatAction(username, messageId, REPORT));

        client.getMenu().createMenuEntry(1)
            .setOption(COPY_TO_CLIPBOARD)
            .setTarget(username)
            .setType(MenuAction.RUNELITE)
            .onClick(entry -> copyUsername(username));
    }

    /** Alias which makes the call site self-documenting. */
    public void addUsernameMenuEntries(UsernameHit hit)
    {
        addMenuEntries(hit);
    }

    private void invokeLegacyChatAction(String username, int messageId, String action)
    {
        clientThread.invokeLater(() ->
        {
            try
            {
                Widget widget = findLegacyChatWidget(username, messageId, action);
                if (widget == null)
                {
                    notifyUnavailable(action + " is unavailable for " + username + ".");
                    return;
                }

int op = opForAction(widget.getActions(), action);
                Object[] listener = widget.getOnOpListener();
                if (op < 0 || listener == null || listener.length == 0)
                {
                    notifyUnavailable(action + " is unavailable for " + username + ".");
                    return;
                }

                Object[] eventArgs = new Object[listener.length];
                System.arraycopy(listener, 0, eventArgs, 0, listener.length);
                String targetName = widget.getName();
                if (targetName == null || targetName.isEmpty())
                {
                    targetName = Text.removeTags(username);
                }
                for (int i = 0; i < eventArgs.length; i++)
                {
                    if (ScriptEvent.NAME.equals(eventArgs[i]))
                    {
                        eventArgs[i] = targetName;
                    }
                }

                ScriptEvent event = client.createScriptEventBuilder(eventArgs)
                    .setSource(widget)
                    .setOp(op)
                    .build();
                event.run();
            }
            catch (Throwable ex)
            {
                log.warn("Unable to execute legacy chat action {} for {}", action, username, ex);
                notifyUnavailable(action + " is unavailable for " + username + ".");
            }
        });
    }

    private Widget findLegacyChatWidget(String username, int messageId, String action)
    {
        final String normalizedUsername = normalize(username);
        if (normalizedUsername.isEmpty())
        {
            return null;
        }

        MessageNode messageNode = findMessageNode(messageId);
        final int expectedType = messageNode != null && messageNode.getType() != null
            ? messageNode.getType().getType()
            : Integer.MIN_VALUE;
        final String expectedBody = messageNode != null ? normalizeBody(messageNode.getValue()) : "";

        List<Widget> candidates = new ArrayList<>();
        collectWidget(client.getWidget(InterfaceID.Chatbox.SCROLLAREA), candidates);
        collectWidget(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLAREA), candidates);
        collectWidget(client.getWidget(InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS), candidates);
        collectWidget(client.getWidget(InterfaceID.Chatbox.CHATDISPLAY), candidates);
        for (int componentId = InterfaceID.Chatbox.LINE0;
             componentId <= InterfaceID.Chatbox.LINE99;
             componentId++)
        {
            collectWidget(client.getWidget(componentId), candidates);
        }

        Set<Integer> seen = new HashSet<>();
        Widget best = null;
        int bestScore = Integer.MIN_VALUE;
        List<String> diagnostics = null;

        for (Widget widget : candidates)
        {
            if (widget == null || !seen.add(widget.getId()))
            {
                continue;
            }

            int op = opForAction(widget.getActions(), action);
            Object[] listener = widget.getOnOpListener();
            boolean nameMatches = matchesUsername(widget, normalizedUsername);
            if (diagnostics == null)
            {
                diagnostics = new ArrayList<>();
            }
            diagnostics.add(String.format(
                "id=%s op=%d nameMatch=%s listener=%s name=%s text=%s actions=%s",
                Integer.toHexString(widget.getId()),
                op,
                nameMatches,
                listener == null ? null : java.util.Arrays.toString(listener),
                widget.getName(),
                widget.getText(),
                widget.getActions() == null ? null : java.util.Arrays.toString(widget.getActions())));

            if (op <= 0 || !nameMatches)
            {
                continue;
            }

            if (listener == null || listener.length == 0)
            {
                continue;
            }

            int score = 0;
            if (expectedType != Integer.MIN_VALUE && listenerMessageType(listener) == expectedType)
            {
                score += 4;
            }
            if (!expectedBody.isEmpty() && expectedBody.equals(normalizeBody(widget.getText())))
            {
                score += 8;
            }

            if (score > bestScore)
            {
                best = widget;
                bestScore = score;
            }
        }

        if (best == null && diagnostics != null)
        {
            final int cap = 100;
            List<String> shown = diagnostics.size() > cap
                ? new ArrayList<>(diagnostics.subList(0, cap))
                : diagnostics;
            log.debug("chatmenu: unable to resolve {} for {} (msg type={}, body={}); {} candidates, showing {}:\n{}",
                action, normalizedUsername,
                expectedType == Integer.MIN_VALUE ? null : expectedType, expectedBody,
                diagnostics.size(), shown.size(),
                String.join("\n", shown));
        }
        else
        {
            log.debug("chatmenu: resolved {} for {} to {}", action, normalizedUsername,
                best == null ? null : Integer.toHexString(best.getId()));
        }

        return best;
    }

    private static void collectWidget(Widget widget, List<Widget> out)
    {
        collectWidget(widget, out, 0);
    }

    private static void collectWidget(Widget widget, List<Widget> out, int depth)
    {
        if (widget == null || depth > 3)
        {
            return;
        }
        out.add(widget);
        Widget[] children = widget.getChildren();
        if (children != null)
        {
            for (Widget child : children)
            {
                if (child != null)
                {
                    collectWidget(child, out, depth + 1);
                }
            }
        }
    }

    private MessageNode findMessageNode(int messageId)
    {
        if (messageId < 0 || client.getMessages() == null)
        {
            return null;
        }

        try
        {
            return client.getMessages().get(messageId);
        }
        catch (Throwable ex)
        {
            log.debug("Unable to resolve MessageNode {}", messageId, ex);
            return null;
        }
    }

    /**
     * Returns the 1-based op for the given action in the widget's actions array, or {@code -1}.
     * The op of a widget menu entry is its 1-based position in {@link Widget#getActions()},
     * not a fixed constant; it must be derived from the actions the game has populated.
     */
    private static int actionOp(String[] actions, String action)
    {
        if (actions == null)
        {
            return -1;
        }

        final String wanted = Text.removeTags(action).trim().toLowerCase(Locale.ENGLISH);
        for (int i = 0; i < actions.length; i++)
        {
            String candidate = Text.removeTags(actions[i]).trim().toLowerCase(Locale.ENGLISH);
            if (wanted.equals(candidate)
                || (wanted.startsWith("report") && candidate.startsWith("report")))
            {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Chooses the op to dispatch for the action. Prefers the action's position in the
     * widget's actions array when the game has populated one, and otherwise falls back
     * to the fixed ops the game's dynamically generated chat-line menu uses.
     */
    private static int opForAction(String[] actions, String action)
    {
        int derived = actionOp(actions, action);
        if (derived > 0)
        {
            return derived;
        }
        switch (action)
        {
            case ADD_IGNORE:
                return OP_ADD_IGNORE;
            case ADD_FRIEND:
                return OP_ADD_FRIEND;
            case REPORT:
                return OP_REPORT;
            default:
                return -1;
        }
    }

    private static boolean matchesUsername(Widget widget, String normalizedUsername)
    {
        if (normalizedUsername.isEmpty())
        {
            return false;
        }

        String name = normalize(widget.getName());
        if (normalizedUsername.equals(name))
        {
            return true;
        }

        String text = normalizeSenderText(widget.getText());
        if (normalizedUsername.equals(text))
        {
            return true;
        }

        // The line widget may hold the full "name: message" text rather than the sender alone.
        if (text.startsWith(normalizedUsername) && text.length() > normalizedUsername.length())
        {
            char boundary = text.charAt(normalizedUsername.length());
            return boundary == ':' || boundary == ' ' || boundary == '-';
        }
        return false;
    }

    private static int listenerMessageType(Object[] listener)
    {
        Object value = listener[listener.length - 1];
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static String normalizeSenderText(String value)
    {
        String normalized = normalize(value);
        while (normalized.endsWith(":"))
        {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static String normalizeBody(String value)
    {
        if (value == null)
        {
            return "";
        }
        return Text.removeTags(value).replace('\u00A0', ' ').trim();
    }

    private static String normalize(String value)
    {
        return value == null ? "" : Text.standardize(value);
    }

    private void lookupPlayer(String username, int messageId)
    {
        HiscorePlugin hiscorePlugin = findActiveHiscorePlugin();
        if (hiscorePlugin == null)
        {
            notifyUnavailable("Enable the HiScore plugin to look up " + username + ".");
            return;
        }

        try
        {
            Method getWorldEndpoint = HiscorePlugin.class.getDeclaredMethod("getWorldEndpoint");
            Method lookupPlayer = HiscorePlugin.class.getDeclaredMethod(
                "lookupPlayer", String.class, HiscoreEndpoint.class);
            getWorldEndpoint.setAccessible(true);
            lookupPlayer.setAccessible(true);

            HiscoreEndpoint endpoint = (HiscoreEndpoint) getWorldEndpoint.invoke(hiscorePlugin);
            if (endpoint == null)
            {
                notifyUnavailable("HiScore lookup is unavailable right now.");
                return;
            }
            endpoint = resolveChatHiscoreEndpoint(username, messageId, endpoint);
            lookupPlayer.invoke(hiscorePlugin, Text.removeTags(username), endpoint);
        }
        catch (ReflectiveOperationException | RuntimeException ex)
        {
            Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null
                ? ex.getCause()
                : ex;
            log.warn("Unable to invoke the HiScore lookup for {}", username, cause);
            notifyUnavailable("HiScore lookup is unavailable right now.");
        }
    }

    /**
     * Match the HiScore plugin's chat-menu endpoint selection. Account icons on a chat name take
     * precedence over the current world's endpoint; a name without a league icon on a seasonal
     * world is treated as a normal-world player.
     */
    private HiscoreEndpoint resolveChatHiscoreEndpoint(
        String username, int messageId, HiscoreEndpoint worldEndpoint)
    {
        MessageNode messageNode = findMessageNode(messageId);
        String rawName = messageNode != null && messageNode.getName() != null
            ? messageNode.getName()
            : username;

        HiscoreEndpoint chatEndpoint = HiscoreEndpoint.NORMAL;
        if (rawName.contains(IconID.IRONMAN.toString()))
        {
            chatEndpoint = HiscoreEndpoint.IRONMAN;
        }
        else if (rawName.contains(IconID.ULTIMATE_IRONMAN.toString()))
        {
            chatEndpoint = HiscoreEndpoint.ULTIMATE_IRONMAN;
        }
        else if (rawName.contains(IconID.HARDCORE_IRONMAN.toString()))
        {
            chatEndpoint = HiscoreEndpoint.HARDCORE_IRONMAN;
        }
        else if (rawName.contains(IconID.LEAGUE.toString()))
        {
            chatEndpoint = HiscoreEndpoint.SEASONAL;
        }

        return chatEndpoint != HiscoreEndpoint.NORMAL || worldEndpoint == HiscoreEndpoint.SEASONAL
            ? chatEndpoint
            : worldEndpoint;
    }

    private HiscorePlugin findActiveHiscorePlugin()
    {
        Collection<Plugin> plugins = pluginManager.getPlugins();
        if (plugins == null)
        {
            return null;
        }

        for (Plugin plugin : plugins)
        {
            if (plugin instanceof HiscorePlugin && pluginManager.isPluginActive(plugin))
            {
                return (HiscorePlugin) plugin;
            }
        }
        return null;
    }

    private void copyUsername(String username)
    {
        try
        {
            StringSelection selection = new StringSelection(Text.removeTags(username));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        }
        catch (Throwable ex)
        {
            log.warn("Unable to copy username {} to the clipboard", username, ex);
            notifyUnavailable("Unable to copy " + username + " to the clipboard.");
        }
    }

    private void notifyUnavailable(String message)
    {
        try
        {
            notificationService.pushChatMessage(message);
        }
        catch (Throwable ex)
        {
            log.debug("Unable to show player-menu notification: {}", message, ex);
        }
    }
}