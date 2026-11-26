package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Das Buch der Besucher: Namen aus der Tab-Liste, Angebote aus dem Fenster, Preise (Kaufpreis der Gegenstaende, Wert des Copper), die Summe der Einkaufsliste.
 */
class VisitorBookTest {

    private static final Map<String, Double> PRICES = Map.of("Enchanted Carrot", 1000.0, "Enchanted Cocoa Bean", 2500.0, "Overgrown Grass", 500.0);
    private static final ToDoubleFunction<String> PRICE = name -> PRICES.getOrDefault(name, -1.0);
    private static final List<String> LORE = List.of("Items Required:", "Enchanted Carrot x3", "", "Rewards:", "+20 Copper", "Overgrown Grass", "", "Click to give!");

    private final ModConfig.GardenRewardWarningCategory chosen = new ModConfig.GardenRewardWarningCategory();

    private List<VisitorReward> apply(VisitorBook.Entry entry, List<String> lore, double copperValue) {
        return entry.applyOffer(lore, VisitorOffer.parse(lore), PRICE, copperValue, chosen);
    }

    // ---- die Tab-Liste ----

    @Test
    void namesFromTheTabListComeInAsNewVisitors() {
        VisitorBook book = new VisitorBook();
        assertTrue(book.sync(List.of("Jacob", "Spaceman"), true));
        assertEquals(2, book.size());
        for (VisitorBook.Entry e : book.entries()) {
            assertEquals(VisitorBook.Status.NEW, e.status());
            assertFalse(e.known(), "sein Fenster war noch nicht offen");
        }
        assertFalse(book.sync(List.of("Jacob", "Spaceman"), true), "nichts Neues");
    }

    @Test
    void aNameMissingFromACompleteListIsGone() {
        VisitorBook book = new VisitorBook();
        book.sync(List.of("Jacob", "Spaceman"), true);
        assertTrue(book.sync(List.of("Jacob"), true));
        assertEquals(1, book.size());
        assertNull(book.get("Spaceman"));
    }

    @Test
    void anIncompleteListNeverRemovesAnybody() {
        VisitorBook book = new VisitorBook();
        book.sync(List.of("Jacob", "Spaceman"), true);
        // Die Liste steht mitten im Aufbau: nur einer ist schon da
        assertFalse(book.sync(List.of("Jacob"), false));
        assertEquals(2, book.size(), "was man ueber Spaceman gelesen hat, geht nicht mit einem halben Bild verloren");
        assertTrue(book.sync(List.of("Jacob", "Anita"), false), "neue Namen kommen dagegen sofort dazu");
        assertEquals(3, book.size());
    }

    // ---- das Angebot ----

    @Test
    void anOfferFillsTheEntryAndPricesIt() {
        VisitorBook book = new VisitorBook();
        VisitorBook.Entry jacob = book.getOrCreate("Jacob");
        apply(jacob, LORE, 5.0);
        assertTrue(jacob.known());
        assertEquals(Map.of("Enchanted Carrot", 3), jacob.required());
        assertEquals(VisitorBook.Status.READY, jacob.status(), "Click to give! steht da");
        // 3 mal 1000 minus das Gras zu 500: 2500; 20 Copper: 125 je Copper; ein Copper ist 5 Coins wert
        assertEquals(2500.0, jacob.totalPrice());
        assertTrue(jacob.hasCopper());
        assertEquals(125, jacob.pricePerCopper());
        assertEquals(100.0, jacob.totalReward());
        assertEquals(List.of("Overgrown Grass"), jacob.rewardNames());
        assertTrue(jacob.pricesKnown());
    }

