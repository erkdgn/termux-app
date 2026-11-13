package com.termux.app.linux;

import java.util.Set;

/** Picks terminal session names that do not collide with already open sessions. */
public final class SessionNames {

    private SessionNames() {}

    /** Returns base, else "base (2)", "base (3)", ... the first one not in taken. */
    public static String firstFree(String base, Set<String> taken) {
        if (taken == null || !taken.contains(base)) return base;
        for (int n = 2; ; n++) {
            String candidate = base + " (" + n + ")";
            if (!taken.contains(candidate)) return candidate;
        }
    }
}
