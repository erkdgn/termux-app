package com.termux.app.linux;

/** Linux distributions that can be installed through proot-distro. */
public enum Distro {
    UBUNTU("ubuntu", "Ubuntu"),
    DEBIAN("debian", "Debian");

    private final String mId;
    private final String mLabel;

    Distro(String id, String label) {
        mId = id;
        mLabel = label;
    }

    public String id() {
        return mId;
    }

    public String label() {
        return mLabel;
    }

    /** Returns the distro with the given proot-distro id, or null if unknown. */
    public static Distro fromId(String id) {
        for (Distro d : values()) {
            if (d.mId.equals(id)) return d;
        }
        return null;
    }
}
