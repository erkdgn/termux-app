package com.termux.app.linux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Read-only description of an install found on disk. */
public final class InstallInfo {

    public enum Status { READY, INSTALLING, INCOMPLETE, NOT_FOUND }

    public final String alias;
    public final Status status;
    /** Null if unknown. */
    public final Distro distro;
    /** Null if unknown. */
    public final DistroVersion version;
    /** Empty string if none. */
    public final String user;
    /** Empty string if unknown. */
    public final String hostname;
    /** Empty string if unknown. */
    public final String timezone;
    public final boolean localeTr;
    public final boolean ssh;
    public final int sshPort;
    public final boolean sshAutoStart;
    public final RootSshLogin rootSshLogin;
    public final Set<PackageGroup> groups;
    public final List<String> extraPackages;
    /** The install chain left a failed marker. */
    public final boolean failed;
    /** The package step of the install chain failed. */
    public final boolean packagesFailed;
    /** A metadata file exists for this install. */
    public final boolean hasMetadata;

    private InstallInfo(Builder b) {
        this.alias = b.mAlias;
        this.status = b.mStatus;
        this.distro = b.mDistro;
        this.version = b.mVersion;
        this.user = b.mUser == null ? "" : b.mUser;
        this.hostname = b.mHostname == null ? "" : b.mHostname;
        this.timezone = b.mTimezone == null ? "" : b.mTimezone;
        this.localeTr = b.mLocaleTr;
        this.ssh = b.mSsh;
        this.sshPort = b.mSshPort;
        this.sshAutoStart = b.mSshAutoStart;
        this.rootSshLogin = b.mRootSshLogin == null ? RootSshLogin.KEY_ONLY : b.mRootSshLogin;
        this.groups = Collections.unmodifiableSet(b.mGroups.isEmpty()
            ? EnumSet.noneOf(PackageGroup.class) : EnumSet.copyOf(b.mGroups));
        this.extraPackages = Collections.unmodifiableList(new ArrayList<>(b.mExtraPackages));
        this.failed = b.mFailed;
        this.packagesFailed = b.mPackagesFailed;
        this.hasMetadata = b.mHasMetadata;
    }

    public static final class Builder {
        private String mAlias = "";
        private Status mStatus = Status.INCOMPLETE;
        private Distro mDistro;
        private DistroVersion mVersion;
        private String mUser = "";
        private String mHostname = "";
        private String mTimezone = "";
        private boolean mLocaleTr;
        private boolean mSsh;
        private int mSshPort;
        private boolean mSshAutoStart;
        private RootSshLogin mRootSshLogin = RootSshLogin.KEY_ONLY;
        private Set<PackageGroup> mGroups = EnumSet.noneOf(PackageGroup.class);
        private List<String> mExtraPackages = Collections.emptyList();
        private boolean mFailed;
        private boolean mPackagesFailed;
        private boolean mHasMetadata;

        public Builder alias(String v) { mAlias = v; return this; }
        public Builder status(Status v) { mStatus = v; return this; }
        public Builder distro(Distro v) { mDistro = v; return this; }
        public Builder version(DistroVersion v) { mVersion = v; return this; }
        public Builder user(String v) { mUser = v; return this; }
        public Builder hostname(String v) { mHostname = v; return this; }
        public Builder timezone(String v) { mTimezone = v; return this; }
        public Builder localeTr(boolean v) { mLocaleTr = v; return this; }
        public Builder ssh(boolean v) { mSsh = v; return this; }
        public Builder sshPort(int v) { mSshPort = v; return this; }
        public Builder sshAutoStart(boolean v) { mSshAutoStart = v; return this; }
        public Builder rootSshLogin(RootSshLogin v) { mRootSshLogin = v; return this; }
        public Builder groups(Set<PackageGroup> v) { mGroups = v == null ? EnumSet.noneOf(PackageGroup.class) : v; return this; }
        public Builder extraPackages(List<String> v) { mExtraPackages = v == null ? Collections.<String>emptyList() : v; return this; }
        public Builder failed(boolean v) { mFailed = v; return this; }
        public Builder packagesFailed(boolean v) { mPackagesFailed = v; return this; }
        public Builder hasMetadata(boolean v) { mHasMetadata = v; return this; }

        public InstallInfo build() {
            return new InstallInfo(this);
        }
    }
}
