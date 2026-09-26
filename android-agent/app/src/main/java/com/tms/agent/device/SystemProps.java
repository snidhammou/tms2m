package com.tms.agent.device;

import java.lang.reflect.Method;

/** Lecture de android.os.SystemProperties (API cachée, accessible par réflexion). */
public final class SystemProps {

    private SystemProps() {
    }

    public static String get(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("get", String.class);
            String value = (String) m.invoke(null, key);
            return value == null || value.isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    /** Première propriété non vide parmi {@code keys}. */
    public static String first(String... keys) {
        for (String key : keys) {
            String v = get(key);
            if (v != null && !"unknown".equalsIgnoreCase(v)) {
                return v;
            }
        }
        return null;
    }
}
