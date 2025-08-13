package com.modernchat.feature;

public interface ChatFeature<T extends ChatFeatureConfig> {
    T getConfig();

    String getConfigGroup();

    boolean isEnabled();

    void startUp();

    default void shutDown() {
        shutDown(false);
    }

    void shutDown(boolean fullShutdown);
}
