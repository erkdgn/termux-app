package com.termux.app.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandBuilderTest {

    private final CommandBuilder b = new CommandBuilder(new LinuxPaths("/R", "/M", "/H"));

    @Test
    public void minimalInstall() {
        InstallOptions o = new InstallOptions(Distro.UBUNTU, "u1", "", false, false, false, 2022,
            false, false, "", "");
        assertEquals("if pkg update -y && pkg install -y proot-distro && proot-distro install --override-alias u1 ubuntu"
            + " && proot-distro login u1 -- env DEBIAN_FRONTEND=noninteractive bash -c 'apt-get update"
            + " && apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold upgrade'"
            + "; then touch /M/u1.done; else touch /M/u1.failed; fi\n", b.install(o));
    }

    @Test
    public void fullInstall() {
        InstallOptions o = new InstallOptions(Distro.DEBIAN, "d1", "erkan", true, true, true, 2022,
            false, true, "ssh-ed25519 AAAAKEY x@y", "ssh-ed25519 AAAAUSER x@y");
        String c = b.install(o);
        assertTrue(c, c.contains("apt-get install -y sudo nano wget curl git net-tools openssh-server"));
        assertTrue(c, c.contains("PermitRootLogin yes"));
        assertTrue(c, c.contains("Port 2022"));
        assertTrue(c, c.contains("useradd -m -s /bin/bash -G sudo erkan"));
        assertTrue(c, c.contains(" && proot-distro login d1 -- sh -c \"mkdir -p /root/.ssh && chmod 700 /root/.ssh && cat > /root/.ssh/authorized_keys && chmod 600 /root/.ssh/authorized_keys\" < /M/d1.root.pub"));
        assertTrue(c, c.contains(" && proot-distro login d1 -- sh -c \"mkdir -p /home/erkan/.ssh && chmod 700 /home/erkan/.ssh && cat > /home/erkan/.ssh/authorized_keys && chmod 600 /home/erkan/.ssh/authorized_keys && chown -R erkan:erkan /home/erkan/.ssh\" < /M/d1.user.pub"));
        assertFalse(c, c.contains("install -D"));
        assertTrue(c, c.startsWith("if pkg update -y && "));
        assertTrue(c, c.endsWith("; then touch /M/d1.done && proot-distro login d1 -- passwd erkan"
            + " && proot-distro login d1 -- passwd root; else touch /M/d1.failed; fi\n"));
    }

    @Test
    public void rootKeyOnlyMode() {
        InstallOptions o = new InstallOptions(Distro.UBUNTU, "u1", "", false, false, true, 2022,
            false, false, "ssh-ed25519 AAAAKEY x@y", "");
        assertTrue(b.install(o).contains("PermitRootLogin prohibit-password"));
    }

    @Test
    public void noSudoGroup() {
        InstallOptions o = new InstallOptions(Distro.UBUNTU, "u1", "erkan", false, false, false, 2022,
            false, false, "", "");
        String c = b.install(o);
        assertTrue(c, c.contains("useradd -m -s /bin/bash erkan'"));
        assertFalse(c, c.contains("-G sudo"));
    }

    @Test
    public void keyTextNeverInCommand() {
        InstallOptions o = new InstallOptions(Distro.UBUNTU, "u1", "", false, false, true, 2022,
            false, false, "ssh-ed25519 AAAAKEY x@y", "");
        assertFalse(b.install(o).contains("AAAAKEY"));
    }

    @Test
    public void backupFallsBackToHome() {
        assertEquals("proot-distro backup --output /H/u1-20261113-120000.tar.gz u1\n",
            b.backup("u1", false, "20261113-120000"));
        assertEquals("proot-distro backup --output /H/storage/downloads/u1-20261113-120000.tar.gz u1\n",
            b.backup("u1", true, "20261113-120000"));
    }

    @Test
    public void removeCleansFailedMarker() {
        assertEquals("proot-distro remove u1 && rm -f /M/u1.properties /M/u1.done /M/u1.failed /M/u1.root.pub /M/u1.user.pub\n",
            b.remove("u1"));
    }

    @Test
    public void loginCommands() {
        assertEquals("proot-distro login u1\n", b.login("u1"));
        assertEquals("proot-distro login u1 --user erkan\n", b.loginUser("u1", "erkan"));
    }

    @Test
    public void sshStartUsesExec() {
        assertEquals("exec proot-distro login u1 -- /usr/sbin/sshd -D -e\n", b.sshStart("u1"));
    }

    @Test
    public void setPassword() {
        assertEquals("proot-distro login u1 -- passwd root\n", b.setPassword("u1", "root"));
    }
}
