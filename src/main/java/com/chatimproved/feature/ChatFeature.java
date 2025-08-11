package com.chatimproved.feature;

public interface ChatFeature<T extends ChatFeatureConfig> {
    T getConfig();
    boolean isEnabled();
    void startUp();
    default void shutDown() {
        shutDown(false);
    }
    void shutDown(boolean fullShutdown);
}
