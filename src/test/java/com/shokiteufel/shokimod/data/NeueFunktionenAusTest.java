package com.shokiteufel.shokimod.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Vorgabe des Users (09.11.): Alles Neue und alle Log-Funktionen sind von Haus aus aus - bei einem schwaecheren Rechner hat es sonst geruckelt.
 * Dieser Test haelt das fest: Kommt ein neuer Schalter dazu, der von Haus aus an ist, faellt er hier auf.
 */
class NeueFunktionenAusTest {

    @BeforeEach
    void frischeConfig() {
        ModConfig.INSTANCE = new ModConfig();
    }

    @Test
    void everyLogSwitchIsOffFromTheStart() throws IllegalAccessException {
        ModConfig.LogsCategory logs = ModConfig.INSTANCE.logs;
        int gezaehlt = 0;
        for (Field f : ModConfig.LogsCategory.class.getFields()) {
            if (f.getType() != boolean.class || Modifier.isTransient(f.getModifiers()) || Modifier.isStatic(f.getModifiers())) continue;
            gezaehlt++;
            assertFalse(f.getBoolean(logs), "Log-Schalter muss von Haus aus aus sein: " + f.getName());
        }
        assertEquals(true, gezaehlt >= 6, "gefunden: " + gezaehlt + " Schalter (petLog, profitDiagnostics, mobGlowDiagnostics, deployableLog, perfLog, kernLog)");
    }

    @Test
    void theNewFunctionsAreOffFromTheStart() {
        ModConfig c = ModConfig.INSTANCE;
        assertFalse(c.profit.enabled, "Profit Tracker");
        assertFalse(c.profit.hideInDungeons, "Hide in Dungeons");
        assertFalse(c.skillXp.enabled, "Skill XP");
        assertFalse(c.mining.forge.enabled, "Forge");
        assertFalse(c.mining.forge.alert, "Forge-Alarm");
        assertFalse(c.ikohS.funnyMap.enabled, "Funny Map");
        assertFalse(c.ikohS.mist.showGhosts, "Show Ghosts");
        assertFalse(c.ikohS.mist.runicGhosts, "Runic Ghosts");
        assertEquals(ModConfig.MistFloor.WHITE, c.ikohS.mist.floor, "Boden der Mist: unveraendert");
        assertFalse(c.ikohS.starMobs.enabled, "Stern-Mobs");
        assertFalse(c.ikohS.starMobs.line, "Stern-Mobs Linie");
        assertFalse(c.ikohS.starMobs.box, "Stern-Mobs Kasten");
    }

    @Test
    void aFreshConfigNeedsNoneOfTheNewMachinery() {
        assertFalse(FeatureGate.itemChanges(), "der Paket-Kern rechnet nur bei Bedarf");
        assertFalse(FeatureGate.funnyMap(), "die Funny Map (und ihr Abgleich mit Noamm)");
        assertFalse(FeatureGate.forge(), "die Forge-Zeiten");
        assertFalse(FeatureGate.forgeAlert(), "der Forge-Alarm");
        assertFalse(FeatureGate.skillXp(), "der Skill-XP-Zaehler");
        assertFalse(StarMobTarget.enabled(), "die Stern-Mobs");
        assertFalse(RunicGhostTarget.enabled(), "die Runic Ghosts");
    }
}
