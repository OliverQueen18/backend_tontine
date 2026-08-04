package com.tontinemarche.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Dates métier au fuseau Mali (évite les écarts si le serveur JVM est en UTC/Europe).
 */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Africa/Bamako");

    private AppClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
