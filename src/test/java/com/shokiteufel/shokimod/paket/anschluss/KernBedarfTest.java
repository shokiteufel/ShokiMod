package com.shokiteufel.shokimod.paket.anschluss;

import com.shokiteufel.shokimod.paket.kern.Ereignis;
import com.shokiteufel.shokimod.paket.kern.Kern;
import com.shokiteufel.shokimod.paket.kern.Stapel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Paket-Kern rechnet nur mit, wenn jemand die Funde aus dem Inventar braucht (Profit Tracker, Rare-Loot-Ueberwachung, Schacht-Bilanz) */
class KernBedarfTest {

    @AfterEach
    void zuruecksetzen() {
        KernAnschluss.bedarfFuerTests(null);
    }

    private static Stapel fisch(int menge) {
        return new Stapel("RAW_FISH", "minecraft:cod", "Raw Fish", menge, null);
    }

    @Test
    void nobodyNeedsTheCoreInAFreshConfig() {
        com.shokiteufel.shokimod.data.ModConfig.INSTANCE = new com.shokiteufel.shokimod.data.ModConfig();
        KernAnschluss.bedarfFuerTests(null);

        assertFalse(KernAnschluss.bedarf(), "von Haus aus braucht niemand die Funde aus dem Inventar: Profit Tracker und Co. sind aus");
    }

    @Test
    void theCoreStartsWhenSomebodyNeedsItAndStopsWhenTheNeedIsGone() {
        boolean[] gebraucht = {false};
        KernAnschluss.bedarfFuerTests(() -> gebraucht[0]);

        assertFalse(KernAnschluss.bedarf());
        gebraucht[0] = true;
        assertTrue(KernAnschluss.bedarf());
        assertTrue(KernAnschluss.bedarf(), "ein zweiter Aufruf startet nicht noch einmal");
        gebraucht[0] = false;
        assertFalse(KernAnschluss.bedarf());
    }

    @Test
    void whatLiesInTheInventoryWhenTheCoreStartsIsNoNewFind() {
        Kern kern = new Kern();
        List<Kern.Zugang> zugaenge = new ArrayList<>();
        kern.hoeren(zugaenge::add);

        List<Stapel> lokal = new ArrayList<>(Collections.nCopies(Kern.PLAETZE, Stapel.LEER));
        lokal.set(13, fisch(5));
        KernAnschluss.spiegelFuellen(kern, 1L, lokal);
        assertTrue(zugaenge.isEmpty(), "das Inventar beim Start ist die Ausgangslage, kein Zugang");

        // Danach zaehlt nur noch, was dazukommt: drei Fische mehr auf demselben Platz, nicht alle acht
        kern.bei(new Ereignis.Platz(2L, 0, 13, fisch(8)));
        assertEquals(1, zugaenge.size(), () -> "gesehen: " + zugaenge);
        assertEquals(3, zugaenge.get(0).menge());
    }

    @Test
    void withoutAnyInventorySlotsTheCoreJustStartsFresh() {
        Kern kern = new Kern();
        List<Kern.Zugang> zugaenge = new ArrayList<>();
        kern.hoeren(zugaenge::add);

        KernAnschluss.spiegelFuellen(kern, 1L, List.of());

        assertTrue(zugaenge.isEmpty());
    }
}
