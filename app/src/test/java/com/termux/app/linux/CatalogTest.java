package com.termux.app.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CatalogTest {

    @Test
    public void ubuntuVersionsInOrder() {
        List<String> ids = new ArrayList<>();
        for (DistroVersion v : DistroVersion.forDistro(Distro.UBUNTU)) ids.add(v.id());
        assertEquals(Arrays.asList("24.04", "26.04", "22.04"), ids);
    }

    @Test
    public void defaults() {
        assertEquals(DistroVersion.UBUNTU_24_04, DistroVersion.defaultFor(Distro.UBUNTU));
        assertEquals(DistroVersion.DEBIAN_13, DistroVersion.defaultFor(Distro.DEBIAN));
        assertTrue(DistroVersion.DEBIAN_13.isDefault());
        assertFalse(DistroVersion.DEBIAN_12.isDefault());
    }

    @Test
    public void imageRefs() {
        assertEquals("debian:12", DistroVersion.DEBIAN_12.imageRef());
        assertEquals("ubuntu:26.04", DistroVersion.UBUNTU_26_04.imageRef());
        assertEquals("Debian 13 (trixie)", DistroVersion.DEBIAN_13.label());
    }

    @Test
    public void fromIdUnknownIsNull() {
        assertNull(DistroVersion.fromId(Distro.UBUNTU, "latest"));
        assertNull(DistroVersion.fromId(Distro.UBUNTU, "13"));
        assertEquals(DistroVersion.DEBIAN_12, DistroVersion.fromId(Distro.DEBIAN, "12"));
    }

    @Test
    public void groupPackages() {
        assertEquals(Arrays.asList("python3", "python3-pip", "python3-venv"), PackageGroup.PYTHON.packages());
        assertEquals(Arrays.asList("build-essential", "git"), PackageGroup.DEV.packages());
        assertEquals(PackageGroup.NGINX, PackageGroup.fromName("NGINX"));
        assertNull(PackageGroup.fromName("nope"));
        assertNull(PackageGroup.fromName(null));
    }

    @Test
    public void timezonesDefaultFirst() {
        assertEquals("Europe/Istanbul", Timezones.all().get(0));
        assertEquals(Timezones.DEFAULT, Timezones.all().get(0));
        assertTrue(Timezones.isKnown("UTC"));
        assertFalse(Timezones.isKnown("../../etc"));
        assertFalse(Timezones.isKnown(null));
    }

    @Test
    public void rootSshLoginValues() {
        assertEquals("prohibit-password", RootSshLogin.KEY_ONLY.sshdValue());
        assertEquals("no", RootSshLogin.NO.sshdValue());
        assertEquals("yes", RootSshLogin.PASSWORD.sshdValue());
        assertEquals(RootSshLogin.KEY_ONLY, RootSshLogin.fromName("KEY_ONLY"));
        assertNull(RootSshLogin.fromName("x"));
    }
}