    @Test
    void withoutClickToGiveTheVisitorIsWaiting() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        apply(e, List.of("Items Required:", "Enchanted Carrot x3", "", "Rewards:", "+20 Copper"), 5.0);
        assertEquals(VisitorBook.Status.WAITING, e.status());
    }

    @Test
    void theSameLoreTwiceChangesNothingAndAnnouncesNothing() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        assertEquals(List.of(VisitorReward.OVERGROWN_GRASS), apply(e, LORE, 5.0));
        e.status(VisitorBook.Status.ACCEPTED);
        assertTrue(apply(e, LORE, 5.0).isEmpty());
        assertEquals(VisitorBook.Status.ACCEPTED, e.status(), "eine unveraenderte Beschreibung fasst den Stand nicht an");
    }

    @Test
    void anAcceptedVisitorStaysAcceptedWhenTheWindowIsReadAgain() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        apply(e, LORE, 5.0);
        e.status(VisitorBook.Status.ACCEPTED);
        apply(e, List.of("Items Required:", "Enchanted Carrot x4", "", "Rewards:", "+20 Copper"), 5.0);
        assertEquals(VisitorBook.Status.ACCEPTED, e.status(), "ein spaeteres Lesen macht ihn nicht wieder zum Wartenden");
        assertEquals(Map.of("Enchanted Carrot", 4), e.required(), "gelesen wird trotzdem");
        e.status(VisitorBook.Status.REFUSED);
        apply(e, List.of("Items Required:", "Enchanted Carrot x5"), 5.0);
        assertEquals(VisitorBook.Status.REFUSED, e.status());
    }

    @Test
    void aRewardIsAnnouncedOnceWhenItFirstShowsUp() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        assertEquals(List.of(VisitorReward.OVERGROWN_GRASS), apply(e, LORE, 5.0));
        // Spaeter eine andere Beschreibung (das "Click to give!" fiel weg): nicht noch einmal melden
        assertTrue(apply(e, List.of("Items Required:", "Enchanted Carrot x3", "", "Rewards:", "+20 Copper", "Overgrown Grass"), 5.0).isEmpty());
        assertEquals(List.of(VisitorReward.OVERGROWN_GRASS), e.warned(), "die Warnung bleibt am Besucher");
    }

    @Test
    void aRewardNotChosenIsNotAnnounced() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        List<String> lore = List.of("Items Required:", "Enchanted Carrot x3", "", "Rewards:", "+20 Copper", "Delicate V");
        assertTrue(apply(e, lore, 5.0).isEmpty());
        assertTrue(e.warned().isEmpty());
        assertEquals(List.of("Delicate V"), e.rewardNames());
    }

    @Test
    void anUnknownPriceMeansTheOfferCannotBeJudgedByPrice() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        // Der Kakao hat keinen Preis
        apply(e, List.of("Items Required:", "Enchanted Carrot x3", "Mystery Item x1", "", "Rewards:", "+20 Copper"), 5.0);
        assertFalse(e.pricesKnown());
        assertFalse(e.facts().hasCopper(), "ohne alle Preise keine Preisregel");
        VisitorDecision.Rules all = new VisitorDecision.Rules(false, false, true, true, true, true, 6000, 150_000, false);
        e.decide(all);
        assertNull(e.block());
    }

    @Test
    void anUnknownCopperValueMeansTheSameAndSoDoesNoCopperAtAll() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        apply(e, LORE, -1.0);
        assertFalse(e.pricesKnown());
        VisitorBook.Entry none = new VisitorBook().getOrCreate("Anita");
        apply(none, List.of("Items Required:", "Enchanted Carrot x3", "", "Rewards:", "+15 Garden Experience"), 5.0);
        assertFalse(none.hasCopper());
        assertFalse(none.facts().hasCopper());
    }

    @Test
    void thePriceOfTheOfferIsNeverBelowZero() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        // Die Belohnung ist mehr wert als das Verlangte
        apply(e, List.of("Items Required:", "Overgrown Grass x1", "", "Rewards:", "+20 Copper", "Enchanted Cocoa Bean x4"), 5.0);
        assertEquals(0.0, e.totalPrice());
        assertEquals(0, e.pricePerCopper());
    }

    @Test
    void theDecisionFollowsTheRulesEvenWhenTheyChangeLater() {
        VisitorBook.Entry e = new VisitorBook().getOrCreate("Jacob");
        apply(e, LORE, 5.0);
        e.offersAccepted(0);
        assertNull(e.block(), "noch nicht entschieden");
        VisitorDecision.Rules fresh = new VisitorDecision.Rules(false, true, false, false, false, false, 6000, 150_000, false);
        e.decide(fresh);
        assertEquals(VisitorDecision.BlockReason.NEVER_ACCEPTED, e.block());
        e.decide(new VisitorDecision.Rules(true, true, false, false, false, false, 6000, 150_000, false));
        assertEquals(VisitorDecision.BlockReason.RARE_REWARD, e.block(), "der Besucher hat eine gewaehlte Belohnung");
        e.decide(new VisitorDecision.Rules(false, false, false, false, false, false, 6000, 150_000, false));
        assertNull(e.block());
    }

    // ---- die Summe der Einkaufsliste ----

    @Test
    void theShoppingListAddsUpWhatTheVisitorsAskFor() {
        VisitorBook book = new VisitorBook();
        apply(book.getOrCreate("Jacob"), List.of("Items Required:", "Enchanted Carrot x3", "Enchanted Cocoa Bean x2"), 5.0);
        apply(book.getOrCreate("Spaceman"), List.of("Items Required:", "Enchanted Carrot x5"), 5.0);
        book.getOrCreate("Anita");
        assertEquals(Map.of("Enchanted Carrot", 8, "Enchanted Cocoa Bean", 2), book.shoppingTotals());
        assertEquals(3, book.activeCount());
    }

    @Test
    void anAcceptedOrRefusedVisitorIsNoLongerCounted() {
        VisitorBook book = new VisitorBook();
        VisitorBook.Entry jacob = book.getOrCreate("Jacob");
        VisitorBook.Entry spaceman = book.getOrCreate("Spaceman");
        apply(jacob, List.of("Items Required:", "Enchanted Carrot x3"), 5.0);
        apply(spaceman, List.of("Items Required:", "Enchanted Carrot x5"), 5.0);
        jacob.status(VisitorBook.Status.ACCEPTED);
        assertEquals(Map.of("Enchanted Carrot", 5), book.shoppingTotals());
        spaceman.status(VisitorBook.Status.REFUSED);
        assertTrue(book.shoppingTotals().isEmpty());
        assertEquals(0, book.activeCount());
    }

    @Test
    void anIgnoredVisitorIsLeftOutOfTheSumAndStaysIgnoredWhenHeComesBack() {
        VisitorBook book = new VisitorBook();
        VisitorBook.Entry jacob = book.getOrCreate("Jacob");
        apply(jacob, List.of("Items Required:", "Enchanted Carrot x3"), 5.0);
        book.toggleIgnored("Jacob");
        assertTrue(jacob.ignored());
        assertTrue(book.shoppingTotals().isEmpty());
        // Er geht und kommt wieder (neuer Eintrag)
        book.sync(List.of(), true);
        assertNull(book.get("Jacob"));
        assertTrue(book.getOrCreate("Jacob").ignored(), "die Wahl gilt fuer den Namen");
        book.toggleIgnored("Jacob");
        assertFalse(book.get("Jacob").ignored());
        // Auch ein Besucher, der noch nicht im Buch steht, laesst sich vormerken
        book.toggleIgnored("Anita");
        assertTrue(book.getOrCreate("Anita").ignored());
    }

    @Test
    void clearEmptiesTheBook() {
        VisitorBook book = new VisitorBook();
        book.getOrCreate("Jacob");
        book.clear();
        assertEquals(0, book.size());
        assertTrue(book.entries().isEmpty());
    }
}
