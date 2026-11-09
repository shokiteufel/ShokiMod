package com.shokiteufel.shokimod.paket;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueft Weg 2 der Paket-Schicht: ausgehende Pakete, im Netzwerk-Thread bearbeitete eingehende Pakete und die Phasen.
 * Die Mixins selbst laufen nur im Spiel (belegt im Dev-Client); hier die Logik dahinter, mit echten Paketobjekten.
 */
class PaketDirektTest {
    private final List<String> ereignisse = new ArrayList<>();
    private final List<PaketKopf> koepfe = new ArrayList<>();
    private final List<PaketDirekt.Aufgabe> warteschlange = new ArrayList<>();
    private boolean hauptThread = true;
    private final PaketDirekt.Hoerer hoerer = new PaketDirekt.Hoerer();

    @BeforeEach
    @AfterEach
    void zuruecksetzen() {
        PaketBus.zuruecksetzenFuerTests();
        PaketDirekt.zuruecksetzenFuerTests();
        ereignisse.clear();
        koepfe.clear();
        warteschlange.clear();
        hauptThread = true;
        PaketDirekt.ersetzenFuerTests(warteschlange::add, () -> hauptThread);
    }

    private void alleMitschreiben() {
        PaketBus.abonnierenAlle(PaketPhase.VOR, "vor", (p, k) -> {
            ereignisse.add("VOR " + p.getClass().getSimpleName() + " #" + k.laufendeNummer());
            koepfe.add(k);
        });
        PaketBus.abonnierenAlle(PaketPhase.NACH, "nach", (p, k) -> {
            ereignisse.add("NACH " + p.getClass().getSimpleName() + " #" + k.laufendeNummer());
            koepfe.add(k);
        });
    }

    private static PacketListener empfaenger(PacketFlow flow, ConnectionProtocol protokoll) {
        return new PacketListener() {
            @Override
            public PacketFlow flow() {
                return flow;
            }

            @Override
            public ConnectionProtocol protocol() {
                return protokoll;
            }

            @Override
            public void onDisconnect(DisconnectionDetails details) {
            }

            @Override
            public boolean isAcceptingMessages() {
                return true;
            }
        };
    }

    // ------------------------------------------------------------------ ausgehend

    @Test
    void anOutgoingPacketFromTheMainThreadIsDeliveredAtOnceBeforeAndAfterWithOneNumber() {
        alleMitschreiben();
        ServerboundKeepAlivePacket paket = new ServerboundKeepAlivePacket(7L);

        PaketAusgang.gesendet(paket, PacketFlow.CLIENTBOUND, hoerer);

        assertEquals(List.of("VOR ServerboundKeepAlivePacket #1", "NACH ServerboundKeepAlivePacket #1"), ereignisse);
        PaketKopf kopf = koepfe.get(0);
        assertEquals(PaketRichtung.AUSGEHEND, kopf.richtung());
        assertTrue(kopf.direkt(), "ausgehende Pakete kommen direkt: VOR und NACH nacheinander");
        assertEquals(ConnectionProtocol.PLAY, kopf.protokoll(), "KeepAlive gehoert zu mehreren Phasen: der Empfaenger entscheidet");
        assertTrue(warteschlange.isEmpty(), "im Haupt-Thread braucht es keinen Umweg ueber die Warteschlange");
    }

    @Test
    void anOutgoingPacketFromAnotherThreadWaitsInTheQueueAndKeepsItsPlaceInOrder() {
        alleMitschreiben();
        hauptThread = false;

        PaketAusgang.gesendet(new ServerboundKeepAlivePacket(1L), PacketFlow.CLIENTBOUND, hoerer);
        PaketAusgang.gesendet(new ServerboundKeepAlivePacket(2L), PacketFlow.CLIENTBOUND, hoerer);

        assertEquals(2, warteschlange.size());
        assertTrue(ereignisse.isEmpty(), "bis der Haupt-Thread die Warteschlange abarbeitet, kommt nichts an");
        for (PaketDirekt.Aufgabe aufgabe : warteschlange) {
            aufgabe.handle(hoerer);
        }
        assertEquals(List.of("VOR ServerboundKeepAlivePacket #1", "NACH ServerboundKeepAlivePacket #1",
                "VOR ServerboundKeepAlivePacket #2", "NACH ServerboundKeepAlivePacket #2"), ereignisse);
    }

