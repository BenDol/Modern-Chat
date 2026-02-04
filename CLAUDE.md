# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Modern Chat is a RuneLite plugin that enhances the OSRS chat experience with a modernized UI, chat toggle/peek features, notifications, and custom commands. Written in Java 11+ using Gradle.

## Build Commands

```bash
./gradlew build          # Build the plugin
./gradlew test           # Run tests
./gradlew shadowJar      # Create fat JAR with all dependencies
```

## Architecture

### Plugin Entry Point
`ModernChatPlugin.java` - Main plugin class that:
- Registers features and services via Guice injection
- Manages plugin lifecycle (startUp/shutDown)
- Handles RuneLite events and posts custom events

### Feature System
Features are modular components implementing `ChatFeature<T>` interface:
- `ChatRedesignFeature` - Modern UI redesign with tabs and custom styling
- `ToggleChatFeature` - Show/hide chat with hotkey (default: Enter)
- `PeekChatFeature` - Preview overlay when chat is hidden
- `CommandsChatFeature` - Custom commands (/pm, /w, /r for private messages)
- `NotificationChatFeature` - Sound/visual notifications
- `MessageHistoryChatFeature` - Navigate message history with keys

Features auto-disable when their config is toggled off. Each feature handles its own startup/shutdown.

### Service Layer
Services handle domain-specific logic (all in `com.modernchat.service`):
- `MessageService` - Message sending with cooldown/rate-limiting (900ms cooldown, 5 hot message limit)
- `PrivateChatService` - Private message UI state and split PM anchoring
- `FontService` - Font loading/caching for 7 font families
- `SoundService` - Notification sound effects
- `FilterService` - Message filtering before send
- `ProfileService`, `TutorialService`, `ImageService`

### Event System
Custom events in `com.modernchat.event` for inter-component communication:
- Chat visibility changes, message layer events
- Tab changes, dialog open/close events
- Notification events, chat toggle events

Events are posted via RuneLite's EventBus.

### Drawing/Rendering
`com.modernchat.draw` contains rendering primitives:
- `Segment` hierarchy: `TextSegment`, `ImageSegment` for rich text
- `RichLine`, `VisualLine` for line composition
- `Tab`, `Margin`, `Padding` for layout

### Overlays
`com.modernchat.overlay`:
- `ChatOverlay` - Main chat rendering
- `ChatPeekOverlay` - Peek feature overlay
- `MessageContainer` - Message display container
- `ResizePanel` - Resizable chat panel

### Configuration
Config group: `modernchat`
- Interface: `ModernChatConfig` extends `Config` and `ModernChatConfigBase`
- 100+ configuration options organized in sections
- Uses RuneLite's ConfigManager with @ConfigItem annotations

### Key Utilities
- `WidgetBucket` - Organizes game widget references (chatbox, PM chat, dialogs)
- `ChatProxy` - Abstraction for chat visibility and bounds
- `ChatUtil`, `ClientUtil`, `StringUtil`, `GeometryUtil` - Common helpers

## Key Patterns

1. **Guice Injection** - Services and features are injected via @Inject
2. **EventBus** - Components communicate via posted events, subscribe with @Subscribe
3. **Client Thread Safety** - Use `clientThread.invoke()` or `clientThread.invokeLater()` for client-side operations
4. **AtomicBoolean** - Used for thread-safe state tracking (e.g., chat visibility)

## Constraints

- **No Reflection** - Do not use Java reflection APIs directly. Exception: GSON POJO serialization is allowed (GSON uses reflection internally, but this is acceptable for JSON serialization).

## Resources

- Fonts: `src/main/resources/com/modernchat/fonts/` (Roboto, Open Sans, Fira Sans, etc.)
- Images: `src/main/resources/com/modernchat/images/`
- Sounds: `src/main/resources/com/modernchat/sounds/`

## Dependencies

- RuneLite Client API (latest.release)
- Lombok 1.18.30 (annotations: @Slf4j, @Getter, etc.)
- JUnit 4.12 for testing
