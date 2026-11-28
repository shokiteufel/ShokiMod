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
        assertFalse(c.ikohS.dungeon.funnyMap.enabled, "Funny Map");
        assertFalse(c.ikohS.mist.showGhosts, "Show Ghosts");
        assertFalse(c.ikohS.mist.runicGhosts, "Runic Ghosts");
        assertEquals(ModConfig.MistFloor.WHITE, c.ikohS.mist.floor, "Boden der Mist: unveraendert");
        assertFalse(c.ikohS.dungeon.starMobs.enabled, "Stern-Mobs");
        assertFalse(c.ikohS.dungeon.starMobs.line, "Stern-Mobs Linie");
        assertFalse(c.ikohS.dungeon.starMobs.box, "Stern-Mobs Kasten");
        assertFalse(c.ikohS.dungeon.starMobs.onlyCurrentRoom, "Stern-Mobs nur im eigenen Raum");
        assertFalse(c.ikohS.dungeon.iHateDoors.enabled, "I hate doors");
        assertFalse(FeatureGate.iHateDoors(), "I hate doors (und die Dungeon-Abfrage dafuer)");
        assertFalse(FeatureGate.roomScan(), "der Welt-Scan der Funny Map fuer die Raum-Regel");
        assertFalse(c.mining.mineshaft.keysFromSack, "Keys from sacks");
        assertFalse(FeatureGate.mineshaftKeys(), "die Schluessel-Zeile (und das Lesen der Leichen dafuer)");
        assertFalse(c.fishing.frozenBlaze.auraTimer, "Frozen Blaze: Aura timer");
        assertFalse(c.fishing.hookDisplay.enabled, "Fishing: Hook display");
        assertFalse(c.fishing.hookDisplay.soundAlert, "Fishing: Alert from the bite sound");
        assertFalse(c.fishing.hookDisplay.quietOthers, "Fishing: Quiet other sounds");
        assertFalse(FeatureGate.hookSounds(), "die Anfrage bei jedem Ton fuer die Lautstaerke beim Angeln");
        assertFalse(c.fishing.baitDisplay.enabled, "Fishing: Bait display");
        assertFalse(c.fishing.cocoon.warn, "Fishing: Cocoon title");
        assertFalse(c.fishing.cocoon.share, "Fishing: Cocoon share");
        assertFalse(c.misc.legionBobbin.enabled, "Misc: Legion/Bobbin Overlay");
        assertFalse(FeatureGate.legionBobbin(), "das Zaehlen von Spielern und Haken fuer das Overlay");
        assertFalse(c.mining.reminder.drillRefuel, "Drill fuel");
        assertFalse(FeatureGate.drillRefuel(), "die Zeile zum Auftanken des Drills");
    }

    @Test
    void aFreshConfigNeedsNoneOfTheNewMachinery() {
        assertFalse(FeatureGate.itemChanges(), "der Paket-Kern rechnet nur bei Bedarf");
        assertFalse(FeatureGate.funnyMap(), "die Funny Map (und ihr Abgleich mit Noamm)");
        assertFalse(FeatureGate.forge(), "die Forge-Zeiten");
        assertFalse(FeatureGate.forgeAlert(), "der Forge-Alarm");
        assertFalse(FeatureGate.skillXp(), "der Skill-XP-Zaehler");
        assertFalse(FeatureGate.frozenBlaze(), "die Frozen-Blaze-Erkennung (Set, Aura-Zeitgeber)");
        assertFalse(StarMobTarget.enabled(), "die Stern-Mobs");
        assertFalse(RunicGhostTarget.enabled(), "die Runic Ghosts");
    }
}
