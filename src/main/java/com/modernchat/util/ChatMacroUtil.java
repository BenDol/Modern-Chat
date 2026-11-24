package com.modernchat.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Expands the game's text macros (e.g. {@code @mes_hl_red@}, {@code @gre@}) in chat messages.
 * <p>
 * Game messages carry these macros raw; the vanilla chatbox swaps them for {@code <col=...>}
 * tags using a translation table the game's scripts fill at login and whenever the chatbox
 * transparency changes. Modern Chat draws messages itself, so it has to expand them too.
 * <p>
 * The game's own table ({@link Client#macroExpand}) is the source of truth, so macros Jagex
 * adds later keep working. The one exception is the set of colors the game swaps by chatbox
 * transparency: the game picks dark shades for the parchment of the opaque chatbox, which are
 * unreadable on Modern Chat's dark backdrop, so those always use the transparent-chatbox shades.
 */
@Slf4j
public final class ChatMacroUtil {

    /**
     * Shades the game uses for these macros when the chatbox is transparent, i.e. text drawn
     * over a dark surface (clientscript 9790). Only the macros the game varies by chatbox
     * transparency are listed; every other macro resolves through the game's own table.
     */
    private static final Map<String, String> DARK_SURFACE_MACROS;

    static {
        Map<String, String> m = new HashMap<>();
        m.put("mes_hl_red", "<col=ff3045>");
        m.put("mes_hl_gre", "<col=229628>");
        m.put("mes_hl_pur", "<col=a53fff>");
        m.put("mes_hl_mag", "<col=ff289d>");
        m.put("mes_hl_blu", "<col=3366ff>");
        m.put("mes_hl_cya", "<col=00e6e6>");
        m.put("mes_hl_ora", "<col=ff8e32>");
        m.put("sail_txt", "<col=0090bc>");
        m.put("tblock_txt", "<col=c356ef>");
        m.put("tempoross_warn", "<col=ff9e7a>");
        m.put("tempoross_info", "<col=98ffff>");
        m.put("tempoross_win", "<col=ff9fff>");
        m.put("tempoross_default", "<col=ff9e7a>");
        m.put("sw_mes_red", "<col=ff3232>");
        m.put("sw_mes_blu", "<col=3366ff>");
        m.put("sw_mes_pur", "<col=3366ff>");
        m.put("q_mes", "<col=0ab0ff>");
        m.put("ap_mes", "<col=008cff>");
        m.put("league_task_comp", "<col=0cc919>");
        m.put("xmas_mes", "<col=0cc919>");
        m.put("ach_comp", "<col=0cc919>");
        m.put("hcm_wf", "<col=a67c00>");
        DARK_SURFACE_MACROS = Collections.unmodifiableMap(m);
    }

    /**
     * Message types whose text a player typed. These are shown as typed: a player writing
     * "@red@" should not be able to recolor their own message.
     */
    private static final Set<ChatMessageType> PLAYER_AUTHORED_TYPES = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.AUTOTYPER,
        ChatMessageType.MODAUTOTYPER,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.PRIVATECHATOUT,
        ChatMessageType.MODPRIVATECHAT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT
    );

    private ChatMacroUtil() {
    }

    /**
     * Expands the macros in a chat message of the given type. Player-typed messages are
     * returned unchanged. Must be called on the client thread.
     */
    public static String expand(Client client, @Nullable ChatMessageType type, String text) {
        if (type != null && PLAYER_AUTHORED_TYPES.contains(type)) return text;
        return expand(text, client::macroExpand);
    }

    /**
     * Expands every macro in {@code text}. Dark-surface shades win; any other macro is passed
     * to {@code gameExpander} on its own. A macro neither side knows is left as written, the
     * same as the game does.
     *
     * @param gameExpander resolves a single {@code @key@} token, returning it unchanged when unknown
     */
    static String expand(@Nullable String text, UnaryOperator<String> gameExpander) {
        if (text == null || text.indexOf('@') < 0) return text;

        StringBuilder out = null;
        int copied = 0;
        int start = text.indexOf('@');
        while (start >= 0) {
            int end = text.indexOf('@', start + 1);
            if (end < 0) break;

            String replacement = null;
            if (isMacroKey(text, start + 1, end)) {
                String token = text.substring(start, end + 1);
                replacement = DARK_SURFACE_MACROS.get(token.substring(1, token.length() - 1));
                if (replacement == null) replacement = expandWithGame(gameExpander, token);
                if (replacement.equals(token)) replacement = null;
            }

            if (replacement == null) {
                // Not a macro (or one nobody knows): keep it as written. Its closing '@' may
                // open the next macro, as in "user@mail @mes_hl_red@".
                start = end;
                continue;
            }

            if (out == null) out = new StringBuilder(text.length() + 16);
            out.append(text, copied, start).append(replacement);
            copied = end + 1;
            start = text.indexOf('@', copied);
        }

        if (out == null) return text;
        return out.append(text, copied, text.length()).toString();
    }

    /** True when {@code text[from, to)} is a non-empty run of {@code [a-z0-9_]}. */
    private static boolean isMacroKey(String text, int from, int to) {
        if (from >= to) return false;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) return false;
        }
        return true;
    }

    private static String expandWithGame(UnaryOperator<String> gameExpander, String token) {
        try {
            String expanded = gameExpander.apply(token);
            return expanded != null ? expanded : token;
        } catch (RuntimeException ex) {
            // Never lose the message over a macro: show it unexpanded instead.
            log.debug("Failed to expand chat macro {}", token, ex);
            return token;
        }
    }
}
