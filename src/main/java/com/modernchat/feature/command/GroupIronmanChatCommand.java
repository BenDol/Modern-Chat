package com.modernchat.feature.command;

import com.modernchat.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Text;

import javax.annotation.Nullable;

/**
 * /g, /gim and /group send the rest of the line to group ironman group chat.
 * <p>
 * The game has to send the message itself, so this command is not handled after
 * submission like the others: GameChatBridge asks {@link CommandsChatFeature#resolveGroupChatAlias}
 * before the game's chat script runs, and sends the message body to group chat.
 */
@Slf4j
public class GroupIronmanChatCommand extends AbstractChatCommand {

    /**
     * The message after the command word, or null when there is none.
     */
    public @Nullable String extractMessage(String raw) {
        if (StringUtil.isNullOrEmpty(raw))
            return null;

        String stripped = Text.removeTags(raw).trim();
        int space = stripped.indexOf(' ');
        if (space < 0)
            return null;

        String text = stripped.substring(space + 1).trim();
        return text.isEmpty() ? null : text;
    }
}
