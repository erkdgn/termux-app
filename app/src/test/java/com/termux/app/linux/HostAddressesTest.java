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
}
