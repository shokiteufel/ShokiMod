package com.shokiteufel.shokimod.handler;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Legion/Bobbin Overlay: die Stufen aus den Daten der Ruestung, die Rechnung, echte Spieler, der Umkreis, die Grenzen. */
class LegionBobbinTest {

    private static CompoundTag piece(String enchantment, int level) {
        CompoundTag enchantments = new CompoundTag();
        enchantments.putInt(enchantment, level);
        CompoundTag data = new CompoundTag();
        data.putString("id", "SOME_ARMOR");
        data.put("enchantments", enchantments);
        return data;
    }

    @Test
    void theNumbersAreTheOnesOfSkyHanni() {
        assertEquals(30.0, LegionBobbin.DISTANCE);
        assertEquals(20, LegionBobbin.LEGION_LIMIT);
        assertEquals(0.07, LegionBobbin.LEGION_PER_LEVEL);
        assertEquals(5, LegionBobbin.BOBBIN_LIMIT);
        assertEquals(0.2, LegionBobbin.BOBBIN_PER_LEVEL);
    }

    @Test
    void theLevelsComeFromTheEnchantmentsOfTheItem() {
        LegionBobbin.Levels legion = LegionBobbin.levelsOf(piece("ultimate_legion", 5));
        assertEquals(5, legion.legion());
        assertEquals(0, legion.bobbin());
        LegionBobbin.Levels bobbin = LegionBobbin.levelsOf(piece("ultimate_bobbin_time", 3));
        assertEquals(0, bobbin.legion());
        assertEquals(3, bobbin.bobbin());
    }

    @Test
    void otherEnchantmentsAndItemsWithoutDataCountNothing() {
        assertEquals(new LegionBobbin.Levels(0, 0), LegionBobbin.levelsOf(piece("sharpness", 7)));
        assertEquals(new LegionBobbin.Levels(0, 0), LegionBobbin.levelsOf(new CompoundTag()));
        assertEquals(new LegionBobbin.Levels(0, 0), LegionBobbin.levelsOf((CompoundTag) null));
        // eine Verzauberung, die kein Zahlenwert ist, zaehlt nicht (und wirft nichts)
        CompoundTag wrong = new CompoundTag();
        CompoundTag enchantments = new CompoundTag();
        enchantments.putString("ultimate_legion", "five");
        wrong.put("enchantments", enchantments);
        assertEquals(new LegionBobbin.Levels(0, 0), LegionBobbin.levelsOf(wrong));
    }

    @Test
    void aNegativeLevelNeverGivesABuffBelowZero() {
        assertEquals(0, LegionBobbin.levelsOf(piece("ultimate_legion", -4)).legion());
    }

    @Test
    void bothEnchantmentsOnOneItemAreReadTogether() {
        CompoundTag enchantments = new CompoundTag();
        enchantments.putInt("ultimate_legion", 2);
        enchantments.putInt("ultimate_bobbin_time", 4);
        CompoundTag data = new CompoundTag();
        data.put("enchantments", enchantments);
        assertEquals(new LegionBobbin.Levels(2, 4), LegionBobbin.levelsOf(data));
    }

    @Test
    void theLevelsOfSeveralPiecesAreAdded() {
        LegionBobbin.Levels sum = new LegionBobbin.Levels(0, 0);
        sum = sum.plus(LegionBobbin.levelsOf(piece("ultimate_legion", 5)));
        sum = sum.plus(LegionBobbin.levelsOf(piece("ultimate_bobbin_time", 3)));
        sum = sum.plus(LegionBobbin.levelsOf(piece("ultimate_legion", 1)));
        assertEquals(new LegionBobbin.Levels(6, 3), sum);
    }

    @Test
    void thePercentIsLevelsTimesValueTimesCount() {
        assertEquals(0.0, LegionBobbin.percent(0, LegionBobbin.LEGION_PER_LEVEL, 12));
        assertEquals(0.0, LegionBobbin.percent(5, LegionBobbin.LEGION_PER_LEVEL, 0));
        // Legion V mit 12 Spielern: 5 * 0,07 * 12 = 4,2
        assertEquals(4.2, LegionBobbin.percent(5, LegionBobbin.LEGION_PER_LEVEL, 12), 1e-9);
        // Bobbin' III mit 5 Haken: 3 * 0,2 * 5 = 3,0
        assertEquals(3.0, LegionBobbin.percent(3, LegionBobbin.BOBBIN_PER_LEVEL, 5), 1e-9);
    }

    @Test
    void onlyRealPlayersCount() {
        assertTrue(LegionBobbin.realPlayer(UUID.fromString("0b3e2a1c-5d4f-4a6b-8c7d-9e0f1a2b3c4d")), "Version 4");
        assertFalse(LegionBobbin.realPlayer(UUID.fromString("0b3e2a1c-5d4f-2a6b-8c7d-9e0f1a2b3c4d")), "Version 2: ein NPC");
        assertFalse(LegionBobbin.realPlayer(UUID.fromString("0b3e2a1c-5d4f-3a6b-8c7d-9e0f1a2b3c4d")), "Version 3");
        assertFalse(LegionBobbin.realPlayer(null));
    }

    @Test
    void theRangeIsThirtyBlocksAndTheEdgeCounts() {
        assertTrue(LegionBobbin.inRange(0.0));
        assertTrue(LegionBobbin.inRange(29.9 * 29.9));
        assertTrue(LegionBobbin.inRange(30.0 * 30.0), "genau 30 Bloecke zaehlen noch");
        assertFalse(LegionBobbin.inRange(30.1 * 30.1));
    }

    @Test
    void nothingIsWornAndNothingCountedFromTheStart() {
        // Aufruf ohne Spiel: die Zaehler stehen auf 0, solange nichts gelesen wurde
        assertFalse(LegionBobbin.wearingLegion());
        assertFalse(LegionBobbin.wearingBobbin());
        assertEquals(0, LegionBobbin.players());
        assertEquals(0, LegionBobbin.bobbers());
        assertEquals(0.0, LegionBobbin.legionPercent());
        assertEquals(0.0, LegionBobbin.bobbinPercent());
        assertNotNull(new LegionBobbin.Levels(1, 2));
    }
}
