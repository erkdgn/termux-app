package com.termux.app.linux;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final String ENV = " -- env DEBIAN_FRONTEND=noninteractive bash -c '";
    private static final String HOSTS_DELETE = "sed -i \"/^127.0.1.1 /d\" /etc/hosts";

    private final LinuxPaths mPaths;
    private final String mUserLabelFormat;
    private final String mRootLabel;

    /** English password labels. */
    public CommandBuilder(LinuxPaths paths) {
        this(paths, "Password for user %s", "Password for root");
    }

    /**
     * @param userPasswordLabelFormat contains one %s for the user name
     * @throws IllegalArgumentException if a formatted label contains characters outside the whitelist
     */
    public CommandBuilder(LinuxPaths paths, String userPasswordLabelFormat, String rootPasswordLabel) {
        if (userPasswordLabelFormat == null || rootPasswordLabel == null
            || !InputValidator.isSafeLabel(String.format(userPasswordLabelFormat, "user"))
            || !InputValidator.isSafeLabel(rootPasswordLabel)) {
            throw new IllegalArgumentException("label");
        }
        mPaths = paths;
        mUserLabelFormat = userPasswordLabelFormat;
        mRootLabel = rootPasswordLabel;
    }

    public String install(InstallOptions o) {
        String a = o.alias;
        String login = loginPrefix(a, o.hostname);
        StringBuilder sb = new StringBuilder("if pkg update -y");
        sb.append(AND).append("pkg install -y proot-distro");
        sb.append(AND).append("proot-distro install --override-alias ").append(a).append(' ').append(o.version.imageRef());
        sb.append(AND).append(login).append(ENV).append(inner(o)).append('\'');
        // Keys are piped into the container, so this works whatever rootfs layout proot-distro uses.
        if (!o.rootKey.isEmpty()) {
            appendKeyInstall(sb, login, mPaths.rootKeyFile(a), "/root/.ssh", null);
        }
        if (o.hasUser() && !o.userKey.isEmpty()) {
            appendKeyInstall(sb, login, mPaths.userKeyFile(a), "/home/" + o.user + "/.ssh", o.user);
        }
        // The marker files tell the app how the install ended; the session itself stays open either way.
        sb.append("; then touch ").append(mPaths.doneFile(a));
        if (o.hasUser()) {
            sb.append(AND).append("echo \"== ").append(String.format(mUserLabelFormat, o.user)).append(" ==\"")
                .append(AND).append(login).append(" -- passwd ").append(o.user);
        }
        if (o.rootPassword) {
            sb.append(AND).append("echo \"== ").append(mRootLabel).append(" ==\"")
                .append(AND).append(login).append(" -- passwd root");
        }
        List<String> pkgs = packageList(o.groups, o.extraPackages);
        if (!pkgs.isEmpty()) {
            // Non-fatal: a failing optional package only sets the marker.
            sb.append("; ").append(login).append(ENV).append("apt-get install -y ").append(join(pkgs, " "))
                .append("' || touch ").append(mPaths.pkgFailedFile(a));
        }
        sb.append("; else touch ").append(mPaths.failedFile(a)).append("; fi");
        return sb.append('\n').toString();
    }

    public String login(String alias, String hostname) {
        return loginPrefix(alias, hostname) + "\n";
    }

    public String loginUser(String alias, String hostname, String user) {
        return loginPrefix(alias, hostname) + " --user " + user + "\n";
    }

    public String sshStart(String alias, String hostname) {
        return "exec " + loginPrefix(alias, hostname) + " -- /usr/sbin/sshd -D -e\n";
    }

    public String update(String alias, String hostname) {
        return loginPrefix(alias, hostname) + ENV + "apt-get update" + AND
            + "apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade'\n";
    }

    /** @throws IllegalArgumentException if there is nothing to install or a token is invalid */
    public String installPackages(String alias, String hostname, Set<PackageGroup> groups, List<String> extras) {
        List<String> pkgs = packageList(groups, extras);
        if (pkgs.isEmpty()) throw new IllegalArgumentException("no packages");
        for (String p : pkgs) {
            if (!InputValidator.isValidPackage(p)) throw new IllegalArgumentException("package");
        }
        return loginPrefix(alias, hostname) + ENV + "apt-get update" + AND + "apt-get install -y " + join(pkgs, " ")
            + "'" + AND + "rm -f " + mPaths.pkgFailedFile(alias) + " || touch " + mPaths.pkgFailedFile(alias) + "\n";
    }

    /**
     * Returns "" when there is nothing to apply.
     *
     * @throws IllegalArgumentException if SSH settings are applied with an invalid port or no mode
     */
    public String applyEdit(String alias, String hostname, boolean sshChanged, boolean ssh, int port,
                            RootSshLogin mode, boolean hostnameChanged) {
        if (sshChanged && ssh && (!InputValidator.isValidPort(port) || mode == null)) {
            throw new IllegalArgumentException("ssh settings");
        }
        StringBuilder steps = new StringBuilder();
        if (sshChanged && ssh) {
            steps.append("(command -v sshd >/dev/null || (apt-get update").append(AND)
                .append("apt-get install -y openssh-server))").append(AND).append("ssh-keygen -A").append(AND)
                .append("mkdir -p /run/sshd").append(AND)
                .append("printf \"Port ").append(port).append("\\nPermitRootLogin ").append(mode.sshdValue())
                .append("\\n\" > /etc/ssh/sshd_config.d/termux.conf");
        }
        if (hostnameChanged) {
            if (steps.length() > 0) steps.append(AND);
            steps.append(hostname == null || hostname.isEmpty() ? HOSTS_DELETE : hostsLine(hostname));
        }
        if (steps.length() == 0) return "";
        return loginPrefix(alias, hostname) + ENV + steps + "'\n";
    }

    /** For display and copying only; never executed by the app. */
    public static String displaySshCommand(String user, int port, String ip) {
        return "ssh -p " + port + " " + (user == null || user.isEmpty() ? "root" : user) + "@" + ip;
    }

    public String backup(String alias, boolean downloadsAvailable, String timestamp) {
        String dir = downloadsAvailable ? mPaths.homeDir() + "/storage/downloads" : mPaths.homeDir();
        return "proot-distro backup --output " + dir + "/" + alias + "-" + timestamp + ".tar.gz " + alias + "\n";
    }

    public String remove(String alias) {
        StringBuilder sb = new StringBuilder("proot-distro remove ").append(alias).append(AND).append("rm -f");
        for (String f : mPaths.metadataFiles(alias)) sb.append(' ').append(f);
        return sb.append('\n').toString();
    }

    public String setPassword(String alias, String hostname, String account) {
        return loginPrefix(alias, hostname) + " -- passwd " + account + "\n";
    }

    /** {@code proot-distro login <alias>[ --hostname <h>]}; the hostname is re-validated. */
    private static String loginPrefix(String alias, String hostname) {
        if (hostname == null || hostname.isEmpty()) return "proot-distro login " + alias;
        if (!InputValidator.isValidHostname(hostname)) throw new IllegalArgumentException("hostname");
        return "proot-distro login " + alias + " --hostname " + hostname;
    }

    private static String hostsLine(String hostname) {
        return HOSTS_DELETE + AND + "echo \"127.0.1.1 " + hostname + "\" >> /etc/hosts";
    }

    private static List<String> packageList(Set<PackageGroup> groups, List<String> extras) {
        Set<String> pkgs = new LinkedHashSet<>();
        if (groups != null) for (PackageGroup g : groups) pkgs.addAll(g.packages());
        if (extras != null) pkgs.addAll(extras);
        return new ArrayList<>(pkgs);
    }

    private static void appendKeyInstall(StringBuilder sb, String login, String keyFile, String sshDir, String owner) {
        sb.append(AND).append(login).append(" -- sh -c \"mkdir -p ").append(sshDir)
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
        if (!o.timezone.isEmpty()) pkgs.add("tzdata");
        if (o.localeTr) pkgs.add("locales");
        if (!pkgs.isEmpty()) {
            sb.append(AND).append("apt-get install -y ").append(join(pkgs, " "));
        }

        if (!o.timezone.isEmpty()) {
            sb.append(AND).append("ln -sf /usr/share/zoneinfo/").append(o.timezone).append(" /etc/localtime")
                .append(AND).append("echo ").append(o.timezone).append(" > /etc/timezone");
        }
        if (o.localeTr) {
            if (o.distro == Distro.UBUNTU) {
                sb.append(AND).append("locale-gen tr_TR.UTF-8").append(AND).append("update-locale LANG=tr_TR.UTF-8");
            } else {
                sb.append(AND).append("sed -i \"s/^# *tr_TR.UTF-8 UTF-8/tr_TR.UTF-8 UTF-8/\" /etc/locale.gen")
                    .append(AND).append("locale-gen").append(AND).append("update-locale LANG=tr_TR.UTF-8");
            }
        }
        if (!o.hostname.isEmpty()) {
            sb.append(AND).append(hostsLine(o.hostname));
        }
        if (o.ssh) {
            String mode = o.rootSshLogin().sshdValue();
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
