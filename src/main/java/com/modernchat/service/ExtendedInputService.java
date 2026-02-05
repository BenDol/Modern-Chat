package com.modernchat.service;

import com.modernchat.common.ExtendedKeybind;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service that handles extended input events (Mouse 4/5 and F13-F24).
 * <p>
 * Mouse buttons 4+ are blocked by RuneLite's MouseManager, so we register
 * an AWT MouseListener directly on the canvas to intercept them first.
 * F13-F24 work via standard KeyManager but are unified here for consistent UX.
 */
@Slf4j
@Singleton
public class ExtendedInputService implements ChatService, KeyListener {

    private final ConcurrentHashMap<String, Binding> bindings = new ConcurrentHashMap<>();

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private KeyManager keyManager;

    @Inject
    private EventBus eventBus;

    private MouseAdapter canvasMouseListener;
    private boolean isStarted = false;

    @Override
    public void startUp() {
        if (isStarted) {
            return;
        }
        isStarted = true;

        eventBus.register(this);

        // Register key listener for F13-F24
        keyManager.registerKeyListener(this);

        // Try to register canvas listener if canvas is available
        registerCanvasListener();
    }

    @Override
    public void shutDown() {
        if (!isStarted) {
            return;
        }
        isStarted = false;

        eventBus.unregister(this);
        keyManager.unregisterKeyListener(this);
        unregisterCanvasListener();
        bindings.clear();
    }

    /**
     * Register a callback for an extended keybind.
     *
     * @param id       Unique identifier for this binding (used to unregister)
     * @param keybind  The extended keybind to listen for
     * @param callback The callback to execute when the keybind is triggered
     */
    public void registerBinding(String id, ExtendedKeybind keybind, Consumer<Void> callback) {
        if (id == null || keybind == null || callback == null) {
            return;
        }
        if (keybind == ExtendedKeybind.NONE) {
            bindings.remove(id);
            return;
        }
        bindings.put(id, new Binding(keybind, callback));
        log.debug("Registered extended binding '{}' for {}", id, keybind);
    }

    /**
     * Unregister a previously registered binding.
     *
     * @param id The identifier used when registering
     */
    public void unregisterBinding(String id) {
        if (id != null) {
            Binding removed = bindings.remove(id);
            if (removed != null) {
                log.debug("Unregistered extended binding '{}'", id);
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // Re-register canvas listener when logging in, as canvas may be recreated
        if (event.getGameState() == GameState.LOGGED_IN) {
            clientThread.invokeLater(this::registerCanvasListener);
        }
    }

    private void registerCanvasListener() {
        Canvas canvas = client.getCanvas();
        if (canvas == null) {
            log.debug("Canvas not available, will retry on game state change");
            return;
        }

        // Remove old listener if exists
        if (canvasMouseListener != null) {
            canvas.removeMouseListener(canvasMouseListener);
        }

        canvasMouseListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePress(e);
            }
        };

        canvas.addMouseListener(canvasMouseListener);
        log.debug("Registered canvas mouse listener for extended buttons");
    }

    private void unregisterCanvasListener() {
        if (canvasMouseListener != null) {
            Canvas canvas = client.getCanvas();
            if (canvas != null) {
                canvas.removeMouseListener(canvasMouseListener);
            }
            canvasMouseListener = null;
        }
    }

    private void handleMousePress(MouseEvent e) {
        int button = e.getButton();
        // We're interested in buttons 4 and 5 (typically mouse side buttons)
        if (button < 4) {
            return;
        }

        for (Binding binding : bindings.values()) {
            if (binding.keybind.isMouse() && binding.keybind.getCode() == button) {
                e.consume();
                clientThread.invoke(() -> {
                    try {
                        binding.callback.accept(null);
                    } catch (Exception ex) {
                        log.warn("Error executing extended keybind callback", ex);
                    }
                });
                return;
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.isConsumed()) {
            return;
        }

        int keyCode = e.getKeyCode();
        // Check if it's an F13-F24 key
        if (keyCode < KeyEvent.VK_F13 || keyCode > KeyEvent.VK_F24) {
            return;
        }

        for (Binding binding : bindings.values()) {
            if (!binding.keybind.isMouse() && binding.keybind.getCode() == keyCode) {
                e.consume();
                clientThread.invoke(() -> {
                    try {
                        binding.callback.accept(null);
                    } catch (Exception ex) {
                        log.warn("Error executing extended keybind callback", ex);
                    }
                });
                return;
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // Not needed
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not needed
    }

    /**
     * Internal binding record.
     */
    private static class Binding {
        final ExtendedKeybind keybind;
        final Consumer<Void> callback;

        Binding(ExtendedKeybind keybind, Consumer<Void> callback) {
            this.keybind = keybind;
            this.callback = callback;
        }
    }
}
