package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.ModConfig.FunnyMapCategory;
import com.shokiteufel.shokimod.data.ModConfig.MistCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ikohS: Funny Map und The Mist stehen in einer eigenen Kategorie, nicht bei den normalen Funktionen (Wunsch des Users, 09.11.). Eine Datei aus
 * der ersten Fassung von 0.2.1 (Funny Map ganz oben, The Mist unter Mining) wird einmal uebernommen.
 */
class ModConfigIkohSTest {

    @Test
    void aFreshConfigHasBothCategoriesUnderIkohSAndNothingAtTheOldPlaces() {
        ModConfig c = new ModConfig();
        assertNotNull(c.ikohS);
        assertNotNull(c.ikohS.dungeon.funnyMap);
        assertNotNull(c.ikohS.mist);
        assertNull(c.funnyMap, "der alte Platz bleibt leer");
        assertNull(c.mining.mist, "der alte Platz bleibt leer");
        assertFalse(c.ikohS.dungeon.funnyMap.enabled, "von Haus aus aus");
        assertFalse(c.ikohS.mist.runicGhosts, "von Haus aus aus");
    }

    @Test
    void anOldFileMovesBothCategoriesAndKeepsTheValues() {
        ModConfig c = new ModConfig();
        c.funnyMap = new FunnyMapCategory();
        c.funnyMap.enabled = true;
        c.funnyMap.followNoamm = false;
        c.funnyMap.x = 77;
        c.mining.mist = new MistCategory();
        c.mining.mist.runicGhosts = true;
        c.mining.mist.runicGhostsBox = true;

        assertTrue(ModConfig.migrateIkohS(c));

        assertTrue(c.ikohS.dungeon.funnyMap.enabled);
        assertFalse(c.ikohS.dungeon.funnyMap.followNoamm);
        assertEquals(77, c.ikohS.dungeon.funnyMap.x);
        assertTrue(c.ikohS.mist.runicGhosts);
        assertTrue(c.ikohS.mist.runicGhostsBox);
        assertNull(c.funnyMap);
        assertNull(c.mining.mist);
        assertFalse(ModConfig.migrateIkohS(c), "ein zweiter Lauf aendert nichts mehr");
    }

    @Test
    void theRealJsonOfTheFirstBuildIsReadAndMoved() {
        String json = "{\"funnyMap\":{\"enabled\":true,\"style\":\"SECRETS\"},\"mining\":{\"mist\":{\"runicGhosts\":true,\"runicGhostsLine\":true}}}";
        ModConfig c = ModConfig.GSON.fromJson(json, ModConfig.class);
        assertNotNull(c.funnyMap);
        assertNotNull(c.mining.mist);

        assertTrue(ModConfig.migrateIkohS(c));

        assertTrue(c.ikohS.dungeon.funnyMap.enabled);
        assertEquals(ModConfig.FunnyStyle.SECRETS, c.ikohS.dungeon.funnyMap.style);
        assertTrue(c.ikohS.mist.runicGhosts);
        assertTrue(c.ikohS.mist.runicGhostsLine);
    }

    @Test
    void theSavedFileHasNoOldPlacesLeft() {
        ModConfig c = new ModConfig();
        c.funnyMap = new FunnyMapCategory();
        c.mining.mist = new MistCategory();
        ModConfig.migrateIkohS(c);
        String json = ModConfig.GSON.toJson(c);
        assertTrue(json.contains("\"ikohS\""));
        assertFalse(json.contains("\"funnyMap\":null"), "Gson schreibt Null-Felder nicht");
        ModConfig back = ModConfig.GSON.fromJson(json, ModConfig.class);
        assertNull(back.funnyMap);
        assertNull(back.mining.mist);
        assertNotNull(back.ikohS.dungeon.funnyMap);
    }

    @Test
    void aFileWithoutIkohSGetsFreshCategories() {
        ModConfig c = ModConfig.GSON.fromJson("{}", ModConfig.class);
        c.ikohS = null;
        assertFalse(ModConfig.migrateIkohS(c));
        assertNotNull(c.ikohS);
        assertNotNull(c.ikohS.dungeon.funnyMap);
        assertNotNull(c.ikohS.mist);
    }

