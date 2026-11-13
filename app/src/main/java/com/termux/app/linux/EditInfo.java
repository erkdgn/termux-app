package com.termux.app.linux;

/** Values the detail page may change on an existing install. */
public final class EditInfo {

    /** Null = unknown. */
    public final Distro distro;
    /** Null = unknown. */
    public final DistroVersion version;
    public final String user;
    public final String hostname;
    public final boolean ssh;
    public final int sshPort;
    public final boolean sshAutoStart;
    public final RootSshLogin rootSshLogin;

    public EditInfo(Distro distro, DistroVersion version, String user, String hostname, boolean ssh,
                    int sshPort, boolean sshAutoStart, RootSshLogin rootSshLogin) {
        this.distro = distro;
        this.version = version;
        this.user = user == null ? "" : user;
        this.hostname = hostname == null ? "" : hostname;
        this.ssh = ssh;
        this.sshPort = sshPort;
        this.sshAutoStart = sshAutoStart;
        this.rootSshLogin = rootSshLogin == null ? RootSshLogin.KEY_ONLY : rootSshLogin;
    }
}
