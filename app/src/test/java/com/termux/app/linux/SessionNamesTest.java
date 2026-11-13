package com.termux.app.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class SessionNamesTest {

    @Test
    public void freeBaseIsUsed() {
        assertEquals("u1", SessionNames.firstFree("u1", Collections.<String>emptySet()));
        assertEquals("u1", SessionNames.firstFree("u1", new HashSet<>(Arrays.asList("u2", "u1-ssh"))));
    }

    @Test
    public void takenBaseGetsNumberedSuffix() {
        assertEquals("u1 (2)", SessionNames.firstFree("u1", new HashSet<>(Arrays.asList("u1"))));
    }

    @Test
    public void skipsTakenNumbers() {
        assertEquals("u1 (4)", SessionNames.firstFree("u1",
            new HashSet<>(Arrays.asList("u1", "u1 (2)", "u1 (3)"))));
        assertEquals("u1 (2)", SessionNames.firstFree("u1", new HashSet<>(Arrays.asList("u1", "u1 (3)"))));
    }

    @Test
    public void nullTakenSetIsEmpty() {
        assertEquals("u1", SessionNames.firstFree("u1", null));
    }

    @Test
    public void reservedNames() {
        assertEquals("foo (install)", SessionNames.install("foo"));
        assertEquals("foo (ssh)", SessionNames.ssh("foo"));
    }

    @Test
    public void belongsToMatchesOnlyOwnSessions() {
        assertTrue(SessionNames.belongsTo("foo", "foo"));
        assertTrue(SessionNames.belongsTo("foo (2)", "foo"));
        assertTrue(SessionNames.belongsTo("foo (ssh)", "foo"));
        assertTrue(SessionNames.belongsTo("foo (install)", "foo"));
        assertFalse(SessionNames.belongsTo("foo-ssh", "foo"));
        assertFalse(SessionNames.belongsTo("foobar", "foo"));
        assertFalse(SessionNames.belongsTo("foo-ssh (ssh)", "foo"));
    }
}
