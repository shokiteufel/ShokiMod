package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seit 2.5.15: "Visitors' Gratitude" sperrte nichts, obwohl die Belohnung und das Sperren an waren (Meldung des Users mit einem Bild von "Accept Offer"). Zwei Dinge
 * kamen in Frage und sind beide behoben: Das Herz hinter der Belohnung ("Visitors' Gratitude ♥") wurde nur als U+2764 erkannt, nicht als U+2665 oder mit
 * Varianten-Zeichen; und wer die Belohnung erst nach dem ersten Lesen des Besuchers einschaltete, wartete, bis sich der Text aenderte.
 */
class VisitorRewardHeartTest {

    private static final String HEART_BLACK_HEART = "❤";
    private static final String HEART_SUIT = "♥";
    private static final String HEART_WITH_VARIANT = "❤️";

    /** Der Text von "Accept Offer" auf dem Bild des Users, ohne Farbcodes; {@code heart} steht hinter den drei Belohnungen mit Herz */
    private static List<String> lore(String heart) {
        return List.of("Items Required:", "Enchanted Baked Potato x9", "", "Rewards:", "+68.5k Farming XP", "+75 Garden Experience", "+476 Copper " + heart,
                "+10 Bits", "Visitors' Gratitude " + heart, "Hypercharge Chip " + heart, "", "Missing items to accept!");
    }

    private static final double NO_PRICE = -1.0;

    // ---- das Zeichen ----

