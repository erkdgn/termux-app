package com.termux.app.linux;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InstallStoreTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private LinuxPaths paths;
    private InstallStore store;

    @Before
    public void setUp() throws IOException {
        File base = tmp.newFolder("base");
        paths = new LinuxPaths(new File(base, "pd").getPath(), new File(base, "meta").getPath(),
            new File(base, "home").getPath());
        store = new InstallStore(paths);
    }

    private static InstallOptions opts(String alias, String rootKey, String userKey) {
        return new InstallOptions(Distro.UBUNTU, alias, "bob", true, true, true, 8022, true, false, rootKey, userKey);
    }

    private void mkRootfs(String alias) {
        assertTrue(new File(paths.rootfs(alias)).mkdirs());
    }

    private void mkDone(String alias) throws IOException {
        assertTrue(new File(paths.doneFile(alias)).createNewFile());
    }

    @Test
    public void saveThenScanReady() throws IOException {
        store.save(opts("u1", "", ""));
        mkRootfs("u1");
        mkDone("u1");
        List<InstallInfo> l = store.scan(Collections.<String>emptySet());
        assertEquals(1, l.size());
        InstallInfo i = l.get(0);
        assertEquals("u1", i.alias);
        assertEquals(InstallInfo.Status.READY, i.status);
        assertEquals(Distro.UBUNTU, i.distro);
        assertEquals("bob", i.user);
        assertTrue(i.ssh);
        assertEquals(8022, i.sshPort);
        assertTrue(i.sshAutoStart);
    }

    @Test
    public void incompleteWithoutDone() throws IOException {
        store.save(opts("u1", "", ""));
        mkRootfs("u1");
        List<InstallInfo> l = store.scan(Collections.<String>emptySet());
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INCOMPLETE, l.get(0).status);
    }

    @Test
    public void installingWhenSessionOpen() throws IOException {
        store.save(opts("u1", "", ""));
        List<InstallInfo> l = store.scan(Collections.singleton(SessionNames.install("u1")));
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INSTALLING, l.get(0).status);
    }

    @Test
    public void installingEvenWhenRootfsExists() throws IOException {
        store.save(opts("u1", "", ""));
        mkRootfs("u1");
        List<InstallInfo> l = store.scan(Collections.singleton(SessionNames.install("u1")));
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INSTALLING, l.get(0).status);
    }

    @Test
    public void plainAliasSessionIsNotInstalling() throws IOException {
        store.save(opts("u1", "", ""));
        mkRootfs("u1");
        List<InstallInfo> l = store.scan(Collections.singleton("u1"));
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INCOMPLETE, l.get(0).status);
    }

    @Test
    public void missingRootfsIsNotFoundAndKept() throws IOException {
        store.save(opts("u1", "k", "k2"));
        mkDone("u1");
        List<InstallInfo> l = store.scan(Collections.<String>emptySet());
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.NOT_FOUND, l.get(0).status);
        assertTrue(l.get(0).hasMetadata);
        assertTrue(new File(paths.metaFile("u1")).exists());
        assertTrue(new File(paths.doneFile("u1")).exists());
        assertTrue(new File(paths.rootKeyFile("u1")).exists());
        assertTrue(new File(paths.userKeyFile("u1")).exists());
    }

    @Test
    public void manualInstallListed() {
        mkRootfs("foo");
        List<InstallInfo> l = store.scan(null);
        assertEquals(1, l.size());
        InstallInfo i = l.get(0);
        assertEquals("foo", i.alias);
        assertNull(i.distro);
        assertEquals("", i.user);
        assertFalse(i.ssh);
        assertEquals(InstallInfo.Status.INCOMPLETE, i.status);
    }

    @Test
    public void missingRootfsDirIsEmpty() {
        assertTrue(store.scan(Collections.<String>emptySet()).isEmpty());
    }

    @Test
    public void keysStaged() throws IOException {
        store.save(opts("u1", "ssh-ed25519 AAA root", ""));
        File root = new File(paths.rootKeyFile("u1"));
        assertEquals("ssh-ed25519 AAA root\n",
            new String(Files.readAllBytes(root.toPath()), StandardCharsets.UTF_8));
        assertFalse(new File(paths.userKeyFile("u1")).exists());
    }

    @Test
    public void staleKeyRemovedOnResave() throws IOException {
        store.save(opts("u1", "k", ""));
        store.save(opts("u1", "", ""));
        assertFalse(new File(paths.rootKeyFile("u1")).exists());
    }

    @Test
    public void aliasTaken() throws IOException {
        assertFalse(store.isAliasTaken("a"));
        mkRootfs("a");
        assertTrue(store.isAliasTaken("a"));
        store.save(opts("b", "", ""));
        assertTrue(store.isAliasTaken("b"));
    }

    @Test
    public void sortedAndInvalidNamesSkipped() throws IOException {
        mkRootfs("zeta");
        mkRootfs("alpha");
        mkRootfs("bad name;x");
        List<InstallInfo> l = store.scan(null);
        assertEquals(2, l.size());
        assertEquals("alpha", l.get(0).alias);
        assertEquals("zeta", l.get(1).alias);
    }

    @Test
    public void unlistableRootfsKeepsMetadata() throws IOException {
        Assume.assumeFalse("root can list unreadable dirs", "root".equals(System.getProperty("user.name")));
        store.save(opts("u1", "k", ""));
        mkRootfs("u1");
        mkDone("u1");
        File rootfs = new File(paths.containersDir());
        assertTrue(rootfs.setReadable(false));
        try {
            assertNull(rootfs.listFiles());
            store.scan(Collections.<String>emptySet());
        } finally {
            rootfs.setReadable(true);
        }
        assertTrue(new File(paths.metaFile("u1")).exists());
        assertTrue(new File(paths.doneFile("u1")).exists());
        assertTrue(new File(paths.rootKeyFile("u1")).exists());
    }

    @Test
    public void containersLayoutIsReady() throws IOException {
        store.save(opts("u1", "", ""));
        assertTrue(new File(paths.containersDir(), "u1/rootfs").mkdirs());
        mkDone("u1");
        List<InstallInfo> list = store.scan(Collections.singleton(SessionNames.install("u1")));
        assertEquals(1, list.size());
        assertEquals(InstallInfo.Status.READY, list.get(0).status);
    }

    @Test
    public void legacyLayoutIsReady() throws IOException {
        store.save(opts("u1", "", ""));
        assertTrue(new File(paths.legacyRootfs("u1")).mkdirs());
        mkDone("u1");
        List<InstallInfo> list = store.scan(Collections.<String>emptySet());
        assertEquals(1, list.size());
        assertEquals(InstallInfo.Status.READY, list.get(0).status);
    }

    @Test
    public void manualContainerWithoutMetadataListed() {
        assertTrue(new File(paths.containersDir(), "deb/rootfs").mkdirs());
        List<InstallInfo> list = store.scan(Collections.<String>emptySet());
        assertEquals(1, list.size());
        assertEquals("deb", list.get(0).alias);
    }

    @Test
    public void aliasTakenInEitherLayout() {
        assertTrue(new File(paths.legacyRootfs("old")).mkdirs());
        assertTrue(new File(paths.containersDir(), "new1").mkdirs());
        assertTrue(store.isAliasTaken("old"));
        assertTrue(store.isAliasTaken("new1"));
        assertFalse(store.isAliasTaken("none"));
    }

    @Test
    public void saveClearsStaleDoneMarker() throws IOException {
        assertTrue(new File(paths.metaDir()).mkdirs());
        mkDone("u1");
        store.save(opts("u1", "", ""));
        assertFalse(new File(paths.doneFile("u1")).exists());
        mkRootfs("u1");
        assertEquals(InstallInfo.Status.INCOMPLETE, store.scan(null).get(0).status);
    }

    private void mkFailed(String alias) throws IOException {
        assertTrue(new File(paths.failedFile(alias)).createNewFile());
    }

    @Test
    public void failedWhileSessionOpenIsIncomplete() throws IOException {
        store.save(opts("u1", "", ""));
        mkRootfs("u1");
        mkFailed("u1");
        List<InstallInfo> l = store.scan(Collections.singleton(SessionNames.install("u1")));
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INCOMPLETE, l.get(0).status);
    }

    @Test
    public void failedWithoutRootfsAndSessionOpenIsListed() throws IOException {
        store.save(opts("u1", "", ""));
        mkFailed("u1");
        List<InstallInfo> l = store.scan(Collections.singleton(SessionNames.install("u1")));
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INCOMPLETE, l.get(0).status);
        assertTrue(new File(paths.metaFile("u1")).exists());
        assertTrue(new File(paths.failedFile("u1")).exists());
    }

    @Test
    public void failedWithoutSessionIsNotFoundFailed() throws IOException {
        store.save(opts("u1", "", ""));
        mkFailed("u1");
        List<InstallInfo> l = store.scan(Collections.<String>emptySet());
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.NOT_FOUND, l.get(0).status);
        assertTrue(l.get(0).failed);
        assertTrue(new File(paths.metaFile("u1")).exists());
    }

    private void writeProps(String alias, String content) throws IOException {
        assertTrue(new File(paths.metaDir()).isDirectory() || new File(paths.metaDir()).mkdirs());
        Files.write(new File(paths.metaFile(alias)).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void v1MetadataLoads() throws IOException {
        writeProps("u1", "distro=ubuntu\nuser=bob\nssh=true\nsshPort=2022\n");
        mkRootfs("u1");
        InstallInfo i = store.scan(null).get(0);
        assertEquals(Distro.UBUNTU, i.distro);
        assertNull(i.version);
        assertEquals("", i.hostname);
        assertEquals("", i.timezone);
        assertFalse(i.localeTr);
        assertEquals(RootSshLogin.KEY_ONLY, i.rootSshLogin);
        assertEquals("bob", i.user);
        assertTrue(i.hasMetadata);
    }

    @Test
    public void v2RoundTrip() throws IOException {
        InstallOptions o = new InstallOptions.Builder().distro(Distro.DEBIAN).version(DistroVersion.DEBIAN_12)
            .alias("d1").user("bob").timezone("UTC").localeTr(true).hostname("box1").rootPassword(true)
            .groups(EnumSet.of(PackageGroup.PYTHON, PackageGroup.NGINX))
            .extraPackages(Arrays.asList("htop")).build();
        store.save(o);
        mkRootfs("d1");
        InstallInfo i = store.scan(null).get(0);
        assertEquals(Distro.DEBIAN, i.distro);
        assertEquals(DistroVersion.DEBIAN_12, i.version);
        assertEquals("UTC", i.timezone);
        assertTrue(i.localeTr);
        assertEquals("box1", i.hostname);
        assertEquals(RootSshLogin.PASSWORD, i.rootSshLogin);
        assertEquals(EnumSet.of(PackageGroup.PYTHON, PackageGroup.NGINX), i.groups);
        assertEquals(Arrays.asList("htop"), i.extraPackages);
    }

    @Test
    public void tamperedMetadataSanitized() throws IOException {
        writeProps("u1", "distro=ubuntu\nhostname=a;rm -rf\ntimezone=../../etc\nuser=e$(id)\n"
            + "groups=PYTHON,EVIL\nextraPackages=htop x$(id)\nrootSshLogin=EVIL\nversion=99\nsshPort=99999\n");
        mkRootfs("u1");
        InstallInfo i = store.scan(null).get(0);
        assertEquals("", i.hostname);
        assertEquals("", i.timezone);
        assertEquals("", i.user);
        assertEquals(EnumSet.of(PackageGroup.PYTHON), i.groups);
        assertEquals(Arrays.asList("htop"), i.extraPackages);
        assertEquals(RootSshLogin.KEY_ONLY, i.rootSshLogin);
        assertNull(i.version);
        assertEquals(0, i.sshPort);
    }

    @Test
    public void saveEditRejectsInvalid() throws IOException {
        try {
            store.saveEdit("u1", new EditInfo(null, null, "e$(id)", "", false, 0, false, RootSshLogin.NO));
            fail();
        } catch (IllegalArgumentException expected) {
            assertFalse(new File(paths.metaFile("u1")).exists());
        }
    }

    @Test
    public void pkgFailedFlag() throws IOException {
        store.save(opts("u1", "", ""));
        mkRootfs("u1");
        assertFalse(store.scan(null).get(0).packagesFailed);
        assertTrue(new File(paths.pkgFailedFile("u1")).createNewFile());
        assertTrue(store.scan(null).get(0).packagesFailed);
    }

    @Test
    public void saveClearsStalePkgFailed() throws IOException {
        assertTrue(new File(paths.metaDir()).mkdirs());
        assertTrue(new File(paths.pkgFailedFile("u1")).createNewFile());
        store.save(opts("u1", "", ""));
        assertFalse(new File(paths.pkgFailedFile("u1")).exists());
    }

    @Test
    public void saveEditCreatesAndMarksReady() throws IOException {
        mkRootfs("m1");
        store.saveEdit("m1", new EditInfo(Distro.DEBIAN, DistroVersion.DEBIAN_13, "bob", "host1", true, 2200,
            true, RootSshLogin.NO));
        InstallInfo i = store.scan(null).get(0);
        assertEquals(InstallInfo.Status.READY, i.status);
        assertEquals("bob", i.user);
        assertEquals("host1", i.hostname);
        assertTrue(i.ssh);
        assertEquals(2200, i.sshPort);
        assertTrue(i.sshAutoStart);
        assertEquals(RootSshLogin.NO, i.rootSshLogin);
        assertEquals(DistroVersion.DEBIAN_13, i.version);
    }

    @Test
    public void saveEditKeepsUnownedKeysAndRemovesUnknownDistro() throws IOException {
        writeProps("u1", "distro=ubuntu\nversion=24.04\ntimezone=UTC\n");
        mkRootfs("u1");
        store.saveEdit("u1", new EditInfo(null, null, "", "", false, 0, false, RootSshLogin.KEY_ONLY));
        InstallInfo i = store.scan(null).get(0);
        assertEquals("UTC", i.timezone);
        assertNull(i.distro);
        assertNull(i.version);
    }

    @Test
    public void saveEditNullVersionKeepsDistro() throws IOException {
        writeProps("u1", "distro=ubuntu\nversion=24.04\n");
        mkRootfs("u1");
        store.saveEdit("u1", new EditInfo(Distro.UBUNTU, null, "", "", false, 0, false, RootSshLogin.KEY_ONLY));
        InstallInfo i = store.scan(null).get(0);
        assertEquals(Distro.UBUNTU, i.distro);
        assertNull(i.version);
    }

    @Test
    public void forgetRemovesAll() throws IOException {
        store.save(opts("u1", "k", "k2"));
        mkDone("u1");
        mkFailed("u1");
        assertTrue(new File(paths.pkgFailedFile("u1")).createNewFile());
        store.forget("u1");
        for (String f : new String[] {paths.metaFile("u1"), paths.doneFile("u1"), paths.failedFile("u1"),
            paths.pkgFailedFile("u1"), paths.rootKeyFile("u1"), paths.userKeyFile("u1")}) {
            assertFalse(f, new File(f).exists());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void forgetRejectsBadAlias() {
        store.forget("../x");
    }

    @Test
    public void saveClearsStaleFailedMarker() throws IOException {
        assertTrue(new File(paths.metaDir()).mkdirs());
        mkFailed("u1");
        store.save(opts("u1", "", ""));
        assertFalse(new File(paths.failedFile("u1")).exists());
        assertEquals(InstallInfo.Status.INSTALLING,
            store.scan(Collections.singleton(SessionNames.install("u1"))).get(0).status);
    }
}
