package com.termux.app.linux;

import java.util.Arrays;
import java.util.List;

import com.termux.shared.termux.TermuxConstants;

/** File system locations used by Linux installs. All values are fixed paths, never user text. */
public final class LinuxPaths {

    /** proot-distro's runtime dir, e.g. $PREFIX/var/lib/proot-distro. */
    private final String mProotDistroDir;
    private final String mMetaDir;
    private final String mHomeDir;

    public LinuxPaths(String prootDistroDir, String metaDir, String homeDir) {
        mProotDistroDir = prootDistroDir;
        mMetaDir = metaDir;
        mHomeDir = homeDir;
    }

    public static LinuxPaths defaults() {
        return new LinuxPaths(
            TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/var/lib/proot-distro",
            TermuxConstants.TERMUX_DATA_HOME_DIR_PATH + "/linux-installs",
            TermuxConstants.TERMUX_HOME_DIR_PATH);
    }

    /** Container root filesystem in the current proot-distro layout: containers/&lt;alias&gt;/rootfs. */
    public String rootfs(String alias) {
        return containersDir() + "/" + alias + "/rootfs";
    }

    /** Root filesystem in the layout of older proot-distro versions: installed-rootfs/&lt;alias&gt;. */
    public String legacyRootfs(String alias) {
        return legacyRootfsDir() + "/" + alias;
    }

    public String metaFile(String alias) {
        return mMetaDir + "/" + alias + ".properties";
    }

    public String doneFile(String alias) {
        return mMetaDir + "/" + alias + ".done";
    }

    public String failedFile(String alias) {
        return mMetaDir + "/" + alias + ".failed";
    }

    public String pkgFailedFile(String alias) {
        return mMetaDir + "/" + alias + ".pkgfailed";
    }

    public String rootKeyFile(String alias) {
        return mMetaDir + "/" + alias + ".root.pub";
    }

    public String userKeyFile(String alias) {
        return mMetaDir + "/" + alias + ".user.pub";
    }

    /** All per-install files under the meta dir (metadata, markers, staged keys). */
    public List<String> metadataFiles(String alias) {
        return Arrays.asList(metaFile(alias), doneFile(alias), failedFile(alias), pkgFailedFile(alias),
            rootKeyFile(alias), userKeyFile(alias));
    }

    public String containersDir() {
        return mProotDistroDir + "/containers";
    }

    public String legacyRootfsDir() {
        return mProotDistroDir + "/installed-rootfs";
    }

    public String metaDir() {
        return mMetaDir;
    }

    public String homeDir() {
        return mHomeDir;
    }
}
