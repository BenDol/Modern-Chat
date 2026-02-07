package com.modernchat.overlay;

import com.modernchat.ModernChatConfig;
import com.modernchat.common.ChatProxy;
import com.modernchat.common.MessageLine;
import com.modernchat.util.ClientUtil;
import net.runelite.api.ChatMessageType;

import javax.inject.Inject;
import javax.inject.Provider;
import java.awt.Rectangle;

public class ChatPeekOverlay extends MessageContainer
{
    @Inject private ChatProxy chatProxy;
    @Inject private ModernChatConfig mainConfig;
    @Inject private Provider<ChatOverlay> chatOverlayProvider;

    public ChatPeekOverlay() {
        setCanShowDecider((c) -> {
            return !isHidden() && (chatProxy == null ||
                (chatProxy.isHidden() && chatProxy.isLegacyHidden() && !ClientUtil.isSystemWidgetActive(client)));
        });
        setBoundsProvider(() -> chatProxy != null ? chatProxy.getBounds() : null);
    }

    @Override
    protected Rectangle calculateViewPort(Rectangle r) {
        Rectangle viewPort = super.calculateViewPort(r);
        return new Rectangle(
            (config.isFollowChatBox() ? viewPort.x : 0),
            (config.isFollowChatBox() ? viewPort.y : (client.getCanvasHeight() - viewPort.height)),
            viewPort.width, viewPort.height);
    }

    @Override
    public void pushLine(MessageLine line) {
        super.pushLine(line);

        if (canAutoResetFade(line.getType(), false)) {
            resetFade();
        }
    }

    @Override
    public void pushLine(String s, ChatMessageType type, long timestamp, String sender,
                         String receiver, String targetName, String prefix)
    {
        super.pushLine(s, type, timestamp, sender, receiver, targetName, prefix);

        if (canAutoResetFade(type, false)) {
            resetFade();
        }
    }

    public boolean canAutoResetFade(ChatMessageType type, boolean isCollapsed) {
        if (isCollapsed || type == ChatMessageType.AUTOTYPER || type == ChatMessageType.SPAM) {
            return false;
        }

        return true;
    }
}
