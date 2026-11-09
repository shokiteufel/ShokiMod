package com.shokiteufel.shokimod.paket;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueft die Logik des Paket-Busses ohne laufendes Spiel (Reihenfolge, Typ und Phase, Nummern, Fehlerisolierung). */
class PaketBusTest {

    /** Zwei Pakettypen, die nichts koennen muessen: Der Bus schaut nur auf die Klasse. */
    static final class PaketA implements Packet<ClientGamePacketListener> {
        @Override
        public PacketType<? extends Packet<ClientGamePacketListener>> type() {
            return null;
        }

        @Override
        public void handle(ClientGamePacketListener listener) {
        }
    }

    static final class PaketB implements Packet<ClientGamePacketListener> {
        @Override
        public PacketType<? extends Packet<ClientGamePacketListener>> type() {
            return null;
        }

        @Override
        public void handle(ClientGamePacketListener listener) {
        }
    }

    private final List<String> ereignisse = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void zuruecksetzen() {
        PaketBus.zuruecksetzenFuerTests();
        ereignisse.clear();
    }

    private void merken(String name, Packet<?> paket, PaketKopf kopf) {
        ereignisse.add(name + ":" + paket.getClass().getSimpleName() + "#" + kopf.laufendeNummer() + "/" + kopf.phase());
    }

    @Test
    void deliversOnlyToSubscribersOfThatPacketType() {
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "a", (p, k) -> merken("a", p, k));

        PaketBus.vor(new PaketA());
        PaketBus.vor(new PaketB());