    @Test
    void aMissingStyleOrPlayerNamesFallBackToTheDefaultAndValuesStayInTheirRange() {
        FunnyMapCategory f = new FunnyMapCategory();
        f.style = null;
        f.playerNames = null;
        f.scale = 99f;
        f.darken = -5;
        f.textScale = 9f;
        f.doorWidth = 2;
        f.borderWidth = 40;
        ModConfig.clampFunnyMap(f);
        assertSame(ModConfig.FunnyStyle.CHECKMARKS, f.style, "wie bei NoammAddons: Checkmarks");
        assertSame(ModConfig.FunnyPlayerNames.OFF, f.playerNames);
        assertEquals(5f, f.scale, "bis 5 wie in Noamms HUD-Editor");
        assertEquals(0, f.darken);
        assertEquals(1.5f, f.textScale);
        assertEquals(6, f.doorWidth);
        assertEquals(5, f.borderWidth);
    }

    @Test
    void theDefaultsAreTheDefaultsOfNoammAddons() {
        FunnyMapCategory f = new FunnyMapCategory();
        assertEquals(ModConfig.FunnyStyle.CHECKMARKS, f.style);
        assertFalse(f.extraLine, "seine Zeile unter der Karte ist von Haus aus aus");
        assertEquals(50, f.darken);
        assertEquals(6, f.doorWidth);
    }

    @Test
    void theFileOf2210MovesFunnyMapAndStarMobsIntoTheDungeonCategory() {
        // Bis 2.2.10 standen beide direkt unter ikohS; seit 2.2.11 unter ikohS > Dungeon
        String json = "{\"ikohS\":{\"funnyMap\":{\"enabled\":true,\"x\":55.0,\"followNoamm\":false},"
                + "\"starMobs\":{\"enabled\":true,\"box\":true},\"mist\":{\"runicGhosts\":true}}}";
        ModConfig c = ModConfig.GSON.fromJson(json, ModConfig.class);
        assertNotNull(c.ikohS.funnyMap, "die alte Stelle wird gelesen");
        assertNotNull(c.ikohS.starMobs, "die alte Stelle wird gelesen");

        assertTrue(ModConfig.migrateIkohS(c));

        assertTrue(c.ikohS.dungeon.funnyMap.enabled);
        assertEquals(55f, c.ikohS.dungeon.funnyMap.x);
        assertFalse(c.ikohS.dungeon.funnyMap.followNoamm);
        assertTrue(c.ikohS.dungeon.starMobs.enabled);
        assertTrue(c.ikohS.dungeon.starMobs.box);
        assertTrue(c.ikohS.mist.runicGhosts, "The Mist bleibt, wo sie war");
        assertNull(c.ikohS.funnyMap, "die alte Stelle ist leer");
        assertNull(c.ikohS.starMobs, "die alte Stelle ist leer");
        assertFalse(ModConfig.migrateIkohS(c), "ein zweiter Lauf aendert nichts mehr");
    }

    @Test
    void theSavedFileWritesTheDungeonCategoryAndNoLegacyKeys() {
        ModConfig c = new ModConfig();
        c.ikohS.dungeon.starMobs.enabled = true;
        String json = ModConfig.GSON.toJson(c);
        com.google.gson.JsonObject ikohS = com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("ikohS");
        assertTrue(ikohS.has("dungeon"));
        assertFalse(ikohS.has("funnyMap"), "unter ikohS steht die Funny Map nicht mehr");
        assertFalse(ikohS.has("starMobs"), "unter ikohS stehen die Stern-Mobs nicht mehr");
        assertTrue(ikohS.getAsJsonObject("dungeon").has("funnyMap"));
        assertTrue(ikohS.getAsJsonObject("dungeon").getAsJsonObject("starMobs").get("enabled").getAsBoolean());
        ModConfig back = ModConfig.GSON.fromJson(json, ModConfig.class);
        assertTrue(back.ikohS.dungeon.starMobs.enabled, "und kommt so wieder herein");
    }

    @Test
    void aFileWithAnEmptyDungeonGetsFreshGroups() {
        ModConfig c = ModConfig.GSON.fromJson("{\"ikohS\":{\"dungeon\":{}}}", ModConfig.class);
        c.ikohS.dungeon.funnyMap = null;
        c.ikohS.dungeon.starMobs = null;
        c.ikohS.dungeon.iHateDoors = null;
        ModConfig.migrateIkohS(c);
        assertNotNull(c.ikohS.dungeon.funnyMap);
        assertNotNull(c.ikohS.dungeon.starMobs);
        assertNotNull(c.ikohS.dungeon.iHateDoors, "auch die Gruppe I hate doors ist immer da (eine Datei bis 2.2.11 kennt sie nicht)");
    }
}
