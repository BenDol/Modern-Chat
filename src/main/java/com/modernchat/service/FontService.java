package com.modernchat.service;

import com.modernchat.common.FontStyle;
import com.modernchat.util.FontUtil;
import lombok.Getter;
import net.runelite.client.ui.FontManager;

import javax.inject.Singleton;
import java.awt.Font;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.modernchat.util.FontUtil.loadFont;
import static java.util.Map.entry;

@Singleton
public class FontService implements ChatService
{
    private static final String BASE_FONT_PATH = "/com/modernchat/fonts/";

    @Getter
    private Map<FontStyle, Font> defaultFontsMap = null;

    private final Map<String, Font> customFontsMap = new ConcurrentHashMap<>();

    @Override
    public void startUp() {
        defaultFontsMap = Map.ofEntries(
            entry(FontStyle.RUNE_REG,              FontManager.getRunescapeFont()),
            entry(FontStyle.RUNE_SMALL,            FontManager.getRunescapeSmallFont()),
            entry(FontStyle.RUNE_BOLD,             FontManager.getRunescapeBoldFont()),
            // Open Sans
            entry(FontStyle.OPEN_SANS_REG,         loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans-Regular.ttf")),
            entry(FontStyle.OPEN_SANS_LIGHT,       loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans-Light.ttf")),
            entry(FontStyle.OPEN_SANS_MED,         loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans-Medium.ttf")),
            entry(FontStyle.OPEN_SANS_BOLD,        loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans-Bold.ttf")),
            entry(FontStyle.OPEN_SANS_D_REG,       loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans_Condensed-Regular.ttf")),
            entry(FontStyle.OPEN_SANS_D_LIGHT,     loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans_Condensed-Light.ttf")),
            entry(FontStyle.OPEN_SANS_D_MED,       loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans_Condensed-Medium.ttf")),
            entry(FontStyle.OPEN_SANS_D_BOLD,      loadFont(BASE_FONT_PATH + "Open_Sans/OpenSans_Condensed-Bold.ttf")),
            // Roboto
            entry(FontStyle.ROBOTO_D_REG,          loadFont(BASE_FONT_PATH + "Roboto/RobotoCondensed-Regular.ttf")),
            entry(FontStyle.ROBOTO_D_MED,          loadFont(BASE_FONT_PATH + "Roboto/RobotoCondensed-Medium.ttf")),
            entry(FontStyle.ROBOTO_D_LIGHT,        loadFont(BASE_FONT_PATH + "Roboto/RobotoCondensed-Light.ttf")),
            entry(FontStyle.ROBOTO_D_THIN,         loadFont(BASE_FONT_PATH + "Roboto/RobotoCondensed-Thin.ttf")),
            entry(FontStyle.ROBOTO_D_BOLD,         loadFont(BASE_FONT_PATH + "Roboto/RobotoCondensed-Bold.ttf")),
            entry(FontStyle.ROBOTO_D_BLK,          loadFont(BASE_FONT_PATH + "Roboto/RobotoCondensed-Black.ttf")),
            // Source Sans 3
            entry(FontStyle.SRC_SANS_REG,          loadFont(BASE_FONT_PATH + "Source_Sans_3/SourceSans3-Regular.ttf")),
            entry(FontStyle.SRC_SANS_BLK,          loadFont(BASE_FONT_PATH + "Source_Sans_3/SourceSans3-Black.ttf")),
            entry(FontStyle.SRC_SANS_BOLD,         loadFont(BASE_FONT_PATH + "Source_Sans_3/SourceSans3-Bold.ttf")),
            entry(FontStyle.SRC_SANS_LIGHT,        loadFont(BASE_FONT_PATH + "Source_Sans_3/SourceSans3-Light.ttf")),
            entry(FontStyle.SRC_SANS_MED,          loadFont(BASE_FONT_PATH + "Source_Sans_3/SourceSans3-Medium.ttf")),
            // IBM Plex Sans
            entry(FontStyle.IBM_PLEX_REG,          loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans-Regular.ttf")),
            entry(FontStyle.IBM_PLEX_BOLD,         loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans-Bold.ttf")),
            entry(FontStyle.IBM_PLEX_LIGHT,        loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans-Light.ttf")),
            entry(FontStyle.IBM_PLEX_MED,          loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans-Medium.ttf")),
            entry(FontStyle.IBM_PLEX_THIN,         loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans-Thin.ttf")),
            entry(FontStyle.IBM_PLEX_D_REG,        loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Regular.ttf")),
            entry(FontStyle.IBM_PLEX_D_BOLD,       loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Bold.ttf")),
            entry(FontStyle.IBM_PLEX_D_LIGHT,      loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Light.ttf")),
            entry(FontStyle.IBM_PLEX_D_MED,        loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Medium.ttf")),
            entry(FontStyle.IBM_PLEX_D_THIN,       loadFont(BASE_FONT_PATH + "IBM_Plex_Sans/IBMPlexSans_Condensed-Thin.ttf")),
            // Fira Sans
            entry(FontStyle.FIRA_SANS_REG,         loadFont(BASE_FONT_PATH + "Fira_Sans/FiraSans-Regular.ttf")),
            entry(FontStyle.FIRA_SANS_BOLD,        loadFont(BASE_FONT_PATH + "Fira_Sans/FiraSans-Bold.ttf")),
            entry(FontStyle.FIRA_SANS_BLK,         loadFont(BASE_FONT_PATH + "Fira_Sans/FiraSans-Black.ttf")),
            entry(FontStyle.FIRA_SANS_LIGHT,       loadFont(BASE_FONT_PATH + "Fira_Sans/FiraSans-Light.ttf")),
            entry(FontStyle.FIRA_SANS_MED,         loadFont(BASE_FONT_PATH + "Fira_Sans/FiraSans-Medium.ttf")),
            entry(FontStyle.FIRA_SANS_THIN,        loadFont(BASE_FONT_PATH + "Fira_Sans/FiraSans-Thin.ttf")),
            // Atkinson Hyperlegible
            entry(FontStyle.ATKIN_HYPER_REG,       loadFont(BASE_FONT_PATH + "Atkinson_Hyperlegible/AtkinsonHyperlegible-Regular.ttf")),
            entry(FontStyle.ATKIN_HYPER_BOLD,      loadFont(BASE_FONT_PATH + "Atkinson_Hyperlegible/AtkinsonHyperlegible-Bold.ttf")),
            // Lexend
            entry(FontStyle.LEXEND_REG,            loadFont(BASE_FONT_PATH + "Lexend/Lexend-Regular.ttf")),
            entry(FontStyle.LEXEND_BOLD,           loadFont(BASE_FONT_PATH + "Lexend/Lexend-Bold.ttf")),
            entry(FontStyle.LEXEND_BLK,            loadFont(BASE_FONT_PATH + "Lexend/Lexend-Black.ttf")),
            entry(FontStyle.LEXEND_LIGHT,          loadFont(BASE_FONT_PATH + "Lexend/Lexend-Light.ttf")),
            entry(FontStyle.LEXEND_MED,            loadFont(BASE_FONT_PATH + "Lexend/Lexend-Medium.ttf")),
            entry(FontStyle.LEXEND_THIN,           loadFont(BASE_FONT_PATH + "Lexend/Lexend-Thin.ttf"))
        );
    }

    @Override
    public void shutDown() {
        // TODO: unload fonts?
        defaultFontsMap.clear();
    }

    public Font getFont(FontStyle style) {
        return defaultFontsMap.get(style);
    }

    public Font getFont(FontStyle style, int size) {
        Font font = defaultFontsMap.get(style);
        if (font == null) {
            return null;
        }
        return font.deriveFont((float) size);
    }

    public Font getFont(FontStyle style, int size, int styleFlags) {
        Font font = defaultFontsMap.get(style);
        if (font == null) {
            return null;
        }
        return font.deriveFont(styleFlags, (float) size);
    }

    public void registerCustomFont(String fontName, String path) {
        Font font = FontUtil.safeLoadFont(path);
        if (font != null) {
            customFontsMap.put(fontName, font);
        }
    }

    public Font getCustomFont(String fontName) {
        return customFontsMap.get(fontName);
    }

    public Font getCustomFont(String fontName, int size) {
        Font font = customFontsMap.get(fontName);
        if (font == null) {
            return null;
        }
        return font.deriveFont((float) size);
    }

    public Font getCustomFont(String fontName, int size, int styleFlags) {
        Font font = customFontsMap.get(fontName);
        if (font == null) {
            return null;
        }
        return font.deriveFont(styleFlags, (float) size);
    }
}
