package com.termux.app.linux;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The device's IPv4 addresses, for showing ready-to-copy SSH commands. */
public final class HostAddresses {

    private HostAddresses() {}

    /**
     * @param ifaceAndIpv4 [interface name, address] pairs
     * @return IPv4 addresses, "wlan*" interfaces first, then the others in input order; loopback,
     * non-IPv4 and duplicate addresses are dropped
     */
    public static List<String> ordered(List<String[]> ifaceAndIpv4) {
        Set<String> wlan = new LinkedHashSet<>();
        Set<String> other = new LinkedHashSet<>();
        if (ifaceAndIpv4 != null) {
            for (String[] p : ifaceAndIpv4) {
                if (p == null || p.length < 2 || p[0] == null || p[1] == null) continue;
                if (!isIpv4(p[1]) || p[1].startsWith("127.")) continue;
                (p[0].startsWith("wlan") ? wlan : other).add(p[1]);
            }
        }
        List<String> out = new ArrayList<>(wlan);
        for (String ip : other) {
            if (!wlan.contains(ip)) out.add(ip);
        }
        return out;
    }

    private static boolean isIpv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    /** [interface name, address] pairs of up, non-loopback interfaces; never throws, empty on failure. */
    public static List<String[]> fromSystem() {
        List<String[]> out = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return out;
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) out.add(new String[]{ni.getName(), a.getHostAddress()});
                }
            }
        } catch (java.net.SocketException | RuntimeException e) {
            return new ArrayList<>();
        }
        return out;
    }
}
