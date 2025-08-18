package com.modernchat.service;

import com.modernchat.common.LazyLoad;
import com.modernchat.common.Sfx;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.util.Map;

import static java.util.Map.entry;

@Slf4j
@Singleton
public class SoundService implements ChatService
{
    private static final String BASE_PATH = "/com/modernchat/sounds/";

    private Map<Sfx, LazyLoad<Clip>> defaultSounds = null;

    @Override
    public void startUp() {
        try {
            defaultSounds = Map.ofEntries(
                entry(Sfx.MSG_RECEIVED_1, lazyLoadClip(BASE_PATH + "message_received_1.wav")),
                entry(Sfx.MSG_RECEIVED_2, lazyLoadClip(BASE_PATH + "message_received_2.wav")),
                entry(Sfx.MSG_RECEIVED_3, lazyLoadClip(BASE_PATH + "message_received_3.wav")),
                entry(Sfx.MSG_RECEIVED_4, lazyLoadClip(BASE_PATH + "message_received_4.wav"))
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to load sound files", e);
        }
    }

    @Override
    public void shutDown() {
        defaultSounds.clear();
    }

    public Clip getSound(Sfx sound) {
        if (defaultSounds == null) {
            throw new IllegalStateException("SoundService not started up yet");
        }
        LazyLoad<Clip> lazyLoad = defaultSounds.get(sound);
        if (lazyLoad == null) {
            throw new IllegalArgumentException("Sound not found: " + sound);
        }
        Clip clip = lazyLoad.get();
        if (clip == null) {
            throw new IllegalStateException("Sound clip is null for: " + sound);
        }
        return clip;
    }

    private LazyLoad<Clip> lazyLoadClip(String resourcePath) {
        return new LazyLoad<>(() -> {
            try {
                return loadClip(resourcePath);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load sound: " + resourcePath, e);
            }
        });
    }

    private Clip loadClip(String resourcePath) throws Exception {
        try (var raw = getClass().getResourceAsStream(resourcePath)) {
            if (raw == null)
                throw new IllegalStateException("Missing sound: " + resourcePath);

            try (AudioInputStream src = AudioSystem.getAudioInputStream(raw)) {
                AudioFormat base = src.getFormat();
                AudioFormat target = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(),
                    16,
                    base.getChannels(),
                    base.getChannels() * 2,
                    base.getSampleRate(),
                    false
                );

                try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, src)) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(pcm);
                    return clip;
                }
            }
        }
    }
}