    @Test
    void aHeartInAnySpellingIsNotPartOfTheRewardName() {
        for (String heart : List.of(HEART_BLACK_HEART, HEART_SUIT, HEART_WITH_VARIANT, "♥️", "♡", "❣")) {
            assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("Visitors' Gratitude " + heart), "Herz U+" + Integer.toHexString(heart.codePointAt(0)));
            assertSame(VisitorReward.HYPERCHARGE_CHIP, VisitorReward.byName("Hypercharge Chip " + heart));
            assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("Visitors' Gratitude" + heart), "auch ohne Leerzeichen davor");
        }
    }

    @Test
    void colourCodesProtectedSpacesAndSeveralMarkersDoNotDisturb() {
        assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("§fVisitors' Gratitude §d" + HEART_SUIT));
        assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("Visitors' Gratitude " + HEART_SUIT + " "));
        assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("Visitors' Gratitude " + HEART_SUIT + " " + HEART_BLACK_HEART));
        assertSame(VisitorReward.VISITORS_GRATITUDE, VisitorReward.byName("Visitors’ Gratitude " + HEART_SUIT), "das typographische Hochkomma gilt weiter");
    }

    @Test
    void realNamesAreLeftAlone() {
        assertSame(VisitorReward.DEDICATION, VisitorReward.byName("Dedication IV"));
        assertSame(VisitorReward.HARVEST_HARBINGER, VisitorReward.byName("Harvest Harbinger V"));
        assertSame(VisitorReward.VOTER_BADGE, VisitorReward.byName("Voter's Badge"));
        assertSame(VisitorReward.OVERGROWN_GRASS, VisitorReward.byName("Overgrown Grass"));
        assertNull(VisitorReward.byName("Enchanted Carrot " + HEART_SUIT), "ein Herz macht keinen Namen zur Belohnung");
    }

    @Test
    void stripMarkerOnlyTakesSymbolsFromTheEnd() {
        assertEquals("Click to give!", VisitorOffer.stripMarker("Click to give!"));
        assertEquals("Rewards:", VisitorOffer.stripMarker("Rewards:"));
        assertEquals("Items Required:", VisitorOffer.stripMarker("Items Required:"));
        assertEquals("+476 Copper", VisitorOffer.stripMarker("+476 Copper " + HEART_SUIT));
        assertEquals("Enchanted Baked Potato x9", VisitorOffer.stripMarker("Enchanted Baked Potato x9"));
        assertEquals("", VisitorOffer.stripMarker(HEART_SUIT));
        assertEquals("", VisitorOffer.stripMarker(""));
        assertEquals("", VisitorOffer.stripMarker(null));
        // Ein Herz mitten im Text bleibt (nur das Ende zaehlt)
        assertEquals("A " + HEART_SUIT + " B", VisitorOffer.stripMarker("A " + HEART_SUIT + " B"));
    }

    // ---- das Angebot aus dem Bild ----

    @Test
    void theOfferOfTheScreenshotIsReadWithEitherHeart() {
        for (String heart : List.of(HEART_BLACK_HEART, HEART_SUIT, HEART_WITH_VARIANT)) {
            VisitorOffer.Parsed offer = VisitorOffer.parse(lore(heart));
            assertEquals(java.util.Map.of("Enchanted Baked Potato", 9), offer.required());
            assertEquals(476, offer.copper(), "das Herz hinter Copper stoert nicht");
            assertEquals(75, offer.gardenExp());
            assertEquals(false, offer.readyToGive(), "es fehlen Gegenstaende");
            List<String> names = offer.rewards().stream().map(VisitorOffer.ItemAmount::name).toList();
            assertTrue(names.contains("Visitors' Gratitude"), names.toString());
            assertTrue(names.contains("Hypercharge Chip"), names.toString());
            assertTrue(names.stream().noneMatch(n -> n.contains(HEART_SUIT) || n.contains(HEART_BLACK_HEART)), "kein Herz im Namen: " + names);
        }
    }

    @Test
    void bothRewardsOfTheScreenshotAreWarnedWhenChosen() {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        c.rewardVisitorsGratitude = true;
        c.rewardHyperchargeChip = true;
        for (String heart : List.of(HEART_BLACK_HEART, HEART_SUIT, HEART_WITH_VARIANT)) {
            VisitorOffer.Parsed offer = VisitorOffer.parse(lore(heart));
            List<String> names = offer.rewards().stream().map(VisitorOffer.ItemAmount::name).toList();
            assertEquals(List.of(VisitorReward.VISITORS_GRATITUDE, VisitorReward.HYPERCHARGE_CHIP), VisitorDecision.warnedRewards(names, c));
        }
    }

    // ---- die Auswahl erst nach dem Lesen ----

    @Test
    void aRewardSwitchedOnAfterTheFirstReadingCountsAtTheNextOne() {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        c.preventRefusing = true;
        VisitorBook.Entry entry = new VisitorBook().getOrCreate("Jacob");
        List<String> lore = lore(HEART_SUIT);

        // Erste Lesung: die Belohnung ist nicht gewaehlt, nichts wird gesperrt
        entry.applyOffer(lore, VisitorOffer.parse(lore), name -> NO_PRICE, 5.0, c);
        entry.decide(VisitorDecision.rulesOf(c));
        assertTrue(entry.warned().isEmpty());
        assertNull(entry.block());

        // Jetzt schaltet der Spieler sie ein; derselbe Text, dasselbe Fenster
        c.rewardVisitorsGratitude = true;
        List<VisitorReward> fresh = entry.applyOffer(lore, VisitorOffer.parse(lore), name -> NO_PRICE, 5.0, c);
        entry.decide(VisitorDecision.rulesOf(c));
        assertEquals(List.of(VisitorReward.VISITORS_GRATITUDE), entry.warned(), "die neue Auswahl gilt, auch wenn der Text derselbe ist");
        assertTrue(fresh.isEmpty(), "gemeldet wird dabei nichts Neues");
        assertEquals(VisitorDecision.BlockReason.RARE_REWARD, entry.block(), "das Ablehnen ist gesperrt");

        // Und wieder aus: die Sperre geht mit
        c.rewardVisitorsGratitude = false;
        entry.applyOffer(lore, VisitorOffer.parse(lore), name -> NO_PRICE, 5.0, c);
        entry.decide(VisitorDecision.rulesOf(c));
        assertTrue(entry.warned().isEmpty());
        assertNull(entry.block());
    }

    @Test
    void aSecondChosenRewardIsAddedToTheWarnedOnesOfTheSameOffer() {
        ModConfig.GardenRewardWarningCategory c = new ModConfig.GardenRewardWarningCategory();
        c.rewardVisitorsGratitude = true;
        VisitorBook.Entry entry = new VisitorBook().getOrCreate("Jacob");
        List<String> lore = lore(HEART_SUIT);
        entry.applyOffer(lore, VisitorOffer.parse(lore), name -> NO_PRICE, 5.0, c);
        assertEquals(List.of(VisitorReward.VISITORS_GRATITUDE), entry.warned());
        c.rewardHyperchargeChip = true;
        entry.applyOffer(lore, VisitorOffer.parse(lore), name -> NO_PRICE, 5.0, c);
        assertEquals(List.of(VisitorReward.VISITORS_GRATITUDE, VisitorReward.HYPERCHARGE_CHIP), entry.warned());
    }
}
