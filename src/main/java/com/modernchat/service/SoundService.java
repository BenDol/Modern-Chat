package com.modernchat.service;

import com.modernchat.common.LazyLoad;
import com.modernchat.common.Sfx;
import com.modernchat.util.MathUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;

import static java.util.Map.entry;

@Slf4j
@Singleton
public class SoundService implements ChatService
{
    private static final String BASE_PATH = "/com/modernchat/sounds/";

    private Map<Sfx, LazyLoad<byte[]>> defaultSounds = null;

    @Override
    public void startUp() {
        defaultSounds = Map.ofEntries(
            entry(Sfx.MSG_RECEIVED_1, lazyLoadBytes(BASE_PATH + "message_received_1.wav")),
            entry(Sfx.MSG_RECEIVED_2, lazyLoadBytes(BASE_PATH + "message_received_2.wav")),
            entry(Sfx.MSG_RECEIVED_3, lazyLoadBytes(BASE_PATH + "message_received_3.wav")),
            entry(Sfx.MSG_RECEIVED_4, lazyLoadBytes(BASE_PATH + "message_received_4.wav"))
        );
    }

    @Override
    public void shutDown() {
        defaultSounds = null;
    }

    public void play(AudioPlayer player, Sfx sfx, int volumePercent) {
        if (player == null)
            return;

        byte[] data = getSoundBytes(sfx);
        if (data == null || data.length == 0) {
            log.debug("No audio data for SFX {}", sfx);
            return;
        }
        float volume = MathUtil.clamp01(volumePercent / 100f);
        try {
            player.play(new ByteArrayInputStream(data), volume);
        } catch (Throwable t) {
            log.debug("Failed to play SFX {}: {}", sfx, t.toString());
        }
    }

    private LazyLoad<byte[]> lazyLoadBytes(String resourcePath) {
        return new LazyLoad<>(() -> {
            try {
                return loadBytes(resourcePath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load sound bytes: " + resourcePath, e);
            }
        });
    }

    private byte[] loadBytes(String resourcePath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing sound resource: " + resourcePath);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(4096, in.available()));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private byte[] getSoundBytes(Sfx sound) {
        if (defaultSounds == null) {
            throw new IllegalStateException("SoundService not started up yet");
        }
        LazyLoad<byte[]> lazy = defaultSounds.get(sound);
        if (lazy == null) {
            log.debug("Unknown SFX {}; available: {}", sound, defaultSounds.keySet());
            return null;
        }
        return lazy.get();
    }
}
