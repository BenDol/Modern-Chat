package com.modernchat.draw;

import java.awt.Rectangle;
import java.util.Objects;

/** Bounds for a username in the rendered chat history. */
public final class UsernameHit
{
    private final Rectangle bounds;
    private final String username;
    private final String rawName;

    public UsernameHit(Rectangle bounds, String username, String rawName) {
        this.bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
        this.username = Objects.requireNonNull(username, "username");
        this.rawName = rawName == null ? username : rawName;
    }

    public Rectangle getBounds() {
        return new Rectangle(bounds);
    }

    public String getUsername() {
        return username;
    }

    public String getRawName() {
        return rawName;
    }
}
