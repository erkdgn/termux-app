package com.termux.app.linux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pinned, installable releases of each distro. Enum order is catalog order. */
public enum DistroVersion {
    UBUNTU_24_04(Distro.UBUNTU, "24.04", "Ubuntu 24.04 LTS", "ubuntu:24.04", true),
    UBUNTU_26_04(Distro.UBUNTU, "26.04", "Ubuntu 26.04 LTS", "ubuntu:26.04", false),
    UBUNTU_22_04(Distro.UBUNTU, "22.04", "Ubuntu 22.04 LTS", "ubuntu:22.04", false),
    DEBIAN_13(Distro.DEBIAN, "13", "Debian 13 (trixie)", "debian:13", true),
    DEBIAN_12(Distro.DEBIAN, "12", "Debian 12 (bookworm)", "debian:12", false);

    private final Distro mDistro;
    private final String mId;
    private final String mLabel;
    private final String mImageRef;
    private final boolean mDefault;

    DistroVersion(Distro distro, String id, String label, String imageRef, boolean isDefault) {
        mDistro = distro;
        mId = id;
        mLabel = label;
        mImageRef = imageRef;
        mDefault = isDefault;
    }

    public Distro distro() {
        return mDistro;
    }

    public String id() {
        return mId;
    }

    public String label() {
        return mLabel;
    }

    public String imageRef() {
        return mImageRef;
    }

    public boolean isDefault() {
        return mDefault;
    }

    /** Versions of the given distro in catalog order. */
    public static List<DistroVersion> forDistro(Distro d) {
        List<DistroVersion> out = new ArrayList<>();
        for (DistroVersion v : values()) {
            if (v.mDistro == d) out.add(v);
        }
        return Collections.unmodifiableList(out);
    }

    public static DistroVersion defaultFor(Distro d) {
        for (DistroVersion v : values()) {
            if (v.mDistro == d && v.mDefault) return v;
        }
        throw new IllegalStateException("No default version for " + d);
    }

    /** Returns the version with the given id for the distro, or null if unknown. */
    public static DistroVersion fromId(Distro d, String id) {
        for (DistroVersion v : values()) {
            if (v.mDistro == d && v.mId.equals(id)) return v;
        }
        return null;
    }
}
