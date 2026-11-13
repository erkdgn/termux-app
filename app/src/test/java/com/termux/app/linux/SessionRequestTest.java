package com.termux.app.linux;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SessionRequestTest {

    @Test
    public void roundTripKeepsOrderAndExactStrings() {
        List<SessionRequest> in = Arrays.asList(
            SessionRequest.open("u1-ssh", "exec sshd -D\n"),
            SessionRequest.open("u1", "proot-distro login u1\n"),
            SessionRequest.send("u1-ssh", "\u0003"));
        String[] flat = SessionRequest.encode(in);
        assertEquals(9, flat.length);
        List<SessionRequest> out = SessionRequest.decode(flat);
        assertEquals(3, out.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(in.get(i).kind, out.get(i).kind);
            assertEquals(in.get(i).sessionName, out.get(i).sessionName);
            assertEquals(in.get(i).input, out.get(i).input);
        }
        assertEquals(SessionRequest.Kind.SEND, out.get(2).kind);
        assertEquals("\u0003", out.get(2).input);
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeRejectsLengthNotDivisibleByThree() {
        SessionRequest.decode(new String[]{"OPEN", "a"});
    }

    @Test(expected = IllegalArgumentException.class)
    public void decodeRejectsUnknownKind() {
        SessionRequest.decode(new String[]{"BOGUS", "a", "b"});
    }
}
