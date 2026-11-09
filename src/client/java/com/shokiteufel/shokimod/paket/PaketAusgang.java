package com.shokiteufel.shokimod.paket;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * Meldet jedes Paket, das der Client an den Server schickt, an den {@link PaketBus} (Weg 2, siehe {@link PaketAbgriff}).
 * Der Haken sitzt in {@code Connection.sendPacket}, der einen Stelle, durch die jedes gesendete Paket geht (alle
 * {@code send}-Varianten, auch die zurueckgestellten vor dem Verbindungsaufbau).
 *
 * <p>Zeitpunkt: gleich beim Senden, <b>bevor</b> das Spiel das Paket abschickt. Sendet der Haupt-Thread (Klick im
 * Inventar, Hotbar, Bewegung, Chat), laeuft der Bus synchron an dieser Stelle; damit steht das Paket exakt vor den Antworten
 * des Servers und innerhalb des Ticks, der es ausgeloest hat. Sendet ein anderer Thread (der Verbindungsaufbau, Antworten
 * wie KeepAlive und ChunkBatchReceived aus dem Netzwerk-Thread), kommt es als Eintrag in die Warteschlange des Haupt-Threads
 * ({@link PaketDirekt}) und steht dort in der Reihenfolge des Sendens. {@code VOR} und {@code NACH} kommen unmittelbar
 * nacheinander ({@link PaketKopf#direkt()}).
 *
 * <p>Nur lesen: Das Paket wird weder veraendert noch zurueckgehalten. Nur die Pakete des Clients zaehlen (die Verbindung
 * des eingebauten Servers im Einzelspieler sendet Pakete an den Client und wird ausgelassen).
 *
 * <p>Absturzschutz: alles in try/catch; Fehler zaehlt {@link PaketDirekt#fehler}.
 */
public final class PaketAusgang {
    private PaketAusgang() {
    }

    /**
     * Beginn von {@code Connection.sendPacket}.
     *
     * @param empfangeneRichtung die Richtung, in der die Verbindung empfaengt ({@code CLIENTBOUND} = Verbindung des Clients)
     * @param empfaenger         der aktuelle Empfaenger der Verbindung (nur fuer die Phase gemeinsamer Pakete), kann null sein
     */
    public static void gesendet(Packet<?> paket, PacketFlow empfangeneRichtung, PacketListener empfaenger) {
        if (paket == null || empfangeneRichtung != PacketFlow.CLIENTBOUND || PaketDirekt.abgeschaltet() || PaketBus.ausgeschaltet()) {
            return;
        }
        try {
            if (paket.type().flow() != PacketFlow.SERVERBOUND) {
                return;
            }
            ConnectionProtocol protokoll = PaketProtokoll.von(paket, empfaenger);
            if (PaketDirekt.imHauptThread()) {
                PaketAbgriff.direkt(paket, PaketRichtung.AUSGEHEND, protokoll);
            } else {
                PaketDirekt.einreihen(paket, PaketRichtung.AUSGEHEND, protokoll);
            }
        } catch (Throwable t) {
            PaketDirekt.fehler(t);
        }
    }
}
