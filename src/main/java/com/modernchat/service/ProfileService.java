package com.modernchat.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modernchat.ModernChatConfig;
import com.modernchat.ModernChatConfigBase;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.modernchat.ModernChatConfigBase.DEFAULTS;
import static com.modernchat.ModernChatConfigBase.Field;
import static com.modernchat.ModernChatConfigBase.GROUP;
import static com.modernchat.ModernChatConfigBase.ModernChatProfile;
import static com.modernchat.ModernChatConfigBase.buildJsonFromConfig;
import static com.modernchat.ModernChatConfigBase.setCfg;

@Slf4j
@Singleton
public class ProfileService implements ChatService
{
    @Inject ConfigManager configManager;
    @Inject Gson gson;

    private static final String PROFILE_EXT = ".json";

    /** Set by the plugin from its plugin directory before startUp. */
    @Getter
    private volatile Filepath profilesDir;

    /** filename (without .json) -> profile config */
    private final Map<String, ModernChatConfigBase> profiles = new LinkedHashMap<>();

    /** currently active profile key */
    private String activeProfile;

    public void setProfilesDir(Filepath profilesDir) { this.profilesDir = Objects.requireNonNull(profilesDir); }

    /** True if a profile file with this name already exists on disk. */
    public boolean profileExists(String profileName) throws IOException {
        return resolveProfileFile(profileName).exists();
    }

    public Map<String, ModernChatConfigBase> getProfiles() { return Collections.unmodifiableMap(profiles); }

    public Optional<String> getActiveProfileName() { return Optional.ofNullable(activeProfile); }

    public Optional<ModernChatConfigBase> getActiveProfile() {
        return Optional.ofNullable(activeProfile).map(profiles::get);
    }

    /** Set active profile by name; returns true if it exists. */
    public boolean setActiveProfile(String name) {
        if (name == null) return false;
        if (!profiles.containsKey(name)) return false;
        activeProfile = name;
        return true;
    }

