package com.chatimproved.feature;

import com.chatimproved.ChatImprovedConfig;
import com.chatimproved.util.ReflectionUtil;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

public abstract class AbstractChatFeature<T extends ChatFeatureConfig> implements ChatFeature<T> {

    protected final T config;
    protected final EventBus eventBus;

    private ConfigChangedHandler configChangeHandler;

    protected AbstractChatFeature(ChatImprovedConfig config, EventBus eventBus) {
        this.config = extractConfig(config);
        this.eventBus = eventBus;
    }

    protected abstract T extractConfig(ChatImprovedConfig config);

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
            if (!e.getGroup().equals(ChatImprovedConfig.GROUP))
                return;

            ConfigGroup configGroup = ReflectionUtil.findAnnotation(config, ConfigGroup.class);
            if (!e.getKey().startsWith(configGroup.value() + "_"))
                return;

            if (e.getKey().endsWith("_Enabled")) {
                // If the feature is disabled, we can soft-shut it down
                String newValue = e.getNewValue();
                if (newValue == null || !isTrue(newValue) && isEnabled()) {
                    shutDown();
                } else if (isTrue(newValue) && !isEnabled()) {
                    startUp();
                }
            }
        }

        private boolean isTrue(String value) {
            return value != null && (value.equalsIgnoreCase("true") || value.equals("1"));
        }
    }
}
