package com.shokiteufel.shokimod.scanner;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Beute einer Leiche darf nicht zweimal zaehlen: aus der Chatzeile ("UMBER CORPSE LOOT!") und aus dem Inventar oder der Sack-Meldung
 * (User 16.11.: "der Loot der direkt ins Inventar kommt wird 2x gerechnet vom Corpse loot").
 */
class VormerkungTest {

    private static final long S = 1_000_000_000L;
    private static final long FENSTER = 10 * S;
    private static final long LEBEN = 60 * S;

    private final List<Vormerkung> vorgebucht = new ArrayList<>();
    private final List<Vormerkung> inventarFunde = new ArrayList<>();

    /** Was ItemChanges.bucheLeiche tut: erst im Inventar nachsehen, den Rest vormerken. Gibt zurueck, wie viel gebucht wird. */
    private int leichenZeile(List<String> ids, int menge, long nanos) {
        int gedeckt = Vormerkung.gedeckt(inventarFunde, ids, menge, nanos, FENSTER);
        int zuBuchen = menge - gedeckt;
        if (zuBuchen > 0) {
            Object linie = new Object();
            for (String id : ids) vorgebucht.add(new Vormerkung(id, zuBuchen, nanos, linie));
        }
        return zuBuchen;
    }

    /** Was ItemChanges.zugang tut: gegen die Vormerkung abziehen, den Rest als Fund merken. Gibt zurueck, wie viel zaehlt. */
    private int inventarZugang(String id, int menge, long nanos) {
        int zaehlt = Vormerkung.vomInventarAbziehen(vorgebucht, id, menge, nanos, FENSTER);
        if (zaehlt > 0) inventarFunde.add(new Vormerkung(id, zaehlt, nanos, null));
        return zaehlt;
    }

    @Test
    void lootThatLandsInTheInventoryAfterTheChatLineCountsOnce() {
        assertEquals(20, leichenZeile(List.of("FINE_JADE_GEM"), 20, 100 * S));
        assertEquals(0, inventarZugang("FINE_JADE_GEM", 20, 101 * S), "die Chatzeile hat die 20 schon gebucht");
    }

    @Test
    void lootThatLandsInTheInventoryBeforeTheChatLineCountsOnce() {
        assertEquals(20, inventarZugang("FINE_JADE_GEM", 20, 100 * S), "der Kern zaehlt zuerst");
        assertEquals(0, leichenZeile(List.of("FINE_JADE_GEM"), 20, 101 * S), "die Chatzeile findet den Fund und bucht nichts");
    }

    @Test
    void onlyTheInventoryPartIsCoveredWhenTheRestGoesIntoTheSack() {
        // 20 laut Chat: 8 fallen zuerst ins Inventar, 12 in den Sack
        assertEquals(8, inventarZugang("FINE_JADE_GEM", 8, 100 * S), "der Kern zaehlt die 8 zuerst");
        assertEquals(12, leichenZeile(List.of("FINE_JADE_GEM"), 20, 101 * S), "die Chatzeile bucht nur die 12, die das Inventar nicht kennt");
        assertEquals(0, Vormerkung.fuerSack(vorgebucht, List.of("FINE_JADE_GEM"), 12, 110 * S, LEBEN),
                "die 12 im Sack sind die Vormerkung der Chatzeile");
    }

    @Test
    void theSackMessageAndTheInventoryShareOnePool() {
        leichenZeile(List.of("FINE_JADE_GEM"), 20, 100 * S);
        // 20 laut Chat: 8 ins Inventar, die Sack-Meldung nennt 12 - zusammen 20, nichts bleibt uebrig
        assertEquals(0, inventarZugang("FINE_JADE_GEM", 8, 101 * S));
        assertEquals(0, Vormerkung.fuerSack(vorgebucht, List.of("FINE_JADE_GEM"), 12, 110 * S, LEBEN));
        assertTrue(vorgebucht.isEmpty(), "die Vormerkung ist aufgebraucht");
        // ein spaeterer echter Fund derselben Ware zaehlt wieder voll
        assertEquals(5, inventarZugang("FINE_JADE_GEM", 5, 120 * S));
    }

