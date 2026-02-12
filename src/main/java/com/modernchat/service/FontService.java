package com.modernchat.service;

import com.modernchat.common.FontStyle;
import com.modernchat.common.LazyLoad;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.FontManager;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Map.entry;

@Slf4j
@Singleton
public class FontService implements ChatService
{
    private static final String BASE_PATH = "/com/modernchat/fonts/";

    @Getter
    private Map<FontStyle, LazyLoad<Font>> defaultFontsMap = null;

    private final Map<String, LazyLoad<Font>> customFontsMap = new ConcurrentHashMap<>();

    @Override
    public void startUp() {
        defaultFontsMap = Map.ofEntries(
            entry(FontStyle.RUNE,              lazyLoadFont(FontManager::getRunescapeFont)),
            entry(FontStyle.RUNE_SMALL,        lazyLoadFont(FontManager::getRunescapeSmallFont)),
            entry(FontStyle.RUNE_BOLD,         lazyLoadFont(FontManager::getRunescapeBoldFont)),
            // Open Sans
            entry(FontStyle.OPEN_SANS,         lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans-Regular.ttf")),
            entry(FontStyle.OPEN_SANS_LIGHT,   lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans-Light.ttf")),
            entry(FontStyle.OPEN_SANS_MED,     lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans-Medium.ttf")),
            entry(FontStyle.OPEN_SANS_BOLD,    lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans-Bold.ttf")),
            entry(FontStyle.OPEN_SANS_D,       lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans_Condensed-Regular.ttf")),
            entry(FontStyle.OPEN_SANS_D_LIGHT, lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans_Condensed-Light.ttf")),
            entry(FontStyle.OPEN_SANS_D_MED,   lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans_Condensed-Medium.ttf")),
            entry(FontStyle.OPEN_SANS_D_BOLD,  lazyLoadFont(BASE_PATH + "Open_Sans/OpenSans_Condensed-Bold.ttf")),
            // Roboto
            entry(FontStyle.ROBOTO_D,          lazyLoadFont(BASE_PATH + "Roboto/RobotoCondensed-Regular.ttf")),
            entry(FontStyle.ROBOTO_D_MED,      lazyLoadFont(BASE_PATH + "Roboto/RobotoCondensed-Medium.ttf")),
            entry(FontStyle.ROBOTO_D_LIGHT,    lazyLoadFont(BASE_PATH + "Roboto/RobotoCondensed-Light.ttf")),
            entry(FontStyle.ROBOTO_D_THIN,     lazyLoadFont(BASE_PATH + "Roboto/RobotoCondensed-Thin.ttf")),
            entry(FontStyle.ROBOTO_D_BOLD,     lazyLoadFont(BASE_PATH + "Roboto/RobotoCondensed-Bold.ttf")),
            entry(FontStyle.ROBOTO_D_BLACK,    lazyLoadFont(BASE_PATH + "Roboto/RobotoCondensed-Black.ttf")),
            // Source Sans 3
            entry(FontStyle.SRC_SANS,          lazyLoadFont(BASE_PATH + "Source_Sans_3/SourceSans3-Regular.ttf")),
            entry(FontStyle.SRC_SANS_BLACK,    lazyLoadFont(BASE_PATH + "Source_Sans_3/SourceSans3-Black.ttf")),
            entry(FontStyle.SRC_SANS_BOLD,     lazyLoadFont(BASE_PATH + "Source_Sans_3/SourceSans3-Bold.ttf")),
            entry(FontStyle.SRC_SANS_LIGHT,    lazyLoadFont(BASE_PATH + "Source_Sans_3/SourceSans3-Light.ttf")),
            entry(FontStyle.SRC_SANS_MED,      lazyLoadFont(BASE_PATH + "Source_Sans_3/SourceSans3-Medium.ttf")),
            // IBM Plex Sans
            entry(FontStyle.IBM_PLEX,          lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans-Regular.ttf")),
            entry(FontStyle.IBM_PLEX_BOLD,     lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans-Bold.ttf")),
            entry(FontStyle.IBM_PLEX_LIGHT,    lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans-Light.ttf")),
            entry(FontStyle.IBM_PLEX_MED,      lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans-Medium.ttf")),
            entry(FontStyle.IBM_PLEX_THIN,     lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans-Thin.ttf")),
            entry(FontStyle.IBM_PLEX_D,        lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Regular.ttf")),
            entry(FontStyle.IBM_PLEX_D_BOLD,   lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Bold.ttf")),
            entry(FontStyle.IBM_PLEX_D_LIGHT,  lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Light.ttf")),
            entry(FontStyle.IBM_PLEX_D_MED,    lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Medium.ttf")),
            entry(FontStyle.IBM_PLEX_D_THIN,   lazyLoadFont(BASE_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Thin.ttf")),
            // Fira Sans
            entry(FontStyle.FIRA_SANS,         lazyLoadFont(BASE_PATH + "Fira_Sans/FiraSans-Regular.ttf")),
            entry(FontStyle.FIRA_SANS_BOLD,    lazyLoadFont(BASE_PATH + "Fira_Sans/FiraSans-Bold.ttf")),
            entry(FontStyle.FIRA_SANS_BLACK,   lazyLoadFont(BASE_PATH + "Fira_Sans/FiraSans-Black.ttf")),
            entry(FontStyle.FIRA_SANS_LIGHT,   lazyLoadFont(BASE_PATH + "Fira_Sans/FiraSans-Light.ttf")),
            entry(FontStyle.FIRA_SANS_MED,     lazyLoadFont(BASE_PATH + "Fira_Sans/FiraSans-Medium.ttf")),
            entry(FontStyle.FIRA_SANS_THIN,    lazyLoadFont(BASE_PATH + "Fira_Sans/FiraSans-Thin.ttf")),
            // Atkinson Hyperlegible
            entry(FontStyle.ATKIN_HYPER,       lazyLoadFont(BASE_PATH + "Atkinson_Hyperlegible/AtkinsonHyperlegible-Regular.ttf")),
            entry(FontStyle.ATKIN_HYPER_BOLD,  lazyLoadFont(BASE_PATH + "Atkinson_Hyperlegible/AtkinsonHyperlegible-Bold.ttf")),
            // Lexend
            entry(FontStyle.LEXEND,            lazyLoadFont(BASE_PATH + "Lexend/Lexend-Regular.ttf")),
            entry(FontStyle.LEXEND_BOLD,       lazyLoadFont(BASE_PATH + "Lexend/Lexend-Bold.ttf")),
            entry(FontStyle.LEXEND_BLACK,      lazyLoadFont(BASE_PATH + "Lexend/Lexend-Black.ttf")),
            entry(FontStyle.LEXEND_LIGHT,      lazyLoadFont(BASE_PATH + "Lexend/Lexend-Light.ttf")),
            entry(FontStyle.LEXEND_MED,        lazyLoadFont(BASE_PATH + "Lexend/Lexend-Medium.ttf")),
            entry(FontStyle.LEXEND_THIN,       lazyLoadFont(BASE_PATH + "Lexend/Lexend-Thin.ttf"))
        );
    }

    @Override
    public void shutDown() {
        defaultFontsMap = null;
    }

    private Font getRunescapeFont(FontStyle style) {
        switch (style) {
            case RUNE_SMALL:
                return FontManager.getRunescapeSmallFont();
            case RUNE_BOLD:
                return FontManager.getRunescapeBoldFont();
            default:
                return FontManager.getRunescapeFont();
        }
    }

    public @Nullable Font getFont(FontStyle style) {
        if (defaultFontsMap == null) {
            log.error("FontService has not started yet, returning default font for style: {}", style);
            return getRunescapeFont(style);
        }

        LazyLoad<Font> fontLoader = defaultFontsMap.get(style);
        if (fontLoader == null) {
            return null;
        }
        return fontLoader.get();
    }

    public @Nullable Font getFont(FontStyle style, int size) {
        if (defaultFontsMap == null) {
            log.error("FontService has not started yet, returning default font for style: {}", style);
            return getRunescapeFont(style);
        }

        LazyLoad<Font> fontLoader = defaultFontsMap.get(style);
        if (fontLoader == null) {
            return null;
        }
        Font font = fontLoader.get();
        return font != null ? font.deriveFont((float) size) : null;
    }

    public @Nullable Font getFont(FontStyle style, int size, int styleFlags) {
        if (defaultFontsMap == null) {
            log.error("FontService has not started yet, returning default font for style: {}", style);
            return getRunescapeFont(style);
        }

        LazyLoad<Font> fontLoader = defaultFontsMap.get(style);
        if (fontLoader == null) {
            return null;
        }
        Font font = fontLoader.get();
        return font != null ? font.deriveFont(styleFlags, (float) size) : null;
    }

    public void registerCustomFont(String fontName, String path) {
        customFontsMap.put(fontName, lazyLoadFont(path));
    }

    public Font getCustomFont(String fontName) {
        if (fontName == null || fontName.isEmpty()) {
            return null;
        }
        if (!customFontsMap.containsKey(fontName)) {
            log.warn("Custom font '{}' not found in the map.", fontName);
            return null;
        }
        LazyLoad<Font> fontLoader = customFontsMap.get(fontName);
        if (fontLoader == null) {
            log.warn("Custom font '{}' is not loaded.", fontName);
            return null;
        }
        Font font = fontLoader.get();
        if (font == null) {
            log.warn("Custom font '{}' is null after loading.", fontName);
            return null;
        }
        return font;
    }

    public Font getCustomFont(String fontName, int size) {
        Font font = getCustomFont(fontName);
        if (font == null) {
            return null;
        }
        return font.deriveFont((float) size);
    }

    public Font getCustomFont(String fontName, int size, int styleFlags) {
        Font font = getCustomFont(fontName);
        if (font == null) {
            return null;
        }
        return font.deriveFont(styleFlags, (float) size);
    }

    public Font safeLoadFont(String path) {
        try {
            return loadFont(path);
        } catch (Exception ex) {
            log.warn("Failed to load font {}: {}", path, ex.toString());
            return null;
        }
    }

    public Font loadFont(String path) {
        try (var in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Font resource not found: " + path);
            }
            Font base = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(base);
            return base;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load font: " + path, ex);
        }
    }

    private LazyLoad<Font> lazyLoadFont(String path) {
        return lazyLoadFont(() -> {
            try {
                return loadFont(path);
            } catch (Exception ex) {
                log.warn("Failed to load font {}: {}", path, ex.toString());
                return null;
            }
        });
    }

    private LazyLoad<Font> lazyLoadFont(Supplier<Font> supplier) {
        return new LazyLoad<>(supplier);
    }
}
