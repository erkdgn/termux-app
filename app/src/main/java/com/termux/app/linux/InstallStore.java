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
import java.util.EnumSet;
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
        p.setProperty("version", o.version.id());
        p.setProperty("timezone", o.timezone);
        p.setProperty("localeTr", String.valueOf(o.localeTr));
        p.setProperty("hostname", o.hostname);
        p.setProperty("rootSshLogin", o.rootSshLogin().name());
        StringBuilder groups = new StringBuilder();
        for (PackageGroup g : o.groups) {
            if (groups.length() > 0) groups.append(',');
            groups.append(g.name());
        }
        p.setProperty("groups", groups.toString());
        StringBuilder extras = new StringBuilder();
        for (String x : o.extraPackages) {
            if (extras.length() > 0) extras.append(' ');
            extras.append(x);
        }
        p.setProperty("extraPackages", extras.toString());
        p.setProperty("user", o.user);
        p.setProperty("sudo", String.valueOf(o.sudo));
        p.setProperty("ssh", String.valueOf(o.ssh));
        p.setProperty("sshPort", String.valueOf(o.sshPort));
        p.setProperty("sshAutoStart", String.valueOf(o.sshAutoStart));
        p.setProperty("rootPassword", String.valueOf(o.rootPassword));
        writeProps(o.alias, p);
        // A stale marker from an earlier install of this alias must not make a new install look READY.
        new File(mPaths.doneFile(o.alias)).delete();
        new File(mPaths.failedFile(o.alias)).delete();
        new File(mPaths.pkgFailedFile(o.alias)).delete();
        writeKey(mPaths.rootKeyFile(o.alias), o.rootKey);
        writeKey(mPaths.userKeyFile(o.alias), o.userKey);
    }

    private void writeProps(String alias, Properties p) throws IOException {
        OutputStream out = new FileOutputStream(mPaths.metaFile(alias));
        try {
            Writer w = new OutputStreamWriter(out, "UTF-8");
            p.store(w, null);
            w.flush();
        } finally {
            out.close();
        }
    }

    /**
     * Merges the edited values into the install's metadata (creating it if missing; keys not owned by
     * the edit are kept) and marks the install done when its rootfs exists.
     */
    public void saveEdit(String alias, EditInfo e) throws IOException {
        if (!InputValidator.isValidAlias(alias)) throw new IllegalArgumentException("alias");
        if (!InputValidator.validate(e).isEmpty()) throw new IllegalArgumentException("invalid edit");
        File dir = new File(mPaths.metaDir());
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        Properties p = readProps(alias);
        if (p == null) p = new Properties();
        if (e.distro == null) {
            p.remove("distro");
            p.remove("version");
        } else {
            p.setProperty("distro", e.distro.id());
            if (e.version == null) p.remove("version");
            else p.setProperty("version", e.version.id());
        }
        p.setProperty("user", e.user);
        p.setProperty("hostname", e.hostname);
        p.setProperty("ssh", String.valueOf(e.ssh));
        p.setProperty("sshPort", String.valueOf(e.sshPort));
        p.setProperty("sshAutoStart", String.valueOf(e.sshAutoStart));
        p.setProperty("rootSshLogin", e.rootSshLogin.name());
        writeProps(alias, p);
        if (hasRootfs(alias)) {
            File done = new File(mPaths.doneFile(alias));
            if (!done.exists() && !done.createNewFile() && !done.exists()) {
                throw new IOException("Cannot create " + done);
            }
        }
    }

    /** Deletes all metadata of an install. Only for explicit user actions. */
    public void forget(String alias) {
        if (!InputValidator.isValidAlias(alias)) throw new IllegalArgumentException("alias");
        for (String f : mPaths.metadataFiles(alias)) new File(f).delete();
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

    /** Lists installs sorted by alias. Never deletes anything. */
    public List<InstallInfo> scan(Set<String> openSessionNames) {
        Set<String> sessions = openSessionNames == null ? Collections.<String>emptySet() : openSessionNames;
        TreeSet<String> aliases = new TreeSet<>();
        // Current proot-distro keeps containers/<alias>/rootfs; older versions installed-rootfs/<alias>.
        for (String dirPath : new String[] {mPaths.containersDir(), mPaths.legacyRootfsDir()}) {
            File dir = new File(dirPath);
            File[] dirs = dir.listFiles();
            if (dirs != null) {
                for (File f : dirs) {
                    if (f.isDirectory() && InputValidator.isValidAlias(f.getName())) aliases.add(f.getName());
                }
            }
        }
        File metaDir = new File(mPaths.metaDir());
        File[] metas = metaDir.listFiles();
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
            boolean hasRootfs = hasRootfs(alias);
            Properties p = readProps(alias);
            boolean installing = sessions.contains(SessionNames.install(alias));
            boolean failed = new File(mPaths.failedFile(alias)).exists();
            InstallInfo.Status status;
            if (hasRootfs && new File(mPaths.doneFile(alias)).exists()) {
                status = InstallInfo.Status.READY;
            } else if (installing && !failed) {
                status = InstallInfo.Status.INSTALLING;
            } else if (hasRootfs || (failed && installing)) {
                // A failed install chain leaves its session open at the prompt.
                status = InstallInfo.Status.INCOMPLETE;
            } else {
                status = InstallInfo.Status.NOT_FOUND;
            }
            InstallInfo.Builder b = new InstallInfo.Builder().alias(alias).status(status).failed(failed)
                .packagesFailed(new File(mPaths.pkgFailedFile(alias)).exists())
                .hasMetadata(new File(mPaths.metaFile(alias)).isFile());
            if (p != null) fill(b, p, alias);
            result.add(b.build());
        }
        return result;
    }

    /** Copies metadata into the builder, re-validating every value. */
    private void fill(InstallInfo.Builder b, Properties p, String alias) {
        Distro distro = Distro.fromId(p.getProperty("distro"));
        b.distro(distro);
        if (distro != null) b.version(DistroVersion.fromId(distro, p.getProperty("version")));
        String user = p.getProperty("user", "");
        b.user(InputValidator.isValidUser(user) ? user : "");
        String hostname = p.getProperty("hostname", "");
        b.hostname(InputValidator.isValidHostname(hostname) ? hostname : "");
        String tz = p.getProperty("timezone", "");
        b.timezone(Timezones.isKnown(tz) ? tz : "");
        b.localeTr(Boolean.parseBoolean(p.getProperty("localeTr")));
        b.ssh(Boolean.parseBoolean(p.getProperty("ssh")));
        b.sshAutoStart(Boolean.parseBoolean(p.getProperty("sshAutoStart")));
        int port;
        try {
            port = Integer.parseInt(p.getProperty("sshPort", "0").trim());
        } catch (NumberFormatException e) {
            port = 0;
        }
        b.sshPort(InputValidator.isValidPort(port) ? port : 0);
        RootSshLogin login = RootSshLogin.fromName(p.getProperty("rootSshLogin"));
        if (login == null) {
            // v1 metadata has no rootSshLogin: derive it like InstallOptions.rootSshLogin() does.
            if (Boolean.parseBoolean(p.getProperty("rootPassword"))) login = RootSshLogin.PASSWORD;
            else if (new File(mPaths.rootKeyFile(alias)).isFile()) login = RootSshLogin.KEY_ONLY;
            else login = RootSshLogin.NO;
        }
        b.rootSshLogin(login);
        Set<PackageGroup> groups = EnumSet.noneOf(PackageGroup.class);
        String g = p.getProperty("groups", "").trim();
        if (!g.isEmpty()) {
            for (String name : g.split(",")) {
                PackageGroup pg = PackageGroup.fromName(name.trim());
                if (pg != null) groups.add(pg);
            }
        }
        b.groups(groups);
        List<String> extras = new ArrayList<>();
        String x = p.getProperty("extraPackages", "").trim();
        if (!x.isEmpty()) {
            for (String t : x.split("\\s+")) {
                if (InputValidator.isValidPackage(t) && extras.size() < InputValidator.MAX_EXTRA_PACKAGES) {
                    extras.add(t);
                }
            }
        }
        b.extraPackages(extras);
    }

    public boolean isAliasTaken(String alias) {
        return new File(mPaths.containersDir(), alias).exists() || new File(mPaths.legacyRootfs(alias)).exists()
            || new File(mPaths.metaFile(alias)).exists();
    }

    private boolean hasRootfs(String alias) {
        return new File(mPaths.rootfs(alias)).isDirectory() || new File(mPaths.legacyRootfs(alias)).isDirectory();
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
}
