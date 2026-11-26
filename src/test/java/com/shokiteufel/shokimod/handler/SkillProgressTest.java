package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory;
import com.shokiteufel.shokimod.data.ModConfig.SkillProgressCategory.SkillInfo;
import com.shokiteufel.shokimod.util.SkillLevels;
import com.shokiteufel.shokimod.util.SkillLevels.Skill;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wunsch des Users (26.11.): Skill progress mit Progress bar und Overflow wie bei SkyHanni. Die Zahlen kommen aus der Aktionsleiste
 * ("+12.5 Mining (22,922/75,000)" oder "+1.1 Mining (48.39%)"), bei der Prozentform dazu die Stufe aus der Tab-Liste.
 */
class SkillProgressTest {

    private static final List<String> NO_TAB = List.of();

    private static SkillProgress.Update apply(String bar, List<String> tab, Map<String, SkillInfo> store) {
        return SkillProgress.apply(bar, tab, store);
    }

    @Test
    void theFractionFormGivesTheLevelFromThePriceOfTheNextOne() {
        Map<String, SkillInfo> store = new HashMap<>();
        SkillProgress.Update u = apply("+12.5 Mining (22,922/75,000)", NO_TAB, store);
        assertNotNull(u);
        assertEquals(Skill.MINING, u.skill());
        SkillInfo info = u.info();
        assertEquals(17, info.level, "75.000 ist der Preis von Stufe 18, also steht man auf 17");
        assertEquals(22_922L, info.currentXp);
        assertEquals(75_000L, info.currentXpMax);
        assertEquals(147_425L + 22_922L, info.totalXp);
        assertEquals("12.5", info.lastGain);
        assertEquals(17, info.overflowLevel);
        assertEquals(22_922L, info.overflowCurrentXp);
        assertSame(info, store.get("mining"));
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }

    @Test
    void anAbbreviatedTargetIsRead() {
        SkillProgress.Update u = apply("+3 Combat (1,234/75k)", NO_TAB, new HashMap<>());
        assertNotNull(u);
        assertEquals(75_000L, u.info().currentXpMax);
        assertEquals(17, u.info().level);
    }

    @Test
    void theBarCanCarryOtherPartsAroundTheGain() {
        SkillProgress.Update u = apply("1,234/1,500❤  +12.5 Mining (22,922/75,000)  567/567✎ Mana", NO_TAB, new HashMap<>());
        assertNotNull(u);
        assertEquals(Skill.MINING, u.skill());
    }

    @Test
    void aMaxedSkillShowsTargetZeroAndCountsPastTheCap() {
        Map<String, SkillInfo> store = new HashMap<>();
        SkillProgress.Update u = apply("+3,909.8 Fishing (1,846,023,505/0)", NO_TAB, store);
        assertNotNull(u);
        SkillInfo info = u.info();
        assertEquals(50, info.level, "die Obergrenze von Fishing");
        assertEquals(0L, info.currentXpMax);
        assertEquals(1_846_023_505L + SkillLevels.xpRequiredForLevel(50), info.totalXp);
        assertTrue(info.overflowLevel > 60);
        assertEquals(1_846_023_505L, info.overflowTotalXp);
    }

    @Test
    void anOverflowLevelUpIsDetectedOnlyAboveTheCap() {
        Map<String, SkillInfo> store = new HashMap<>();
        SkillInfo before = new SkillInfo();
        before.overflowLevel = 61;
        store.put("mining", before);
        long beyond = SkillLevels.xpRequiredForLevel(62) - SkillLevels.xpRequiredForLevel(60);
        SkillProgress.Update u = apply("+1 Mining (" + (beyond + 100) + "/0)", NO_TAB, store);
        assertNotNull(u);
        assertEquals(62, u.info().overflowLevel);
        assertTrue(u.overflowLevelUp());
        assertEquals(61, u.oldOverflowLevel());

        // Dieselbe Leiste noch einmal: keine neue Stufe
        SkillProgress.Update again = apply("+1 Mining (" + (beyond + 100) + "/0)", NO_TAB, store);
        assertFalse(again.overflowLevelUp());
    }

    @Test
    void aLevelUpBelowTheCapIsNotAnOverflowLevelUp() {
        Map<String, SkillInfo> store = new HashMap<>();
        SkillInfo before = new SkillInfo();
        before.overflowLevel = 17;
        store.put("mining", before);
        SkillProgress.Update u = apply("+1 Mining (10/100,000)", NO_TAB, store);
        assertNotNull(u);
        assertEquals(18, u.info().level, "100.000 ist der Preis von Stufe 19");
        assertFalse(u.overflowLevelUp());
    }

