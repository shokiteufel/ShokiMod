package com.shokiteufel.shokimod.scanner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meldung des Users (19.11.): Seit dem neuen Compactor zaehlt der Profit Tracker dieselbe Kohle zweimal - als Coal x835k und als Enchanted Coal x4.441
 * (160 Coal je Stueck). Die Zeilen sind aus seinem latest.log vom 18./19.11.
 */
class CompactorZeilenTest {

    private static final long S = 1_000_000_000L;

    @Test
    void readsTheCompactorLine() {
        assertEquals("Enchanted Hard Stone", CompactorZeilen.lesen("COMPACT! You found an Enchanted Hard Stone!"));
        assertEquals("Enchanted Coal", CompactorZeilen.lesen("COMPACT! You found an Enchanted Coal!"));
        assertEquals("Enchanted Iron Ingot", CompactorZeilen.lesen("COMPACT! You found an Enchanted Iron Ingot!"));
        assertEquals("Enchanted Lapis Lazuli", CompactorZeilen.lesen("COMPACT! You found an Enchanted Lapis Lazuli!"));
    }

    @Test
    void readsItWithTheCounterAChatModAppends() {
        assertEquals("Enchanted Hard Stone", CompactorZeilen.lesen("COMPACT! You found an Enchanted Hard Stone! (2)"));
        assertEquals("Enchanted Hard Stone", CompactorZeilen.lesen("COMPACT! You found an Enchanted Hard Stone! (1,828)".replace(",", "")));
    }

    @Test
    void readsAnItemWithoutAnArticle() {
        assertEquals("Refined Mineral", CompactorZeilen.lesen("COMPACT! You found a Refined Mineral!"));
        assertEquals("Enchanted Coal", CompactorZeilen.lesen("COMPACT! You found Enchanted Coal!"));
    }

    @Test
    void otherLinesAreNoCompactorLines() {
        assertNull(CompactorZeilen.lesen("You found an Enchanted Coal!"), "ohne Etikett");
        assertNull(CompactorZeilen.lesen("COMPACT!"));
        assertNull(CompactorZeilen.lesen("COMPACT! You found !"));
        assertNull(CompactorZeilen.lesen("RARE DROP! Hyperion (+500% Magic Find)"));
        assertNull(CompactorZeilen.lesen("[Sacks] +80,959 items, -33,888 items. (Last 30s.)"));
        assertNull(CompactorZeilen.lesen("Party > [MVP+] Anna: COMPACT! You found an Enchanted Coal!"), "Zeilen anderer Kanaele haben ein Praefix");
        assertNull(CompactorZeilen.lesen(""));
        assertNull(CompactorZeilen.lesen(null));
    }

    @Test
    void aLineInTheWindowOfTheSackMessageMarksItsItem() {
        // Die Sack-Meldung kam bei 100 s und fasst die letzten 30 s zusammen; die Zeile kam bei 85 s
        List<CompactorZeilen.Eintrag> eintraege = List.of(new CompactorZeilen.Eintrag(List.of("ENCHANTED_COAL"), 85 * S));
        assertTrue(CompactorZeilen.imFenster(eintraege, List.of("ENCHANTED_COAL"), 70 * S, 103 * S));
        assertTrue(CompactorZeilen.imFenster(eintraege, List.of("COAL", "ENCHANTED_COAL"), 70 * S, 103 * S), "eine der Kennungen genuegt");
    }

    @Test
    void aLineOutsideTheWindowOrOfAnotherItemMarksNothing() {
        List<CompactorZeilen.Eintrag> eintraege = List.of(new CompactorZeilen.Eintrag(List.of("ENCHANTED_COAL"), 85 * S));
        assertFalse(CompactorZeilen.imFenster(eintraege, List.of("ENCHANTED_COAL"), 90 * S, 103 * S), "die Zeile kam vor dem Fenster");
        assertFalse(CompactorZeilen.imFenster(eintraege, List.of("ENCHANTED_COAL"), 60 * S, 80 * S), "die Zeile kam nach dem Fenster");
        assertFalse(CompactorZeilen.imFenster(eintraege, List.of("ENCHANTED_HARD_STONE"), 70 * S, 103 * S), "eine andere Sorte");
        assertFalse(CompactorZeilen.imFenster(eintraege, List.of("COAL"), 70 * S, 103 * S), "die Rohware ist kein Ergebnis des Compactors");
    }

    @Test
    void theEdgesOfTheWindowCount() {
        List<CompactorZeilen.Eintrag> eintraege = List.of(new CompactorZeilen.Eintrag(List.of("ENCHANTED_COAL"), 70 * S));
        assertTrue(CompactorZeilen.imFenster(eintraege, List.of("ENCHANTED_COAL"), 70 * S, 103 * S));
        assertTrue(CompactorZeilen.imFenster(eintraege, List.of("ENCHANTED_COAL"), 40 * S, 70 * S));
    }

