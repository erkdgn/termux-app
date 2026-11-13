package com.termux.app.linux;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds the shell command lines that are typed into a Termux terminal session.
 *
 * <p>Callers must pass {@link InstallOptions} that already passed {@link InputValidator#validate};
 * this class does not re-validate. Only validated tokens (alias, user, port, distro id) and fixed
 * paths are emitted. Key text is never part of a command: keys are copied from staging files.
 * Every returned command ends with a newline (except {@link #SSH_STOP}).
 */
public final class CommandBuilder {

    /** Ctrl+C, sent to the terminal to stop a foreground sshd. */
    public static final String SSH_STOP = "\u0003";

    private static final String AND = " && ";

    private final LinuxPaths mPaths;

    public CommandBuilder(LinuxPaths paths) {
        mPaths = paths;
    }

    public String install(InstallOptions o) {
        String a = o.alias;
        StringBuilder sb = new StringBuilder("if pkg update -y");
        sb.append(AND).append("pkg install -y proot-distro");
        sb.append(AND).append("proot-distro install --override-alias ").append(a).append(' ').append(o.distro.id());
        sb.append(AND).append("proot-distro login ").append(a)
            .append(" -- env DEBIAN_FRONTEND=noninteractive bash -c '").append(inner(o)).append('\'');
        // Keys are piped into the container, so this works whatever rootfs layout proot-distro uses.
        if (!o.rootKey.isEmpty()) {
            appendKeyInstall(sb, a, mPaths.rootKeyFile(a), "/root/.ssh", null);
        }
        if (o.hasUser() && !o.userKey.isEmpty()) {
            appendKeyInstall(sb, a, mPaths.userKeyFile(a), "/home/" + o.user + "/.ssh", o.user);
        }
        // The marker files tell the app how the install ended; the session itself stays open either way.
        sb.append("; then touch ").append(mPaths.doneFile(a));
        if (o.hasUser()) {
            sb.append(AND).append(passwd(a, o.user));
        }
        if (o.rootPassword) {
            sb.append(AND).append(passwd(a, "root"));
        }
        sb.append("; else touch ").append(mPaths.failedFile(a)).append("; fi");
        return sb.append('\n').toString();
    }

    public String login(String alias) {
        return "proot-distro login " + alias + "\n";
    }

    public String loginUser(String alias, String user) {
        return "proot-distro login " + alias + " --user " + user + "\n";
    }

    public String sshStart(String alias) {
        return "exec proot-distro login " + alias + " -- /usr/sbin/sshd -D -e\n";
    }

    public String backup(String alias, boolean downloadsAvailable, String timestamp) {
        String dir = downloadsAvailable ? mPaths.homeDir() + "/storage/downloads" : mPaths.homeDir();
        return "proot-distro backup --output " + dir + "/" + alias + "-" + timestamp + ".tar.gz " + alias + "\n";
    }

    public String remove(String alias) {
        return "proot-distro remove " + alias + AND + "rm -f " + mPaths.metaFile(alias) + " "
            + mPaths.doneFile(alias) + " " + mPaths.failedFile(alias) + " " + mPaths.rootKeyFile(alias) + " " + mPaths.userKeyFile(alias) + "\n";
    }

    public String setPassword(String alias, String account) {
        return passwd(alias, account) + "\n";
    }

    private static String passwd(String alias, String account) {
        return "proot-distro login " + alias + " -- passwd " + account;
    }

    private static void appendKeyInstall(StringBuilder sb, String alias, String keyFile, String sshDir, String owner) {
        sb.append(AND).append("proot-distro login ").append(alias).append(" -- sh -c \"mkdir -p ").append(sshDir)
            .append(AND).append("chmod 700 ").append(sshDir)
            .append(AND).append("cat > ").append(sshDir).append("/authorized_keys")
            .append(AND).append("chmod 600 ").append(sshDir).append("/authorized_keys");
        if (owner != null) {
            sb.append(AND).append("chown -R ").append(owner).append(':').append(owner).append(' ').append(sshDir);
        }
        sb.append("\" < ").append(keyFile);
    }

    /** Commands run inside the distro; contains no single quote characters. */
    private static String inner(InstallOptions o) {
        StringBuilder sb = new StringBuilder();
        sb.append("apt-get update");
        sb.append(AND).append("apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade");

        Set<String> pkgs = new LinkedHashSet<>();
        if (o.basicTools) {
            pkgs.add("sudo");
            pkgs.add("nano");
            pkgs.add("wget");
            pkgs.add("curl");
            pkgs.add("git");
            pkgs.add("net-tools");
        }
        if (o.sudo) pkgs.add("sudo");
        if (o.ssh) pkgs.add("openssh-server");
        if (!pkgs.isEmpty()) {
            sb.append(AND).append("apt-get install -y ").append(join(pkgs, " "));
        }

        if (o.ssh) {
            String mode = o.rootPassword ? "yes" : (!o.rootKey.isEmpty() ? "prohibit-password" : "no");
            sb.append(AND).append("ssh-keygen -A").append(AND).append("mkdir -p /run/sshd").append(AND)
                .append("printf \"Port ").append(o.sshPort).append("\\nPermitRootLogin ").append(mode)
                .append("\\n\" > /etc/ssh/sshd_config.d/termux.conf");
        }
        if (o.hasUser()) {
            sb.append(AND).append("useradd -m -s /bin/bash").append(o.sudo ? " -G sudo " : " ").append(o.user);
        }
        return sb.toString();
    }

    private static String join(Collection<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }
}