    @Test
    void thePercentFormNeedsTheLevelFromTheTabList() {
        Map<String, SkillInfo> store = new HashMap<>();
        assertNull(apply("+1.1 Farming (12.4%)", NO_TAB, store), "ohne Widget keine Stufe");
        SkillProgress.Update u = apply("+1.1 Farming (12.4%)", List.of("Farming 35: 12.4%", "Mining 14: 8.0%"), store);
        assertNotNull(u);
        SkillInfo info = u.info();
        assertEquals(35, info.level);
        assertEquals(1_800_000L, info.currentXpMax, "der Preis von Stufe 36");
        assertEquals(223_200L, info.currentXp);
        assertEquals(SkillLevels.xpRequiredForLevel(35) + 223_200L, info.totalXp);
    }

    @Test
    void thePercentFormTakesTheOtherSkillsLineOnlyForItsOwnSkill() {
        SkillProgress.Update u = apply("+1 Mining (48.39%)", List.of("Farming 35: 12.4%", "Mining 14: 22.0%"), new HashMap<>());
        assertNotNull(u);
        assertEquals(14, u.info().level);
    }

    @Test
    void aTabLineWithNumbersGivesTheStandDirectly() {
        SkillProgress.Update u = apply("+1 Mining (48.39%)", List.of("Mining 14: 22,922/75k"), new HashMap<>());
        assertNotNull(u);
        assertEquals(14, u.info().level);
        assertEquals(22_922L, u.info().currentXp);
        assertEquals(75_000L, u.info().currentXpMax);
        assertEquals(SkillLevels.xpRequiredForLevel(14) + 22_922L, u.info().totalXp);
    }

    @Test
    void aMaxTabLineSaysNothingAboutThePercent() {
        assertNull(apply("+1 Farming (48.39%)", List.of("Farming 60: MAX"), new HashMap<>()));
    }

    @Test
    void unknownSkillsAndOtherLinesAreIgnored() {
        assertNull(apply("+5 Runecrafting (1/2)", NO_TAB, new HashMap<>()));
        assertNull(apply("1,234/1,500❤  567/567✎ Mana", NO_TAB, new HashMap<>()));
        assertNull(apply("+5 coins", NO_TAB, new HashMap<>()));
        assertNull(apply("", NO_TAB, new HashMap<>()));
    }

    // ------------------------------------------------------------------ Menue der Faehigkeiten

    @Test
    void theSkillsMenuGivesTheStandBeforeAnyGain() {
        Map<String, SkillInfo> store = new HashMap<>();
        SkillProgress.MenuItem item = new SkillProgress.MenuItem("Mining XIV", List.of(
                "View your Mining progress and rewards.", "", "Progress to Level XV: 29.5%", "12,345/40,000", "", "Click to view!"));
        assertEquals(Skill.MINING, SkillProgress.readMenuItem(item, store));
        SkillInfo info = store.get("mining");
        assertEquals(14, info.level);
        assertEquals(12_345L, info.currentXp);
        assertEquals(40_000L, info.currentXpMax);
        assertEquals(SkillLevels.xpRequiredForLevel(14) + 12_345L, info.totalXp);
    }

    @Test
    void aMaxedSkillInTheMenuGivesItsTotalExperience() {
        Map<String, SkillInfo> store = new HashMap<>();
        long total = SkillLevels.xpRequiredForLevel(60) + 50_000_000L;
        SkillProgress.MenuItem item = new SkillProgress.MenuItem("Combat LX", List.of(
                "View your Combat progress and rewards.", "", "Max Skill level reached!", String.format(java.util.Locale.US, "%,d", total), "", "Click to view!"));
        assertEquals(Skill.COMBAT, SkillProgress.readMenuItem(item, store));
        SkillInfo info = store.get("combat");
        assertEquals(60, info.level);
        assertEquals(total, info.totalXp);
        assertEquals(50_000_000L, info.currentXp);
        assertEquals(0L, info.currentXpMax);
        assertEquals(50_000_000L, info.overflowTotalXp);
        assertTrue(info.overflowLevel > 60);
    }

    @Test
    void anItemThatIsNoSkillIsLeftAlone() {
        Map<String, SkillInfo> store = new HashMap<>();
        assertNull(SkillProgress.readMenuItem(new SkillProgress.MenuItem("Go Back", List.of("To SkyBlock Menu")), store));
        assertNull(SkillProgress.readMenuItem(new SkillProgress.MenuItem("Social", List.of("Click to view!")), store), "keine dieser Faehigkeiten");
        assertTrue(store.isEmpty());
    }

    // ------------------------------------------------------------------ Anzeige

    private static SkillInfo sample() {
        SkillInfo info = new SkillInfo();
        info.level = 35;
        info.currentXp = 12_345L;
        info.currentXpMax = 40_000L;
        info.overflowLevel = 62;
        info.overflowCurrentXp = 100L;
        info.overflowCurrentXpMax = 8_800_000L;
        info.lastGain = "12.4";
        return info;
    }

