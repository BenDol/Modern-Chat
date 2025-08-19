package com.modernchat.service;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedSprite;
import net.runelite.client.game.SpriteManager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class ImageService implements ChatService
{
    public static final Pattern IMG_TAG = Pattern.compile("(?i)<img=(\\d+)>");

    private final Map<Integer, Image> modIconCache = new ConcurrentHashMap<>();

    @Inject private Client client;
    @Inject private SpriteManager spriteManager;

    @Override
    public void startUp() {

    }

    @Override
    public void shutDown() {

    }

    public @Nullable Image getModIcon(int id) {
        if (id < 0)
            return null;
        Image cached = modIconCache.get(id);
        if (cached != null)
            return cached;

        IndexedSprite[] icons = client.getModIcons();
        if (icons == null || id >= icons.length)
            return null;

        BufferedImage img = indexedToBufferedImage(icons[id]);
        modIconCache.put(id, img);
        return img;
    }

    public boolean isValidModIcon(int icon) {
        if (icon < 0)
            return false;
        Image cached = modIconCache.get(icon);
        if (cached != null)
            return true;

        IndexedSprite[] icons = client.getModIcons();
        return icons != null && icon < icons.length;
    }

    public static BufferedImage indexedToBufferedImage(IndexedSprite s) {
        if (s == null) return null;

        final int w = s.getWidth();
        final int h = s.getHeight();
        final int[] out = new int[w * h];

        final byte[] pix = s.getPixels();
        final int[] pal  = s.getPalette(); // ARGB-ish ints from the client

        // Note: index 0 is usually transparent in RS sprites
        for (int i = 0; i < pix.length && i < out.length; i++) {
            int idx = pix[i] & 0xFF;
            if (idx == 0) {
                out[i] = 0x00000000; // transparent
            } else {
                int argb = pal[idx];
                // Ensure alpha set (some palettes have 0 alpha)
                if ((argb & 0xFF000000) == 0) argb |= 0xFF000000;
                out[i] = argb;
            }
        }

        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, w, h, out, 0, w);
        return bi;
    }
}
