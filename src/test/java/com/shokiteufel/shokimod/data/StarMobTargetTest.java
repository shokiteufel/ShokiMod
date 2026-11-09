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
        ModConfig.INSTANCE.odin.starMobs = true;
        assertTrue(StarMobTarget.enabled());
        assertFalse(StarMobTarget.scanning(), "draussen bedeutet das Sternchen nichts");
        StarMobTarget.setInDungeon(true);
        assertTrue(StarMobTarget.scanning());
        assertTrue(StarMobTarget.INSTANCE.highlight());
    }

    @Test
    void itFollowsTheMobVisualsMasterSwitch() {
        ModConfig.INSTANCE.odin.starMobs = true;
        ModConfig.INSTANCE.mobVisuals.masterEnabled = false;
        StarMobTarget.setInDungeon(true);
        assertFalse(StarMobTarget.enabled());
        assertFalse(StarMobTarget.scanning());
    }

    @Test
    void lineAndBoxAreOptionalExtras() {
        ModConfig.INSTANCE.odin.starMobs = true;
        assertFalse(StarMobTarget.INSTANCE.tracer());
        assertFalse(StarMobTarget.INSTANCE.box());
        ModConfig.INSTANCE.odin.starMobsLine = true;
        ModConfig.INSTANCE.odin.starMobsBox = true;
        assertTrue(StarMobTarget.INSTANCE.tracer());
        assertTrue(StarMobTarget.INSTANCE.box());
        assertFalse(StarMobTarget.INSTANCE.nameplate(), "ein Namensschild haengt der Server selbst darueber");
    }

    @Test
    void theMarkerGateSeesTheStarMobs() {
        ModConfig.INSTANCE.odin.starMobs = true;
        assertTrue(FeatureGate.mobMarkers());
        assertEquals(0xFFFF55, StarMobTarget.INSTANCE.glowColorRGB());
    }
}
