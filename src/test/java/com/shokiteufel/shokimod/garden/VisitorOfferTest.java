package com.shokiteufel.shokimod.garden;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Angebot eines Besuchers aus der Beschreibung des Knopfes "Accept Offer": Gegenstandszeilen (SkyHannis REGEX-TEST-Beispiele und die Schreibweisen
 * des Spiels), die Liste der verlangten Gegenstaende, die Belohnungen, Copper und Garden Experience, "Click to give!" und das Schild des Besuchers.
 */
class VisitorOfferTest {

    @Test
    void anAmountBehindTheName() {
        assertEquals(new VisitorOffer.ItemAmount("Enchanted Carrot", 3), VisitorOffer.parseItem("Enchanted Carrot x3"));
        assertEquals(new VisitorOffer.ItemAmount("Enchanted Cocoa Bean", 1280), VisitorOffer.parseItem("Enchanted Cocoa Bean x1,280"));
        // Mit den Farbcodes, wie das Spiel die Zeile schreibt
        assertEquals(new VisitorOffer.ItemAmount("Enchanted Carrot", 3), VisitorOffer.parseItem("§aEnchanted Carrot §8x3"));
    }

    @Test
    void anAmountInFrontOfTheName() {
        // REGEX-TEST: 8x Enchanted Pork
        assertEquals(new VisitorOffer.ItemAmount("Enchanted Pork", 8), VisitorOffer.parseItem("8x Enchanted Pork"));
        // WRAPPED-REGEX-TEST: "  §810x §r§bGlacite Jewel"
        assertEquals(new VisitorOffer.ItemAmount("Glacite Jewel", 10), VisitorOffer.parseItem("  §810x §r§bGlacite Jewel"));
        assertEquals(new VisitorOffer.ItemAmount("Glacite Jewel", 10), VisitorOffer.parseItem("  10x Glacite Jewel"));
        assertEquals(new VisitorOffer.ItemAmount("Wheat", 1500), VisitorOffer.parseItem("1.5k Wheat"));
        assertEquals(new VisitorOffer.ItemAmount("Gold Ingot", 2_000_000), VisitorOffer.parseItem("2m Gold Ingot"));
    }

    @Test
    void noAmountMeansOne() {
        assertEquals(new VisitorOffer.ItemAmount("Dedication IV", 1), VisitorOffer.parseItem("Dedication IV"));
        assertEquals(new VisitorOffer.ItemAmount("Overgrown Grass", 1), VisitorOffer.parseItem("§9Overgrown Grass"));
    }

    @Test
    void aBookLineIsTheEnchantment() {
        // SkyHannis Umweg fuer 'Turbo Cacti I Book'
        assertEquals(new VisitorOffer.ItemAmount("Turbo-Cacti I", 1), VisitorOffer.parseItem("Turbo-Cacti I Book"));
    }

    @Test
    void anEmptyLineIsNoItem() {
        assertNull(VisitorOffer.parseItem(""));
        assertNull(VisitorOffer.parseItem("   "));
        assertNull(VisitorOffer.parseItem(null));
    }

    @Test
    void theOfferIsReadSectionBySection() {
        List<String> lore = List.of("Items Required:", "Enchanted Carrot x3", "Cocoa Beans x64", "", "Rewards:", "+20 Copper", "+15 Garden Experience",
                "Overgrown Grass", "", "Click to give!");
        VisitorOffer.Parsed offer = VisitorOffer.parse(lore);
        assertEquals(Map.of("Enchanted Carrot", 3, "Cocoa Beans", 64), offer.required());
        assertEquals(List.of("Enchanted Carrot", "Cocoa Beans"), List.copyOf(offer.required().keySet()), "in der Reihenfolge der Beschreibung");
        assertEquals(List.of(new VisitorOffer.ItemAmount("Overgrown Grass", 1)), offer.rewards(), "Copper und Garden Experience sind keine Gegenstaende");
        assertEquals(20, offer.copper());
        assertEquals(15, offer.gardenExp());
        assertTrue(offer.hasCopper());
        assertTrue(offer.readyToGive());
    }

    @Test
    void withoutClickToGiveTheOfferIsNotReady() {
        VisitorOffer.Parsed offer = VisitorOffer.parse(List.of("Items Required:", "Wheat x10", "", "Rewards:", "+5 Copper", "", "You do not have the items!"));
        assertFalse(offer.readyToGive());
    }

    @Test
    void aCharmedVisitorsCopperLineHasAHeartBehindIt() {
        // WRAPPED-REGEX-TEST: " §8+§c150 Copper §d❤" - und das Herz steht auch hinter den Gegenstaenden
        VisitorOffer.Parsed offer = VisitorOffer.parse(List.of("Items Required:", "Wheat x10", "", "Rewards:", "+150 Copper ❤", "Overgrown Grass ❤"));
        assertEquals(150, offer.copper());
        assertEquals(List.of(new VisitorOffer.ItemAmount("Overgrown Grass", 1)), offer.rewards());
    }

    @Test
    void anOfferWithoutCopperHasNone() {
        VisitorOffer.Parsed offer = VisitorOffer.parse(List.of("Items Required:", "Wheat x10", "", "Rewards:", "+15 Garden Experience"));
        assertFalse(offer.hasCopper());
        assertEquals(-1, offer.copper());
        assertEquals(15, offer.gardenExp());
    }

    @Test
    void nothingToReadGivesAnEmptyOffer() {
        VisitorOffer.Parsed offer = VisitorOffer.parse(null);
        assertTrue(offer.required().isEmpty());
        assertTrue(offer.rewards().isEmpty());
        assertFalse(offer.hasCopper());
        assertFalse(VisitorOffer.parse(List.of()).readyToGive());
    }

    @Test
    void theVisitorSignHasFourLinesTheLastOneOffersAccepted() {
        List<String> sign = List.of("Emissary Carlton", "Rarity: UNCOMMON", "", "Offers Accepted: 12");
        assertTrue(VisitorOffer.isInfoLore(sign));
        assertEquals(12, VisitorOffer.offersAccepted(sign));
        assertTrue(VisitorOffer.isInfoLore(List.of("a", "b", "c", "§7Offers Accepted: §a0")));
        assertEquals(0, VisitorOffer.offersAccepted(List.of("a", "b", "c", "§7Offers Accepted: §a0")));
        assertFalse(VisitorOffer.isInfoLore(List.of("a", "b", "Offers Accepted: 12")), "drei Zeilen sind kein Schild");
        assertFalse(VisitorOffer.isInfoLore(List.of("a", "b", "c", "something else")));
        assertFalse(VisitorOffer.isInfoLore(null));
        assertEquals(-1, VisitorOffer.offersAccepted(List.of("a", "b", "c", "d")));
    }
}
