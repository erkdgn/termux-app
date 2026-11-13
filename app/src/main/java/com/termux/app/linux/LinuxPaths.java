package com.termux.app.linux;

import com.termux.shared.termux.TermuxConstants;

/** File system locations used by Linux installs. All values are fixed paths, never user text. */
public final class LinuxPaths {

    private final String mRootfsDir;
    private final String mMetaDir;
    private final String mHomeDir;

    public LinuxPaths(String rootfsDir, String metaDir, String homeDir) {
        mRootfsDir = rootfsDir;
        mMetaDir = metaDir;
        mHomeDir = homeDir;
    }

    public static LinuxPaths defaults() {
        return new LinuxPaths(
            TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/proot-distro/installed-rootfs",
            TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/linux-installs",
            TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    public String rootfs(String alias) {
        return mRootfsDir + "/" + alias;
    }

    public String metaFile(String alias) {
        return mMetaDir + "/" + alias + ".properties";
    }

    public String doneFile(String alias) {
        return mMetaDir + "/" + alias + ".done";
    }

    public String rootKeyFile(String alias) {
        return mMetaDir + "/" + alias + ".root.pub";
    }

    public String userKeyFile(String alias) {
        return mMetaDir + "/" + alias + ".user.pub";
    }

    public String rootfsDir() {
        return mRootfsDir;
    }

    public String metaDir() {
        return mMetaDir;
    }

    public String homeDir() {
        return mHomeDir;
    }
}
