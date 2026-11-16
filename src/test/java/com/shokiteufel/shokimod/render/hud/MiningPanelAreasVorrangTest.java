package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Mining-Kasten: Was im HUD-Editor unter "Areas" gewaehlt ist (Bild 1), hat Vorrang vor "Show where" im Einstellungsfenster (Bild 2)
 * (User 16.11.: "die Ansicht bei /shoki hud soll Vorrang haben anstelle von Screen 2").
 */
class MiningPanelAreasVorrangTest {

    private String mapVorher;

    @BeforeEach
    void frischeConfig() {
        ModConfig.INSTANCE = new ModConfig();
        mapVorher = GameState.Server.map;
    }

    @AfterEach
    void aufraeumen() {
        GameState.Server.map = mapVorher;
    }

    private static boolean zeigtHier() {
        return SafariHud.Panel.MINING.showsHere();
    }

    private static void areas(String... gewaehlt) {
        ModConfig.INSTANCE.hud.areasFor("MINING").addAll(java.util.List.of(gewaehlt));
    }

    @Test
    void withoutAChoiceTheSettingDecides() {
        GameState.Server.map = "Crimson Isle";
        ModConfig.INSTANCE.mining.hud.visibility = ModConfig.HudVisibility.MINING_ISLANDS;
        assertFalse(zeigtHier(), "Mining islands: auf der Crimson Isle liest die Tab-Liste keine Auftraege, der Kasten bleibt weg");

        ModConfig.INSTANCE.mining.hud.visibility = ModConfig.HudVisibility.EVERYWHERE;
        assertTrue(zeigtHier(), "Everywhere: ueberall");
    }

    @Test
    void aChosenAreaOverridesMiningIslands() {
        GameState.Server.map = "Crimson Isle";
        ModConfig.INSTANCE.mining.hud.visibility = ModConfig.HudVisibility.MINING_ISLANDS;
        areas("Crimson Isle");
        assertTrue(zeigtHier(), "die Wahl im HUD-Editor schlaegt Mining islands");
    }

    @Test
    void aChosenAreaOverridesEverywhere() {
        GameState.Server.map = "Crimson Isle";
        ModConfig.INSTANCE.mining.hud.visibility = ModConfig.HudVisibility.EVERYWHERE;
        areas("Dwarven Mines");
        assertFalse(zeigtHier(), "die Wahl im HUD-Editor schlaegt Everywhere: nur in den Dwarven Mines");

        GameState.Server.map = "Dwarven Mines";
        assertTrue(zeigtHier());
    }

    @Test
    void clearingTheChoiceGivesTheDecisionBackToTheSetting() {
        GameState.Server.map = "Crimson Isle";
        ModConfig.INSTANCE.mining.hud.visibility = ModConfig.HudVisibility.EVERYWHERE;
        areas("Dwarven Mines");
        assertFalse(zeigtHier());

        ModConfig.INSTANCE.hud.areasFor("MINING").clear();
        assertTrue(zeigtHier(), "Clear all im Waehler: wieder gilt Show where");
    }
}
