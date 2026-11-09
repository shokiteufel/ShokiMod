package com.shokiteufel.shokimod.paket;

import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Belegt die Kosten der Paket-Schicht, statt sie zu behaupten (Nutzerregel "nur das Noetige"): Fuer ein Paket, das niemand
 * abonniert hat, wird nichts angelegt. Gemessen wird der auf dem Thread angelegte Speicher, das ist stabiler als die Zeit.
 */
class PaketKostenTest {
    private static final int DURCHLAEUFE = 200_000;

    private final com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    @BeforeEach
    @AfterEach
    void zuruecksetzen() {
        PaketBus.zuruecksetzenFuerTests();
    }

    private long angelegteBytes(PaketBusTest.PaketA paket) {
        for (int i = 0; i < 20_000; i++) { // Aufwaermen, damit die JIT-Uebersetzung nicht mitgemessen wird
            PaketAbgriff.vor(paket, true);
            PaketAbgriff.nach(paket);
        }
        long vorher = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < DURCHLAEUFE; i++) {
            PaketAbgriff.vor(paket, true);
            PaketAbgriff.nach(paket);
        }
        return threads.getCurrentThreadAllocatedBytes() - vorher;
    }

    @Test
    void aPacketNobodyListensToAllocatesNothingWithoutAnySubscriber() {
        long bytes = angelegteBytes(new PaketBusTest.PaketA());

        System.out.println("Kosten ohne Abonnent: " + bytes + " Byte fuer " + DURCHLAEUFE + " Pakete");
        assertTrue(bytes < DURCHLAEUFE / 10, "ohne Abonnent darf nichts je Paket angelegt werden, gemessen: " + bytes + " Byte");
    }

    @Test
    void aPacketNobodyListensToAllocatesNothingEvenWhenOtherTypesAreSubscribed() {
        PaketBus.abonnieren(PaketBusTest.PaketB.class, PaketPhase.VOR, "anderer", (p, k) -> { });
        PaketBus.abonnieren(PaketBusTest.PaketB.class, PaketPhase.NACH, "anderer", (p, k) -> { });

        long bytes = angelegteBytes(new PaketBusTest.PaketA());

        System.out.println("Kosten bei Abonnenten anderer Typen: " + bytes + " Byte fuer " + DURCHLAEUFE + " Pakete");
        assertTrue(bytes < DURCHLAEUFE / 10, "Abonnenten anderer Typen duerfen ein Paket nicht verteuern, gemessen: " + bytes + " Byte");
    }

    @Test
    void aSubscribedPacketCostsOnlyTheTwoHeaders() {
        long[] zaehler = new long[1];
        PaketBus.abonnieren(PaketBusTest.PaketA.class, PaketPhase.VOR, "a", (p, k) -> zaehler[0]++);
        PaketBus.abonnieren(PaketBusTest.PaketA.class, PaketPhase.NACH, "a", (p, k) -> zaehler[0]++);

        long bytes = angelegteBytes(new PaketBusTest.PaketA());

        long jePaket = bytes / DURCHLAEUFE;
        System.out.println("Kosten mit Abonnent: " + jePaket + " Byte je Paket (VOR und NACH)");
        // Ein Kopf hat 56 Byte (drei long, Phase, Richtung, Verbindungsphase, direkt; vor dem Ausbau um Richtung und Phase 40 Byte)
        assertTrue(jePaket <= 128, "hoechstens zwei kleine Koepfe je Paket, gemessen: " + jePaket + " Byte");
    }

    @Test
    void theSeamIsFastEvenWithoutSubscribers() {
        PaketBusTest.PaketA paket = new PaketBusTest.PaketA();
        for (int i = 0; i < 50_000; i++) {
            PaketAbgriff.vor(paket, true);
            PaketAbgriff.nach(paket);
        }
        long start = System.nanoTime();
        for (int i = 0; i < DURCHLAEUFE; i++) {
            PaketAbgriff.vor(paket, true);
            PaketAbgriff.nach(paket);
        }
        long jePaket = (System.nanoTime() - start) / DURCHLAEUFE;

        System.out.println("Zeit ohne Abonnent: " + jePaket + " ns je Paket");
        assertTrue(jePaket < 5_000, "grosszuegige Grenze gegen Rueckschritte (Hunderte Pakete je Sekunde), gemessen: " + jePaket + " ns");
    }
}
