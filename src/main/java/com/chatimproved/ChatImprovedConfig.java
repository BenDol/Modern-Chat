package com.chatimproved;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("chatimproved")
public interface ChatImprovedConfig extends Config
{
    @ConfigItem(
        keyName = "toggleKey",
        name = "Toggle hotkey",
        description = "Key used to show/hide the chatbox"
    )
    default Keybind toggleKey()
    {
        // Default to Enter. Modify if you prefer another default.
        return new Keybind(KeyEvent.VK_ENTER, 0);
    }

    @ConfigItem(
        keyName = "startHidden",
        name = "Start hidden",
        description = "Hide the chatbox when the plugin starts"
    )
    default boolean startHidden()
    {
        return false;
    }
}
