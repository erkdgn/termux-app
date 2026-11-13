package com.termux.app.linux;

/** Read-only description of an install found on disk. */
public final class InstallInfo {

    public enum Status { READY, INCOMPLETE, INSTALLING }

    public final String alias;
    public final Status status;
    /** Null if unknown. */
    public final Distro distro;
    /** Empty string if none. */
    public final String user;
    public final boolean ssh;
    public final int sshPort;
    public final boolean sshAutoStart;

    public InstallInfo(String alias, Status status, Distro distro, String user,
                       boolean ssh, int sshPort, boolean sshAutoStart) {
        this.alias = alias;
        this.status = status;
        this.distro = distro;
        this.user = user == null ? "" : user;
        this.ssh = ssh;
        this.sshPort = sshPort;
        this.sshAutoStart = sshAutoStart;
    }
}
