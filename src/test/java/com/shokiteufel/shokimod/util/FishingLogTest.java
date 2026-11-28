package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Fishing log schreibt nur, wenn sich etwas geaendert hat - und von Haus aus gar nicht.
 */
class FishingLogTest {

    @BeforeEach
    void freshConfig() {
        ModConfig.INSTANCE = new ModConfig();
        FishingLog.forget();
    }

    @Test
    void theFirstCallOfASlotCountsAsAChange() {
        Map<String, String> last = new HashMap<>();
        assertTrue(FishingLog.changed(last, "state", "a"));
    }

    @Test
    void theSameSignatureIsNotWrittenTwice() {
        Map<String, String> last = new HashMap<>();
        assertTrue(FishingLog.changed(last, "state", "a"));
        assertFalse(FishingLog.changed(last, "state", "a"));
        assertTrue(FishingLog.changed(last, "state", "b"));
        assertTrue(FishingLog.changed(last, "state", "a"), "zurueck zu a ist wieder eine Aenderung");
    }

    @Test
    void slotsAreIndependent() {
        Map<String, String> last = new HashMap<>();
        assertTrue(FishingLog.changed(last, "state", "a"));
        assertTrue(FishingLog.changed(last, "scene", "a"));
        assertFalse(FishingLog.changed(last, "state", "a"));
    }

    @Test
    void itIsOffFromTheStartAndWritesNothingThen() {
        assertFalse(FishingLog.enabled());
        assertFalse(FishingLog.lineIfChanged("state", "a", "STATE", "never written"));
    }
}
