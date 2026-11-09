package com.shokiteufel.shokimod.paket;

import com.shokiteufel.shokimod.verkehr.Einordnung;
import net.minecraft.network.ConnectionProtocol;

/**
 * Wann ein Paket bearbeitet wird: die Angaben, die zu jedem Ereignis des {@link PaketBus} gehoeren.
 *
 * @param laufendeNummer Zaehlt <b>jedes</b> Paket einer Verbindung ab 1, ein- und ausgehend, in allen Phasen (Handshake,
 *                       Login, Konfiguration, Spiel), in der Reihenfolge, in der der Bus es im Haupt-Thread liefert.
 *                       Die Nummer haengt nicht davon ab, wer abonniert hat. {@code VOR} und {@code NACH} desselben
 *                       Pakets tragen dieselbe Nummer; Teilpakete eines Buendels zaehlen einzeln. Jede neue Verbindung
 *                       beginnt wieder bei 1 (Beginn: das Handshake-Paket); ein Serverwechsel innerhalb derselben
 *                       Verbindung (Proxy) zaehlt weiter.
 * @param tick           Zaehler der Client-Ticks seit Spielstart. Pakete mit gleichem Wert wurden im selben Tick
 *                       bearbeitet ("kamen zusammen an").
 * @param nanos          {@code System.nanoTime()} beim Abgriff (nur fuer Abstaende, nie fuer die Uhrzeit)
 * @param phase          {@link PaketPhase#VOR} oder {@link PaketPhase#NACH}
 * @param richtung       {@link PaketRichtung#EINGEHEND} (vom Server) oder {@link PaketRichtung#AUSGEHEND} (zum Server)
 * @param protokoll      Verbindungsphase des Pakets (Handshake, Login, Konfiguration, Spiel, Status); {@code null}, wenn sie
 *                       sich nicht bestimmen liess. Dieselbe Paketklasse kann in mehreren Phasen vorkommen
 *                       (zum Beispiel KeepAlive), dann sagt dieses Feld, in welcher.
 * @param direkt         {@code true}: Das Spiel bearbeitet dieses Paket nicht im Haupt-Thread um den Bus herum, deshalb
 *                       liefert der Bus {@code VOR} und {@code NACH} unmittelbar nacheinander und der Spielzustand
 *                       dazwischen ist unveraendert. Gilt fuer alle ausgehenden Pakete (geliefert, bevor das Spiel sie
 *                       abschickt) und fuer eingehende, die Vanilla schon im Netzwerk-Thread bearbeitet hat (ChunkBatch,
 *                       KeepAlive, Pong, Disconnect, Custom Payloads unbekannter Kanaele und weitere): Der Bus
 *                       reiht sie an der Stelle ihrer Ankunft in die Reihenfolge des Haupt-Threads ein, ihre
 *                       Bearbeitung durch das Spiel war aber zu dem Zeitpunkt schon geschehen.
 * @param einordnung     Gehoert das Paket zum eigenen Spieler ({@code EIGEN}), zu anderen ({@code FREMD}), zu beiden
 *                       ({@code GEMISCHT}) oder zu keinem ({@code WELT})? Dazu die Basis (woraus sich das ergibt: Entity-ID,
 *                       UUID, Art des Pakets ...) und die gefundenen Subjekte ({@code ids}, {@code uuids}). Eingeordnet mit dem
 *                       Stand der eigenen ID und UUID, der in <b>Bus-Reihenfolge</b> gilt (siehe {@code PaketEinordner}).
 *                       Ausgehende Pakete sind immer {@code EIGEN}. {@code null}, wenn die Einordnung fehlschlug.
 */
public record PaketKopf(long laufendeNummer, long tick, long nanos, PaketPhase phase,
                        PaketRichtung richtung, ConnectionProtocol protokoll, boolean direkt, Einordnung einordnung) {

    /** Kopf ohne Einordnung. */
    public PaketKopf(long laufendeNummer, long tick, long nanos, PaketPhase phase,
                     PaketRichtung richtung, ConnectionProtocol protokoll, boolean direkt) {
        this(laufendeNummer, tick, nanos, phase, richtung, protokoll, direkt, null);
    }

    /** Kopf eines eingehenden Pakets der Spiel-Phase, das das Spiel im Haupt-Thread bearbeitet (der urspruengliche Fall). */
    public PaketKopf(long laufendeNummer, long tick, long nanos, PaketPhase phase) {
        this(laufendeNummer, tick, nanos, phase, PaketRichtung.EINGEHEND, ConnectionProtocol.PLAY, false, null);
    }
}