    /**
     * Show a custom dialog to choose active profile.
     */
    public Optional<String> chooseActiveProfile(Component parent) {
        if (profiles.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                "No profiles found in:\n" + profilesDir,
                "ModernChat Profiles", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }

        final JList<String> list = new JList<>(profiles.keySet().toArray(new String[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.min(8, profiles.size()));
        if (activeProfile != null) list.setSelectedValue(activeProfile, true);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Select active profile:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnSet = new JButton("Set Active");
        JButton btnCancel = new JButton("Cancel");
        buttons.add(btnSet);
        buttons.add(btnCancel);
        panel.add(buttons, BorderLayout.SOUTH);

        final JDialog dialog = new JDialog(
            SwingUtilities.getWindowAncestor(parent),
            "ModernChat Profile",
            Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);

        final String[] result = new String[1];
        btnSet.addActionListener(ae -> {
            String sel = list.getSelectedValue();
            if (sel != null) result[0] = sel;
            dialog.dispose();
        });
        btnCancel.addActionListener(ae -> {
            result[0] = null;
            dialog.dispose();
        });

        dialog.setVisible(true);

        if (result[0] != null) {
            setActiveProfile(result[0]);
            return Optional.of(result[0]);
        }
        return Optional.empty();
    }

    @Override
    public void startUp() {
        try {
            loadAllProfiles();
            if (profiles.size() == 1) {
                activeProfile = profiles.keySet().iterator().next();
            }
        } catch (IOException e) {
            log.error("[ModernChat] Failed to load profiles: {}", e.getMessage(), e);
        }
    }

    @Override
    public void shutDown() {
    }

    /** Load all *.json files in the profiles directory. */
    public void loadAllProfiles() throws IOException {
        Filepath dir = ensureDir();
        profiles.clear();

        List<Filepath> files;
        try (Stream<Filepath> s = dir.walk(1)) {
            files = s.filter(Filepath::isFile)
                .filter(f -> f.getFileName().toLowerCase(Locale.ROOT).endsWith(PROFILE_EXT))
                .sorted()
                .collect(Collectors.toList());
        }

        for (Filepath f : files) {
            try {
                profiles.put(stripExt(f.getFileName()), loadProfile(f));
            } catch (IOException | RuntimeException e) {
                // one bad file should not hide the rest of the profiles
                log.warn("[ModernChat] Skipping unreadable profile {}: {}", f.getFileName(), e.getMessage());
            }
        }
    }

    /** Load a single profile JSON into a ModernChatConfigBase. */
    public ModernChatConfigBase loadProfile(Filepath file) throws IOException {
        try (BufferedReader br = file.openBufferedReader()) {
            JsonElement root = new JsonParser().parse(br);
            if (root == null || !root.isJsonObject()) {
                throw new IOException("Invalid profile JSON (expecting object): " + file);
            }
            return new ModernChatProfile(root.getAsJsonObject());
        }
    }

    public void applyActiveProfileToConfig() {
        ModernChatConfigBase cfg = getActiveProfile().orElse(DEFAULTS);
        applyProfileToConfig(configManager, cfg);
    }

    public static void applyProfileToConfig(ConfigManager cm, ModernChatConfigBase cfg) {
        for (Field k : ModernChatConfigBase.Field.values()) {
            if (k.key.equals(Field.FEATURE_EXAMPLE.key))
                continue; // skip example feature, not a real config key

            Object v = k.read(cfg);
            setCfg(cm, GROUP, k.key, v);
        }
    }

    public void saveCurrentToProfile(ConfigManager cm, String profileName) throws IOException {
        if (profileName == null || profileName.trim().isEmpty())
            throw new IllegalArgumentException("profileName is empty");

        ModernChatConfigBase cfg = cm.getConfig(ModernChatConfig.class); // reads live RL values
        saveProfile(profileName.trim(), cfg);
    }

    public void saveCurrentAsNewProfile(ConfigManager cm, String profileName) throws IOException {
        if (profileName == null || profileName.trim().isEmpty())
            throw new IllegalArgumentException("profileName is empty");

        ModernChatConfigBase cfg = cm.getConfig(ModernChatConfig.class);
        saveProfile(profileName.trim(), cfg, true);
    }

    public void saveProfile(String profileName, ModernChatConfigBase cfg) throws IOException {
        saveProfile(profileName, cfg, false);
    }

    private void saveProfile(String profileName, ModernChatConfigBase cfg, boolean failIfExists) throws IOException {
        ensureDir();
        Filepath out = resolveProfileFile(profileName);

        if (failIfExists && out.exists())
            throw new FileAlreadyExistsException(out.toString());

        JsonObject root = buildJsonFromConfig(cfg);
        var gsonPretty = gson.newBuilder().setPrettyPrinting().create();
        try (var w = out.openBufferedWriter(
            StandardOpenOption.CREATE, failIfExists ? StandardOpenOption.CREATE_NEW : StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE))
        {
            gsonPretty.toJson(root, w);
        }
    }

    /**
     * Resolve a profile name to its file. Filepath rejects names that would
     * leave the profiles folder or are not valid file names on Windows.
     */
    private Filepath resolveProfileFile(String profileName) throws IOException {
        try {
            return requireDir().joinSegment(profileName + PROFILE_EXT);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid profile name \"" + profileName + "\": " + e.getMessage(), e);
        }
    }

    private Filepath requireDir() throws IOException {
        Filepath dir = profilesDir;
        if (dir == null)
            throw new IOException("Modern Chat data folder is unavailable");
        return dir;
    }

    private Filepath ensureDir() throws IOException {
        Filepath dir = requireDir();
        if (!dir.exists())
            dir.createDirectories();
        if (!dir.isDirectory())
            throw new IOException("Not a directory: " + dir);
        return dir;
    }

    private static String stripExt(String fn) {
        int i = fn.lastIndexOf('.');
        return (i > 0 ? fn.substring(0, i) : fn);
    }
}
