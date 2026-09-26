package com.tms.server.domain;

import java.util.Locale;

/**
 * Constructeurs de terminaux supportés. La détection se fait à partir de
 * Build.MANUFACTURER / Build.BRAND remontés par l'agent Android.
 */
public enum Manufacturer {
    NEWLAND, PAX, SUNMI, UROVO, INGENICO, VERIFONE, CASTLES, OTHER;

    public static Manufacturer detect(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        for (Manufacturer m : values()) {
            if (m != OTHER && value.contains(m.name())) {
                return m;
            }
        }
        return OTHER;
    }
}
