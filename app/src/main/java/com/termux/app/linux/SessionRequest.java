package com.termux.app.linux;

import java.util.ArrayList;
import java.util.List;

/** A request to open a named terminal session with input, or to type into an existing one. */
public final class SessionRequest {

    public static final String EXTRA_REQUESTS = "com.termux.linux.extra.REQUESTS";
    public static final String EXTRA_OPEN_SESSIONS = "com.termux.linux.extra.OPEN_SESSIONS";

    public enum Kind { OPEN, SEND }

    public final Kind kind;
    public final String sessionName;
    public final String input;

    public SessionRequest(Kind kind, String sessionName, String input) {
        if (kind == null) throw new IllegalArgumentException("kind is null");
        this.kind = kind;
        this.sessionName = sessionName == null ? "" : sessionName;
        this.input = input == null ? "" : input;
    }

    public static SessionRequest open(String sessionName, String input) {
        return new SessionRequest(Kind.OPEN, sessionName, input);
    }

    public static SessionRequest send(String sessionName, String input) {
        return new SessionRequest(Kind.SEND, sessionName, input);
    }

    /** Flattens to triples of kind, name, input. */
    public static String[] encode(List<SessionRequest> requests) {
        String[] out = new String[requests.size() * 3];
        int i = 0;
        for (SessionRequest r : requests) {
            out[i++] = r.kind.name();
            out[i++] = r.sessionName;
            out[i++] = r.input;
        }
        return out;
    }

    /** @throws IllegalArgumentException if the array is null, not a multiple of 3, or has an unknown kind. */
    public static List<SessionRequest> decode(String[] flat) {
        if (flat == null || flat.length % 3 != 0)
            throw new IllegalArgumentException("Malformed session requests");
        List<SessionRequest> out = new ArrayList<>();
        for (int i = 0; i < flat.length; i += 3) {
            Kind kind;
            try {
                kind = Kind.valueOf(flat[i]);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Unknown session request kind: " + flat[i]);
            }
            out.add(new SessionRequest(kind, flat[i + 1], flat[i + 2]));
        }
        return out;
    }
}
