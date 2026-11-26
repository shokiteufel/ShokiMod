package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Belohnungen der Besucher: Namen, die Vorauswahl von SkyHanni und der Schalter je Belohnung in der Einstellung.
 */
class VisitorRewardTest {

    @Test
    void thereAreThirtyTwoRewardsLikeInSkyHanni() {
        assertEquals(32, VisitorReward.values().length);
    }

    @Test
    void aRewardIsFoundByItsNameWhateverTheColourAndCase() {
        assertSame(VisitorReward.DEDICATION, VisitorReward.byName("Dedication IV"));
        assertSame(VisitorReward.DEDICATION, VisitorReward.byName("§9Dedication IV"));
        assertSame(VisitorReward.DEDICATION, VisitorReward.byName("dedication iv"));
        assertSame(VisitorReward.DEDICATION, VisitorReward.byName("  Dedication IV  "));
        assertSame(VisitorReward.OVERGROWN_GRASS, VisitorReward.byName("Overgrown Grass ❤"), "das Herz eines 'charmed' Besuchers gehoert nicht zum Namen");
    }

    @Test
    void otherSpellingsOfTheSameRewardAreFound() {
        assertSame(VisitorReward.MUSIC_RUNE, VisitorReward.byName("Music Rune"));
        assertSame(VisitorReward.MUSIC_RUNE, VisitorReward.byName("Music Rune I"));
        assertSame(VisitorReward.HARVEST_HARBINGER, VisitorReward.byName("Harvest Harbinger V Potion"));
        // Das typographische Hochkomma ist das gerade
        assertSame(VisitorReward.VOTER_BADGE, VisitorReward.byName("Voter’s Badge"));
        assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("Visitors' Gratitude"));
    }

    @Test
    void anUnknownNameIsNoReward() {
        assertNull(VisitorReward.byName("Enchanted Carrot"));
        assertNull(VisitorReward.byName(""));
        assertNull(VisitorReward.byName(null));
    }

    @Test
    void everyRewardHasItsOwnNameAndNoNameIsFoundTwice() {
        Set<String> seen = new HashSet<>();
        for (VisitorReward reward : VisitorReward.values()) {
            assertSame(reward, VisitorReward.byName(reward.displayName()), reward.name());
            assertTrue(seen.add(VisitorReward.normalize(reward.displayName())), "doppelter Name " + reward.displayName());
        }
    }

    @Test
    void theDefaultsAreSkyHannisTenRewards() {
        Set<VisitorReward> expected = EnumSet.of(VisitorReward.OVERGROWN_GRASS, VisitorReward.GREEN_BANDANA, VisitorReward.DEDICATION, VisitorReward.MUSIC_RUNE,
                VisitorReward.SPACE_HELMET, VisitorReward.CULTIVATING, VisitorReward.REPLENISH, VisitorReward.COPPER_DYE, VisitorReward.FARMING_EXP_BOOST_EPIC,
                VisitorReward.DYE_WILD_STRAWBERRY);
        Set<VisitorReward> byDefault = EnumSet.noneOf(VisitorReward.class);
        for (VisitorReward reward : VisitorReward.values()) {
            if (reward.byDefault()) byDefault.add(reward);
        }
        assertEquals(expected, byDefault);
    }

    @Test
    void theConfigStartsWithTheDefaultsChosen() {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        for (VisitorReward reward : VisitorReward.values()) {
            assertEquals(reward.byDefault(), reward.chosen(c), reward.name());
        }
    }

    @Test
    void eachConfigSwitchBelongsToExactlyOneReward() throws Exception {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        Set<VisitorReward> reached = EnumSet.noneOf(VisitorReward.class);
        int fields = 0;
        for (Field field : ModConfig.GardenRewardWarningCategory.class.getDeclaredFields()) {
            if (!field.getName().startsWith("reward")) continue;
            fields++;
            // Alle aus, nur diesen an: genau eine Belohnung ist gewaehlt
            for (Field other : ModConfig.GardenRewardWarningCategory.class.getDeclaredFields()) {
                if (other.getName().startsWith("reward")) other.setBoolean(c, false);
            }
            field.setBoolean(c, true);
            Set<VisitorReward> chosen = EnumSet.noneOf(VisitorReward.class);
            for (VisitorReward reward : VisitorReward.values()) {
                if (reward.chosen(c)) chosen.add(reward);
            }
            assertEquals(1, chosen.size(), field.getName());
            reached.addAll(chosen);
        }
        assertEquals(VisitorReward.values().length, fields, "ein Schalter je Belohnung");
        assertEquals(EnumSet.allOf(VisitorReward.class), reached, "jede Belohnung hat einen Schalter");
        assertFalse(reached.isEmpty());
    }
}
