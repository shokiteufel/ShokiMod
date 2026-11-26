package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory.SkillInfo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Skill progress (2.4.12): von Haus aus aus, speichern und laden, beschaedigte Dateien. */
class ModConfigSkillProgressTest {

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        ModConfig.INSTANCE = c;
        assertFalse(c.skillProgress.enabled);
        assertFalse(c.skillProgress.bar.enabled);
        assertFalse(c.skillProgress.bar.chroma);
        assertFalse(FeatureGate.skillProgress());
    }

    @Test
    void theDefaultsAreSkyHannisMeasures() {
        ModConfig.SkillBarCategory bar = new ModConfig().skillProgress.bar;
        assertEquals(182, bar.width);
        assertEquals(6, bar.height);
        assertEquals(0xFF0000, ColourText.rgb(bar.colour, 0));
        assertTrue(new ModConfig().skillProgress.showLevel);
        assertTrue(new ModConfig().skillProgress.useIcon);
        assertFalse(new ModConfig().skillProgress.useSkillName);
    }

    @Test
    void whatWasReadSurvivesSavingAndLoading() {
        ModConfig c = new ModConfig();
        SkillInfo info = new SkillInfo();
        info.level = 35;
        info.totalXp = 123_456_789L;
        info.overflowLevel = 62;
        info.lastGain = "12.4";
        c.skillProgress.skills.put("mining", info);
        c.skillProgress.lastSkill = "Mining";
        c.skillProgress.overflow.inDisplay = true;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertEquals(35, again.skillProgress.skills.get("mining").level);
        assertEquals(123_456_789L, again.skillProgress.skills.get("mining").totalXp);
        assertEquals(62, again.skillProgress.skills.get("mining").overflowLevel);
        assertEquals("12.4", again.skillProgress.skills.get("mining").lastGain);
        assertEquals("Mining", again.skillProgress.lastSkill);
        assertTrue(again.skillProgress.overflow.inDisplay);
    }

    @Test
    void aFileWithoutTheNewPartsGetsThemOnLoading() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"skillProgress\":null}", ModConfig.class);
        ModConfig.repairSkillProgress(loaded);
        assertNotNull(loaded.skillProgress);
        assertNotNull(loaded.skillProgress.bar);
        assertNotNull(loaded.skillProgress.overflow);
        assertFalse(loaded.skillProgress.enabled);
    }

    @Test
    void damagedValuesAreStraightened() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"skillProgress\":{\"bar\":{\"width\":5000,\"height\":-4,\"colour\":null},"
                + "\"skills\":{\"mining\":null,\"combat\":{\"level\":3}},\"lastSkill\":null}}", ModConfig.class);
        ModConfig.repairSkillProgress(loaded);
        assertEquals(1000, loaded.skillProgress.bar.width);
        assertEquals(3, loaded.skillProgress.bar.height);
        assertEquals(0xFF0000, ColourText.rgb(loaded.skillProgress.bar.colour, 0));
        assertFalse(loaded.skillProgress.skills.containsKey("mining"), "ein Eintrag ohne Wert faellt weg");
        assertEquals(3, loaded.skillProgress.skills.get("combat").level);
        assertEquals("", loaded.skillProgress.lastSkill);
    }

    @Test
    void anOldHexColourIsWrittenForThePicker() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"skillProgress\":{\"bar\":{\"colour\":\"00FF00\"}}}", ModConfig.class);
        ModConfig.repairSkillProgress(loaded);
        assertTrue(loaded.skillProgress.bar.colour.indexOf(':') >= 0, "im Format des Waehlers");
        assertEquals(0x00FF00, ColourText.rgb(loaded.skillProgress.bar.colour, 0));
    }
}
