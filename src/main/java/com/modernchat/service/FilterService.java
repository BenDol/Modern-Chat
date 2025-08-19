package com.modernchat.service;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.Set;

@Slf4j
@Singleton
public class FilterService implements ChatService
{
    public Set<String> filterRegexes = null;

    @Override
    public void startUp() {
        filterRegexes = Set.of(
            ".*(\\b(?:https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|])",
            ".*(\\b(?:www\\.)[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|])",
            ".*(\\b(?:mailto:)[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|])"
        );
        log.debug("FilterService started with regexes: {}", filterRegexes);
    }

    @Override
    public void shutDown() {

    }

    public boolean isFiltered(String message) {
        if (message == null || message.isEmpty())
            return false;

        for (String regex : filterRegexes) {
            if (message.matches(regex)) {
                log.debug("Message filtered: {}", message);
                return true;
            }
        }
        return false;
    }
}
