package com.termux.app.linux;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Predefined package bundles offered at install time. */
public enum PackageGroup {
    DEV("build-essential", "git"),
    PYTHON("python3", "python3-pip", "python3-venv"),
    NODEJS("nodejs", "npm"),
    NGINX("nginx"),
    POSTGRESQL("postgresql"),
    MARIADB("mariadb-server");

    private final List<String> mPackages;

    PackageGroup(String... packages) {
        mPackages = Collections.unmodifiableList(Arrays.asList(packages));
    }

    public List<String> packages() {
        return mPackages;
    }

    /** Returns the group with the given enum name, or null if unknown. */
    public static PackageGroup fromName(String name) {
        if (name == null) return null;
        for (PackageGroup g : values()) {
            if (g.name().equals(name)) return g;
        }
        return null;
    }
}
