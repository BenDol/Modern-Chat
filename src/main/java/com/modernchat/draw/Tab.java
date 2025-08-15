package com.modernchat.draw;

import lombok.Data;

import javax.annotation.Nullable;
import java.awt.Rectangle;

@Data
public final class Tab {
    final String key;
    final String title;
    final boolean closeable;
    final Rectangle bounds = new Rectangle();
    Rectangle closeBounds = new Rectangle();
    int unread = 0;
    boolean hidden;

    public Tab(String key, String title, boolean closeable) {
        this(key, title, closeable, false);
    }

    public Tab(String key, String title, boolean closeable, boolean hidden) {
        this.key = key;
        this.title = title;
        this.closeable = closeable;
        this.hidden = hidden;
    }

    public void incrementUnread() {
        if (unread < Integer.MAX_VALUE) {
            unread++;
        }
    }

    public boolean isPrivate() {
        return key != null && key.startsWith("private_");
    }

    public @Nullable String getTargetName() {
        return isPrivate() ? getTitle() : null;
    }
}