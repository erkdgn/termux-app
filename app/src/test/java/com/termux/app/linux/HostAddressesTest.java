package com.termux.app.linux;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class HostAddressesTest {

    private static List<String[]> pairs(String... flat) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i < flat.length; i += 2) l.add(new String[]{flat[i], flat[i + 1]});
        return l;
    }

    @Test
    public void wlanFirst() {
        assertEquals(Arrays.asList("192.168.1.5", "10.0.0.2"),
            HostAddresses.ordered(pairs("rmnet0", "10.0.0.2", "wlan0", "192.168.1.5")));
    }

    @Test
    public void ipv6AndLoopbackIgnored() {
        assertEquals(Collections.emptyList(),
            HostAddresses.ordered(pairs("lo", "127.0.0.1", "wlan0", "fe80::1")));
    }

    @Test
    public void emptyWhenNone() {
        assertEquals(Collections.emptyList(), HostAddresses.ordered(new ArrayList<String[]>()));
        assertEquals(Collections.emptyList(), HostAddresses.ordered(null));
    }

    @Test
    public void duplicatesDropped() {
        assertEquals(Arrays.asList("192.168.1.5"),
            HostAddresses.ordered(pairs("wlan0", "192.168.1.5", "wlan1", "192.168.1.5")));
    }

    @Test
    public void kindOfInterfaces() {
        assertEquals(HostAddresses.Kind.WIFI, HostAddresses.kindOf("wlan0"));
        assertEquals(HostAddresses.Kind.HOTSPOT, HostAddresses.kindOf("swlan0"));
        assertEquals(HostAddresses.Kind.HOTSPOT, HostAddresses.kindOf("ap0"));
        assertEquals(HostAddresses.Kind.HOTSPOT, HostAddresses.kindOf("softap0"));
        assertEquals(HostAddresses.Kind.HOTSPOT, HostAddresses.kindOf("wlan1"));
        assertEquals(HostAddresses.Kind.MOBILE, HostAddresses.kindOf("rmnet_data0"));
        assertEquals(HostAddresses.Kind.MOBILE, HostAddresses.kindOf("ccmni1"));
        assertEquals(HostAddresses.Kind.MOBILE, HostAddresses.kindOf("v4-rmnet_data0"));
        assertEquals(HostAddresses.Kind.USB, HostAddresses.kindOf("rndis0"));
        assertEquals(HostAddresses.Kind.ETHERNET, HostAddresses.kindOf("eth0"));
        assertEquals(HostAddresses.Kind.OTHER, HostAddresses.kindOf("tun0"));
        assertEquals(HostAddresses.Kind.OTHER, HostAddresses.kindOf(null));
    }

    @Test
    public void classifiedOrderMobileLast() {
        List<HostAddresses.Address> l = HostAddresses.classified(pairs(
            "rmnet_data0", "10.75.159.214", "swlan0", "192.168.43.1", "wlan0", "192.168.55.103", "tun0", "10.8.0.2"));
        assertEquals(4, l.size());
        assertEquals("192.168.55.103", l.get(0).ip);
        assertEquals(HostAddresses.Kind.WIFI, l.get(0).kind);
        assertEquals("wlan0", l.get(0).iface);
        assertEquals("192.168.43.1", l.get(1).ip);
        assertEquals(HostAddresses.Kind.HOTSPOT, l.get(1).kind);
        assertEquals("10.8.0.2", l.get(2).ip);
        assertEquals("10.75.159.214", l.get(3).ip);
        assertEquals(HostAddresses.Kind.MOBILE, l.get(3).kind);
    }

    @Test
    public void classifiedDropsLoopbackIpv6AndDuplicates() {
        List<HostAddresses.Address> l = HostAddresses.classified(pairs(
            "lo", "127.0.0.1", "wlan0", "fe80::1", "wlan0", "192.168.1.5", "eth0", "192.168.1.5"));
        assertEquals(1, l.size());
        assertEquals(HostAddresses.Kind.WIFI, l.get(0).kind);
    }
}
