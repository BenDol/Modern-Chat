package com.modernchat.common;

import lombok.Getter;

@Getter
public enum ChatMode
{
    CLAN_GIM(-2, -1),
    PRIVATE(-1, -1),
    PUBLIC(0, 0),
    FRIENDS_CHAT(2, 1),
    CLAN_MAIN(3, 2),
    CLAN_GUEST(4, 3);

    final int value, order;
    ChatMode(int value, int order) {
        this.value = value;
        this.order = order;
    }
}
