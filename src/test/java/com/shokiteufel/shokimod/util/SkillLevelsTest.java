package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.util.SkillLevels.Level;
import com.shokiteufel.shokimod.util.SkillLevels.Skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): Skill progress mit Overflow wie bei SkyHanni. Die Regel: bis Stufe 60 kostet jede Stufe, was in der Tabelle steht
 * (zusammen 111.672.425); danach 7.000.000 plus einen Zuschlag, der mit jeder Stufe um 600.000 waechst und sich bei jeder zehnten verdoppelt.
 */
class SkillLevelsTest {

    @Test
    void theTableAddsUpToTheKnownTotalForLevelSixty() {
        long sum = 0;
        for (long cost : SkillLevels.LEVEL_COSTS) sum += cost;
        assertEquals(60, SkillLevels.LEVEL_COSTS.length);
        assertEquals(SkillLevels.XP_FOR_60, sum, "SkyHannis XP_NEEDED_FOR_60");
        assertEquals(111_672_425L, sum);
    }

    @Test
    void theTableOnlyGoesUp() {
        for (int i = 1; i < SkillLevels.LEVEL_COSTS.length; i++) {
            assertTrue(SkillLevels.LEVEL_COSTS[i] > SkillLevels.LEVEL_COSTS[i - 1], "Stufe " + (i + 1));
        }
    }

    @Test
    void xpRequiredForTheFirstLevels() {
        assertEquals(0L, SkillLevels.xpRequiredForLevel(0));
        assertEquals(50L, SkillLevels.xpRequiredForLevel(1));
        assertEquals(175L, SkillLevels.xpRequiredForLevel(2));
        assertEquals(9_925L, SkillLevels.xpRequiredForLevel(10));
        assertEquals(522_425L, SkillLevels.xpRequiredForLevel(20));
        assertEquals(55_172_425L, SkillLevels.xpRequiredForLevel(50));
        assertEquals(111_672_425L, SkillLevels.xpRequiredForLevel(60));
    }

    @Test
    void theOverflowLevelsCostMoreAndMoreAndTheSurchargeDoublesEveryTenth() {
        assertEquals(7_600_000L, SkillLevels.costOfLevelUncapped(61));
        assertEquals(8_200_000L, SkillLevels.costOfLevelUncapped(62));
        assertEquals(13_000_000L, SkillLevels.costOfLevelUncapped(70));
        assertEquals(13_600_000L, SkillLevels.costOfLevelUncapped(71), "der Zuschlag gilt noch mit 600.000, das Verdoppeln beginnt danach");
        assertEquals(14_800_000L, SkillLevels.costOfLevelUncapped(72), "ab hier 1.200.000");
        assertEquals(SkillLevels.XP_FOR_60 + 7_600_000L, SkillLevels.xpRequiredForLevel(61));
        assertEquals(SkillLevels.xpRequiredForLevel(61) + 8_200_000L, SkillLevels.xpRequiredForLevel(62));
    }

    @Test
    void theCostOfALevelIsTheDifferenceOfTheTotalsAtEveryLevel() {
        for (int level = 1; level <= 200; level++) {
            assertEquals(SkillLevels.xpRequiredForLevel(level) - SkillLevels.xpRequiredForLevel(level - 1),
                    SkillLevels.costOfLevelUncapped(level), "Stufe " + level);
        }
    }

    @Test
    void theLevelFollowsFromTheExperienceAndBack() {
        Level zero = SkillLevels.calculate(0, 60);
        assertEquals(new Level(0, 0, 50, 0), zero);
        assertEquals(new Level(0, 49, 50, 0), SkillLevels.calculate(49, 60));
        assertEquals(new Level(1, 0, 125, 0), SkillLevels.calculate(50, 60));
        assertEquals(new Level(1, 100, 125, 0), SkillLevels.calculate(150, 60));
    }

    @Test
    void everyLevelRoundTripsAlsoPastTheTable() {
        for (int level = 0; level <= 200; level++) {
            Level calc = SkillLevels.calculate(SkillLevels.xpRequiredForLevel(level), 60);
            assertEquals(level, calc.level(), "Stufe " + level);
            assertEquals(0L, calc.xpCurrent(), "Stufe " + level);
            assertEquals(SkillLevels.costOfLevelUncapped(level + 1), calc.xpForNext(), "Preis nach Stufe " + level);
        }
    }

    @Test
    void theOverflowExperienceIsCountedFromTheCapOfTheSkill() {
        long atFifty = SkillLevels.xpRequiredForLevel(50);
        assertEquals(0L, SkillLevels.calculate(atFifty, 50).overflowXp());
        assertEquals(1_000L, SkillLevels.calculate(atFifty + 1_000L, 50).overflowXp());
        // Bei einer Obergrenze ueber 60 zaehlt die Tabelle nur bis 60
        assertEquals(500L, SkillLevels.calculate(SkillLevels.XP_FOR_60 + 500L, 70).overflowXp());
    }

    @Test
    void aMaxedFishingSkillReadsAsAnOverflowLevel() {
        long total = SkillLevels.xpRequiredForLevel(50) + 1_846_023_505L;
        Level calc = SkillLevels.calculate(total, 50);
        assertTrue(calc.level() > 60, "weit ueber die Tabelle hinaus: " + calc.level());
        assertEquals(1_846_023_505L, calc.overflowXp());
    }

    @Test
    void aPriceOfTheTableNamesItsLevel() {
        assertEquals(1, SkillLevels.levelWithCost(50));
        assertEquals(18, SkillLevels.levelWithCost(75_000));
        assertEquals(60, SkillLevels.levelWithCost(7_000_000));
        assertEquals(-1, SkillLevels.levelWithCost(40_000));
        assertEquals(-1, SkillLevels.levelWithCost(0));
    }

    @Test
    void numbersWithAbbreviationsAreRead() {
        assertEquals(75_000L, SkillLevels.parseAmount("75k"));
        assertEquals(4_000_000L, SkillLevels.parseAmount("4M"));
        assertEquals(1_500_000_000L, SkillLevels.parseAmount("1.5B"));
        assertEquals(12_345L, SkillLevels.parseAmount("12,345"));
        assertEquals(0L, SkillLevels.parseAmount("0"));
        assertNull(SkillLevels.parseAmount("abc"));
        assertNull(SkillLevels.parseAmount(""));
        assertNull(SkillLevels.parseAmount(null));
    }

    @Test
    void romanNumeralsGoBothWays() {
        assertEquals(14, SkillLevels.romanToDecimal("XIV"));
        assertEquals(4, SkillLevels.romanToDecimal("IV"));
        assertEquals(40, SkillLevels.romanToDecimal("XL"));
        assertEquals(35, SkillLevels.romanToDecimal("35"));
        assertEquals(0, SkillLevels.romanToDecimal("abc"));
        assertEquals("XIV", SkillLevels.toRoman(14));
        assertEquals("XCIX", SkillLevels.toRoman(99));
        for (int i = 1; i <= 399; i++) assertEquals(i, SkillLevels.romanToDecimal(SkillLevels.toRoman(i)), "Zahl " + i);
    }

    @Test
    void theSkillsKnowTheirCapsAndNames() {
        assertEquals(60, Skill.MINING.maxLevel());
        assertEquals(50, Skill.FISHING.maxLevel());
        assertEquals(57, Skill.FORAGING.maxLevel());
        assertNotNull(Skill.byName("mining"));
        assertEquals(Skill.FARMING, Skill.byName(" Farming "));
        assertNull(Skill.byName("Runecrafting"));
        assertNull(Skill.byName(null));
    }
}
