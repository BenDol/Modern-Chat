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
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

import static com.modernchat.ModernChatConfigBase.Field.*;
import static com.modernchat.feature.NotificationChatFeature.*;

@Slf4j
@Singleton
public class NotificationChatFeature extends AbstractChatFeature<NotificationChatFeatureConfig>
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

    // Audio state
    private Clip notifyClip;
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

        if (notifyClip != null) {
            try {
                notifyClip.stop();
                notifyClip.close();
            } catch (Exception ignored) {}
            notifyClip = null;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged e) {
        if (!e.getGroup().startsWith(ModernChatConfigBase.GROUP)) {
            return;
        }

        if (e.getKey().equalsIgnoreCase(NOTIFY_MESSAGE_RECEIVED_SFX.key)) {
            playSfx(Sfx.valueOf(e.getNewValue()));
        }
    }

    @Subscribe
    public void onNotificationEvent(NotificationEvent e) {
        NotificationChatFeatureConfig c = config;

        switch ((ChatMessageType) e.getKey()) {
            case PUBLICCHAT:
                if (!c.featureNotify_OnPublicMessage())
                    return;
                break;
            case PRIVATECHAT:
                if (!c.featureNotify_OnPrivateMessage())
                    return;
                break;
            case FRIENDSCHAT:
            case FRIENDSCHATNOTIFICATION:
                if (!c.featureNotify_OnFriendsChat())
                    return;
                break;
            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
                if (!c.featureNotify_OnClan())
                    return;
                break;
            default:
                log.debug("Received unsupported notification event: {}", e.getKey());
                return;
        }

        if (e.isAllowSound() && c.featureNotify_SoundEnabled()) {
            // throttle to avoid spam
            long now = System.currentTimeMillis();
            if (now - lastPlayMs < Math.max(0, 300))
                return;
            lastPlayMs = now;

            if (c.featureNotify_UseRuneLiteSound()) {
                // respects users RL notification settings
                notifier.notify("New message");
            } else {
                playSfx(config.featureNotify_MessageReceivedSfx(), percentToDb(c.featureNotify_VolumePercent()));
            }
        }
    }

    private void playSfx(Sfx sfx) {
        playSfx(sfx, percentToDb(config.featureNotify_VolumePercent()));
    }

    private void playSfx(Sfx sfx, float gainDb) {
        if (notifyClip != null)
            stopClip(notifyClip);

        notifyClip = soundService.getSound(sfx);
        playClip(notifyClip, gainDb);
    }

    private static void stopClip(Clip clip) {
        if (clip == null)
            return;
        try {
            if (clip.isRunning())
                clip.stop();
            clip.setFramePosition(0);
        } catch (Exception ignored) { }
    }

    private static void playClip(Clip clip, float gainDb) {
        if (clip == null)
            return;
        try {
            setClipVolume(clip, gainDb);
            stopClip(clip);
            clip.start();
        } catch (Exception ignored) { }
    }

    private static float percentToDb(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        if (p == 0)
            return -80.0f; // effectively silent
        return (float)(20.0 * Math.log10(p / 100.0));
    }

    private static void setClipVolume(Clip clip, float gainDb) {
        if (clip == null)
            return;
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl ctrl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float clamped = Math.max(ctrl.getMinimum(), Math.min(ctrl.getMaximum(), gainDb));
                ctrl.setValue(clamped);
            }
        } catch (Exception ignored) { }
    }
}
