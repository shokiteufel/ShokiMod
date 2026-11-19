package com.shokiteufel.shokimod.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meldung des Users (19.11.): Seit dem neuen Personal Compactor zaehlt der Profit Tracker dieselbe Kohle zweimal - als Coal x835k und als
 * Enchanted Coal x4.441. Die Zeilen "COMPACT! You found an Enchanted Coal!" gehoeren NICHT zum Compactor, sondern zur Verzauberung Compact der
 * Spitzhacke (kleine Chance auf die Enchanted-Form des abgebauten Stuecks): ein echter Fund, ein Stueck je Zeile. Die Zahlen sind aus seinem
 * latest.log vom 19.11.
 */
class VerdichtungTest {

    private static final long S = 1_000_000_000L;

    @Test
    void readsTheLineOfTheEnchantment() {
        assertEquals("Enchanted Hard Stone", Verdichtung.lesen("COMPACT! You found an Enchanted Hard Stone!"));
        assertEquals("Enchanted Coal", Verdichtung.lesen("COMPACT! You found an Enchanted Coal!"));
        assertEquals("Enchanted Iron Ingot", Verdichtung.lesen("COMPACT! You found an Enchanted Iron Ingot!"));
        assertEquals("Enchanted Lapis Lazuli", Verdichtung.lesen("COMPACT! You found an Enchanted Lapis Lazuli!"));
    }

    @Test
    void readsItWithTheCounterAChatModAppends() {
        assertEquals("Enchanted Hard Stone", Verdichtung.lesen("COMPACT! You found an Enchanted Hard Stone! (2)"));
        assertEquals("Enchanted Hard Stone", Verdichtung.lesen("COMPACT! You found an Enchanted Hard Stone! (1828)"));
    }

    @Test
    void readsAnItemWithoutAnArticle() {
        assertEquals("Refined Mineral", Verdichtung.lesen("COMPACT! You found a Refined Mineral!"));
        assertEquals("Enchanted Coal", Verdichtung.lesen("COMPACT! You found Enchanted Coal!"));
    }

    @Test
    void otherLinesAreNoCompactLines() {
        assertNull(Verdichtung.lesen("You found an Enchanted Coal!"), "ohne Etikett");
        assertNull(Verdichtung.lesen("COMPACT!"));
        assertNull(Verdichtung.lesen("COMPACT! You found !"));
        assertNull(Verdichtung.lesen("RARE DROP! Hyperion (+500% Magic Find)"));
        assertNull(Verdichtung.lesen("[Sacks] +80,959 items, -33,888 items. (Last 30s.)"));
        assertNull(Verdichtung.lesen("Party > [MVP+] Anna: COMPACT! You found an Enchanted Coal!"), "Zeilen anderer Kanaele haben ein Praefix");
        assertNull(Verdichtung.lesen(""));
        assertNull(Verdichtung.lesen(null));
    }

    private static Verdichtung.Eintrag zeile(String id, long sekunden) {
        return new Verdichtung.Eintrag(List.of(id), sekunden * S);
    }

    @Test
    void countsOnePiecePerLineInTheWindow() {
        List<Verdichtung.Eintrag> e = List.of(zeile("ENCHANTED_HARD_STONE", 85), zeile("ENCHANTED_HARD_STONE", 90), zeile("ENCHANTED_HARD_STONE", 99),
                zeile("ENCHANTED_COAL", 91));
        assertEquals(3, Verdichtung.funde(e, List.of("ENCHANTED_HARD_STONE"), 70 * S, 103 * S));
        assertEquals(1, Verdichtung.funde(e, List.of("ENCHANTED_COAL"), 70 * S, 103 * S));
        assertEquals(4, Verdichtung.funde(e, List.of("ENCHANTED_COAL", "ENCHANTED_HARD_STONE"), 70 * S, 103 * S), "eine Zeile zaehlt nur einmal, auch mit zwei passenden Kennungen");
    }

    @Test
    void aLineOutsideTheWindowOrOfAnotherItemCountsNothing() {
        List<Verdichtung.Eintrag> e = List.of(zeile("ENCHANTED_COAL", 85));
        assertEquals(0, Verdichtung.funde(e, List.of("ENCHANTED_COAL"), 90 * S, 103 * S), "die Zeile kam vor dem Fenster");
        assertEquals(0, Verdichtung.funde(e, List.of("ENCHANTED_COAL"), 60 * S, 80 * S), "die Zeile kam nach dem Fenster");
        assertEquals(0, Verdichtung.funde(e, List.of("ENCHANTED_HARD_STONE"), 70 * S, 103 * S), "eine andere Sorte");
        assertEquals(0, Verdichtung.funde(e, List.of("COAL"), 70 * S, 103 * S), "die Rohware ist kein Stueck der Verzauberung");
    }

