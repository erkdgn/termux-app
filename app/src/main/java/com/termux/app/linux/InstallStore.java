package com.termux.app.linux;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Reads and writes install metadata and scans installs on disk. Pure Java, no Android APIs. */
public final class InstallStore {

    private static final String SUFFIX = ".properties";

    private final LinuxPaths mPaths;

    public InstallStore(LinuxPaths paths) {
        mPaths = paths;
    }

    /** Writes metadata and (non-empty) keys for the install, creating the meta dir. */
    public void save(InstallOptions o) throws IOException {
        File dir = new File(mPaths.metaDir());
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        Properties p = new Properties();
        p.setProperty("distro", o.distro.id());
        p.setProperty("user", o.user);
        p.setProperty("sudo", String.valueOf(o.sudo));
        p.setProperty("ssh", String.valueOf(o.ssh));
        p.setProperty("sshPort", String.valueOf(o.sshPort));
        p.setProperty("sshAutoStart", String.valueOf(o.sshAutoStart));
        p.setProperty("rootPassword", String.valueOf(o.rootPassword));
        OutputStream out = new FileOutputStream(mPaths.metaFile(o.alias));
        try {
            Writer w = new OutputStreamWriter(out, "UTF-8");
            p.store(w, null);
            w.flush();
        } finally {
            out.close();
        }
        // A stale marker from an earlier install of this alias must not make a new install look READY.
        new File(mPaths.doneFile(o.alias)).delete();
        new File(mPaths.failedFile(o.alias)).delete();
        writeKey(mPaths.rootKeyFile(o.alias), o.rootKey);
        writeKey(mPaths.userKeyFile(o.alias), o.userKey);
    }

    private static void writeKey(String path, String key) throws IOException {
        File f = new File(path);
        if (key.isEmpty()) {
            if (f.exists()) f.delete();
            return;
        }
        OutputStream out = new FileOutputStream(f);
        try {
            Writer w = new OutputStreamWriter(out, "UTF-8");
            w.write(key + "\n");
            w.flush();
        } finally {
            out.close();
        }
    }

    /** Lists installs sorted by alias; removes orphaned metadata. */
    public List<InstallInfo> scan(Set<String> openSessionNames) {
        Set<String> sessions = openSessionNames == null ? Collections.<String>emptySet() : openSessionNames;
        TreeSet<String> aliases = new TreeSet<>();
        boolean listingFailed = false;
        File rootfsDir = new File(mPaths.rootfsDir());
        File[] dirs = rootfsDir.listFiles();
        if (dirs == null && rootfsDir.exists()) listingFailed = true;
        if (dirs != null) {
            for (File f : dirs) {
                if (f.isDirectory() && InputValidator.isValidAlias(f.getName())) aliases.add(f.getName());
            }
        }
        File metaDir = new File(mPaths.metaDir());
        File[] metas = metaDir.listFiles();
        if (metas == null && metaDir.exists()) listingFailed = true;
        if (metas != null) {
            for (File f : metas) {
                String n = f.getName();
                if (f.isFile() && n.endsWith(SUFFIX)) {
                    String alias = n.substring(0, n.length() - SUFFIX.length());
                    if (InputValidator.isValidAlias(alias)) aliases.add(alias);
                }
            }
        }

        List<InstallInfo> result = new ArrayList<>();
        for (String alias : aliases) {
            boolean hasRootfs = new File(mPaths.rootfs(alias)).isDirectory();
            Properties p = readProps(alias);
            InstallInfo.Status status;
            boolean installing = sessions.contains(SessionNames.install(alias));
            boolean failed = new File(mPaths.failedFile(alias)).exists();
            if (hasRootfs && new File(mPaths.doneFile(alias)).exists()) {
                status = InstallInfo.Status.READY;
            } else if (failed && (installing || hasRootfs)) {
                // The install chain failed; its session stays open at the prompt, so check this first.
                status = InstallInfo.Status.INCOMPLETE;
            } else if (installing) {
                status = InstallInfo.Status.INSTALLING;
            } else if (hasRootfs) {
                status = InstallInfo.Status.INCOMPLETE;
            } else {
                // Never delete metadata when a listing failed: the rootfs may merely be unreadable.
                if (!listingFailed) removeMetadata(alias);
                continue;
            }
            Distro distro = null;
            String user = "";
            boolean ssh = false;
            int port = 0;
            boolean autoStart = false;
            if (p != null) {
                distro = Distro.fromId(p.getProperty("distro"));
                user = p.getProperty("user", "");
                ssh = Boolean.parseBoolean(p.getProperty("ssh"));
                autoStart = Boolean.parseBoolean(p.getProperty("sshAutoStart"));
                try {
                    port = Integer.parseInt(p.getProperty("sshPort", "0").trim());
                } catch (NumberFormatException e) {
                    port = 0;
                }
            }
            result.add(new InstallInfo(alias, status, distro, user, ssh, port, autoStart));
        }
        return result;
    }

    public boolean isAliasTaken(String alias) {
        return new File(mPaths.rootfs(alias)).exists() || new File(mPaths.metaFile(alias)).exists();
    }

    /** Returns null if the metadata is missing or unreadable. */
    private Properties readProps(String alias) {
        File f = new File(mPaths.metaFile(alias));
        if (!f.isFile()) return null;
        try {
            InputStream in = new FileInputStream(f);
            try {
                Reader r = new InputStreamReader(in, "UTF-8");
                Properties p = new Properties();
                p.load(r);
                return p;
            } finally {
                in.close();
            }
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    private void removeMetadata(String alias) {
        new File(mPaths.metaFile(alias)).delete();
        new File(mPaths.doneFile(alias)).delete();
        new File(mPaths.failedFile(alias)).delete();
        new File(mPaths.rootKeyFile(alias)).delete();
        new File(mPaths.userKeyFile(alias)).delete();
    }
}
