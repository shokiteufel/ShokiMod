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
        assertNotNull(c.ikohS.funnyMap);
        assertNotNull(c.ikohS.mist);
        assertNull(c.funnyMap, "der alte Platz bleibt leer");
        assertNull(c.mining.mist, "der alte Platz bleibt leer");
        assertFalse(c.ikohS.funnyMap.enabled, "von Haus aus aus");
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

        assertTrue(c.ikohS.funnyMap.enabled);
        assertFalse(c.ikohS.funnyMap.followNoamm);
        assertEquals(77, c.ikohS.funnyMap.x);
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

        assertTrue(c.ikohS.funnyMap.enabled);
        assertEquals(ModConfig.FunnyStyle.SECRETS, c.ikohS.funnyMap.style);
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
        assertNotNull(back.ikohS.funnyMap);
    }

    @Test
    void aFileWithoutIkohSGetsFreshCategories() {
        ModConfig c = ModConfig.GSON.fromJson("{}", ModConfig.class);
        c.ikohS = null;
        assertFalse(ModConfig.migrateIkohS(c));
        assertNotNull(c.ikohS);
        assertNotNull(c.ikohS.funnyMap);
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
}