    @Test
    void theEdgesOfTheWindowCount() {
        List<Verdichtung.Eintrag> e = List.of(zeile("ENCHANTED_COAL", 70));
        assertEquals(1, Verdichtung.funde(e, List.of("ENCHANTED_COAL"), 70 * S, 103 * S));
        assertEquals(1, Verdichtung.funde(e, List.of("ENCHANTED_COAL"), 40 * S, 70 * S));
    }

    @Test
    void emptyInputsCountNothing() {
        assertEquals(0, Verdichtung.funde(List.of(), List.of("ENCHANTED_COAL"), 0, 100 * S));
        assertEquals(0, Verdichtung.funde(null, List.of("ENCHANTED_COAL"), 0, 100 * S));
        assertEquals(0, Verdichtung.funde(List.of(zeile("ENCHANTED_COAL", 1)), List.of(), 0, 100 * S));
        assertEquals(0, Verdichtung.funde(List.of(zeile("ENCHANTED_COAL", 1)), null, 0, 100 * S));
    }

    private static List<Verdichtung.Entnommen> minus(Object... paare) {
        java.util.ArrayList<Verdichtung.Entnommen> aus = new java.util.ArrayList<>();
        for (int i = 0; i < paare.length; i += 2) aus.add(new Verdichtung.Entnommen((String) paare[i], (Integer) paare[i + 1]));
        return aus;
    }

    @Test
    void theRawItemsInTheMinusMakeTheEnchantedPiecesACompaction() {
        // Aus dem Log vom 19.11., 21:07:56: +1.825 Enchanted Redstone ohne eine COMPACT!-Zeile, im Minus 292.000 Redstone
        assertEquals(1825, Verdichtung.aus("Enchanted Redstone", 1825, 0, minus("Redstone", 292_000, "Coal", 32_000)));
        // 21:02:44: 191 Enchanted Coal und 75 Enchanted Hard Stone, im Minus Coal und Hard Stone
        assertEquals(191, Verdichtung.aus("Enchanted Coal", 191, 0, minus("Coal", 30_560, "Hard Stone", 37_696)));
        assertEquals(75, Verdichtung.aus("Enchanted Hard Stone", 75, 0, minus("Coal", 30_560, "Hard Stone", 37_696)));
    }

    @Test
    void theCompactFindsOfTheEnchantmentStayFinds() {
        // Von 75 Enchanted Hard Stone kamen 9 als COMPACT!-Zeile (Verzauberung, echter Fund): verdichtet sind 66
        assertEquals(66, Verdichtung.aus("Enchanted Hard Stone", 75, 9, minus("Hard Stone", 38_016)));
        // Kamen alle als Zeile, ist nichts verdichtet
        assertEquals(0, Verdichtung.aus("Enchanted Iron", 3, 3, minus("Iron Ingot", 40_000)));
        // Mehr Zeilen als Plus (Fensterrand): nie unter null
        assertEquals(0, Verdichtung.aus("Enchanted Iron", 3, 5, minus("Iron Ingot", 40_000)));
    }

    @Test
    void ingotsAndDustAreMatchedByTheirName() {
        assertEquals(4, Verdichtung.aus("Enchanted Iron", 4, 0, minus("Iron Ingot", 640)));
        assertEquals(3, Verdichtung.aus("Enchanted Gold", 3, 0, minus("Gold Ingot", 480)));
        assertEquals(1, Verdichtung.aus("Enchanted Redstone Dust", 1, 0, minus("Redstone", 160)));
        assertEquals(1, Verdichtung.aus("Enchanted Lapis Lazuli", 1, 0, minus("Lapis Lazuli", 160)));
    }

    @Test
    void withoutTheRawItemInTheMinusNothingIsACompaction() {
        assertEquals(0, Verdichtung.aus("Enchanted Coal", 3, 0, minus()), "kein Minus: ein echter Fund");
        assertEquals(0, Verdichtung.aus("Enchanted Coal", 3, 0, minus("Hard Stone", 40_000)), "eine andere Rohware");
        assertEquals(0, Verdichtung.aus("Enchanted Coal", 3, 0, null));
        assertEquals(0, Verdichtung.aus("Coal", 835_000, 0, minus("Coal", 710_560)), "die Rohware selbst bleibt ein Fund");
        assertEquals(0, Verdichtung.aus("Hyperion", 1, 0, minus("Coal", 710_560)));
        assertEquals(0, Verdichtung.aus("Enchanted Coal", 0, 0, minus("Coal", 710_560)));
    }

