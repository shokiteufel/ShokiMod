package com.shokiteufel.shokimod.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Toene der Mod sind gekennzeichnet (28.11.2026): "die Sounds, die von ShokiMod kommen, sollen weiterhin abgespielt werden" - der Alarm aus dem Biss-Ton und die
 * Lautstaerke-Einstellung muessen sie von denen des Servers unterscheiden koennen.
 */
class ModSoundTest {

    @Test
    void outsideOfAModSoundNothingIsMarked() {
        assertFalse(ModSound.active());
    }

    @Test
    void whileAModSoundPlaysTheMarkIsSet() {
        boolean[] seen = new boolean[1];
        ModSound.run(() -> seen[0] = ModSound.active());
        assertTrue(seen[0]);
        assertFalse(ModSound.active(), "danach wieder frei");
    }

    @Test
    void nestedCallsKeepTheMarkUntilTheOutermostEnds() {
        boolean[] afterInner = new boolean[1];
        ModSound.run(() -> {
            ModSound.run(() -> {
            });
            afterInner[0] = ModSound.active();
        });
        assertTrue(afterInner[0], "der aeussere Ton klingt noch");
        assertFalse(ModSound.active());
    }

    @Test
    void theMarkFallsEvenIfPlayingFails() {
        assertThrows(IllegalStateException.class, () -> ModSound.run(() -> {
            throw new IllegalStateException("kaputt");
        }));
        assertFalse(ModSound.active(), "sonst waeren alle spaeteren Toene des Servers als eigene gekennzeichnet");
    }

    @Test
    void theMarkBelongsToOneThread() throws InterruptedException {
        boolean[] other = new boolean[] {true};
        ModSound.run(() -> {
            Thread thread = new Thread(() -> other[0] = ModSound.active());
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertEquals(false, other[0], "ein anderer Faden sieht die Marke nicht");
    }
}
