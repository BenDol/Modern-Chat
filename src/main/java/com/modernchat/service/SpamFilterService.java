package com.modernchat.service;

import com.modernchat.common.ChatMessageBuilder;
import com.modernchat.common.NotificationService;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Filepath;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.modernchat.ModernChatConfigBase.GROUP;

/**
 * Service for managing user-defined spam/ham corpus for chat filtering.
 * Allows users to mark messages as spam or ham (not spam) to build
 * a personal filter list.
 *
 * Uses the same corpus file names and format as the Spam Filter plugin so
 * its lists can be imported with {@link #importCorpus(Filepath)}.
 */
@Slf4j
@Singleton
public class SpamFilterService implements ChatService
{
    /** Sub-folder of the plugin directory holding the corpus files. */
    public static final String SPAMFILTER_DIR = "spam-filter";
    // Same file names as the Spam Filter plugin so its folder can be imported as-is
    private static final String SPAM_CORPUS_FILE = "user_bad_corpus.txt";
    private static final String HAM_CORPUS_FILE = "user_good_corpus.txt";
    // Plain state key (not a ModernChatConfigBase.Field) so profiles never export it
    private static final String IMPORT_NOTICE_SHOWN_KEY = "spamFilterImportNoticeShown";

    @Inject private ConfigManager configManager;
    @Inject private NotificationService notificationService;

    /** Set by the plugin from its plugin directory before startUp. */
    private volatile Filepath dataDir;
    private volatile Filepath spamCorpusFile;
    private volatile Filepath hamCorpusFile;

    // Read from the client thread, modified from the client thread and the EDT (import)
    @Getter
    private final List<String> spamCorpus = new CopyOnWriteArrayList<>();
    @Getter
    private final List<String> hamCorpus = new CopyOnWriteArrayList<>();

    /**
     * Outcome of {@link #importCorpus(Filepath)}. The found flags say whether each
     * source file existed; the added counts exclude entries we already had.
     */
    @Value
    public static class ImportResult
    {
        boolean foundSpamFile;
        boolean foundHamFile;
        int spamAdded;
        int hamAdded;
    }