    @Test
    void thePacketsOfTheIntegratedServerAndClientboundPacketsAreNotReportedAsOutgoing() {
        alleMitschreiben();

        PaketAusgang.gesendet(new ServerboundKeepAlivePacket(1L), PacketFlow.SERVERBOUND, hoerer);   // Verbindung des Servers
        PaketAusgang.gesendet(new ClientboundKeepAlivePacket(1L), PacketFlow.CLIENTBOUND, hoerer);   // Paket in Richtung Client

        assertTrue(ereignisse.isEmpty());
        assertEquals(0, PaketBus.laufendeNummer());
    }

    @Test
    void theHandshakePacketStartsANewConnectionWithNumberOne() {
        alleMitschreiben();
        PaketAbgriff.direkt(new ServerboundKeepAlivePacket(1L), PaketRichtung.AUSGEHEND, ConnectionProtocol.PLAY);
        PaketAbgriff.direkt(new ServerboundKeepAlivePacket(2L), PaketRichtung.AUSGEHEND, ConnectionProtocol.PLAY);
        assertEquals(2, PaketBus.laufendeNummer());
        ereignisse.clear();

        PaketAbgriff.direkt(new ClientIntentionPacket(776, "localhost", 25565, ClientIntent.LOGIN),
                PaketRichtung.AUSGEHEND, ConnectionProtocol.HANDSHAKING);

        assertEquals(List.of("VOR ClientIntentionPacket #1", "NACH ClientIntentionPacket #1"), ereignisse);
    }

    // ------------------------------------------------------------------ eingehend, im Netzwerk-Thread bearbeitet

    @Test
    void aPacketTheGameQueuedItselfIsLeftToTheMainPath() {
        ClientboundKeepAlivePacket paket = new ClientboundKeepAlivePacket(5L);

        PaketDirekt.beginn(empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY));
        PaketDirekt.eingereiht(paket);          // das Spiel stellt es in die Warteschlange
        PaketDirekt.ende(paket);

