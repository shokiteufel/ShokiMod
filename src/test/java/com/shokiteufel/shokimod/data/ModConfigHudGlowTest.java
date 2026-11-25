package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (25.11.): Das Leuchten der HUD-Schrift ist von Haus aus aus (es nahm bei grossen Panels die Haelfte der Spielzeit).
 * Alte Dateien tragen die frueher mitgeschriebene Vorgabe "an" und werden einmal auf aus gestellt.
 */
class ModConfigHudGlowTest {

    @Test
    void theGlowIsOffFromTheStart() {
        assertFalse(new ModConfig().hud.glowingHudText);
        assertFalse(new ModConfig().hud.glowDefaultMoved);
    }

    @Test
    void anOldFileThatStillHoldsTheOldDefaultIsSwitchedOffOnce() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"hud\":{\"glowingHudText\":true}}", ModConfig.class);
        assertTrue(loaded.hud.glowingHudText, "so stand es in der Datei");

        assertTrue(ModConfig.migrateHudGlow(loaded.hud));

        assertFalse(loaded.hud.glowingHudText);
        assertTrue(loaded.hud.glowDefaultMoved);
        assertFalse(ModConfig.migrateHudGlow(loaded.hud), "ein zweiter Lauf aendert nichts mehr");
    }

    @Test
    void whoSwitchesItOnAgainAfterTheMoveKeepsIt() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"hud\":{\"glowingHudText\":true}}", ModConfig.class);
        ModConfig.migrateHudGlow(loaded.hud);
        loaded.hud.glowingHudText = true;   // bewusst wieder eingeschaltet

        assertFalse(ModConfig.migrateHudGlow(loaded.hud));
        assertTrue(loaded.hud.glowingHudText, "das Merkzeichen verhindert ein zweites Ausschalten");
    }

    @Test
    void theMarkSurvivesSavingAndLoading() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"hud\":{\"glowingHudText\":true}}", ModConfig.class);
        ModConfig.migrateHudGlow(loaded.hud);
        loaded.hud.glowingHudText = true;

        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(loaded), ModConfig.class);
        assertTrue(again.hud.glowDefaultMoved);
        assertTrue(again.hud.glowingHudText);
        assertFalse(ModConfig.migrateHudGlow(again.hud));
    }

    @Test
    void anOldFileWithTheGlowAlreadyOffStaysOffAndGetsTheMark() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"hud\":{\"glowingHudText\":false}}", ModConfig.class);
        assertTrue(ModConfig.migrateHudGlow(loaded.hud), "das Merkzeichen wird gesetzt, also ist zu speichern");
        assertFalse(loaded.hud.glowingHudText);
    }

    @Test
    void aMissingHudCategoryIsLeftAlone() {
        assertFalse(ModConfig.migrateHudGlow(null));
    }
}