    public void setDataDir(Filepath dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Tell an upgrading player, once, that spam they marked before now has to be
     * imported. Older versions shared .runelite/spam-filter, which Filepath no
     * longer lets us read on our own.
     */
    public void showImportNoticeOnce() {
        if (Boolean.TRUE.equals(configManager.getConfiguration(GROUP, IMPORT_NOTICE_SHOWN_KEY, Boolean.class)))
            return;
        markImportNoticeShown();

        // nothing to tell if we can't save, or the player already has a list here
        if (spamCorpusFile == null || !spamCorpus.isEmpty() || !hamCorpus.isEmpty())
            return;

        notificationService.pushChatMessage(new ChatMessageBuilder()
            .append("Spam you marked in older versions is no longer loaded automatically. To bring it back, open the ")
            .append(Color.ORANGE, "Modern Chat side panel")
            .append(", click ")
            .append(Color.ORANGE, "Import Spam List")
            .append(" and pick the ")
            .append(Color.ORANGE, SPAMFILTER_DIR)
            .append(" folder inside your .runelite folder."));
    }

    /** Fresh installs have no old list, so the intro marks the notice as seen. */
    public void markImportNoticeShown() {
        configManager.setConfiguration(GROUP, IMPORT_NOTICE_SHOWN_KEY, true);
    }

    @Override
    public void startUp() {
        spamCorpus.clear();
        hamCorpus.clear();

        Filepath dir = dataDir;
        if (dir == null) {
            log.warn("Spam filter data folder is unavailable, marked messages will not be saved");
            return;
        }

        try {
            spamCorpusFile = dir.joinSegment(SPAM_CORPUS_FILE);
            hamCorpusFile = dir.joinSegment(HAM_CORPUS_FILE);
            ensureDir(dir);
            spamCorpus.addAll(readCorpus(spamCorpusFile));
            hamCorpus.addAll(readCorpus(hamCorpusFile));
            log.debug("SpamFilterService started - loaded {} spam entries, {} ham entries",
                spamCorpus.size(), hamCorpus.size());
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to initialize spam filter service", e);
        }
    }

    @Override
    public void shutDown() {
        // Corpus is saved on each modification, no action needed here
    }

    /**
     * Mark a message as spam. Adds it to the spam corpus.
     *
     * @param message the message text to mark as spam
     */
    public void markSpam(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String cleanMessage = cleanMessage(message);
        appendToCorpus(cleanMessage, spamCorpus, spamCorpusFile);
        log.debug("Marked as spam: {}", cleanMessage);
    }

    /**
     * Mark a message as ham (not spam). Adds it to the ham corpus.
     *
     * @param message the message text to mark as ham
     */
    public void markHam(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        String cleanMessage = cleanMessage(message);
        appendToCorpus(cleanMessage, hamCorpus, hamCorpusFile);
        log.debug("Marked as ham: {}", cleanMessage);
    }

    /**
     * Check if a message is in the spam corpus.
     *
     * @param message the message to check
     * @return true if the message is marked as spam
     */
    public boolean isMarkedSpam(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String cleanMessage = cleanMessage(message);
        return spamCorpus.contains(cleanMessage);
    }

    /**
     * Check if a message is in the ham corpus.
     *
     * @param message the message to check
     * @return true if the message is marked as ham
     */
    public boolean isMarkedHam(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String cleanMessage = cleanMessage(message);
        return hamCorpus.contains(cleanMessage);
    }

    /**
     * Remove a message from the spam corpus.
     *
     * @param message the message to remove
     * @return true if the message was removed
     */
    public synchronized boolean removeFromSpam(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String cleanMessage = cleanMessage(message);
        boolean removed = spamCorpus.remove(cleanMessage);
        if (removed) {
            saveCorpus(spamCorpusFile, spamCorpus);
            log.debug("Removed from spam corpus: {}", cleanMessage);
        }
        return removed;
    }

    /**
     * Remove a message from the ham corpus.
     *
     * @param message the message to remove
     * @return true if the message was removed
     */
    public synchronized boolean removeFromHam(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        String cleanMessage = cleanMessage(message);
        boolean removed = hamCorpus.remove(cleanMessage);
        if (removed) {
            saveCorpus(hamCorpusFile, hamCorpus);
            log.debug("Removed from ham corpus: {}", cleanMessage);
        }
        return removed;
    }

    /**
     * Get an unmodifiable view of the spam corpus.
     *
     * @return unmodifiable list of spam messages
     */
    public List<String> getSpamCorpusView() {
        return Collections.unmodifiableList(spamCorpus);
    }

    /**
     * Get an unmodifiable view of the ham corpus.
     *
     * @return unmodifiable list of ham messages
     */
    public List<String> getHamCorpusView() {
        return Collections.unmodifiableList(hamCorpus);
    }

    /**
     * Clear all entries from the spam corpus.
     */
    public synchronized void clearSpamCorpus() {
        spamCorpus.clear();
        saveCorpus(spamCorpusFile, spamCorpus);
        log.debug("Cleared spam corpus");
    }

    /**
     * Clear all entries from the ham corpus.
     */
    public synchronized void clearHamCorpus() {
        hamCorpus.clear();
        saveCorpus(hamCorpusFile, hamCorpus);
        log.debug("Cleared ham corpus");
    }

    /**
     * Merge spam/ham lists from a folder the user picked (e.g. the Spam Filter
     * plugin's .runelite/spam-filter folder). Entries already present are skipped.
     *
     * @param sourceDir folder chosen through {@link Filepath.Chooser}
     */
    public ImportResult importCorpus(Filepath sourceDir) throws IOException {
        if (spamCorpusFile == null || hamCorpusFile == null) {
            // without a data folder the import would only live until restart
            throw new IOException("Modern Chat data folder is unavailable");
        }

        Filepath spamSource = sourceDir.joinSegment(SPAM_CORPUS_FILE);
        Filepath hamSource = sourceDir.joinSegment(HAM_CORPUS_FILE);
        boolean foundSpam = spamSource.isFile();
        boolean foundHam = hamSource.isFile();

        // read outside the lock so marking messages on the client thread never waits on
        // this disk read, and read both first so a failed read imports nothing
        List<String> spamEntries = foundSpam ? readCorpus(spamSource) : Collections.emptyList();
        List<String> hamEntries = foundHam ? readCorpus(hamSource) : Collections.emptyList();

        int spamAdded;
        int hamAdded;
        synchronized (this) {
            spamAdded = mergeInto(spamEntries, spamCorpus);
            hamAdded = mergeInto(hamEntries, hamCorpus);
            if (spamAdded > 0) {
                saveCorpus(spamCorpusFile, spamCorpus);
            }
            if (hamAdded > 0) {
                saveCorpus(hamCorpusFile, hamCorpus);
            }
        }

        log.debug("Imported {} spam and {} ham entries from {}", spamAdded, hamAdded, sourceDir);
        return new ImportResult(foundSpam, foundHam, spamAdded, hamAdded);
    }

    /**
     * Clean a message by removing any score suffix patterns like " (0.85)".
     */
    private String cleanMessage(String message) {
        // Remove spam score suffix if present (e.g., " (0.85)" at the end)
        return message.replaceAll("\\s*\\([0-9.]+\\)$", "").trim();
    }

    private synchronized void appendToCorpus(String message, List<String> corpus, Filepath corpusFile) {
        if (corpus.contains(message)) {
            return; // Already in corpus
        }

        corpus.add(message);
        saveCorpus(corpusFile, corpus);
    }

    private static int mergeInto(List<String> entries, List<String> corpus) {
        int added = 0;
        for (String entry : entries) {
            if (!corpus.contains(entry)) {
                corpus.add(entry);
                added++;
            }
        }
        return added;
    }

    private static List<String> readCorpus(Filepath file) throws IOException {
        List<String> entries = new ArrayList<>();
        if (!file.exists()) {
            return entries;
        }

        try (BufferedReader br = file.openBufferedReader()) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !entries.contains(trimmed)) {
                    entries.add(trimmed);
                }
            }
        }
        return entries;
    }

    private void saveCorpus(Filepath file, List<String> corpus) {
        if (file == null) {
            return; // data folder unavailable, keep the in-memory list only
        }

        StringBuilder sb = new StringBuilder();
        for (String line : corpus) {
            sb.append(line).append('\n');
        }

        Filepath tmp = null;
        try {
            Filepath dir = file.getParent();
            ensureDir(dir);
            // write a sibling temp file and swap it in, so a crash mid-write
            // can never leave the player's list empty or half written
            tmp = dir.createTempFile(file.getFileName() + ".", ".tmp");
            tmp.write(sb.toString(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                tmp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (IOException e) {
            log.error("Failed to save corpus file: {}", file, e);
        } finally {
            if (tmp != null) {
                try {
                    tmp.deleteIfExists();
                } catch (IOException e) {
                    log.debug("Failed to delete temp corpus file: {}", tmp, e);
                }
            }
        }
    }

    private static void ensureDir(Filepath dir) throws IOException {
        if (!dir.exists()) {
            dir.createDirectories();
        }
        if (!dir.isDirectory()) {
            throw new IOException("Not a directory: " + dir);
        }
    }
}