        assertTrue(warteschlange.isEmpty(), "ein eingereihtes Paket meldet der Weg 1; Weg 2 darf es nicht doppelt bringen");
    }

    @Test
    void aPacketTheGameHandledInTheNetworkThreadIsQueuedOnceWithItsPhase() {
        alleMitschreiben();
        ClientboundKeepAlivePacket paket = new ClientboundKeepAlivePacket(5L);

        PaketDirekt.beginn(empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.CONFIGURATION));
        PaketDirekt.ende(paket);                // nicht eingereiht: schon bearbeitet

        assertEquals(1, warteschlange.size());
        assertTrue(ereignisse.isEmpty(), "erst der Haupt-Thread meldet es dem Bus");
        warteschlange.get(0).handle(hoerer);
        assertEquals(List.of("VOR ClientboundKeepAlivePacket #1", "NACH ClientboundKeepAlivePacket #1"), ereignisse);
        PaketKopf kopf = koepfe.get(0);
        assertEquals(PaketRichtung.EINGEHEND, kopf.richtung());
        assertEquals(ConnectionProtocol.CONFIGURATION, kopf.protokoll(), "die Phase vor der Bearbeitung zaehlt");
        assertTrue(kopf.direkt());
    }

    @Test
    void theStateOfOnePacketDoesNotLeakIntoTheNext() {
        ClientboundKeepAlivePacket erstes = new ClientboundKeepAlivePacket(1L);
        ClientboundKeepAlivePacket zweites = new ClientboundKeepAlivePacket(2L);
        PacketListener spiel = empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY);

        PaketDirekt.beginn(spiel);
        PaketDirekt.eingereiht(erstes);
        PaketDirekt.ende(erstes);
        PaketDirekt.beginn(spiel);
        PaketDirekt.ende(zweites);              // dieses wurde nicht eingereiht

        assertEquals(1, warteschlange.size(), "nur das zweite Paket kommt ueber Weg 2");
    }

    @Test
    void aPacketWithoutAReceiverIsStillQueuedWithAnUnknownPhase() {
        PaketDirekt.beginn(null);
        PaketDirekt.ende(new ClientboundKeepAlivePacket(1L));

        assertFalse(warteschlange.isEmpty(), "auch ohne Empfaenger geht das Paket nicht verloren");
        PaketDirekt.Aufgabe aufgabe = warteschlange.get(0);
        alleMitschreiben();
        aufgabe.handle(hoerer);
        assertNull(koepfe.get(0).protokoll(), "die Phase ist dann unbekannt (null)");
    }

    @Test
    void theNumberDoesNotDependOnWhoSubscribed() {
        PaketAbgriff.direkt(new ServerboundKeepAlivePacket(1L), PaketRichtung.AUSGEHEND, ConnectionProtocol.PLAY);
        PaketAbgriff.direkt(new ServerboundKeepAlivePacket(2L), PaketRichtung.AUSGEHEND, ConnectionProtocol.PLAY);
        alleMitschreiben();

        PaketAbgriff.direkt(new ServerboundKeepAlivePacket(3L), PaketRichtung.AUSGEHEND, ConnectionProtocol.PLAY);

        assertEquals(List.of("VOR ServerboundKeepAlivePacket #3", "NACH ServerboundKeepAlivePacket #3"), ereignisse,
                "die Nummer zaehlt auch Pakete, die niemand abonniert hatte");
    }

    // ------------------------------------------------------------------ Weg 1 mit allen Phasen

    @Test
    void theMainPathNowReportsEveryClientPhaseAndSkipsServersAndItsOwnEntries() {
        alleMitschreiben();
        Packet<?> konfig = new ClientboundKeepAlivePacket(1L);

        PaketAbgriff.vor(konfig, empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.CONFIGURATION));
        PaketAbgriff.nach(konfig);
        assertEquals(ConnectionProtocol.CONFIGURATION, koepfe.get(0).protokoll());
        assertFalse(koepfe.get(0).direkt(), "was das Spiel im Haupt-Thread bearbeitet, hat einen echten Stand zwischen VOR und NACH");
        ereignisse.clear();

        PaketAbgriff.vor(new ClientboundKeepAlivePacket(2L), empfaenger(PacketFlow.SERVERBOUND, ConnectionProtocol.PLAY)); // Server im Einzelspieler
        PaketAbgriff.vor(new PaketDirekt.Aufgabe(konfig, PaketRichtung.EINGEHEND, ConnectionProtocol.PLAY), hoerer);       // eigener Eintrag
        PaketAbgriff.vor(new ClientboundKeepAlivePacket(3L), null);

        assertTrue(ereignisse.isEmpty());
    }

    // ------------------------------------------------------------------ Phase aus dem Paketnamen

    @Test
    void thePhaseOfOutgoingPacketsComesFromThePacketBeforeTheReceiver() {
        PacketListener schonKonfiguration = empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.CONFIGURATION);

        assertEquals(ConnectionProtocol.LOGIN, PaketProtokoll.von(ServerboundLoginAcknowledgedPacket.INSTANCE, schonKonfiguration),
                "LoginAcknowledged geht in der Login-Phase raus, obwohl der Empfaenger schon der der Konfiguration ist");
        assertEquals(ConnectionProtocol.CONFIGURATION, PaketProtokoll.von(ServerboundFinishConfigurationPacket.INSTANCE,
                empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY)));
        assertEquals(ConnectionProtocol.HANDSHAKING,
                PaketProtokoll.von(new ClientIntentionPacket(776, "h", 1, ClientIntent.LOGIN), null));
        assertEquals(ConnectionProtocol.PLAY, PaketProtokoll.von(new ServerboundKeepAlivePacket(1L),
                empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY)), "gemeinsame Pakete: der Empfaenger entscheidet");
        assertEquals(ConnectionProtocol.CONFIGURATION, PaketProtokoll.von(new ServerboundKeepAlivePacket(1L), schonKonfiguration));
        assertNull(PaketProtokoll.von(new ServerboundKeepAlivePacket(1L), null), "gemeinsames Paket ohne Empfaenger: unbekannt");
    }

    @Test
    void thePhaseNamesFollowTheProtocolPackages() {
        assertEquals(ConnectionProtocol.PLAY, PaketProtokoll.nachName("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos"));
        assertEquals(ConnectionProtocol.CONFIGURATION, PaketProtokoll.nachName("net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks"));
        assertEquals(ConnectionProtocol.LOGIN, PaketProtokoll.nachName("net.minecraft.network.protocol.login.ServerboundHelloPacket"));
        assertEquals(ConnectionProtocol.STATUS, PaketProtokoll.nachName("net.minecraft.network.protocol.status.ServerboundStatusRequestPacket"));
        assertNull(PaketProtokoll.nachName("net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket"));
        assertNull(PaketProtokoll.nachName("net.minecraft.network.protocol.ping.ServerboundPingRequestPacket"));
        assertNull(PaketProtokoll.nachName("com.andere.Mod$Paket"));
    }

    // ------------------------------------------------------------------ Absturzschutz

    @Test
    void theQueueEntryNeverThrowsEvenIfASubscriberDoes() {
        PaketBus.abonnierenAlle(PaketPhase.VOR, "kaputt", (p, k) -> {
            throw new IllegalStateException("Abonnent kaputt");
        });
        PaketDirekt.Aufgabe aufgabe = new PaketDirekt.Aufgabe(new ClientboundKeepAlivePacket(1L), PaketRichtung.EINGEHEND, ConnectionProtocol.PLAY);

        assertDoesNotThrow(() -> aufgabe.handle(hoerer));
        assertDoesNotThrow(() -> new PaketDirekt.Aufgabe(null, PaketRichtung.EINGEHEND, null).handle(hoerer));
        assertDoesNotThrow(() -> hoerer.onPacketError(new ClientboundKeepAlivePacket(1L), new RuntimeException("Handler-Fehler")),
                "Vanilla wuerde hier einen Absturzbericht erzeugen; der eigene Eintrag verschluckt den Fehler");
    }

    @Test
    void theDirectPathSwitchesItselfOffAfterTooManyErrorsAndNothingElseBreaks() {
        PaketDirekt.ersetzenFuerTests(aufgabe -> {
            throw new IllegalStateException("Warteschlange kaputt");
        }, () -> true);
        PacketListener spiel = empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY);

        for (int i = 0; i < 20; i++) {
            PaketDirekt.beginn(spiel);
            int n = i;
            assertDoesNotThrow(() -> PaketDirekt.ende(new ClientboundKeepAlivePacket(n)));
        }

        assertTrue(PaketDirekt.abgeschaltet());
        assertTrue(PaketDirekt.fehlerAnzahl() >= 10, "mindestens zehn Fehler gezaehlt");
        assertFalse(PaketBus.ausgeschaltet(), "der Bus selbst bleibt an");
    }

    @Test
    void aRejectedQueueDuringShutdownIsNotCountedAsAnError() {
        PaketDirekt.ersetzenFuerTests(aufgabe -> {
            throw new java.util.concurrent.RejectedExecutionException("Server already shutting down");
        }, () -> true);
        PaketDirekt.beginn(empfaenger(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY));

        PaketDirekt.ende(new ClientboundKeepAlivePacket(1L));

        assertEquals(0, PaketDirekt.fehlerAnzahl());
    }
}
