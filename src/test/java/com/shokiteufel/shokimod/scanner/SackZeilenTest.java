package com.shokiteufel.shokimod.scanner;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Meldungen aus dem Mitschnitt {@code verkehr-20261107-222043-927}: seq 9 458 082 (zwei Waren in einem Paket) und die Baum-Meldungen. */
class SackZeilenTest {

    @org.junit.jupiter.api.Test
    void theCutOffRestIsReadAsItsOwnAmount() {
        // Mitschnitt 09.11. 13:18:33: Hypixel zeigt hoechstens etwa fuenfzehn Posten und nennt den Rest nur als Zahl
        SackZeilen.Meldung m = SackZeilen.lesen("[Sacks] +254 items, -13 items. (Last 30s.)", List.of(
                "Added items:\n  +9 Chum (Fishing Sack)\n  +2 Dark Bait (Bait Sack)\n  +36 other items.\n\nRemoved items:\n  -13 Whale Bait (Bait Sack)"));
        assertEquals(2, m.hinzu().size());
        assertEquals(36, m.versteckt());
        assertEquals(1, m.entfernt().size());
    }

    @org.junit.jupiter.api.Test
    void aMessageWithoutACutOffHasNoHiddenAmount() {
        SackZeilen.Meldung m = SackZeilen.lesen("[Sacks] +232 items. (Last 5s.)", List.of("Added items:\n+232 Oak Log (Foraging Sack)"));
        assertEquals(0, m.versteckt());
    }

    @Test
    void oneMessageCanCarryManyItemsAndNamesItsOwnWindow() {
        String kopf = "[Sacks] +558 items. (Last 5s.)";
        String block = "Added items:\n+327 Seeds (Agronomy Sack)\n+231 Wheat (Agronomy Sack)\n\nThis message can be disabled in the settings.";
        SackZeilen.Meldung m = SackZeilen.lesen(kopf, List.of(block));
        assertEquals(5, m.fensterSekunden());
        assertEquals(List.of(new SackZeilen.Zeile("Seeds", 327), new SackZeilen.Zeile("Wheat", 231)), m.hinzu());
        assertEquals(558, m.hinzu().stream().mapToInt(SackZeilen.Zeile::menge).sum(), "327 + 231 = 558, wie im Kopf");
    }

    @Test
    void singleItemLineFromTheTreeFelling() {
        SackZeilen.Meldung m = SackZeilen.lesen("[Sacks] +232 items. (Last 5s.)", List.of("Added items:\n+232 Oak Log (Foraging Sack)"));
        assertEquals(List.of(new SackZeilen.Zeile("Oak Log", 232)), m.hinzu());
    }

    @Test
    void removedItemsAndMinusLinesAreNotAdditions() {
        String block = "Added items:\n+5 Raw Fish (Fishing Sack)\nRemoved items:\n-3 Obfuscated Fish 1 (Fishing Sack)";
        SackZeilen.Meldung m = SackZeilen.lesen("[Sacks] +5 items, -3 items. (Last 30s.)", List.of(block));
        assertEquals(List.of(new SackZeilen.Zeile("Raw Fish", 5)), m.hinzu());
        assertEquals(List.of(new SackZeilen.Zeile("Obfuscated Fish 1", 3)), m.entfernt(), "die Zeile unter Removed items steht fuer sich");
        assertEquals(30, m.fensterSekunden());
    }

    @Test
    void removedBaitIsReadWithTheSignOfItsOwnBlock() {
        String block = "Removed items:\n-12 Blessed Bait (Fishing Sack)\n-4 Whale Bait (Fishing Sack)";
        SackZeilen.Meldung m = SackZeilen.lesen("[Sacks] -16 items. (Last 30s.)", List.of(block));
        assertEquals(List.of(new SackZeilen.Zeile("Blessed Bait", 12), new SackZeilen.Zeile("Whale Bait", 4)), m.entfernt());
        assertTrue(m.hinzu().isEmpty());
    }

    @Test
    void thousandsSeparatorsAndEachBlockOnItsOwn() {
        SackZeilen.Meldung m = SackZeilen.lesen("[Sacks] +1,200 items.",
                List.of("Added items:\n+1,200 Cobblestone (Mining Sack)", "+3 Stone (Mining Sack)"));
        assertEquals(List.of(new SackZeilen.Zeile("Cobblestone", 1200)), m.hinzu(), "die Ueberschrift eines Blocks gilt nicht fuer den naechsten");
        assertEquals(SackZeilen.STANDARD_FENSTER, m.fensterSekunden());
    }

    @Test
    void onlyTheSackLineIsASackMessage() {
        assertTrue(SackZeilen.istSackMeldung("[Sacks] +1 items."));
        assertFalse(SackZeilen.istSackMeldung("You have 20,200 materials stashed away!"));
    }
}
