package com.modernchat.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FormatUtil {

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public static String toHmsTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(HMS);
    }

    public static String toHmTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(HM);
    }
}
