package com.modernchat.common;

import lombok.Getter;

@Getter
public enum ClanType
{
    NORMAL(0),
    IRONMAN(1);

    private final int value;
    ClanType(int value) {
        this.value = value;
    }
}
