package com.termux.app.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;

public class InputValidatorTest {

    private static InstallOptions opts(boolean sudo, String user, boolean ssh, boolean rootPassword,
                                       String rootKey, String userKey) {
        return new InstallOptions(Distro.UBUNTU, "ubuntu", user, sudo, false, ssh, 2022, false,
            rootPassword, rootKey, userKey);
    }

    @Test
    public void aliasRules() {
        StringBuilder max = new StringBuilder("a");
        for (int i = 0; i < 31; i++) max.append('b');
        StringBuilder tooLong = new StringBuilder(max).append('b');
        assertTrue(InputValidator.isValidAlias("ubuntu-web"));
        assertTrue(InputValidator.isValidAlias("a"));
        assertTrue(InputValidator.isValidAlias(max.toString()));
        assertFalse(InputValidator.isValidAlias(""));
        assertFalse(InputValidator.isValidAlias("Ubuntu"));
        assertFalse(InputValidator.isValidAlias("1ubuntu"));
        assertFalse(InputValidator.isValidAlias("ubu ntu"));
        assertFalse(InputValidator.isValidAlias("ubu;rm"));
        assertFalse(InputValidator.isValidAlias(tooLong.toString()));
        assertFalse(InputValidator.isValidAlias(null));
        assertFalse(InputValidator.isValidAlias("ubuntu\n"));
    }

    @Test
    public void userRules() {
        assertTrue(InputValidator.isValidUser("erkan"));
        assertTrue(InputValidator.isValidUser("_svc"));
        assertTrue(InputValidator.isValidUser("a-b_c"));
        assertFalse(InputValidator.isValidUser("root"));
        assertFalse(InputValidator.isValidUser("Erkan"));
        assertFalse(InputValidator.isValidUser("e$(id)"));
        assertFalse(InputValidator.isValidUser(""));
        assertFalse(InputValidator.isValidUser(null));
    }

    @Test
    public void portRules() {
        assertTrue(InputValidator.isValidPort(1024));
        assertTrue(InputValidator.isValidPort(65535));
        assertFalse(InputValidator.isValidPort(1023));
        assertFalse(InputValidator.isValidPort(65536));
        assertFalse(InputValidator.isValidPort(0));
    }

    @Test
    public void keyNormalization() {
        assertEquals("ssh-ed25519 AAAAC3Nza erkan@phone",
            InputValidator.normalizeKey("ssh-ed25519 AAAAC3Nza erkan@phone\r\n"));
        assertEquals("ssh-rsa AAAA==", InputValidator.normalizeKey("  ssh-rsa AAAA==  "));
        assertEquals("", InputValidator.normalizeKey(""));
        assertEquals("", InputValidator.normalizeKey(null));
        assertNull(InputValidator.normalizeKey("ssh-ed25519 AAAA\nssh-rsa BBBB"));
        assertNull(InputValidator.normalizeKey("ssh-ed25519 AAAA; rm -rf /"));
        assertNull(InputValidator.normalizeKey("ssh-ed25519 $(id)"));
        assertNull(InputValidator.normalizeKey("ssh-dss AAAA"));
    }

    @Test
    public void sshNeedsAccess() {
        EnumSet<InputValidator.Error> e = InputValidator.validate(opts(false, "", true, false, "", ""));
        assertTrue(e.contains(InputValidator.Error.SSH_NO_ACCESS));
        assertTrue(InputValidator.validate(opts(false, "", true, true, "", "")).isEmpty());
    }

    @Test
    public void sudoNeedsUser() {
        EnumSet<InputValidator.Error> e = InputValidator.validate(opts(true, "", false, false, "", ""));
        assertTrue(e.contains(InputValidator.Error.SUDO_WITHOUT_USER));
    }

    @Test
    public void otherErrors() {
        InstallOptions bad = new InstallOptions(Distro.DEBIAN, "Bad", "root", false, false, true, 80,
            false, false, "ssh-dss AAAA", "ssh-ed25519 AAAA\n");
        EnumSet<InputValidator.Error> e = InputValidator.validate(bad);
        assertTrue(e.contains(InputValidator.Error.ALIAS));
        assertTrue(e.contains(InputValidator.Error.USER));
        assertTrue(e.contains(InputValidator.Error.PORT));
        assertTrue(e.contains(InputValidator.Error.ROOT_KEY));
        assertTrue(e.contains(InputValidator.Error.USER_KEY));
        EnumSet<InputValidator.Error> f = InputValidator.validate(opts(false, "", false, false, "", "ssh-rsa AAAA"));
        assertTrue(f.contains(InputValidator.Error.USER_KEY_WITHOUT_USER));
        // Port is ignored when ssh is off.
        InstallOptions noSsh = new InstallOptions(Distro.UBUNTU, "u", "bob", false, false, false, 1, false,
            false, "", "");
        assertTrue(InputValidator.validate(noSsh).isEmpty());
        assertEquals(Distro.UBUNTU, Distro.fromId("ubuntu"));
        assertNull(Distro.fromId("arch"));
    }
}