    @Test
    void theTextIsLevelGainAndProgress() {
        SkillProgressCategory c = new SkillProgressCategory();
        assertEquals("§9[§d35§9] §b+12.4 §7(§612,345§7/§640,000§7)", SkillProgress.displayText(Skill.MINING, sample(), c));
    }

    @Test
    void theNameThePercentageAndTheActionsLeftAreOptional() {
        SkillProgressCategory c = new SkillProgressCategory();
        c.showLevel = false;
        c.useSkillName = true;
        c.usePercentage = true;
        assertEquals("§b+12.4 Mining §7(§630.86%§7)", SkillProgress.displayText(Skill.MINING, sample(), c));
        c.usePercentage = false;
        c.showActionLeft = true;
        // (40.000 - 12.345) / 12,4 = 2230,2 -> 2231 mit dem Rest
        assertEquals("§b+12.4 Mining §7(§612,345§7/§640,000§7) - §62,231 Left", SkillProgress.displayText(Skill.MINING, sample(), c));
    }

    @Test
    void overflowInTheDisplayUsesTheOverflowLevelAndProgress() {
        SkillProgressCategory c = new SkillProgressCategory();
        c.overflow.inDisplay = true;
        assertEquals("§9[§d62§9] §b+12.4 §7(§6100§7/§68,800,000§7)", SkillProgress.displayText(Skill.MINING, sample(), c));
    }

    @Test
    void aMaxedSkillShowsOnlyTheExperience() {
        SkillInfo info = sample();
        info.currentXpMax = 0L;
        info.currentXp = 1_846_023_505L;
        SkillProgressCategory c = new SkillProgressCategory();
        assertEquals("§9[§d35§9] §b+12.4 §7(§61,846,023,505§7)", SkillProgress.displayText(Skill.FISHING, info, c));
        c.usePercentage = true;
        assertTrue(SkillProgress.displayText(Skill.FISHING, info, c).contains("100.00%"));
    }

    @Test
    void withoutAGainTheActionsLeftAreInfinite() {
        assertEquals("§6∞ Left", SkillProgress.actionsLeft("", 100, 10));
        assertEquals("§6∞ Left", SkillProgress.actionsLeft("abc", 100, 10));
        assertEquals("§6∞ Left", SkillProgress.actionsLeft("0", 100, 10));
    }

    @Test
    void theBarFractionIsTheProgressOrFullAtTargetZero() {
        SkillInfo info = sample();
        assertEquals(12_345.0 / 40_000.0, SkillProgress.barFraction(info, false), 1e-12);
        assertEquals(100.0 / 8_800_000.0, SkillProgress.barFraction(info, true), 1e-12);
        info.currentXpMax = 0L;
        assertEquals(1.0, SkillProgress.barFraction(info, false));
        info.currentXp = 99_999L;
        info.currentXpMax = 40_000L;
        assertEquals(1.0, SkillProgress.barFraction(info, false), "nie ueber voll");
    }

    // ------------------------------------------------------------------ Tooltip

    @Test
    void theOverflowTooltipReplacesTheMaxLine() {
        SkillInfo info = sample();
        List<String> plain = List.of("Combat LX", "View your Combat progress.", "", "Max Skill level reached!", "                    123,456,789", "", "Click to view!");
        List<Component> original = new ArrayList<>();
        for (String line : plain) original.add(Component.literal(line));
        List<Component> out = SkillProgress.overflowTooltip(plain, original, Skill.COMBAT, info, true);
        assertNotNull(out);
        assertEquals("§7Progress to Level LXIII: §e0.0%", out.get(3).getString());
        assertTrue(out.get(4).getString().contains("100§6/§e8,800,000"));
        assertTrue(out.stream().anyMatch(l -> l.getString().contains("OVERFLOW XP:")));
    }

    @Test
    void aSkillBelowTheCapKeepsTheTooltipOfTheGame() {
        List<String> plain = List.of("Mining XIV", "Progress to Level XV: 29.5%", "12,345/40,000", "Click to view!");
        List<Component> original = new ArrayList<>();
        for (String line : plain) original.add(Component.literal(line));
        assertNull(SkillProgress.overflowTooltip(plain, original, Skill.MINING, sample(), true));
    }

    @Test
    void everythingIsOffFromTheStart() {
        SkillProgressCategory c = new ModConfig().skillProgress;
        assertFalse(c.enabled);
        assertFalse(c.bar.enabled);
        assertFalse(c.overflow.inDisplay);
        assertFalse(c.overflow.inProgressBar);
        assertFalse(c.overflow.inSkillMenuStackSize);
        assertFalse(c.overflow.inSkillMenuTooltip);
        assertFalse(c.overflow.inChat);
    }
}
