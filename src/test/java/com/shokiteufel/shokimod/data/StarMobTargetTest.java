package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Stern-Mobs im Dungeon: Namen wie im Mitschnitt von Schiggy (09.11., Catacombs Floor V) und die Schalter. */
class StarMobTargetTest {

    @BeforeEach
    void frisch() {
        ModConfig.INSTANCE = new ModConfig();
        StarMobTarget.setInDungeon(false);
    }

    @AfterEach
    void aufraeumen() {
        StarMobTarget.setInDungeon(false);
    }

    @Test
    void theNamesOfStarMobsAreRecognised() {
        // Vorn stehen private Zeichen (Symbole der Mob-Art), dahinter das Sternchen, Name und Leben - so kommt es vom Server
        assertTrue(StarMobTarget.isStar(" ✯ Zombie Soldier 240k❤"));
        assertTrue(StarMobTarget.isStar(" ✯ Speedy Crypt Lurker 92,250❤"));
        assertTrue(StarMobTarget.isStar(" ✯ Angry Archaeologist 141.8k❤"));
        assertTrue(StarMobTarget.isStar(" ✯ Fortified Skeleton Soldier 243.8k❤"));
    }

    @Test
    void namesWithoutTheStarAreNot() {
        assertFalse(StarMobTarget.isStar("Zombie Soldier 240k❤"));
        assertFalse(StarMobTarget.isStar("[Lv100] Revenant Horror"));
        assertFalse(StarMobTarget.isStar(""));
        assertFalse(StarMobTarget.isStar(null));
    }

    @Test
    void theBodyStandsOneIdBeforeItsNameTagAndThreeForAWithermancer() {
        assertEquals(1, StarMobTarget.bodyIdOffset(" ✯ Zombie Soldier 240k❤"));
        assertEquals(1, StarMobTarget.bodyIdOffset(" ✯ Angry Archaeologist 141.8k❤"));
        assertEquals(1, StarMobTarget.bodyIdOffset("✯ Fortified Fels 450k❤"));
        assertEquals(3, StarMobTarget.bodyIdOffset(" ✯ Healthy Withermancer 733.2k❤"));
        assertEquals(1, StarMobTarget.bodyIdOffset(null));
    }

    @Test
    void everythingIsOffFromTheStart() {
        assertFalse(StarMobTarget.enabled());
        assertFalse(StarMobTarget.scanning());
        assertFalse(StarMobTarget.INSTANCE.highlight());
        assertFalse(StarMobTarget.INSTANCE.tracer());
        assertFalse(StarMobTarget.INSTANCE.box());
    }

    @Test
    void itScansOnlyInsideADungeon() {
        ModConfig.INSTANCE.ikohS.starMobs.enabled = true;
        assertTrue(StarMobTarget.enabled());
        assertFalse(StarMobTarget.scanning(), "draussen bedeutet das Sternchen nichts");
        StarMobTarget.setInDungeon(true);
        assertTrue(StarMobTarget.scanning());
        assertTrue(StarMobTarget.INSTANCE.highlight());
    }

    @Test
    void itHasItsOwnSwitchAndDoesNotNeedTheMobVisualsMasterSwitch() {
        ModConfig.INSTANCE.ikohS.starMobs.enabled = true;
        ModConfig.INSTANCE.mobVisuals.masterEnabled = false;
        StarMobTarget.setInDungeon(true);
        assertTrue(StarMobTarget.enabled());
        assertTrue(StarMobTarget.scanning());
        assertTrue(FeatureGate.mobMarkers(), "auch ohne den Hauptschalter der Mob-Markierungen muss der Markierer laufen");
    }

    @Test
    void itIsOffFromTheStartEvenWhenAnOldFileStillCarriesTheOdinKeys() {
        // Bis 2.2.3 standen die Schalter bei Odin; eine alte Datei traegt sie noch. Sie werden nicht uebernommen: von Haus aus aus
        ModConfig alt = new com.google.gson.Gson().fromJson("{\"odin\":{\"starMobs\":true,\"starMobsLine\":true,\"starMobsBox\":true}}", ModConfig.class);
        ModConfig.migrateIkohS(alt);
        assertFalse(alt.ikohS.starMobs.enabled);
        assertFalse(alt.ikohS.starMobs.line);
        assertFalse(alt.ikohS.starMobs.box);
    }

    @Test
    void lineAndBoxAreOptionalExtras() {
        ModConfig.INSTANCE.ikohS.starMobs.enabled = true;
        assertFalse(StarMobTarget.INSTANCE.tracer());
        assertFalse(StarMobTarget.INSTANCE.box());
        ModConfig.INSTANCE.ikohS.starMobs.line = true;
        ModConfig.INSTANCE.ikohS.starMobs.box = true;
        assertTrue(StarMobTarget.INSTANCE.tracer());
        assertTrue(StarMobTarget.INSTANCE.box());
        assertFalse(StarMobTarget.INSTANCE.nameplate(), "ein Namensschild haengt der Server selbst darueber");
    }

    @Test
    void theMarkerGateSeesTheStarMobs() {
        ModConfig.INSTANCE.ikohS.starMobs.enabled = true;
        assertTrue(FeatureGate.mobMarkers());
        assertEquals(0xFFFF55, StarMobTarget.INSTANCE.glowColorRGB());
    }
}
