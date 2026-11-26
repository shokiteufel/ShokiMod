package com.shokiteufel.shokimod.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Misc (2.4.16): von Haus aus aus, speichern und laden, eine Datei von vorher, kaputte Werte. */
class ModConfigMiscTest {

    @Test
    void everythingIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        assertFalse(c.misc.npcLimit.enabled);
        assertFalse(c.misc.shortenWarp);
        assertFalse(c.misc.replaceWarpIs);
        assertFalse(c.misc.fixDoubleClicks);
        assertFalse(FeatureGate.npcDayLimit());
        assertFalse(FeatureGate.warpCommands());
        assertFalse(FeatureGate.fixDoubleClicks());
        assertEquals(ModConfig.NpcNumberFormat.SHORT, c.misc.npcLimit.numberFormat);
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.misc.npcLimit.enabled = true;
        c.misc.npcLimit.numberFormat = ModConfig.NpcNumberFormat.LONG;
        c.misc.npcLimit.soldCoins = 123_456L;
        c.misc.npcLimit.gmtEpochDay = 20_600L;
        c.misc.npcLimit.hudScale = 1.5f;
        c.misc.shortenWarp = true;
        c.misc.fixDoubleClicks = true;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.misc.npcLimit.enabled);
        assertEquals(ModConfig.NpcNumberFormat.LONG, again.misc.npcLimit.numberFormat);
        assertEquals(123_456L, again.misc.npcLimit.soldCoins);
        assertEquals(20_600L, again.misc.npcLimit.gmtEpochDay);
        assertEquals(1.5f, again.misc.npcLimit.hudScale);
        assertTrue(again.misc.shortenWarp);
        assertFalse(again.misc.replaceWarpIs);
        assertTrue(again.misc.fixDoubleClicks);
    }

    @Test
    void aFileFromBeforeMiscGetsIt() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"misc\":null}", ModConfig.class);
        ModConfig.repairMisc(loaded);
        assertNotNull(loaded.misc);
        assertNotNull(loaded.misc.npcLimit);
        ModConfig second = ModConfig.GSON.fromJson("{\"misc\":{\"npcLimit\":null}}", ModConfig.class);
        ModConfig.repairMisc(second);
        assertNotNull(second.misc.npcLimit);
    }

    @Test
    void brokenValuesAreRepaired() {
        ModConfig loaded = ModConfig.GSON.fromJson(
                "{\"misc\":{\"npcLimit\":{\"numberFormat\":\"NOPE\",\"soldCoins\":-5,\"gmtEpochDay\":-1}}}", ModConfig.class);
        ModConfig.repairMisc(loaded);
        assertEquals(ModConfig.NpcNumberFormat.SHORT, loaded.misc.npcLimit.numberFormat);
        assertEquals(0L, loaded.misc.npcLimit.soldCoins);
        assertEquals(0L, loaded.misc.npcLimit.gmtEpochDay);
    }

    @Test
    void anySwitchTurnsOnTheLocationReader() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.misc.shortenWarp = true;
            assertTrue(FeatureGate.warpCommands());
            assertTrue(FeatureGate.location());
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.misc.replaceWarpIs = true;
            assertTrue(FeatureGate.warpCommands());
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.misc.fixDoubleClicks = true;
            assertTrue(FeatureGate.location());
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.misc.npcLimit.enabled = true;
            assertTrue(FeatureGate.location());
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
