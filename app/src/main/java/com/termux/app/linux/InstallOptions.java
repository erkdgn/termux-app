package com.termux.app.linux;

import java.util.Objects;

/** Immutable set of user choices for a Linux install. Values must be checked with {@link InputValidator}. */
public final class InstallOptions {

    public final Distro distro;
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

    public InstallOptions(Distro distro, String alias, String user, boolean sudo, boolean basicTools,
                          boolean ssh, int sshPort, boolean sshAutoStart, boolean rootPassword,
                          String rootKey, String userKey) {
        this.distro = Objects.requireNonNull(distro, "distro");
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
    }

    public boolean hasUser() {
        return !user.isEmpty();
    }
}
