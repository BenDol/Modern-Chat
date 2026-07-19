package com.modernchat.common;

/**
 * Controls which parts of the chat input line are colored with the
 * active channel's chat color instead of the static config colors.
 */
public enum InputColorMode
{
    OFF,
    PREFIX,
    TEXT,
    BOTH;
}
