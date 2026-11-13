package com.termux.app.linux;

import static org.junit.Assert.assertEquals;

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
}
