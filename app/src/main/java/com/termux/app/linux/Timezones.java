package com.termux.app.linux;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed list of selectable timezones; only these ever reach a command. */
public final class Timezones {

    public static final String DEFAULT = "Europe/Istanbul";

    private static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
        DEFAULT, "UTC", "Europe/London", "Europe/Berlin", "America/New_York", "Asia/Tokyo"));

    private Timezones() {}

    public static List<String> all() {
        return ALL;
    }

    public static boolean isKnown(String tz) {
        return tz != null && ALL.contains(tz);
    }
}