    @Test
    void aSmallMinusOnlyCoversWhatItCanHold() {
        // 100 Coal reichen hoechstens fuer ein paar Stueck: von zehn Enchanted Coal gelten nur fuenf als verdichtet, der Rest bleibt ein Fund
        assertEquals(5, Verdichtung.aus("Enchanted Coal", 10, 0, minus("Coal", 100)));
    }

    @Test
    void theRawItemsOfTheCompactedPiecesComeOffTheTracker() {
        // 191 Enchanted Coal verdichtet; im Minus 30.560 Coal und 37.696 Hard Stone: nur die Coal gehen fuer die Enchanted Coal ab
        List<Verdichtung.Entnommen> entfernt = minus("Coal", 30_560, "Hard Stone", 37_696);
        int[] uebrig = {30_560, 37_696};
        List<Verdichtung.Entnommen> roh = Verdichtung.rohwareFuer("Enchanted Coal", 191, uebrig, entfernt);
        assertEquals(List.of(new Verdichtung.Entnommen("Coal", 30_560)), roh);
        assertEquals(0, uebrig[0], "die Entnahme ist zugeteilt");
        assertEquals(37_696, uebrig[1], "die andere bleibt unberuehrt");
    }

    @Test
    void theRawItemsAreCappedByTheBiggestRecipe() {
        // Nimmt der Spieler nebenbei 100.000 Coal aus dem Sack, gehen fuer drei verdichtete Stuecke hoechstens 3 mal 576 ab
        int[] uebrig = {100_000};
        List<Verdichtung.Entnommen> roh = Verdichtung.rohwareFuer("Enchanted Coal", 3, uebrig, minus("Coal", 100_000));
        assertEquals(List.of(new Verdichtung.Entnommen("Coal", 1_728)), roh);
        assertEquals(100_000 - 1_728, uebrig[0]);
    }

    @Test
    void twoPiecesShareOneWithdrawalOnlyOnce() {
        // Enchanted Coal und ein zweiter Posten derselben Rohware: die Entnahme reicht nicht fuer beide ganz
        int[] uebrig = {1_000};
        List<Verdichtung.Entnommen> erster = Verdichtung.rohwareFuer("Enchanted Coal", 1, uebrig, minus("Coal", 1_000));
        List<Verdichtung.Entnommen> zweiter = Verdichtung.rohwareFuer("Enchanted Coal", 1, uebrig, minus("Coal", 1_000));
        assertEquals(576, erster.get(0).menge());
        assertEquals(424, zweiter.get(0).menge(), "nur der Rest");
        assertEquals(0, uebrig[0]);
    }

    @Test
    void nothingComesOffWithoutACompaction() {
        assertTrue(Verdichtung.rohwareFuer("Enchanted Coal", 0, new int[]{100}, minus("Coal", 100)).isEmpty());
        assertTrue(Verdichtung.rohwareFuer("Coal", 5, new int[]{100}, minus("Coal", 100)).isEmpty(), "die Rohware selbst");
        assertTrue(Verdichtung.rohwareFuer("Enchanted Coal", 5, new int[]{100}, minus("Hard Stone", 100)).isEmpty(), "eine andere Rohware");
        assertTrue(Verdichtung.rohwareFuer("Enchanted Coal", 5, new int[]{0}, minus("Coal", 100)).isEmpty(), "schon zugeteilt");
        assertTrue(Verdichtung.rohwareFuer("Enchanted Coal", 5, new int[]{100, 5}, minus("Coal", 100)).isEmpty(), "die Stellen passen nicht zusammen");
        assertTrue(Verdichtung.rohwareFuer("Enchanted Coal", 5, null, minus("Coal", 100)).isEmpty());
        assertTrue(Verdichtung.rohwareFuer(null, 5, new int[]{100}, minus("Coal", 100)).isEmpty());
    }

    @Test
    void namesMatchOnWholeWordsOnly() {
        assertTrue(Verdichtung.gehoert("Iron", "Iron Ingot"));
        assertTrue(Verdichtung.gehoert("Redstone Dust", "Redstone"));
        assertTrue(Verdichtung.gehoert("coal", "COAL"));
        assertFalse(Verdichtung.gehoert("Iron", "Ironwood Log"), "nur ganze Woerter");
        assertFalse(Verdichtung.gehoert("Coal", "Hard Stone"));
        assertFalse(Verdichtung.gehoert("", "Coal"));
        assertFalse(Verdichtung.gehoert("Coal", null));
    }
}
