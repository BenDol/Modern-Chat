package com.modernchat.common;

import lombok.Getter;

@Getter
public enum ChatMode
{
    // value is the CHAT_SEND channel argument. CLAN_GIM and PRIVATE use sentinels
    // because they are not sent with their own channel value: group chat goes out
    // as CLAN_MAIN with the ironman clan type, private chat through PRIVMSG.
    // chatboxMode is the game's VarClientID.CHATBOX_MODE value that routes an
    // unprefixed message to this channel (-1 when no chatbox mode does).
    CLAN_GIM(-2, 4),
    PRIVATE(-1, -1),
    PUBLIC(0, 0),
    FRIENDS_CHAT(2, 1),
    CLAN_MAIN(3, 2),
    CLAN_GUEST(4, 3);

    final int value, chatboxMode;
    ChatMode(int value, int chatboxMode) {
        this.value = value;
        this.chatboxMode = chatboxMode;
    }
}
