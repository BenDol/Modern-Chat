package com.modernchat;

import com.modernchat.service.ProfileService;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class ModernChatPanel extends PluginPanel
{
    private final ProfileService profileService;
    private final ConfigManager configManager;

    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);

    private final JLabel activeLabel = new JLabel("Active: (none)");
    private final JButton selectBtn = new JButton("Select");
    private final JButton reloadBtn = new JButton("Reload");
    private final JButton openFolderBtn = new JButton("Open Folder");
    private final JButton saveBtn = new JButton("Save");
    private final JButton saveAsBtn = new JButton("Save As");

    public ModernChatPanel(ProfileService profileService, ConfigManager configManager) {
        super(false); // no wrap
        this.profileService = profileService;
        this.configManager = configManager;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Modern Chat Profiles");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.NORTH);
        top.add(activeLabel, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(10);
        list.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    setActiveFromSelection();
                }
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 3));
        buttons.add(selectBtn);
        add(buttons, BorderLayout.SOUTH);

        JPanel inner = new JPanel(new GridLayout(2, 2, 3, 3));
        inner.add(saveBtn);
        inner.add(saveAsBtn);
        inner.add(reloadBtn);
        inner.add(openFolderBtn);
        buttons.add(inner, BorderLayout.SOUTH);

        selectBtn.addActionListener(ae -> setActiveFromSelection());
        reloadBtn.addActionListener(ae -> loadProfilesIntoList());
        openFolderBtn.addActionListener(ae -> openProfilesFolder());
        saveBtn.addActionListener(ae -> saveCurrentSettingsToProfile());
        saveAsBtn.addActionListener(ae -> saveCurrentSettingsAsNewProfile());

        // initial load
        loadProfilesIntoList();
        updateActiveLabel();
    }

    /**
     * Called by plugin on shutdown to perform any cleanup if needed.
     */
    public void onClose() {
    }

    private void loadProfilesIntoList() {
        model.clear();
        try {
            profileService.loadAllProfiles();
            for (String name : profileService.getProfiles().keySet()) {
                model.addElement(name);
            }
            updateActiveLabel();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to load profiles:\n" + ex.getMessage(),
                "Modern Chat", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setActiveFromSelection() {
        String sel = list.getSelectedValue();
        if (sel == null) {
            JOptionPane.showMessageDialog(this,
                "Select a profile first.", "Modern Chat", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!profileService.setActiveProfile(sel)) {
            JOptionPane.showMessageDialog(this,
                "Could not set profile: " + sel, "Modern Chat", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Apply to RuneLite config
        profileService.applyActiveProfileToConfig();
        updateActiveLabel();

        // TODO: not sure how to reload the config UI properly, warn user to restart
        JOptionPane.showMessageDialog(this,
            "Active profile set to: " + sel + " \n\nPlease restart the client to see the configurations updated.", "Modern Chat", JOptionPane.WARNING_MESSAGE);
    }

    private void updateActiveLabel() {
        String active = profileService.getActiveProfileName().orElse("(none)");
        activeLabel.setText("Active: " + active);
        list.setSelectedValue(active, true);
    }

    private void openProfilesFolder() {
        try {
            var dir = profileService.getProfilesDir();
            if (!Files.exists(dir))
                Files.createDirectories(dir);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                JOptionPane.showMessageDialog(this,
                    "Profiles directory:\n" + dir.toAbsolutePath(),
                    "Modern Chat", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Unable to open folder:\n" + ex.getMessage(),
                "Modern Chat", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrentSettingsToProfile() {
        // Choose default name: selection -> active -> prompt
        String name = list.getSelectedValue();
        if (name == null) {
            name = profileService.getActiveProfileName().orElse(null);
        }
        if (name == null) {
            name = JOptionPane.showInputDialog(this,
                "Enter a profile name to save current settings:",
                "Save Profile", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) return; // cancelled
            name = name.trim();
        }

        // Confirm overwrite
        int res = JOptionPane.showConfirmDialog(this,
            "Save current Modern Chat settings to profile \"" + name + "\"?\n"
                + "This will overwrite " + name + ".json in your profiles folder.",
            "Confirm Save", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);

        if (res != JOptionPane.OK_OPTION) return;

        try {
            profileService.saveCurrentToProfile(configManager, name);
            // Refresh list & selection
            loadProfilesIntoList();
            list.setSelectedValue(name, true);
            JOptionPane.showMessageDialog(this,
                "Saved to profile: " + name, "Modern Chat", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to save profile:\n" + ex.getMessage(),
                "Modern Chat", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrentSettingsAsNewProfile() {
        // suggest a name: active or “profile-copy”
        String suggested = profileService.getActiveProfileName()
            .map(n -> n + "-copy")
            .orElse("modernchat-profile");

        String input = (String) JOptionPane.showInputDialog(
            this,
            "Enter a new profile name (will create <name>.json):",
            "Save As…",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            suggested
        );
        if (input == null) return; // cancelled

        String name = sanitizeProfileName(input);
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Profile name cannot be empty.", "Modern Chat", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // enforce "new" unless the user explicitly agrees to overwrite
        Path out = profileService.getProfilesDir().resolve(name + ".json");
        if (Files.exists(out)) {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Profile \"" + name + "\" already exists.\nDo you want to overwrite it?",
                "Profile Exists",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) return;
            // user agreed to overwrite -> fall back to "Save Current"
            try {
                profileService.saveCurrentToProfile(configManager, name);
                loadProfilesIntoList();
                list.setSelectedValue(name, true);
                JOptionPane.showMessageDialog(this,
                    "Overwrote profile: " + name, "Modern Chat", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to save profile:\n" + ex.getMessage(),
                    "Modern Chat", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        // create a brand-new file
        try {
            profileService.saveCurrentAsNewProfile(configManager, name);
            loadProfilesIntoList();
            list.setSelectedValue(name, true);
            JOptionPane.showMessageDialog(this,
                "Saved new profile: " + name, "Modern Chat", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save profile:\n" + ex.getMessage(),
                "Modern Chat", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** keep filenames friendly: letters, numbers, dash/underscore; collapse spaces to dash */
    private static String sanitizeProfileName(String in) {
        String s = in.trim().replaceAll("\\s+", "-");
        s = s.replaceAll("[^A-Za-z0-9_\\-.]", "");
        // avoid hidden dotfiles or just ".json"
        if (s.equalsIgnoreCase(".json"))
            s = "profile";
        // strip trailing ".json" if user typed it
        if (s.toLowerCase(Locale.ROOT).endsWith(".json"))
            s = s.substring(0, s.length() - 5);
        return s;
    }
}
