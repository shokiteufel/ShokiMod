package com.shokiteufel.shokimod.scanner;

import com.shokiteufel.shokimod.paket.kern.Ereignis;
import com.shokiteufel.shokimod.paket.kern.Kern;
import com.shokiteufel.shokimod.paket.kern.Stapel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Schiggy 09.11., 19:37: 13 Lotus Gold aus dem Sack geholt, auf die Chat-Meldung der Entnahme gewartet, wieder eingelegt - und der Tracker
 * zeigte die 13 als Fund. Der Abgang aus dem Inventar lag am Anfang der Fuenf-Sekunden-Spanne der Meldung und war bei ihr knapp aelter als das Fenster.
 */
class SackRueckgabeTest {
    private static final long S = 1_000_000_000L;
    private Kern kern;
    private SackRueckgabe rueckgabe;

    @BeforeEach
    void aufbauen() {
        kern = new Kern();
        rueckgabe = new SackRueckgabe();
    }

    private static Stapel lotus(int menge) {
        return new Stapel("LOTUS_GOLD", "minecraft:gold_ingot", "Lotus Gold", menge, null, false);
    }

    /** Das Inventar mit 13 Lotus Gold auf Platz 13, dann geht der Stapel zur Zeit {@code sekunden} ohne eigenen Schritt aus dem Inventar. */
    private void lotusAbgang(long sekunden) {
        List<Stapel> l = new ArrayList<>(Collections.nCopies(Kern.PLAETZE, Stapel.LEER));
        l.set(13, lotus(13));
        kern.bei(new Ereignis.Welt(1L));
        kern.bei(new Ereignis.Inhalt(2L, 0, l));
        kern.uhr(sekunden * S);
        kern.bei(new Ereignis.Platz(100L, 0, 13, Stapel.LEER));
    }

    @Test
    void aReturnAfterTheRemovalMessageIsNotAFind() {
        rueckgabe.entnommen("LOTUS_GOLD", 13, 100 * S); // die Meldung "Removed items" kam, der Spieler wartete auf sie
        lotusAbgang(130);
        kern.uhr(136 * S); // die "+"-Meldung kommt 6 s nach dem Abgang, ihr Fenster ist 5 s (zu knapp fuer den Abgleich der Meldung)
        assertEquals(13, kern.abgangAbziehen("LOTUS_GOLD", 13, 5 * S), "ohne Spielraum greift der Abgleich nicht: so kam es in den Tracker");
        assertEquals(0, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, 136 * S), "das Konto der Entnahmen rechnet die Rueckgabe auf");
    }

    @Test
    void theSpielraumCoversAnOutflowFromTheStartOfTheSpan() {
        lotusAbgang(130);
        kern.uhr(135_400_000_000L); // 5,4 s alt bei einem Fenster von 5 s
        assertEquals(0, kern.abgangAbziehen("LOTUS_GOLD", 13, 5 * S + SackRueckgabe.ABGANG_SPIELRAUM_NANOS));
    }

    @Test
    void withoutARemovalTheKontoDoesNothing() {
        // Beute, die direkt in den Sack fiel, waehrend zufaellig derselbe Stapel das Inventar verliess: ohne Entnahme kein Aufrechnen
        lotusAbgang(130);
        kern.uhr(136 * S);
        assertEquals(13, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, 136 * S));
    }

    @Test
    void withoutAnOutflowFromTheInventoryTheKontoDoesNothing() {
        // Entnommen, aber nichts verlaesst das Inventar: was der Sack danach bekommt, ist neu (zum Beispiel Beute)
        rueckgabe.entnommen("LOTUS_GOLD", 13, 100 * S);
        kern.bei(new Ereignis.Welt(1L));
        kern.uhr(136 * S);
        assertEquals(13, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, 136 * S));
        assertEquals(13, rueckgabe.entnommenOffen("LOTUS_GOLD", 136 * S), "nichts verrechnet, das Konto bleibt");
    }

    @Test
    void onlyAsMuchAsWasTakenOutIsCreditedAndNeverTwice() {
        rueckgabe.entnommen("LOTUS_GOLD", 5, 100 * S);
        lotusAbgang(130);
        kern.uhr(136 * S);
        assertEquals(8, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, 136 * S), "5 geholt, 13 zurueck: 8 bleiben uebrig");
        assertEquals(0, rueckgabe.entnommenOffen("LOTUS_GOLD", 136 * S));
        assertEquals(13, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, 137 * S), "dieselben 5 zaehlen nicht zweimal");
    }

    @Test
    void aRemovalOlderThanTenMinutesNoLongerCounts() {
        rueckgabe.entnommen("LOTUS_GOLD", 13, 100 * S);
        lotusAbgang(100 + 601);
        kern.uhr((100 + 607) * S);
        assertEquals(13, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, (100 + 607) * S));
    }

    @Test
    void anOtherWareIsNotAffected() {
        rueckgabe.entnommen("ENCHANTED_POTATO", 13, 100 * S);
        lotusAbgang(130);
        kern.uhr(136 * S);
        assertEquals(13, rueckgabe.verrechnen(kern, "LOTUS_GOLD", 13, 136 * S));
    }
}
