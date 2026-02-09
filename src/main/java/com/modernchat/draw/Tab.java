package com.modernchat.draw;

import lombok.Data;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.Objects;

@Data
public final class Tab {
    final String key;
    final String title;
    final boolean closeable;
    final Rectangle bounds = new Rectangle();
    Rectangle closeBounds = new Rectangle();
    int unread = 0;
    boolean hidden;
    boolean readOnly;
    boolean muted = false;
    int iconId = -1;

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
        if (muted) {
            return; // Don't increment unread on muted tabs
        }
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Tab tab = (Tab) o;
        return Objects.equals(key, tab.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }
}