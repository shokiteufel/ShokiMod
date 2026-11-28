package com.shokiteufel.shokimod.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Der eigene Angelhaken (28.11.2026, "letzter Hook wurde nicht erkannt"): Nennt {@code player.fishing} ihn nicht, wird er in der Welt gesucht. Hier die Wahl unter
 * mehreren - der alte, noch nicht entfernte Haken und der neue.
 */
class OwnHookTest {

    @Test
    void theNewestHookWinsWhenTheOldOneIsNotRemovedYet() {
        // Im Log: der alte 287924 und der neue 288094 gleichzeitig
        assertEquals("neu", OwnHook.newest(List.of("alt:287924", "neu:288094"), s -> Integer.parseInt(s.substring(s.indexOf(':') + 1))).substring(0, 3));
        assertEquals(288094, (int) OwnHook.newest(List.of(287924, 288094), Integer::intValue));
        assertEquals(288094, (int) OwnHook.newest(List.of(288094, 287924), Integer::intValue), "die Reihenfolge der Liste spielt keine Rolle");
    }

    @Test
    void oneHookIsTheHook() {
        assertEquals(286883, (int) OwnHook.newest(List.of(286883), Integer::intValue));
    }

    @Test
    void noHookNoResult() {
        assertNull(OwnHook.newest(List.<Integer>of(), Integer::intValue));
    }
}
