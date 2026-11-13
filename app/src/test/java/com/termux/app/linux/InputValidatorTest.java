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

    @Test
    public void hostnameRules() {
        StringBuilder l63 = new StringBuilder();
        for (int i = 0; i < 63; i++) l63.append('a');
        assertTrue(InputValidator.isValidHostname("myserver"));
        assertTrue(InputValidator.isValidHostname("a"));
        assertTrue(InputValidator.isValidHostname("a-1"));
        assertTrue(InputValidator.isValidHostname(l63.toString()));
        assertFalse(InputValidator.isValidHostname("-a"));
        assertFalse(InputValidator.isValidHostname("a-"));
        assertFalse(InputValidator.isValidHostname("My"));
        assertFalse(InputValidator.isValidHostname("a.b"));
        assertFalse(InputValidator.isValidHostname(l63 + "a"));
        assertFalse(InputValidator.isValidHostname("a;b"));
        assertFalse(InputValidator.isValidHostname(null));
    }

    @Test
    public void parsePackagesWhitespace() {
        assertEquals(java.util.Arrays.asList("htop", "tmux", "htop"),
            InputValidator.parsePackages(" htop\ttmux\n\nhtop "));
        assertNull(InputValidator.parsePackages("x$(id)"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 31; i++) many.append("p").append(i).append(' ');
        assertNull(InputValidator.parsePackages(many.toString()));
        assertTrue(InputValidator.parsePackages("").isEmpty());
        assertTrue(InputValidator.parsePackages(null).isEmpty());
    }

    @Test
    public void labelWhitelist() {
        assertTrue(InputValidator.isSafeLabel("== \u015eifre: deploy_bot =="));
        assertFalse(InputValidator.isSafeLabel("a\"b"));
        assertFalse(InputValidator.isSafeLabel("$x"));
        assertFalse(InputValidator.isSafeLabel("a`b"));
        assertFalse(InputValidator.isSafeLabel("a'b"));
        assertFalse(InputValidator.isSafeLabel(""));
        assertTrue(InputValidator.isSafeLabel("ubuntu - Debian 13 (trixie) - SSH sunucusu - port 2022"));
        assertFalse(InputValidator.isSafeLabel("a;b"));
        assertFalse(InputValidator.isSafeLabel("a!b"));
        assertFalse(InputValidator.isSafeLabel("a\\b"));
    }

    @Test
    public void versionMustMatchDistro() {
        InstallOptions o = new InstallOptions.Builder().distro(Distro.DEBIAN)
            .version(DistroVersion.UBUNTU_24_04).alias("deb").build();
        assertTrue(InputValidator.validate(o).contains(InputValidator.Error.VERSION));
    }

    @Test
    public void builderDefaults() {
        InstallOptions o = new InstallOptions.Builder().distro(Distro.UBUNTU).alias("ubuntu").build();
        assertEquals(DistroVersion.UBUNTU_24_04, o.version);
        assertEquals(2022, o.sshPort);
        assertEquals("", o.timezone);
        assertTrue(o.groups.isEmpty());
        assertTrue(o.extraPackages.isEmpty());
        assertEquals(RootSshLogin.NO, o.rootSshLogin());
    }

    @Test
    public void newFieldsValidated() {
        InstallOptions o = new InstallOptions.Builder().distro(Distro.UBUNTU).alias("ubuntu")
            .hostname("Bad_Host").timezone("../x")
            .extraPackages(java.util.Arrays.asList("ok", "bad;x")).build();
        EnumSet<InputValidator.Error> e = InputValidator.validate(o);
        assertTrue(e.contains(InputValidator.Error.HOSTNAME));
        assertTrue(e.contains(InputValidator.Error.TIMEZONE));
        assertTrue(e.contains(InputValidator.Error.PACKAGES));
    }

    @Test
    public void validateEditInfo() {
        assertTrue(InputValidator.validate(new EditInfo(Distro.UBUNTU, DistroVersion.UBUNTU_24_04, "bob", "box1",
            true, 2022, false, RootSshLogin.NO)).isEmpty());
        EnumSet<InputValidator.Error> e = InputValidator.validate(new EditInfo(Distro.DEBIAN,
            DistroVersion.UBUNTU_24_04, "Bad;", "a;b", true, 80, false, null));
        assertEquals(EnumSet.of(InputValidator.Error.USER, InputValidator.Error.HOSTNAME,
            InputValidator.Error.PORT, InputValidator.Error.VERSION), e);
        assertTrue(InputValidator.validate(new EditInfo(null, null, "", "", false, 0, false, null)).isEmpty());
    }
}
