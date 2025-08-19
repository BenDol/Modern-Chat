package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import com.modernchat.ModernChatConfigBase;
import com.modernchat.common.Sfx;
import com.modernchat.event.NotificationEvent;
import com.modernchat.service.SoundService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.modernchat.ModernChatConfigBase.Field.*;

@Slf4j
@Singleton
public class NotificationChatFeature extends AbstractChatFeature<NotificationChatFeature.NotificationChatFeatureConfig>
{
    @Override
    public String getConfigGroup() {
        return "featureNotify";
    }

    public interface NotificationChatFeatureConfig extends ChatFeatureConfig {
        boolean featureNotify_Enabled();
        boolean featureNotify_SoundEnabled();
        boolean featureNotify_UseRuneLiteSound();
        int featureNotify_VolumePercent();
        Sfx featureNotify_MessageReceivedSfx();
        boolean featureNotify_OnPublicMessage();
        boolean featureNotify_OnPrivateMessage();
        boolean featureNotify_OnFriendsChat();
        boolean featureNotify_OnClan();
    }

    @Inject private Client client;
    @Inject private Notifier notifier;
    @Inject private SoundService soundService;
    @Inject private AudioPlayer audioPlayer;

    private long lastPlayMs = 0L;

    @Inject
    public NotificationChatFeature(ModernChatConfig config, EventBus eventBus) {
        super(config, eventBus);
    }

    @Override
    protected NotificationChatFeatureConfig partitionConfig(ModernChatConfig config) {
        return new NotificationChatFeatureConfig() {
            @Override public boolean featureNotify_Enabled() { return config.featureNotify_Enabled(); }
            @Override public boolean featureNotify_SoundEnabled() { return config.featureNotify_SoundEnabled(); }
            @Override public boolean featureNotify_UseRuneLiteSound() { return config.featureNotify_UseRuneLiteSound(); }
            @Override public int featureNotify_VolumePercent() { return config.featureNotify_VolumePercent(); }
            @Override public Sfx featureNotify_MessageReceivedSfx() { return config.featureNotify_MessageReceivedSfx(); }
            @Override public boolean featureNotify_OnPublicMessage() { return config.featureNotify_OnPublicMessage(); }
            @Override public boolean featureNotify_OnPrivateMessage() { return config.featureNotify_OnPrivateMessage(); }
            @Override public boolean featureNotify_OnFriendsChat() { return config.featureNotify_OnFriendsChat(); }
            @Override public boolean featureNotify_OnClan() { return config.featureNotify_OnClan(); }
        };
    }

    @Override
    public boolean isEnabled() {
        return config.featureNotify_Enabled();
    }

    @Override
    public void startUp() {
        super.startUp();
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        super.shutDown(fullShutdown);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().startsWith(ModernChatConfigBase.GROUP)) {
            return;
        }

        if (e.getKey().equalsIgnoreCase(NOTIFY_MESSAGE_RECEIVED_SFX.key)) {
            try {
                Sfx sfx = Sfx.valueOf(e.getNewValue());
                playSfxPreview(sfx);
            } catch (Exception ex) {
                log.debug("Preview SFX parse failed for '{}': {}", e.getNewValue(), ex.getMessage());
            }
        }
    }

    @Subscribe
    public void onNotificationEvent(NotificationEvent e) {
        if (!isEnabled())
            return;

        // Filter by chat types
        ChatMessageType type = (ChatMessageType) e.getKey();
        switch (type) {
            case PUBLICCHAT:
                if (!config.featureNotify_OnPublicMessage())
                    return;
                break;
            case PRIVATECHAT:
                if (!config.featureNotify_OnPrivateMessage())
                    return;
                break;
            case FRIENDSCHAT:
            case FRIENDSCHATNOTIFICATION:
                if (!config.featureNotify_OnFriendsChat())
                    return;
                break;
            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
                if (!config.featureNotify_OnClan())
                    return;
                break;
            default:
                log.debug("Notification ignored for unsupported type: {}", type);
                return;
        }

        if (!e.isAllowSound() || !config.featureNotify_SoundEnabled())
            return;

        // throttle to avoid spam
        long now = System.currentTimeMillis();
        if (now - lastPlayMs < 300)
            return;
        lastPlayMs = now;

        if (config.featureNotify_UseRuneLiteSound()) {
            notifier.notify("New message");
        } else {
            playSfx(config.featureNotify_MessageReceivedSfx(), config.featureNotify_VolumePercent());
        }
    }

    private void playSfxPreview(Sfx sfx) {
        playSfx(sfx, config.featureNotify_VolumePercent());
    }

    private void playSfx(Sfx sfx, int volumePercent) {
        try {
            soundService.play(audioPlayer, sfx, volumePercent);
        } catch (Exception ex) {
            log.debug("Failed to play SFX {}: {}", sfx, ex.toString());
        }
    }
}
