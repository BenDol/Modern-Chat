package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

public abstract class AbstractChatFeature<T extends ChatFeatureConfig> implements ChatFeature<T> {

    protected final T config;
    protected final EventBus eventBus;

    private ConfigChangedHandler configChangeHandler;

    protected AbstractChatFeature(ModernChatConfig config, EventBus eventBus) {
        this.config = partitionConfig(config);
        this.eventBus = eventBus;
    }

    protected abstract T partitionConfig(ModernChatConfig config);

    protected void onFeatureConfigChanged(ConfigChanged e) {
        // Default implementation does nothing
    }

    @Override
    public T getConfig() {
        return config;
    }

    @Override
    public void startUp() {
        eventBus.register(this);

        if (configChangeHandler == null) {
            configChangeHandler = new ConfigChangedHandler();
            eventBus.register(configChangeHandler);
        }
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        eventBus.unregister(this);

        if (fullShutdown) {
            if (configChangeHandler != null) {
                eventBus.unregister(configChangeHandler);
                configChangeHandler = null;
            }
        }
    }

    public class ConfigChangedHandler {

        @Subscribe
        public void onConfigChanged(ConfigChanged e) {
            if (!e.getGroup().equals(ModernChatConfig.GROUP))
                return;

            String configGroup = getConfigGroup();
            String key = e.getKey();
            if (!key.startsWith(configGroup + "_"))
                return;

            if (key.endsWith("_Enabled")) {
                // If the feature is disabled, we can soft-shut it down
                boolean currentlyEnabled = Boolean.parseBoolean(e.getOldValue());
                boolean enabling = Boolean.parseBoolean(e.getNewValue());
                if (!enabling && currentlyEnabled) {
                    shutDown();
                } else if (enabling && !currentlyEnabled) {
                    startUp();
                }
            }

            onFeatureConfigChanged(e);
        }
    }
}