    private static List<CompactorZeilen.Entnommen> minus(Object... paare) {
        java.util.ArrayList<CompactorZeilen.Entnommen> aus = new java.util.ArrayList<>();
        for (int i = 0; i < paare.length; i += 2) aus.add(new CompactorZeilen.Entnommen((String) paare[i], (Integer) paare[i + 1]));
        return aus;
    }

    @Test
    void theRawItemsInTheMinusMakeTheEnchantedPiecesACompaction() {
        // Aus dem Log vom 19.11., 21:07:56: +1.825 Enchanted Redstone ohne COMPACT!-Zeile, im Minus 292.000 Redstone
        assertEquals(1825, CompactorZeilen.aus("Enchanted Redstone", 1825, minus("Redstone", 292_000, "Coal", 32_000)));
        // 21:02:44: 191 Enchanted Coal und 75 Enchanted Hard Stone, im Minus Coal und Hard Stone
        assertEquals(191, CompactorZeilen.aus("Enchanted Coal", 191, minus("Coal", 30_560, "Hard Stone", 37_696)));
        assertEquals(75, CompactorZeilen.aus("Enchanted Hard Stone", 75, minus("Coal", 30_560, "Hard Stone", 37_696)));
    }

    @Test
    void ingotsAndDustAreMatchedByTheirName() {
        assertEquals(4, CompactorZeilen.aus("Enchanted Iron", 4, minus("Iron Ingot", 640)));
        assertEquals(3, CompactorZeilen.aus("Enchanted Gold", 3, minus("Gold Ingot", 480)));
        assertEquals(1, CompactorZeilen.aus("Enchanted Redstone Dust", 1, minus("Redstone", 160)));
        assertEquals(1, CompactorZeilen.aus("Enchanted Lapis Lazuli", 1, minus("Lapis Lazuli", 160)));
    }

    @Test
    void withoutTheRawItemInTheMinusNothingIsACompaction() {
        assertEquals(0, CompactorZeilen.aus("Enchanted Coal", 3, minus()), "kein Minus: ein echter Fund");
        assertEquals(0, CompactorZeilen.aus("Enchanted Coal", 3, minus("Hard Stone", 40_000)), "eine andere Rohware");
        assertEquals(0, CompactorZeilen.aus("Enchanted Coal", 3, null));
        assertEquals(0, CompactorZeilen.aus("Coal", 835_000, minus("Coal", 710_560)), "die Rohware selbst bleibt ein Fund");
        assertEquals(0, CompactorZeilen.aus("Hyperion", 1, minus("Coal", 710_560)));
        assertEquals(0, CompactorZeilen.aus("Enchanted Coal", 0, minus("Coal", 710_560)));
    }

    @Test
    void aSmallMinusOnlyCoversWhatItCanHold() {
        // 100 Coal reichen hoechstens fuer ein paar Stueck: von zehn Enchanted Coal gelten nur fuenf als verdichtet, der Rest bleibt ein Fund
        assertEquals(5, CompactorZeilen.aus("Enchanted Coal", 10, minus("Coal", 100)));
    }

    @Test
    void namesMatchOnWholeWordsOnly() {
        assertTrue(CompactorZeilen.gehoert("Iron", "Iron Ingot"));
        assertTrue(CompactorZeilen.gehoert("Redstone Dust", "Redstone"));
        assertTrue(CompactorZeilen.gehoert("coal", "COAL"));
        assertFalse(CompactorZeilen.gehoert("Iron", "Ironwood Log"), "nur ganze Woerter");
        assertFalse(CompactorZeilen.gehoert("Coal", "Hard Stone"));
        assertFalse(CompactorZeilen.gehoert("", "Coal"));
        assertFalse(CompactorZeilen.gehoert("Coal", null));
    }

    @Test
    void emptyInputsMarkNothing() {
        assertFalse(CompactorZeilen.imFenster(List.of(), List.of("ENCHANTED_COAL"), 0, 100 * S));
        assertFalse(CompactorZeilen.imFenster(null, List.of("ENCHANTED_COAL"), 0, 100 * S));
        assertFalse(CompactorZeilen.imFenster(List.of(new CompactorZeilen.Eintrag(List.of("ENCHANTED_COAL"), S)), List.of(), 0, 100 * S));
        assertFalse(CompactorZeilen.imFenster(List.of(new CompactorZeilen.Eintrag(List.of("ENCHANTED_COAL"), S)), null, 0, 100 * S));
    }
}
