package com.modernchat.draw;

import java.awt.Rectangle;
import java.util.Objects;

/**
 * The rendered bounds and backing message identity of a player username.
 *
 * <p>The rectangle is defensively copied on input and output so callers cannot mutate the
 * hit-test data after it has been recorded.</p>
 */
public final class UsernameHit
{
    private final Rectangle bounds;
    private final String username;
    private final int messageId;

    public UsernameHit(Rectangle bounds, String username, int messageId)
    {
        this.bounds = new Rectangle(Objects.requireNonNull(bounds, "bounds"));
        this.username = Objects.requireNonNull(username, "username");
        this.messageId = messageId;
    }

    public Rectangle getBounds()
    {
        return new Rectangle(bounds);
    }

    public String getUsername()
    {
        return username;
    }

    public int getMessageId()
    {
        return messageId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof UsernameHit))
        {
            return false;
        }

        UsernameHit that = (UsernameHit) other;
        return messageId == that.messageId
            && bounds.equals(that.bounds)
            && username.equals(that.username);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(bounds, username, messageId);
    }

    @Override
    public String toString()
    {
        return "UsernameHit{" +
            "bounds=" + bounds +
            ", username='" + username + '\'' +
            ", messageId=" + messageId +
            '}';
    }
}