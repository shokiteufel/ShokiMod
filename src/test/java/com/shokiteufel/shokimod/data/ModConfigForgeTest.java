package com.shokiteufel.shokimod.data;

import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.data.ModConfig.ForgeCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Forge wohnt seit 2.2.3 unter Mining > Forge und ist von Haus aus aus; alte Dateien mit der eigenen Kategorie ziehen mit um. */
class ModConfigForgeTest {

    /** So sah die Datei bis 2.2.2 aus: die Forge als eigene Kategorie, von Haus aus an, mit einem laufenden Stueck in Platz 2 */
    private static final String ALT = """
            {"forge":{"enabled":true,"showHud":true,"slots":5,
              "slotData":[{"state":"EMPTY","item":"","finishAt":0},
                          {"state":"BUSY","item":"Refined Diamond","finishAt":1794225000000}],
              "hudX":0.4,"hudY":0.3,"hudScale":1.5,"hudAlpha":0.8}}
            """;

    @Test
    void theForgeIsOffByDefaultAndLivesUnderMining() {
        ModConfig fresh = new ModConfig();
        assertNotNull(fresh.mining.forge);
        assertFalse(fresh.mining.forge.enabled);
        assertFalse(fresh.mining.forge.alert);
        assertFalse(fresh.mining.forge.onlyShortly);
        assertTrue(fresh.mining.forge.showHud, "mit 'Enabled' zusammen soll der Kasten da sein");
        assertNull(fresh.forge, "die alte Kategorie gibt es nicht mehr");
    }

    @Test
    void anOldFileMovesItsForgeUnderMiningAndSwitchesItOff() {
        ModConfig loaded = ModConfig.GSON.fromJson(ALT, ModConfig.class);
        assertNotNull(loaded.forge, "das alte Feld wird noch gelesen");

        assertTrue(ModConfig.migrateForge(loaded));

        assertNull(loaded.forge);
        ForgeCategory forge = loaded.mining.forge;
        assertFalse(forge.enabled, "von Haus aus an war es, jetzt ist es aus - wer es will, schaltet es ein");
        assertEquals(5, forge.slots);
        assertEquals(0.4f, forge.hudX);
        assertEquals(1.5f, forge.hudScale);
        assertEquals("Refined Diamond", forge.slotData.get(1).item);
        assertEquals(1794225000000L, forge.slotData.get(1).finishAt);

        assertFalse(ModConfig.migrateForge(loaded), "ein zweiter Lauf findet nichts mehr");
    }

    @Test
    void theMovedForgeIsWrittenOnlyUnderMining() {
        ModConfig loaded = ModConfig.GSON.fromJson(ALT, ModConfig.class);
        ModConfig.migrateForge(loaded);
        JsonObject json = ModConfig.GSON.toJsonTree(loaded).getAsJsonObject();
        assertFalse(json.has("forge"), "oben steht nichts mehr");
        assertTrue(json.getAsJsonObject("mining").has("forge"));
    }

    @Test
    void aFileWithoutAnyForgeKeepsTheDefaults() {
        ModConfig loaded = ModConfig.GSON.fromJson("{}", ModConfig.class);
        assertFalse(ModConfig.migrateForge(loaded));
        assertFalse(loaded.mining.forge.enabled);
    }

    @Test
    void theSkillXpPauseIsThirtySecondsFromTheStart() {
        assertEquals(30, new ModConfig().skillXp.idleSeconds);
    }

    @Test
    void anOldFileThatStillHoldsTheOldDefaultOf60MovesToThirtyOnce() {
        ModConfig.SkillXpCategory c = new ModConfig.SkillXpCategory();
        c.idleSeconds = 60;
        assertTrue(ModConfig.migrateSkillXpIdle(c));
        assertEquals(30, c.idleSeconds);
        assertFalse(ModConfig.migrateSkillXpIdle(c), "ein zweiter Lauf aendert nichts mehr");
        c.idleSeconds = 60;   // jetzt bewusst auf 60 gestellt: bleibt
        assertFalse(ModConfig.migrateSkillXpIdle(c));
        assertEquals(60, c.idleSeconds);
    }

