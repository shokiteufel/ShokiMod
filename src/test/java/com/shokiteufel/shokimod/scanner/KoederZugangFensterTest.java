package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.paket.kern.Ereignis;
import com.shokiteufel.shokimod.paket.kern.Kern;
import com.shokiteufel.shokimod.paket.kern.Stapel;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Fall aus dem Log vom 12.11. (21:28:45 bis 21:29:16): vier Mal 1.408 Whale Bait aus dem Sack geholt und im Basar verkauft. Die
 * Sack-Meldung kam mit "Last 30s", die erste Entnahme lag gut 31 s davor und fiel aus dem Fenster - 1.408 Stueck galten als beim Angeln
 * verbraucht ("-1415" im Tracker).
 */
class KoederZugangFensterTest {
    private static final long S = 1_000_000_000L;

    private static Stapel bait(int menge) {
        return new Stapel("WHALE_BAIT", "minecraft:lime_dye", "Whale Bait", menge, null);
    }

    /** Eine Entnahme aus dem Sack: Befehl, dann kommt die Ware im Inventar an (Platz {@code platz}) */
    private static void holen(Kern kern, long nanos, long nr, int platz, int menge) {
        kern.uhr(nanos);
        kern.bei(new Ereignis.Befehl(nr));
        kern.bei(new Ereignis.Platz(nr + 4, 0, platz, bait(menge)));
    }

    private static Kern vierEntnahmen() {
        Kern kern = new Kern();
        kern.bei(new Ereignis.Welt(1L));
        kern.bei(new Ereignis.Inhalt(2L, 0, new ArrayList<>(Collections.nCopies(Kern.PLAETZE, Stapel.LEER))));
        // 21:28:45, :50, :53, :56 - als Sekunden ab der ersten Entnahme
        holen(kern, 100 * S, 1_000L, 12, 64);
        holen(kern, 105 * S, 2_000L, 13, 64);
        holen(kern, 108 * S, 3_000L, 14, 64);
        holen(kern, 111 * S, 4_000L, 15, 64);
        return kern;
    }

    @Test
    void theFirstOfFourWithdrawalsLiesJustOutsideTheNamedWindowButInsideTheOneUsed() {
        Kern kern = vierEntnahmen();
        // 21:29:16: 31 s nach der ersten, "Last 30s"; die Meldung nennt 4 x 64 + 7 verbraucht
        kern.uhr(131 * S);
        assertEquals(7, kern.zugangAbziehen("WHALE_BAIT", 4 * 64 + 7, ItemChanges.zugangsFenster(30 * S)),
                "alle vier Entnahmen sind erklaert, nur die 7 vom Angeln bleiben");
    }

    @Test
    void theNamedWindowAloneWouldHaveMissedTheFirstOne() {
        Kern kern = vierEntnahmen();
        kern.uhr(131 * S);
        assertEquals(64 + 7, kern.zugangAbziehen("WHALE_BAIT", 4 * 64 + 7, 30 * S), "so war es: die erste Entnahme fiel heraus");
    }

    @Test
    void aWithdrawalFarOlderThanTheWindowIsStillNotUsed() {
        Kern kern = new Kern();
        kern.bei(new Ereignis.Welt(1L));
        kern.bei(new Ereignis.Inhalt(2L, 0, new ArrayList<>(Collections.nCopies(Kern.PLAETZE, Stapel.LEER))));
        holen(kern, 100 * S, 1_000L, 12, 64);
        kern.uhr(160 * S);
        assertEquals(64, kern.zugangAbziehen("WHALE_BAIT", 64, ItemChanges.zugangsFenster(30 * S)), "60 s alt: gehoert zu einer frueheren Meldung");
    }

    @Test
    void theUsedWindowIsWiderThanTheNamedOneAndNotByMuch() {
        long w = ItemChanges.zugangsFenster(30 * S);
        assertTrue(w > 33 * S, "mehr als die 3 s, die der Abgang bekommt");
        assertTrue(w <= 45 * S, "aber kein Freibrief: " + w / S + " s");
    }
}
