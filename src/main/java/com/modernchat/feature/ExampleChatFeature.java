package com.modernchat.feature;

import com.modernchat.ModernChatConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;

import static com.modernchat.feature.ExampleChatFeature.ExampleChatFeatureConfig;

@Slf4j
public class ExampleChatFeature extends AbstractChatFeature<ExampleChatFeatureConfig> {

    @Override
    public String getConfigGroup() {
        return "featureExample";
    }

    public interface ExampleChatFeatureConfig extends ChatFeatureConfig {
        boolean featureExample_Enabled();
    }

    @Inject
    private Client client;

    @Inject
    public ExampleChatFeature(ModernChatConfig config, EventBus eventBus) {
        super(config, eventBus);
    }

    @Override
    protected ExampleChatFeatureConfig partitionConfig(ModernChatConfig config) {
        return new ExampleChatFeatureConfig() {
            @Override public boolean featureExample_Enabled() { return config.featureExample_Enabled(); }
        };
    }

    @Override
    public boolean isEnabled() {
        return config.featureExample_Enabled();
    }

    @Override
    public void startUp() {
        super.startUp();

        // Example: Register a key listener to toggle chat visibility
    }

    @Override
    public void shutDown(boolean fullShutdown) {
        super.shutDown(fullShutdown);

        // Example: Unregister any listeners or clean up resources
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e) {
        if (e.getGameState() == GameState.LOGGED_IN) {
            // Example: Perform actions when the player logs in
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        // Example: Handle game ticks for periodic updates
    }
}
