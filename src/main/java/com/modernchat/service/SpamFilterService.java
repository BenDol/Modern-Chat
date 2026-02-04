package com.modernchat.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for managing user-defined spam/ham corpus for chat filtering.
 * Allows users to mark messages as spam or ham (not spam) to build
 * a personal filter list.
 *
 * Uses the same corpus files as SpamFilterPlugin for compatibility.
 */
@Slf4j
@Singleton
public class SpamFilterService implements ChatService
{
    // Use same directory and file names as SpamFilterPlugin for compatibility
    private static final String SPAMFILTER_DIR = "spam-filter";
    private static final String SPAM_CORPUS_FILE = "user_bad_corpus.txt";
    private static final String HAM_CORPUS_FILE = "user_good_corpus.txt";

    private Path dataDir;
    private Path spamCorpusFile;
    private Path hamCorpusFile;

    @Getter
    private final List<String> spamCorpus = new ArrayList<>();
    @Getter
    private final List<String> hamCorpus = new ArrayList<>();

    @Override
    public void startUp() {
        dataDir = resolveDataDir();
        spamCorpusFile = dataDir.resolve(SPAM_CORPUS_FILE);
        hamCorpusFile = dataDir.resolve(HAM_CORPUS_FILE);

        try {
            ensureDir(dataDir);
            loadCorpus(spamCorpusFile, spamCorpus);
            loadCorpus(hamCorpusFile, hamCorpus);
            log.debug("SpamFilterService started - loaded {} spam entries, {} ham entries",
                spamCorpus.size(), hamCorpus.size());
        } catch (IOException e) {
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
    public boolean removeFromSpam(String message) {
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
    public boolean removeFromHam(String message) {
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
    public void clearSpamCorpus() {
        spamCorpus.clear();
        saveCorpus(spamCorpusFile, spamCorpus);
        log.debug("Cleared spam corpus");
    }

    /**
     * Clear all entries from the ham corpus.
     */
    public void clearHamCorpus() {
        hamCorpus.clear();
        saveCorpus(hamCorpusFile, hamCorpus);
        log.debug("Cleared ham corpus");
    }

    /**
     * Clean a message by removing any score suffix patterns like " (0.85)".
     */
    private String cleanMessage(String message) {
        // Remove spam score suffix if present (e.g., " (0.85)" at the end)
        return message.replaceAll("\\s*\\([0-9.]+\\)$", "").trim();
    }

    private void appendToCorpus(String message, List<String> corpus, Path corpusFile) {
        if (corpus.contains(message)) {
            return; // Already in corpus
        }

        corpus.add(message);
        saveCorpus(corpusFile, corpus);
    }

    private void loadCorpus(Path file, List<String> corpus) throws IOException {
        corpus.clear();

        if (!Files.exists(file)) {
            // Create empty file
            Files.createFile(file);
            return;
        }

        List<String> lines = Files.readAllLines(file);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                corpus.add(trimmed);
            }
        }
    }

    private void saveCorpus(Path file, List<String> corpus) {
        try {
            Files.write(file, corpus,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.error("Failed to save corpus file: {}", file, e);
        }
    }

    private static void ensureDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }
    }

    private static Path resolveDataDir() {
        Path rlHome = null;
        try {
            java.io.File dir = RuneLite.RUNELITE_DIR;
            if (dir != null) {
                rlHome = dir.toPath();
            }
        } catch (Throwable ignored) {
        }
        if (rlHome == null) {
            rlHome = Paths.get(System.getProperty("user.home", ".")).resolve(".runelite");
        }
        // Use same directory as SpamFilterPlugin for compatibility
        return rlHome.resolve(SPAMFILTER_DIR);
    }
}
