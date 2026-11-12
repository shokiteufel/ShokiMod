package com.shokiteufel.shokimod.paket.kern;

import com.shokiteufel.shokimod.paket.kern.Ereignis.Kurz;
import com.shokiteufel.shokimod.paket.kern.Kern.Ursache;
import com.shokiteufel.shokimod.paket.kern.Kern.Zugang;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ein Klick gilt als Beleg, solange er im Paketfenster oder in der Zeit liegt. Der Fall aus dem Mitschnitt vom 12.11. (Schiggy, Glacite Mineshaft):
 * Klick im Bazaar-Menue (Fenster 3, Platz 16) um 17:37:47.112, das Fenster 4 geoeffnet und wieder geschlossen, um 17:37:47.590 liegen 10 Plasma
 * aus dem Sack im Inventar (Container 0, Platz 11) - 0,48 s und 344 Pakete nach dem Klick. Das Paketfenster (78) war zu eng, das Plasma kam als Fund.
 */
class KlickZeitTest {
    private static final long MS = 1_000_000L;
    private Kern kern;
    private List<Zugang> zugaenge;

    @BeforeEach
    void aufbauen() {
        kern = new Kern();
        zugaenge = new ArrayList<>();
        kern.hoeren(new Kern.Hoerer() {
            @Override
            public void zugang(Zugang z) {
                zugaenge.add(z);
            }
        });
    }

    private static Stapel plasma(int menge) {
        return new Stapel("PLASMA", "minecraft:paper", "Plasma", menge, null);
    }

    private static List<Stapel> leer(int n) {
        return new ArrayList<>(Collections.nCopies(n, Stapel.LEER));
    }

    /** Container 0 leer als Ausgangsstand */
    private void ausgang() {
        kern.bei(new Ereignis.Welt(1L));
        kern.bei(new Ereignis.Inhalt(2L, 0, leer(Kern.PLAETZE)));
        zugaenge.clear();
    }

    /** Fenster 3 offen und geladen, ein Klick darin, dann Fenster 4 auf und zu - wie im Mitschnitt; die Zeit steht danach bei {@code t0} */
    private void klickImBazaar(long t0) {
        kern.uhr(t0 - 700 * MS);
        kern.bei(new Ereignis.MenueAuf(33_504L, 3, "Bazaar"));
        kern.bei(new Ereignis.Inhalt(33_698L, 3, leer(90)));
        kern.uhr(t0 - 478 * MS);
        kern.bei(new Ereignis.Klick(34_078L, 3, 16, "PICKUP", Map.of(16, Kurz.LEER), Kurz.LEER));
        kern.bei(new Ereignis.MenueAuf(34_126L, 4, "Sell Offer"));
        kern.uhr(t0 - 150 * MS);
        kern.bei(new Ereignis.MenueZu(34_269L, 4));
    }

    private Zugang einziger() {
        assertEquals(1, zugaenge.size(), () -> "erwartet genau einen Zugang: " + zugaenge);
        return zugaenge.get(0);
    }

    @Test
    void itemsArrivingHalfASecondAfterAClickAreItsAnswerEvenAfter344Packets() {
        ausgang();
        long t0 = 50_000L * MS;
        klickImBazaar(t0);
        kern.uhr(t0);
        kern.bei(new Ereignis.Platz(34_422L, 0, 11, plasma(10)));

        Zugang z = einziger();
        assertEquals(Ursache.KLICK_ANTWORT, z.ursache());
        assertTrue(z.beleg().contains("344 Pakete davor") && z.beleg().contains("ms"), z.beleg());
        assertFalse(FundRegel.istFund(z), "aus dem Sack geholt ist kein Fund");
    }

    @Test
    void aSecondAndALittleLaterIsNoLongerTheAnswerAndStaysAFind() {
        ausgang();
        long t0 = 50_000L * MS;
        klickImBazaar(t0);
        kern.uhr(t0 + 1_000 * MS);
        kern.bei(new Ereignis.Platz(36_000L, 0, 11, plasma(10)));

        Zugang z = einziger();
        // 478 ms + 1000 ms nach dem Klick = 1,48 s > 1 s
        assertEquals(Ursache.UNBEKANNT, z.ursache());
        assertTrue(FundRegel.istFund(z), "ohne Beleg bleibt es ein Fund, wie bisher");
    }

    @Test
    void withoutAClockOnlyThePacketWindowCounts() {
        ausgang();
        // keine Uhr (jetzt = 0): wie vor der Aenderung
        kern.bei(new Ereignis.MenueAuf(33_504L, 3, "Bazaar"));
        kern.bei(new Ereignis.Inhalt(33_698L, 3, leer(90)));
        kern.bei(new Ereignis.Klick(34_078L, 3, 16, "PICKUP", Map.of(16, Kurz.LEER), Kurz.LEER));
        kern.bei(new Ereignis.MenueZu(34_269L, 3));
        kern.bei(new Ereignis.Platz(34_422L, 0, 11, plasma(10)));
        assertEquals(Ursache.UNBEKANNT, einziger().ursache());
    }

    @Test
    void insideThePacketWindowTheClickStillCountsWithoutAnyTime() {
        ausgang();
        kern.bei(new Ereignis.MenueAuf(33_504L, 3, "Bazaar"));
        kern.bei(new Ereignis.Inhalt(33_698L, 3, leer(90)));
        kern.bei(new Ereignis.Klick(34_078L, 3, 16, "PICKUP", Map.of(16, Kurz.LEER), Kurz.LEER));
        // Das Menue bleibt offen: der Klick steht noch, die Ankunft traegt die Nummer des Fensters
        kern.bei(new Ereignis.Platz(34_120L, 0, 11, plasma(10)));
        Zugang z = einziger();
        assertEquals(Ursache.KLICK_ANTWORT, z.ursache());
        assertTrue(z.beleg().contains("42 Pakete davor") && !z.beleg().contains("ms"), z.beleg());
    }

    @Test
    void aMobDropWithoutAnyClickIsStillAMobDrop() {
        ausgang();
        kern.uhr(50_000L * MS);
        kern.bei(new Ereignis.MobTod(40_000L, 77));
        kern.bei(new Ereignis.Platz(40_005L, 0, 11, plasma(1)));
        Zugang z = einziger();
        assertEquals(Ursache.MOB_DROP, z.ursache());
        assertTrue(FundRegel.istFund(z));
    }
}
