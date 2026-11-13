package com.termux.app.linux;

import com.termux.shared.termux.TermuxConstants;

/** File system locations used by Linux installs. All values are fixed paths, never user text. */
final class LinuxPaths {

    private final String mRootfsDir;
    private final String mMetaDir;
    private final String mHomeDir;

    LinuxPaths(String rootfsDir, String metaDir, String homeDir) {
        mRootfsDir = rootfsDir;
        mMetaDir = metaDir;
        mHomeDir = homeDir;
    }

    static LinuxPaths defaults() {
        return new LinuxPaths(
            TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/proot-distro/installed-rootfs",
            TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/linux-installs",
            TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    String rootfs(String alias) {
        return mRootfsDir + "/" + alias;
    }

    String metaFile(String alias) {
        return mMetaDir + "/" + alias + ".properties";
    }

    String doneFile(String alias) {
        return mMetaDir + "/" + alias + ".done";
    }

    String rootKeyFile(String alias) {
        return mMetaDir + "/" + alias + ".root.pub";
    }

    String userKeyFile(String alias) {
        return mMetaDir + "/" + alias + ".user.pub";
    }

    String rootfsDir() {
        return mRootfsDir;
    }

    String metaDir() {
        return mMetaDir;
    }

    String homeDir() {
        return mHomeDir;
    }
}
