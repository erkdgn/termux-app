package com.termux.app.linux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable set of user choices for a Linux install. Values must be checked with {@link InputValidator}. */
public final class InstallOptions {

    public final Distro distro;
    /** Non-null; {@link InputValidator} checks it belongs to {@link #distro}. */
    public final DistroVersion version;
    public final String alias;
    /** Empty string means no regular user. */
    public final String user;
    public final boolean sudo;
    public final boolean basicTools;
    public final boolean ssh;
    public final int sshPort;
    public final boolean sshAutoStart;
    public final boolean rootPassword;
    /** Empty string means none; expected to be already normalized. */
    public final String rootKey;
    /** Empty string means none. */
    public final String userKey;
    /** Empty string means leave the image default. */
    public final String timezone;
    public final boolean localeTr;
    /** Empty string means none. */
    public final String hostname;
    /** Unmodifiable. */
    public final Set<PackageGroup> groups;
    /** Unmodifiable; may contain duplicates. */
    public final List<String> extraPackages;

    public InstallOptions(Distro distro, String alias, String user, boolean sudo, boolean basicTools,
                          boolean ssh, int sshPort, boolean sshAutoStart, boolean rootPassword,
                          String rootKey, String userKey) {
        this(distro, null, alias, user, sudo, basicTools, ssh, sshPort, sshAutoStart, rootPassword,
            rootKey, userKey, "", false, "", null, null);
    }

    private InstallOptions(Distro distro, DistroVersion version, String alias, String user, boolean sudo,
                           boolean basicTools, boolean ssh, int sshPort, boolean sshAutoStart,
                           boolean rootPassword, String rootKey, String userKey, String timezone,
                           boolean localeTr, String hostname, Set<PackageGroup> groups,
                           List<String> extraPackages) {
        this.distro = Objects.requireNonNull(distro, "distro");
        this.version = version == null ? DistroVersion.defaultFor(distro) : version;
        this.alias = alias == null ? "" : alias;
        this.user = user == null ? "" : user;
        this.sudo = sudo;
        this.basicTools = basicTools;
        this.ssh = ssh;
        this.sshPort = sshPort;
        this.sshAutoStart = sshAutoStart;
        this.rootPassword = rootPassword;
        this.rootKey = rootKey == null ? "" : rootKey;
        this.userKey = userKey == null ? "" : userKey;
        this.timezone = timezone == null ? "" : timezone;
        this.localeTr = localeTr;
        this.hostname = hostname == null ? "" : hostname;
        this.groups = Collections.unmodifiableSet(groups == null || groups.isEmpty()
            ? EnumSet.noneOf(PackageGroup.class) : EnumSet.copyOf(groups));
        this.extraPackages = extraPackages == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(extraPackages));
    }

    public RootSshLogin rootSshLogin() {
        if (rootPassword) return RootSshLogin.PASSWORD;
        if (!rootKey.isEmpty()) return RootSshLogin.KEY_ONLY;
        return RootSshLogin.NO;
    }

    public static final class Builder {
        private Distro mDistro;
        private DistroVersion mVersion;
        private String mAlias = "";
        private String mUser = "";
        private boolean mSudo;
        private boolean mBasicTools;
        private boolean mSsh;
        private int mSshPort = InputValidator.DEFAULT_PORT;
        private boolean mSshAutoStart;
        private boolean mRootPassword;
        private String mRootKey = "";
        private String mUserKey = "";
        private String mTimezone = "";
        private boolean mLocaleTr;
        private String mHostname = "";
        private Set<PackageGroup> mGroups = EnumSet.noneOf(PackageGroup.class);
        private List<String> mExtraPackages = Collections.emptyList();

        public Builder distro(Distro v) { mDistro = v; return this; }
        public Builder version(DistroVersion v) { mVersion = v; return this; }
        public Builder alias(String v) { mAlias = v; return this; }
        public Builder user(String v) { mUser = v; return this; }
        public Builder sudo(boolean v) { mSudo = v; return this; }
        public Builder basicTools(boolean v) { mBasicTools = v; return this; }
        public Builder ssh(boolean v) { mSsh = v; return this; }
        public Builder sshPort(int v) { mSshPort = v; return this; }
        public Builder sshAutoStart(boolean v) { mSshAutoStart = v; return this; }
        public Builder rootPassword(boolean v) { mRootPassword = v; return this; }
        public Builder rootKey(String v) { mRootKey = v; return this; }
        public Builder userKey(String v) { mUserKey = v; return this; }
        public Builder timezone(String v) { mTimezone = v; return this; }
        public Builder localeTr(boolean v) { mLocaleTr = v; return this; }
        public Builder hostname(String v) { mHostname = v; return this; }
        public Builder groups(Set<PackageGroup> v) { mGroups = v; return this; }
        public Builder extraPackages(List<String> v) { mExtraPackages = v; return this; }

        /** @throws IllegalStateException if no distro was set */
        public InstallOptions build() {
            if (mDistro == null) throw new IllegalStateException("distro not set");
            return new InstallOptions(mDistro, mVersion, mAlias, mUser, mSudo, mBasicTools, mSsh,
                mSshPort, mSshAutoStart, mRootPassword, mRootKey, mUserKey, mTimezone, mLocaleTr,
                mHostname, mGroups, mExtraPackages);
        }
    }

    public boolean hasUser() {
        return !user.isEmpty();
    }
}