    @Test
    void aSackFindThatTheCorpseDidNotBringStillCounts() {
        leichenZeile(List.of("FINE_JADE_GEM"), 20, 100 * S);
        assertEquals(15, Vormerkung.fuerSack(vorgebucht, List.of("FINE_JADE_GEM"), 35, 110 * S, LEBEN),
                "20 der 35 im Sack sind die Beute der Leiche, 15 sind ein echter Fund");
    }

    @Test
    void severalIdsOfOneLineAreOneAmount() {
        // eine Zeile mit zwei moeglichen Kennungen: das Inventar nennt die eine, die Sack-Meldung spaeter beide - nichts bleibt uebrig
        leichenZeile(List.of("FINE_JADE_GEM", "JADE_GEM_FINE"), 6, 100 * S);
        assertEquals(0, inventarZugang("FINE_JADE_GEM", 6, 101 * S));
        assertEquals(4, Vormerkung.fuerSack(vorgebucht, List.of("FINE_JADE_GEM", "JADE_GEM_FINE"), 4, 105 * S, LEBEN),
                "die Schwester-Kennung darf die 6 nicht noch einmal abziehen: der Sack-Fund von 4 ist ein echter Fund");
        assertTrue(vorgebucht.isEmpty());
    }

    @Test
    void aFindOutsideTheWindowIsNotTheCorpseLoot() {
        leichenZeile(List.of("FINE_JADE_GEM"), 20, 100 * S);
        assertEquals(20, inventarZugang("FINE_JADE_GEM", 20, 100 * S + FENSTER + S), "erst nach dem Fenster: ein anderer Fund");
        assertEquals(20, leichenZeile(List.of("FINE_TOPAZ_GEM"), 20, 200 * S));
        // und umgekehrt: ein alter Fund deckt die spaetere Zeile nicht
        List<Vormerkung> alt = new ArrayList<>();
        alt.add(new Vormerkung("FINE_RUBY_GEM", 9, 0, null));
        assertEquals(0, Vormerkung.gedeckt(alt, List.of("FINE_RUBY_GEM"), 9, FENSTER + S, FENSTER));
    }

    @Test
    void craftAndRescueLinesAreNotTouchedByTheInventory() {
        // Vormerkungen ohne Leichen-Zeile (Craft, Nachtrag) haben ihren eigenen Weg: ein Zugang zieht dort nichts ab
        vorgebucht.add(new Vormerkung("ENCHANTED_COAL", 5, 100 * S, null));
        assertEquals(5, inventarZugang("ENCHANTED_COAL", 5, 101 * S));
        assertFalse(vorgebucht.isEmpty());
        assertEquals(0, Vormerkung.fuerSack(vorgebucht, List.of("ENCHANTED_COAL"), 5, 102 * S, LEBEN), "die Sack-Meldung zieht wie bisher ab");
    }

    @Test
    void oneFindCoversOneLineOnly() {
        assertEquals(10, inventarZugang("FINE_JADE_GEM", 10, 100 * S));
        assertEquals(0, leichenZeile(List.of("FINE_JADE_GEM"), 10, 101 * S));
        assertEquals(10, leichenZeile(List.of("FINE_JADE_GEM"), 10, 102 * S), "der Fund ist verbraucht, die zweite Zeile ist eine zweite Leiche");
    }

    @Test
    void oldEntriesExpire() {
        vorgebucht.add(new Vormerkung("X", 3, 0, new Object()));
        assertEquals(3, Vormerkung.fuerSack(vorgebucht, List.of("X"), 3, LEBEN + S, LEBEN), "nach der Lebensdauer ist die Vormerkung weg");
    }
}
