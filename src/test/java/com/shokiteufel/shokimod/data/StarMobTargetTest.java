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
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        assertTrue(StarMobTarget.enabled());
        assertFalse(StarMobTarget.scanning(), "draussen bedeutet das Sternchen nichts");
        StarMobTarget.setInDungeon(true);
        assertTrue(StarMobTarget.scanning());
        assertTrue(StarMobTarget.INSTANCE.highlight());
    }

    @Test
    void itHasItsOwnSwitchAndDoesNotNeedTheMobVisualsMasterSwitch() {
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
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
        assertFalse(alt.ikohS.dungeon.starMobs.enabled);
        assertFalse(alt.ikohS.dungeon.starMobs.line);
        assertFalse(alt.ikohS.dungeon.starMobs.box);
    }

    @Test
    void lineAndBoxAreOptionalExtras() {
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        assertFalse(StarMobTarget.INSTANCE.tracer());
        assertFalse(StarMobTarget.INSTANCE.box());
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.line = true;
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.box = true;
        assertTrue(StarMobTarget.INSTANCE.tracer());
        assertTrue(StarMobTarget.INSTANCE.box());
        assertFalse(StarMobTarget.INSTANCE.nameplate(), "ein Namensschild haengt der Server selbst darueber");
    }

    @Test
    void theMarkerGateSeesTheStarMobs() {
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        assertTrue(FeatureGate.mobMarkers());
        // Gelb wie bei NoammAddons (Star Mob Color); bis 2.2.12 war es ein helleres Gelb
        assertEquals(0xFFFF00, StarMobTarget.INSTANCE.glowColorRGB());
    }

    @Test
    void theRoomRuleIsOffByDefaultAndNeedsTheMarkingToBeOn() {
        assertFalse(StarMobTarget.onlyCurrentRoom(), "von Haus aus aus");
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.onlyCurrentRoom = true;
        assertFalse(StarMobTarget.onlyCurrentRoom(), "ohne die Markierung gilt auch die Regel nicht");
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        assertTrue(StarMobTarget.onlyCurrentRoom());
        assertTrue(FeatureGate.roomScan(), "die Regel braucht die Raeume des Welt-Scans, auch ohne die Karte");
    }

    @Test
    void theWorldScanRunsForTheStarMobsAndOnlyForThem() {
        assertFalse(FeatureGate.roomScan(), "alles aus: kein Scan");
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        assertTrue(FeatureGate.roomScan(), "die Stern-Mobs brauchen die Raumnamen (benannte Figuren) und die Raum-Regel");
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = false;
        ModConfig.INSTANCE.ikohS.dungeon.funnyMap.enabled = true;
        assertTrue(FeatureGate.roomScan());
    }

    @Test
    void aStarTagNeedsTheStarAndTheHeartAtTheEnd() {
        assertTrue(StarMobTarget.isStarTag("✯ Zombie Soldier 240k❤"));
        assertTrue(StarMobTarget.isStarTag("✯ Withermancer 1.5M❤  "), "Leerzeichen am Ende stoeren nicht");
        assertFalse(StarMobTarget.isStarTag("✯ Zombie Soldier"), "ohne das Herz ist es ein anderer Ruestungsstaender");
        assertFalse(StarMobTarget.isStarTag("Zombie Soldier 240k❤"), "ohne Sternchen kein Stern-Mob");
        assertFalse(StarMobTarget.isStarTag(null));
        assertFalse(StarMobTarget.isStarTag(""));
    }

    @Test
    void theFourNamedFiguresAreAlwaysStarMobs() {
        assertTrue(StarMobTarget.isNamedStar("Shadow Assassin"));
        assertTrue(StarMobTarget.isNamedStar("Lost Adventurer"));
        assertTrue(StarMobTarget.isNamedStar("Diamond Guy"));
        assertTrue(StarMobTarget.isNamedStar("King Midas"));
        assertFalse(StarMobTarget.isNamedStar("Shadow Assassin 2"));
        assertFalse(StarMobTarget.isNamedStar("shadow assassin"), "Gross- und Kleinschreibung zaehlt, wie bei Noamm");
        assertFalse(StarMobTarget.isNamedStar("Notch"));
        assertFalse(StarMobTarget.isNamedStar(null));
    }

    @Test
    void theNamedFiguresDoNotCountInTheRoomsWhereTheyAreOnlyBystanders() {
        for (String raum : new String[] {"Duncan", "Balcony", "Wizard", "Pit", "Waterfall", "Atlas", "Chambers", "Layers", "Perch", "Golden Oasis"}) {
            assertFalse(StarMobTarget.raumErlaubt(raum, 70), raum);
        }
        assertTrue(StarMobTarget.raumErlaubt("Blaze", 70));
        assertTrue(StarMobTarget.raumErlaubt("Stairs", 80), "in Stairs bis Hoehe 80 zaehlen sie");
        assertFalse(StarMobTarget.raumErlaubt("Stairs", 80.5), "darueber nicht");
        assertTrue(StarMobTarget.raumErlaubt(null, 200), "Raum nicht bekannt: sie zaehlen");
    }

    @Test
    void theBatsOfTheDungeonHaveFixedHealth() {
        assertTrue(StarMobTarget.isDungeonBatHealth(100f));
        assertTrue(StarMobTarget.isDungeonBatHealth(200f));
        assertTrue(StarMobTarget.isDungeonBatHealth(400f));
        assertTrue(StarMobTarget.isDungeonBatHealth(800f));
        assertFalse(StarMobTarget.isDungeonBatHealth(6f), "eine gewoehnliche Fledermaus");
        assertFalse(StarMobTarget.isDungeonBatHealth(300f));
    }

    @Test
    void theBatsAreOnByDefaultButOnlyCountWithTheMarkingOn() {
        assertTrue(ModConfig.INSTANCE.ikohS.dungeon.starMobs.highlightBats, "bei Noamm an");
        assertFalse(StarMobTarget.highlightBats(), "ohne die Markierung nicht");
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        assertTrue(StarMobTarget.highlightBats());
        assertTrue(StarMobTarget.BAT.highlight());
        assertFalse(StarMobTarget.BAT.tracer(), "Fledermaeuse: nur der Umriss");
        assertFalse(StarMobTarget.BAT.box());
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.highlightBats = false;
        assertFalse(StarMobTarget.BAT.highlight());
    }

    @Test
    void theColoursAreTheOnesOfNoammAddons() {
        assertEquals(0xFFFF00, StarMobTarget.INSTANCE.glowColorRGB(), "Star Mob Color: gelb");
        assertEquals(0xFFAFAF, StarMobTarget.FEL.glowColorRGB(), "Fel Color: rosa");
        assertEquals(0x00FF00, StarMobTarget.BAT.glowColorRGB(), "Bat Color: gruen");
    }

    @Test
    void aChosenColourIsUsed() {
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.starColour = com.shokiteufel.shokimod.data.ColourText.ofRgb(0x112233);
        assertEquals(0x112233, StarMobTarget.INSTANCE.glowColorRGB());
    }

    @Test
    void theFelFollowsTheSwitchesOfTheStarMobs() {
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.enabled = true;
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.line = true;
        ModConfig.INSTANCE.ikohS.dungeon.starMobs.box = true;
        assertTrue(StarMobTarget.FEL.highlight());
        assertTrue(StarMobTarget.FEL.tracer());
        assertTrue(StarMobTarget.FEL.box());
    }
}
