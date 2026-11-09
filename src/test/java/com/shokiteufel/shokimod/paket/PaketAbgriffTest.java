package com.shokiteufel.shokimod.paket;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueft den Abgriff: ein Paket, ein Buendel, fremde Phasen, Fehler bei der Bearbeitung, Fehler im Abonnenten. */
class PaketAbgriffTest {
    private final List<String> ereignisse = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void zuruecksetzen() {
        PaketBus.zuruecksetzenFuerTests();
        ereignisse.clear();
    }

    private void alleMitschreiben() {
        PaketBus.abonnierenAlle(PaketPhase.VOR, "vor", (p, k) -> ereignisse.add("VOR " + p.getClass().getSimpleName() + " #" + k.laufendeNummer()));
        PaketBus.abonnierenAlle(PaketPhase.NACH, "nach", (p, k) -> ereignisse.add("NACH " + p.getClass().getSimpleName() + " #" + k.laufendeNummer()));
    }

    @Test
    void reportsASinglePacketBeforeAndAfterWithTheSameNumber() {
        alleMitschreiben();
        PaketBusTest.PaketA paket = new PaketBusTest.PaketA();

        PaketAbgriff.vor(paket, true);
        ereignisse.add("(Spiel bearbeitet)");
        PaketAbgriff.nach(paket);

        assertEquals(List.of("VOR PaketA #1", "(Spiel bearbeitet)", "NACH PaketA #1"), ereignisse);
    }

    @Test
    void resolvesABundleIntoItsPartsWithOneNumberEach() {
        alleMitschreiben();
        List<Packet<? super ClientGamePacketListener>> teile = List.of(
                new PaketBusTest.PaketA(), new PaketBusTest.PaketB(), new PaketBusTest.PaketA());
        ClientboundBundlePacket buendel = new ClientboundBundlePacket(teile);

        PaketAbgriff.vor(buendel, true);
        ereignisse.add("(Spiel bearbeitet)");
        PaketAbgriff.nach(buendel);

        assertEquals(List.of(
                "VOR PaketA #1", "VOR PaketB #2", "VOR PaketA #3",
                "(Spiel bearbeitet)",
                "NACH PaketA #1", "NACH PaketB #2", "NACH PaketA #3"), ereignisse,
                "Das Buendel selbst wird nicht gemeldet, seine Teile einzeln, alle VOR zuerst und alle NACH danach");
        assertEquals(3, PaketBus.laufendeNummer());
    }

    @Test
    void anEmptyBundleReportsNothing() {
        alleMitschreiben();
        ClientboundBundlePacket leer = new ClientboundBundlePacket(List.of());

        PaketAbgriff.vor(leer, true);
        PaketAbgriff.nach(leer);

        assertTrue(ereignisse.isEmpty());
    }

    @Test
    void ignoresPacketsOutsideThePlayPhase() {
        alleMitschreiben();
        PaketBusTest.PaketA paket = new PaketBusTest.PaketA();

        PaketAbgriff.vor(paket, false);
        PaketAbgriff.nach(paket);

        assertTrue(ereignisse.isEmpty());
        assertEquals(0, PaketBus.laufendeNummer(), "ein ungemeldetes Paket bekommt keine Nummer");
    }

    @Test
    void doesNotReportAfterWhenHandlingFailedAndStartsCleanWithTheNextPacket() {
        alleMitschreiben();
        PaketBusTest.PaketA erstes = new PaketBusTest.PaketA();
        PaketBusTest.PaketB zweites = new PaketBusTest.PaketB();

        PaketAbgriff.vor(erstes, true);
        // Das Spiel wirft bei der Bearbeitung: nach() wird nie aufgerufen
        PaketAbgriff.vor(zweites, true);
        PaketAbgriff.nach(zweites);

        assertEquals(List.of("VOR PaketA #1", "VOR PaketB #2", "NACH PaketB #2"), ereignisse);
    }

    @Test
    void ignoresAnAfterCallForAnotherPacket() {
        alleMitschreiben();
        PaketAbgriff.vor(new PaketBusTest.PaketA(), true);

        PaketAbgriff.nach(new PaketBusTest.PaketB());

        assertEquals(List.of("VOR PaketA #1"), ereignisse);
    }

    @Test
    void aFailingSubscriberNeverReachesTheGame() {
        PaketBus.abonnierenAlle(PaketPhase.VOR, "kaputt", (p, k) -> {
            throw new IllegalStateException("absichtlich");
        });
        PaketBus.abonnierenAlle(PaketPhase.NACH, "kaputt", (p, k) -> {
            throw new IllegalStateException("absichtlich");
        });
        PaketBusTest.PaketA paket = new PaketBusTest.PaketA();

        assertDoesNotThrow(() -> {
            PaketAbgriff.vor(paket, true);
            PaketAbgriff.nach(paket);
        });
    }

    @Test
    void doesNotRetainPacketsAfterwards() {
        alleMitschreiben();
        PaketBusTest.PaketA paket = new PaketBusTest.PaketA();
        PaketAbgriff.vor(paket, true);
        PaketAbgriff.nach(paket);

        // Ein zweites nach() fuer dasselbe Paket darf nichts mehr melden: der Abgriff hat alles freigegeben
        ereignisse.clear();
        PaketAbgriff.nach(paket);

        assertTrue(ereignisse.isEmpty());
    }

    @Test
    void aSwitchedOffBusReportsNothingAndNumbersNothing() {
        alleMitschreiben();
        for (int i = 0; i < PaketBus.MAX_FEHLER_GESAMT; i++) {
            PaketBus.abgriffFehler(new IllegalStateException("absichtlich"));
        }
        assertTrue(PaketBus.ausgeschaltet());

        PaketAbgriff.vor(new PaketBusTest.PaketA(), true);

        assertTrue(ereignisse.isEmpty());
    }
}
