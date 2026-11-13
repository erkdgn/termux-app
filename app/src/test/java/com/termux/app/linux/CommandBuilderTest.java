package com.termux.app.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class CommandBuilderTest {

    private static final String ENV = " -- env DEBIAN_FRONTEND=noninteractive bash -c '";

    private final CommandBuilder b = new CommandBuilder(new LinuxPaths("/R", "/M", "/H"));

    private static InstallOptions.Builder ub() {
        return new InstallOptions.Builder().distro(Distro.UBUNTU).alias("u1");
    }

    private static InstallOptions full(Distro d) {
        return new InstallOptions.Builder().distro(d).alias("f1").user("erkan").sudo(true).basicTools(true)
            .ssh(true).sshPort(2022).rootPassword(true).rootKey("ssh-ed25519 AAAAKEY x@y")
            .userKey("ssh-ed25519 AAAAUSER x@y").timezone("Europe/Istanbul").localeTr(true).hostname("box1")
            .groups(EnumSet.of(PackageGroup.DEV, PackageGroup.PYTHON)).extraPackages(Arrays.asList("htop", "git"))
            .build();
    }

    @Test
    public void minimalInstallUsesImageRef() {
        assertEquals("if pkg update -y && pkg install -y proot-distro && proot-distro install --override-alias u1 ubuntu:24.04"
            + " && proot-distro login u1 -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get update"
            + " && apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade'"
            + "; then touch /M/u1.done; else touch /M/u1.failed; fi\n", b.install(ub().build()));
    }

    @Test
    public void versionPinned() {
        String c = b.install(new InstallOptions.Builder().distro(Distro.DEBIAN).alias("d1")
            .version(DistroVersion.DEBIAN_12).build());
        assertTrue(c, c.contains("--override-alias d1 debian:12 "));
    }

    @Test
    public void fullInstallCore() {
        String c = b.install(full(Distro.DEBIAN));
        assertTrue(c, c.contains("apt-get install -y sudo nano wget curl git net-tools openssh-server tzdata locales"));
        assertTrue(c, c.contains("PermitRootLogin yes"));
        assertTrue(c, c.contains("Port 2022"));
        assertTrue(c, c.contains("useradd -m -s /bin/bash -G sudo erkan"));
        assertTrue(c, c.contains(" && proot-distro login f1 --hostname box1 -- sh -c \"mkdir -p /root/.ssh && chmod 700 /root/.ssh"
            + " && cat > /root/.ssh/authorized_keys && chmod 600 /root/.ssh/authorized_keys\" < /M/f1.root.pub"));
        assertTrue(c, c.contains("chown -R erkan:erkan /home/erkan/.ssh\" < /M/f1.user.pub"));
        assertFalse(c, c.contains("AAAAKEY"));
        assertTrue(c, c.startsWith("if pkg update -y && "));
    }

    @Test
    public void rootKeyOnlyMode() {
        String c = b.install(ub().ssh(true).rootKey("ssh-ed25519 AAAAKEY x@y").build());
        assertTrue(c.contains("PermitRootLogin prohibit-password"));
    }

    @Test
    public void noSudoGroup() {
        String c = b.install(ub().user("erkan").build());
        assertTrue(c, c.contains("useradd -m -s /bin/bash erkan'"));
        assertFalse(c, c.contains("-G sudo"));
    }

    @Test
    public void timezoneAndLocaleUbuntu() {
        String c = b.install(ub().timezone("Europe/Istanbul").localeTr(true).build());
        assertTrue(c, c.contains("apt-get install -y tzdata locales"));
        assertTrue(c, c.contains("ln -sf /usr/share/zoneinfo/Europe/Istanbul /etc/localtime && echo Europe/Istanbul > /etc/timezone"));
        assertTrue(c, c.contains("locale-gen tr_TR.UTF-8 && update-locale LANG=tr_TR.UTF-8"));
        assertFalse(c, c.contains("locale.gen"));
    }

    @Test
    public void utcTimezoneUsesEtcUtc() {
        String c = b.install(ub().timezone("UTC").build());
        assertTrue(c, c.contains("ln -sf /usr/share/zoneinfo/Etc/UTC /etc/localtime && echo Etc/UTC > /etc/timezone"));
        assertFalse(c, c.contains("zoneinfo/UTC "));
    }

    @Test
    public void localeWritesProfileDForBothDistros() {
        String u = b.install(ub().localeTr(true).build());
        String d = b.install(new InstallOptions.Builder().distro(Distro.DEBIAN).alias("d1").localeTr(true).build());
        String line = "echo \"export LANG=tr_TR.UTF-8\" > /etc/profile.d/locale-tr.sh";
        assertTrue(u, u.contains(line));
        assertTrue(d, d.contains(line));
    }

    @Test
    public void localeDebianUsesLocaleGen() {
        String c = b.install(new InstallOptions.Builder().distro(Distro.DEBIAN).alias("d1").localeTr(true).build());
        assertTrue(c, c.contains("sed -i \"s/^# *tr_TR.UTF-8 UTF-8/tr_TR.UTF-8 UTF-8/\" /etc/locale.gen"
            + " && locale-gen && update-locale LANG=tr_TR.UTF-8"));
    }

    @Test
    public void hostsLineWhenHostname() {
        String c = b.install(ub().hostname("box1").build());
        assertTrue(c, c.contains("sed -i \"/^127.0.1.1 /d\" /etc/hosts && echo \"127.0.1.1 box1\" >> /etc/hosts'"));
        assertFalse(b.install(ub().build()).contains("/etc/hosts"));
    }

    @Test
    public void hostnameOnEveryLogin() {
        String c = b.install(ub().user("erkan").rootPassword(true).hostname("box1")
            .groups(EnumSet.of(PackageGroup.NGINX)).rootKey("ssh-ed25519 AAAAKEY x@y").build());
        int n = 0;
        int i = 0;
        while ((i = c.indexOf("proot-distro login u1", i)) >= 0) {
            assertTrue(c, c.startsWith("proot-distro login u1 --hostname box1", i));
            i += 5;
            n++;
        }
        assertTrue(c, n >= 5);
    }

    @Test
    public void labeledPasswords() {
        String c = b.install(ub().user("erkan").rootPassword(true).build());
        assertTrue(c, c.contains("; then touch /M/u1.done && echo \"== Password for user erkan ==\""
            + " && proot-distro login u1 -- passwd erkan && echo \"== Password for root ==\""
            + " && proot-distro login u1 -- passwd root; else touch /M/u1.failed; fi\n"));
    }

    @Test
    public void turkishLabelConstructor() {
        CommandBuilder tr = new CommandBuilder(new LinuxPaths("/R", "/M", "/H"),
            "%s kullanıcısının şifresi", "root şifresi");
        assertTrue(tr.install(ub().user("erkan").build()).contains("echo \"== erkan kullanıcısının şifresi ==\""));
    }

    @Test
    public void packagesStepAfterPasswordsNonFatal() {
        String c = b.install(ub().rootPassword(true).groups(EnumSet.of(PackageGroup.NGINX))
            .extraPackages(Arrays.asList("htop")).build());
        assertTrue(c, c.contains("passwd root; proot-distro login u1" + ENV + "apt-get install -y nginx htop'"
            + " || touch /M/u1.pkgfailed; else touch /M/u1.failed; fi\n"));
    }

    @Test
    public void noPackagesNoPkgStep() {
        assertFalse(b.install(ub().rootPassword(true).build()).contains("pkgfailed"));
    }

    @Test
    public void unsafeLabelRejected() {
        try {
            new CommandBuilder(new LinuxPaths("/R", "/M", "/H"), "x$(id) %s", "root");
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new CommandBuilder(new LinuxPaths("/R", "/M", "/H"), "%s", "r\"oot");
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void backupFallsBackToHome() {
        assertEquals("proot-distro backup --output /H/u1-20261113-120000.tar.gz u1\n",
            b.backup("u1", false, "20261113-120000"));
        assertEquals("proot-distro backup --output /H/storage/downloads/u1-20261113-120000.tar.gz u1\n",
            b.backup("u1", true, "20261113-120000"));
    }

    @Test
    public void removeCleansAllMetadata() {
        String c = b.remove("u1");
        assertEquals("proot-distro remove u1 && rm -f /M/u1.properties /M/u1.done /M/u1.failed /M/u1.pkgfailed"
            + " /M/u1.root.pub /M/u1.user.pub\n", c);
        assertTrue(c.contains(".pkgfailed"));
    }

    @Test
    public void loginCommands() {
        assertEquals("proot-distro login u1\n", b.login("u1", ""));
        assertEquals("proot-distro login u1 --hostname box1\n", b.login("u1", "box1"));
        assertEquals("proot-distro login u1 --hostname box1 --user erkan\n", b.loginUser("u1", "box1", "erkan"));
        assertEquals("proot-distro login u1 --user erkan\n", b.loginUser("u1", "", "erkan"));
    }

    @Test
    public void sshStartUsesExec() {
        assertEquals("exec proot-distro login u1 --hostname box1 -- /usr/sbin/sshd -D -e\n", b.sshStart("u1", "box1"));
        assertEquals("exec proot-distro login u1 -- /usr/sbin/sshd -D -e\n", b.sshStart("u1", ""));
    }

    @Test
    public void sshStartWithBanner() {
        assertEquals("clear; echo \"== ubuntu - Ubuntu 26.04 LTS - SSH sunucusu - port 2022 ==\"; "
                + "exec proot-distro login ubuntu --hostname ubuntu -- /usr/sbin/sshd -D -e\n",
            b.sshStart("ubuntu", "ubuntu", "ubuntu - Ubuntu 26.04 LTS - SSH sunucusu - port 2022"));
        assertEquals(b.sshStart("u1", ""), b.sshStart("u1", "", ""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sshStartUnsafeBannerRejected() {
        b.sshStart("u1", "", "x$(id)");
    }

    @Test
    public void setPassword() {
        assertEquals("proot-distro login u1 -- passwd root\n", b.setPassword("u1", "", "root"));
        assertEquals("proot-distro login u1 --hostname box1 -- passwd root\n", b.setPassword("u1", "box1", "root"));
    }

    @Test
    public void invalidHostnameRejected() {
        try {
            b.login("u1", "a;b");
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void updateShape() {
        assertEquals("proot-distro login u1" + ENV + "apt-get update && apt-get -y -o Dpkg::Options::=--force-confdef"
            + " -o Dpkg::Options::=--force-confold upgrade'\n", b.update("u1", ""));
    }

    @Test
    public void installPackagesShape() {
        assertEquals("proot-distro login u1 --hostname box1" + ENV + "apt-get update && apt-get install -y python3 python3-pip"
                + " python3-venv htop' && rm -f /M/u1.pkgfailed || touch /M/u1.pkgfailed\n",
            b.installPackages("u1", "box1", EnumSet.of(PackageGroup.PYTHON), Arrays.asList("htop")));
    }

    @Test
    public void installPackagesRequiresPackages() {
        try {
            b.installPackages("u1", "", EnumSet.noneOf(PackageGroup.class), Collections.<String>emptyList());
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void applyEditRejectsInvalidSshSettings() {
        try {
            b.applyEdit("u1", "", true, true, 80, RootSshLogin.NO, false);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            b.applyEdit("u1", "", true, true, 2022, null, false);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        assertEquals("", b.applyEdit("u1", "", false, true, 80, null, false));
        assertEquals("", b.applyEdit("u1", "", true, false, 80, null, false));
    }

    @Test
    public void applyEditNothing() {
        assertEquals("", b.applyEdit("u1", "", false, true, 2022, RootSshLogin.NO, false));
        assertEquals("", b.applyEdit("u1", "", true, false, 2022, RootSshLogin.NO, false));
    }

    @Test
    public void applyEditSshAndHost() {
        assertEquals("proot-distro login u1 --hostname box1" + ENV
            + "(command -v sshd >/dev/null || (apt-get update && apt-get install -y openssh-server))"
            + " && ssh-keygen -A && mkdir -p /run/sshd"
            + " && printf \"Port 2200\\nPermitRootLogin prohibit-password\\n\" > /etc/ssh/sshd_config.d/termux.conf"
            + " && sed -i \"/^127.0.1.1 /d\" /etc/hosts && echo \"127.0.1.1 box1\" >> /etc/hosts'\n",
            b.applyEdit("u1", "box1", true, true, 2200, RootSshLogin.KEY_ONLY, true));
    }

    @Test
    public void applyEditClearsHost() {
        assertEquals("proot-distro login u1" + ENV + "sed -i \"/^127.0.1.1 /d\" /etc/hosts'\n",
            b.applyEdit("u1", "", false, false, 0, RootSshLogin.NO, true));
    }

    @Test
    public void displaySshCommandRootFallback() {
        assertEquals("ssh -p 2022 root@10.0.0.5", CommandBuilder.displaySshCommand("", 2022, "10.0.0.5"));
        assertEquals("ssh -p 2022 erkan@10.0.0.5", CommandBuilder.displaySshCommand("erkan", 2022, "10.0.0.5"));
    }

    @Test
    public void noSingleQuoteInsideInner() {
        List<String> all = new ArrayList<>();
        all.add(b.install(full(Distro.DEBIAN)));
        all.add(b.install(full(Distro.UBUNTU)));
        all.add(b.install(ub().build()));
        all.add(b.update("u1", "box1"));
        all.add(b.installPackages("u1", "box1", EnumSet.of(PackageGroup.DEV), Collections.<String>emptyList()));
        all.add(b.applyEdit("u1", "box1", true, true, 2200, RootSshLogin.PASSWORD, true));
        all.add(b.applyEdit("u1", "", false, false, 0, RootSshLogin.NO, true));
        for (String c : all) {
            int n = 0;
            for (int i = 0; i < c.length(); i++) if (c.charAt(i) == '\'') n++;
            assertEquals(c, 0, n % 2);
        }
    }
}
