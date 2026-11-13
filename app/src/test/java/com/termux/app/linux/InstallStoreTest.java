package com.termux.app.linux;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
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
        paths = new LinuxPaths(new File(base, "rootfs").getPath(), new File(base, "meta").getPath(),
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
        List<InstallInfo> l = store.scan(Collections.singleton("u1"));
        assertEquals(1, l.size());
        assertEquals(InstallInfo.Status.INSTALLING, l.get(0).status);
    }

    @Test
    public void orphanMetadataRemoved() throws IOException {
        store.save(opts("u1", "k", "k2"));
        mkDone("u1");
        assertTrue(store.scan(Collections.<String>emptySet()).isEmpty());
        assertFalse(new File(paths.metaFile("u1")).exists());
        assertFalse(new File(paths.doneFile("u1")).exists());
        assertFalse(new File(paths.rootKeyFile("u1")).exists());
        assertFalse(new File(paths.userKeyFile("u1")).exists());
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
        File rootfs = new File(paths.rootfsDir());
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
    public void saveClearsStaleDoneMarker() throws IOException {
        assertTrue(new File(paths.metaDir()).mkdirs());
        mkDone("u1");
        store.save(opts("u1", "", ""));
        assertFalse(new File(paths.doneFile("u1")).exists());
        mkRootfs("u1");
        assertEquals(InstallInfo.Status.INCOMPLETE, store.scan(null).get(0).status);
    }
}
