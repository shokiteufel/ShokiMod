package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Kasten zum Legion/Bobbin Overlay: Zahlen schreiben, Zeilen, wann er steht. */
class LegionBobbinHudTest {

    @Test
    void numbersAreWrittenWithTwoPlacesAtMostLikeSkyHanni() {
        assertEquals("0.0", LegionBobbinHud.number(0.0));
        assertEquals("7.0", LegionBobbinHud.number(7.0));
        assertEquals("4.2", LegionBobbinHud.number(4.2));
        assertEquals("0.35", LegionBobbinHud.number(0.35));
        assertEquals("1.41", LegionBobbinHud.number(1.409));
    }

    @Test
    void floatingPointNoiseNeverReachesTheScreen() {
        // 5 * 0,07 * 20 ergibt in Gleitkomma 7.000000000000001
        assertEquals("7.0", LegionBobbinHud.number(5 * 0.07 * 20));
        assertEquals("4.2", LegionBobbinHud.number(12 * 0.35));
        assertEquals("1.8", LegionBobbinHud.number(3 * 0.6));
    }

    @Test
    void theLegionLineHasLabelCountAndPercent() {
        assertEquals("§d§lLegion: §r§b12 §r§7(4.2%)", LegionBobbinHud.legionLine(12, 4.2));
        assertEquals("§d§lLegion: §r§b0 §r§7(0.0%)", LegionBobbinHud.legionLine(0, 0.0));
    }

    @Test
    void theBobbinLineHasLabelCountAndPercent() {
        assertEquals("§3§lBobbin': §r§b3 §r§7(1.8%)", LegionBobbinHud.bobbinLine(3, 1.8));
        assertEquals("§3§lBobbin': §r§b5 §r§7(3.0%)", LegionBobbinHud.bobbinLine(5, 3.0));
    }

    @Test
    void aLineStandsWithTheEnchantOrWhenNothingIsHidden() {
        assertTrue(LegionBobbinHud.showsLine(true, true));
        assertFalse(LegionBobbinHud.showsLine(true, false), "ohne die Verzauberung versteckt");
        assertTrue(LegionBobbinHud.showsLine(false, true));
        assertTrue(LegionBobbinHud.showsLine(false, false), "ohne Hide without enchant immer");
    }

    @Test
    void theBoxIsNeverShownWhileTheFunctionIsOff() {
        ModConfig saved = ModConfig.INSTANCE;
        String savedType = GameState.Server.gametype;
        try {
            ModConfig.INSTANCE = new ModConfig();
            GameState.Server.gametype = "SKYBLOCK";
            ModConfig.INSTANCE.misc.legionBobbin.hideWithoutEnchant = false;
            assertFalse(LegionBobbinHud.shownNow());
        } finally {
            ModConfig.INSTANCE = saved;
            GameState.Server.gametype = savedType;
        }
    }

    @Test
    void theBoxStandsInSkyBlockOnlyAndOnlyWithSomethingToSay() {
        ModConfig saved = ModConfig.INSTANCE;
        String savedType = GameState.Server.gametype;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.misc.legionBobbin.enabled = true;
            GameState.Server.gametype = "SKYBLOCK";
            // Vorgabe: ohne die Verzauberung an der Ruestung nichts zu sagen
            assertFalse(LegionBobbinHud.shownNow(), "versteckt ohne Verzauberung");
            ModConfig.INSTANCE.misc.legionBobbin.hideWithoutEnchant = false;
            assertTrue(LegionBobbinHud.shownNow(), "ohne Hide without enchant steht er");
            GameState.Server.gametype = "BEDWARS";
            assertFalse(LegionBobbinHud.shownNow(), "ausserhalb von SkyBlock nie");
        } finally {
            ModConfig.INSTANCE = saved;
            GameState.Server.gametype = savedType;
        }
    }

    @Test
    void theBuiltBoxHasOneRowPerLineThatStands() {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            assertEquals(0, LegionBobbinHud.build().rowCount(), "ohne Verzauberung und mit Hide without enchant keine Zeile");
            ModConfig.INSTANCE.misc.legionBobbin.hideWithoutEnchant = false;
            assertEquals(2, LegionBobbinHud.build().rowCount(), "Legion und Bobbin'");
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }
}