        assertEquals(List.of("a:PaketA#1/VOR"), ereignisse);
    }

    @Test
    void keepsBeforeAndAfterApartAndGivesThemTheSameNumber() {
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "vor", (p, k) -> merken("vor", p, k));
        PaketBus.abonnieren(PaketA.class, PaketPhase.NACH, "nach", (p, k) -> merken("nach", p, k));
        PaketA paket = new PaketA();

        long nummer = PaketBus.vor(paket);
        PaketBus.nach(paket, nummer);

        assertEquals(List.of("vor:PaketA#1/VOR", "nach:PaketA#1/NACH"), ereignisse);
    }

    @Test
    void callsTypeSubscribersBeforeAllSubscribersEachInRegistrationOrder() {
        PaketBus.abonnierenAlle(PaketPhase.VOR, "alle1", (p, k) -> merken("alle1", p, k));
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "typ1", (p, k) -> merken("typ1", p, k));
        PaketBus.abonnierenAlle(PaketPhase.VOR, "alle2", (p, k) -> merken("alle2", p, k));
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "typ2", (p, k) -> merken("typ2", p, k));

        PaketBus.vor(new PaketA());

        assertEquals(List.of("typ1:PaketA#1/VOR", "typ2:PaketA#1/VOR", "alle1:PaketA#1/VOR", "alle2:PaketA#1/VOR"), ereignisse);
    }

    @Test
    void headerCarriesNumberTickAndPhase() {
        List<PaketKopf> koepfe = new ArrayList<>();
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "kopf", (p, k) -> koepfe.add(k));
        PaketBus.tickFuerTests(42);

        PaketBus.vor(new PaketA());
        PaketBus.vor(new PaketA());

        assertEquals(2, koepfe.size());
        assertEquals(1, koepfe.get(0).laufendeNummer());
        assertEquals(2, koepfe.get(1).laufendeNummer());
        assertEquals(42, koepfe.get(0).tick());
        assertEquals(PaketPhase.VOR, koepfe.get(0).phase());
        assertTrue(koepfe.get(1).nanos() >= koepfe.get(0).nanos());
    }

    @Test
    void numbersEveryPacketEvenWithoutAnySubscriber() {
        long erste = PaketBus.vor(new PaketA());
        long zweite = PaketBus.vor(new PaketB());

        assertEquals(1, erste);
        assertEquals(2, zweite);
        assertEquals(2, PaketBus.laufendeNummer());
    }

    @Test
    void numberingRestartsWhenTheConnectionEndsAndNeverRepeatsWithinOne() {
        long a = PaketBus.vor(new PaketA());
        long b = PaketBus.vor(new PaketA());
        // Ein neues Login-Paket innerhalb derselben Verbindung (Proxy-Serverwechsel) setzt nichts zurueck: weiter mit 3
        long c = PaketBus.vor(new PaketB());

        PaketBus.verbindungGetrennt();
        long neu = PaketBus.vor(new PaketA());

        assertEquals(1, a);
        assertEquals(2, b);
        assertEquals(3, c);
        assertEquals(1, neu, "nach dem Trennen beginnt die naechste Verbindung wieder bei 1");
    }

    @Test
    void aFailingSubscriberNeitherStopsOthersNorEscapes() {
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "kaputt", (p, k) -> {
            throw new IllegalStateException("absichtlich");
        });
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "gesund", (p, k) -> merken("gesund", p, k));

        PaketBus.vor(new PaketA());

        assertEquals(List.of("gesund:PaketA#1/VOR"), ereignisse);
        assertEquals(1, PaketBus.fehlerGesamt());
    }

    @Test
    void aSubscriberIsSwitchedOffAfterTooManyErrorsAndTheOthersKeepRunning() {
        PaketBus.Abo kaputt = PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "kaputt", (p, k) -> {
            throw new IllegalStateException("absichtlich");
        });
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "gesund", (p, k) -> merken("gesund", p, k));

        for (int i = 0; i < PaketBus.MAX_FEHLER_JE_ABONNENT + 5; i++) {
            PaketBus.vor(new PaketA());
        }

        assertFalse(kaputt.aktiv());
        assertEquals(PaketBus.MAX_FEHLER_JE_ABONNENT, PaketBus.fehlerGesamt(), "nach dem Abschalten kommen keine Fehler mehr dazu");
        assertEquals(PaketBus.MAX_FEHLER_JE_ABONNENT + 5, ereignisse.size());
        assertEquals(1, PaketBus.abonnenten());
    }

    @Test
    void theWholeBusSwitchesOffAfterTooManyErrorsInTotal() {
        // Viele verschiedene Abonnenten, jeder bleibt unter seiner Grenze, zusammen ueberschreiten sie die Gesamtgrenze
        int abonnenten = PaketBus.MAX_FEHLER_GESAMT / (PaketBus.MAX_FEHLER_JE_ABONNENT - 1) + 1;
        for (int i = 0; i < abonnenten; i++) {
            PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "kaputt" + i, (p, k) -> {
                throw new IllegalStateException("absichtlich");
            });
        }
        PaketBus.abonnieren(PaketB.class, PaketPhase.VOR, "andererTyp", (p, k) -> merken("b", p, k));

        for (int i = 0; i < PaketBus.MAX_FEHLER_JE_ABONNENT - 1; i++) {
            PaketBus.vor(new PaketA());
        }

        assertTrue(PaketBus.ausgeschaltet());
        PaketBus.vor(new PaketB());
        assertTrue(ereignisse.isEmpty(), "ein abgeschalteter Bus liefert nichts mehr");
    }

    @Test
    void unsubscribingStopsDeliveryAndIsSafeFromInsideACallback() {
        PaketBus.Abo[] eigenes = new PaketBus.Abo[1];
        eigenes[0] = PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "einmal", (p, k) -> {
            merken("einmal", p, k);
            eigenes[0].abmelden();
        });
        PaketBus.abonnieren(PaketA.class, PaketPhase.VOR, "immer", (p, k) -> merken("immer", p, k));

        PaketBus.vor(new PaketA());
        PaketBus.vor(new PaketA());

        assertEquals(List.of("einmal:PaketA#1/VOR", "immer:PaketA#1/VOR", "immer:PaketA#2/VOR"), ereignisse);
        assertFalse(eigenes[0].aktiv());
        assertEquals(1, PaketBus.abonnenten());
    }

    @Test
    void packetsNobodyListensToDoNotWakeSubscribersOfOtherTypes() {
        PaketBus.abonnieren(PaketB.class, PaketPhase.NACH, "b", (p, k) -> merken("b", p, k));

        long nummer = PaketBus.vor(new PaketA());
        PaketBus.nach(new PaketA(), nummer);

        assertTrue(ereignisse.isEmpty());
    }
}
