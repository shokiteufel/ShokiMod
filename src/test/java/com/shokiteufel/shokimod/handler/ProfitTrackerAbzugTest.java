package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Der Compactor verdichtet Rohware in den Saecken zu Enchanted: Das Stueck steht als Fund im Kasten, die Rohware geht ab (User 19.11.:
 * "jetzt steht nicht mal mehr Enchanted Coal im Tracker, dabei wird das normale immer in Enchanted umgewandelt").
 */
class ProfitTrackerAbzugTest {

    @BeforeEach
    void vorbereiten() {
        ModConfig.INSTANCE = new ModConfig();
        ModConfig.INSTANCE.profit.enabled = true;
    }

    private static void setze(String id, int menge, double coins) {
        ModConfig.INSTANCE.profit.counts.put(id, menge);
        ModConfig.INSTANCE.profit.dayCounts.put(id, menge);
        ModConfig.INSTANCE.profit.totalCounts.put(id, menge);
        ModConfig.INSTANCE.profit.dayCoins.put(id, coins);
    }

    @Test
    void theRawItemsShrinkInAllThreePeriods() {
        setze("COAL", 1000, 7700.0);
        ProfitTracker.deduct(Map.of("COAL", 400));
        assertEquals(600, ModConfig.INSTANCE.profit.counts.get("COAL"));
        assertEquals(600, ModConfig.INSTANCE.profit.dayCounts.get("COAL"));
        assertEquals(600, ModConfig.INSTANCE.profit.totalCounts.get("COAL"));
        // Der Wert des Tages schrumpft anteilig, zum Preis von damals
        assertEquals(7700.0 * 600 / 1000, ModConfig.INSTANCE.profit.dayCoins.get("COAL"), 0.0001);
    }

    @Test
    void nothingGoesBelowZero() {
        // Die Rohware kann aus der Zeit vor dem letzten Reset stammen: dann verschwindet die Zeile, statt ins Minus zu laufen
        setze("COAL", 300, 2300.0);
        ProfitTracker.deduct(Map.of("COAL", 5000));
        assertNull(ModConfig.INSTANCE.profit.counts.get("COAL"));
        assertNull(ModConfig.INSTANCE.profit.dayCounts.get("COAL"));
        assertNull(ModConfig.INSTANCE.profit.totalCounts.get("COAL"));
        assertNull(ModConfig.INSTANCE.profit.dayCoins.get("COAL"));
    }

    @Test
    void anItemThatWasNeverCountedStaysAbsent() {
        ProfitTracker.deduct(Map.of("REDSTONE", 292_000));
        assertFalse(ModConfig.INSTANCE.profit.counts.containsKey("REDSTONE"));
        assertFalse(ModConfig.INSTANCE.profit.totalCounts.containsKey("REDSTONE"));
    }

    @Test
    void otherItemsAreUntouched() {
        setze("COAL", 1000, 7700.0);
        setze("ENCHANTED_COAL", 40, 57_600.0);
        ProfitTracker.deduct(Map.of("COAL", 400));
        assertEquals(40, ModConfig.INSTANCE.profit.counts.get("ENCHANTED_COAL"));
    }

    @Test
    void doesNothingWhileTheTrackerIsOff() {
        ModConfig.INSTANCE.profit.enabled = false;
        setze("COAL", 1000, 7700.0);
        ProfitTracker.deduct(Map.of("COAL", 400));
        assertEquals(1000, ModConfig.INSTANCE.profit.counts.get("COAL"));
    }

    @Test
    void emptyAndOddInputsDoNothing() {
        setze("COAL", 1000, 7700.0);
        ProfitTracker.deduct(null);
        ProfitTracker.deduct(Map.of());
        ProfitTracker.deduct(Map.of("COAL", 0));
        ProfitTracker.deduct(Map.of("COAL", -5));
        assertEquals(1000, ModConfig.INSTANCE.profit.counts.get("COAL"));
    }
}
