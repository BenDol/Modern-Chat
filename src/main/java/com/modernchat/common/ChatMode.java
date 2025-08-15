package com.modernchat.common;

import lombok.Getter;

@Getter
public enum ChatMode
{
    PUBLIC(0),
    DEVELOP(1),
    FRIENDS_CHAT(2),
    CLAN_MAIN(3),
    CLAN_GUEST(4);

    final int value;
    ChatMode(int value) {
        this.value = value;
    }
}
