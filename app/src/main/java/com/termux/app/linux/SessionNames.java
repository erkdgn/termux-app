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

    /** Session of a running install. Contains a space, so it can never equal a valid alias. */
    public static String install(String alias) {
        return alias + " (install)";
    }

    /** Session of the SSH server. Contains a space, so it can never equal a valid alias. */
    public static String ssh(String alias) {
        return alias + " (ssh)";
    }

    /** True for the alias's own sessions: the plain name, "alias (2)", "alias (ssh)", ... */
    public static boolean belongsTo(String sessionName, String alias) {
        return sessionName.equals(alias) || sessionName.startsWith(alias + " (");
    }
}
