package com.modernchat.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Expands the game's text macros (e.g. {@code @mes_hl_red@}, {@code @gre@}) in chat messages.
 * <p>
 * Game messages carry these macros raw; the vanilla chatbox swaps them for {@code <col=...>}
 * tags using a translation table the game's scripts fill at login and whenever the chatbox
 * transparency changes. Modern Chat draws messages itself, so it has to expand them too.
 * Player-typed text never contains a raw '@' (the game escapes it as {@code <at>}, see
 * RuneLite's {@code Text.escapeJagex}), so any '@' left in a message belongs to a macro and
 * the whole message can be expanded.
 * <p>
 * The game's own table ({@link Client#macroExpand}) is the source of truth, so macros Jagex
 * adds later keep working. The one exception is the set of colors the game swaps by chatbox
 * transparency: the game picks dark shades for the parchment of the opaque chatbox, which are
 * unreadable on Modern Chat's dark backdrop, so those are swapped for the transparent-chatbox
 * shades before the game expands the rest.
 */
@Slf4j
public final class ChatMacroUtil {

    /**
     * Shades the game uses for these macros when the chatbox is transparent, i.e. text drawn
     * over a dark surface (clientscript 9790). These are the fixed colors the game swaps by
     * chatbox transparency; every other macro resolves through the game's own table. The one
     * transparency-dependent macro not listed is league_txt, whose shades come from the
     * current league's data rather than a fixed color.
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

    private ChatMacroUtil() {
    }

    /**
     * Expands the macros in a chat message. Must be called on the client thread.
     */
    public static String expand(Client client, String text) {
        return expand(text, client::macroExpand);
    }

    /**
     * Swaps in the dark-surface shades, then hands the whole message to {@code gameExpander}
     * for every other macro. Messages without a macro are returned as-is.
     *
     * @param gameExpander expands every macro in a string, like {@link Client#macroExpand}
     */
    static String expand(@Nullable String text, UnaryOperator<String> gameExpander) {
        if (text == null || text.indexOf('@') < 0) return text;

        String shaded = applyDarkSurfaceShades(text);
        try {
            String expanded = gameExpander.apply(shaded);
            return expanded != null ? expanded : shaded;
        } catch (RuntimeException ex) {
            // Never lose the message over a macro: show it with the shades applied instead.
            log.debug("Failed to expand chat macros in {}", shaded, ex);
            return shaded;
        }
    }

    /**
     * Replaces the macros in {@link #DARK_SURFACE_MACROS}, leaving every other macro for the
     * game. Macros pair left to right, so "@gre@@mes_hl_red@" is "@gre@" then "@mes_hl_red@".
     */
    private static String applyDarkSurfaceShades(String text) {
        StringBuilder out = null;
        int copied = 0;
        int start = text.indexOf('@');
        while (start >= 0) {
            int end = text.indexOf('@', start + 1);
            if (end < 0) break;

            if (!isMacroKey(text, start + 1, end)) {
                // Not a macro: its closing '@' may still open one
                start = end;
                continue;
            }

            String shade = DARK_SURFACE_MACROS.get(text.substring(start + 1, end));
            if (shade != null) {
                if (out == null) out = new StringBuilder(text.length() + 16);
                out.append(text, copied, start).append(shade);
                copied = end + 1;
            }
            start = text.indexOf('@', end + 1);
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
}