    @Test
    void aHandChosenPauseIsKeptWhenTheFlagIsSet() {
        ModConfig.SkillXpCategory c = new ModConfig.SkillXpCategory();
        c.idleSeconds = 120;
        assertTrue(ModConfig.migrateSkillXpIdle(c));
        assertEquals(120, c.idleSeconds);
    }

    @Test
    void clampingFixesHandEditedValuesAndAlwaysKeepsSevenSlots() {
        ForgeCategory forge = new ForgeCategory();
        forge.slots = 99;
        forge.shortlyMinutes = 0;
        forge.alertBeforeSeconds = 99_999;
        forge.bannerDesign = null;
        forge.soundFile = null;
        forge.slotData = null;

        ModConfig.clampForge(forge);

        assertEquals(7, forge.slots);
        assertEquals(1, forge.shortlyMinutes);
        assertEquals(600, forge.alertBeforeSeconds, "hoechstens zehn Minuten");
        assertEquals(BannerDesign.ALERT_BAND, forge.bannerDesign);
        assertEquals("", forge.soundFile);
        assertEquals(7, forge.slotData.size());

        forge.alertBeforeSeconds = 2;
        ModConfig.clampForge(forge);
        assertEquals(10, forge.alertBeforeSeconds, "mindestens zehn Sekunden");
    }

    // ------------------------------------------------------------ Vorwarnung in Sekunden (2.4.8)

    @Test
    void theWarningWasInMinutesBeforeAndMovesToSeconds() {
        ForgeCategory forge = ModConfig.GSON.fromJson("{\"alertBeforeMinutes\":3}", ForgeCategory.class);
        assertEquals(3, forge.alertBeforeMinutes, "das alte Feld wird noch gelesen");

        assertTrue(ModConfig.migrateForgeAlert(forge));

        assertNull(forge.alertBeforeMinutes);
        assertTrue(forge.alertEarly);
        assertEquals(180, forge.alertBeforeSeconds);
        assertFalse(ModConfig.migrateForgeAlert(forge), "ein zweiter Lauf findet nichts mehr");
    }

    @Test
    void zeroMinutesBecameNoEarlyWarning() {
        ForgeCategory forge = ModConfig.GSON.fromJson("{\"alertBeforeMinutes\":0}", ForgeCategory.class);
        assertTrue(ModConfig.migrateForgeAlert(forge));
        assertFalse(forge.alertEarly);
        assertEquals(60, forge.alertBeforeSeconds, "die Sekunden bleiben, falls die Vorwarnung wieder eingeschaltet wird");
    }

    @Test
    void theOldDefaultOfOneMinuteStaysOneMinute() {
        ForgeCategory forge = ModConfig.GSON.fromJson("{\"alertBeforeMinutes\":1}", ForgeCategory.class);
        ModConfig.migrateForgeAlert(forge);
        assertTrue(forge.alertEarly);
        assertEquals(60, forge.alertBeforeSeconds);
    }

    @Test
    void aFileWithoutTheOldFieldIsLeftAlone() {
        ForgeCategory forge = ModConfig.GSON.fromJson("{\"alertBeforeSeconds\":45,\"alertEarly\":false}", ForgeCategory.class);
        assertFalse(ModConfig.migrateForgeAlert(forge));
        assertEquals(45, forge.alertBeforeSeconds);
        assertFalse(forge.alertEarly);
    }

    @Test
    void theOldFieldIsNotWrittenAfterTheMove() {
        ForgeCategory forge = ModConfig.GSON.fromJson("{\"alertBeforeMinutes\":2}", ForgeCategory.class);
        ModConfig.migrateForgeAlert(forge);
        JsonObject json = ModConfig.GSON.toJsonTree(forge).getAsJsonObject();
        assertFalse(json.has("alertBeforeMinutes"), "ein leeres Feld wird nicht gespeichert");
        assertEquals(120, json.get("alertBeforeSeconds").getAsInt());
    }

    @Test
    void anOldFileWithTheForgeInItsOwnCategoryAlsoGetsTheNewWarning() {
        ModConfig loaded = ModConfig.GSON.fromJson(
                "{\"forge\":{\"alertBeforeMinutes\":4,\"slotData\":[]}}", ModConfig.class);
        ModConfig.migrateForge(loaded);
        assertTrue(ModConfig.migrateForgeAlert(loaded.mining.forge));
        assertEquals(240, loaded.mining.forge.alertBeforeSeconds);
    }
}
