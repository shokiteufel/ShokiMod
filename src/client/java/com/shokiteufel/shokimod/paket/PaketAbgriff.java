package com.shokiteufel.shokimod.paket;

import com.shokiteufel.shokimod.verkehr.Einordnung;
import java.util.Arrays;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;

/**
 * Wie Pakete in den {@link PaketBus} kommen. Zusammen mit den Mixins in {@code paket.mixin} (und {@link PaketDirekt},
 * {@link PaketAusgang}) die <b>einzige Stelle, die je Minecraft-Version anders sein kann</b> (wie {@code ParticlePacketView}):
 * Auf jedem Zweig unterscheiden sich nur diese Dateien, der Bus und alles, was ihn nutzt, bleibt gleich.
 *
 * <p><b>Weg 1, eingehende Pakete, die das Spiel im Haupt-Thread bearbeitet</b> ({@link #vor(Packet, PacketListener)}): Das
 * Spiel haengt sie in einen {@code PacketProcessor} ein und ruft {@code packet.handle(listener)} dann im Haupt-Thread auf,
 * genau einmal je Paket und in Ankunftsreihenfolge. Das Mixin meldet dort {@link #vor} und {@link #nach}. (Die Methoden der
 * {@code ClientPacketListener} laufen dagegen zweimal, erst im Netzwerk-Thread, dann im Haupt-Thread; dort einzuhaken
 * wuerde jedes Paket doppelt zaehlen.)
 *
 * <p><b>Weg 2, alles andere</b> ({@link #direkt}): Eingehende Pakete, die Vanilla schon im Netzwerk-Thread bearbeitet und nie
 * in den {@code PacketProcessor} stellt (ChunkBatch, KeepAlive, Pong, Disconnect, Custom Payloads unbekannter Kanaele,
 * Pakete der Login- und Konfigurationsphase), und alle ausgehenden Pakete. Sie kommen als eigener Eintrag in dieselbe
 * Warteschlange des {@code PacketProcessor} (siehe {@link PaketDirekt}) und stehen damit in genau der Ankunftsreihenfolge
 * zwischen den anderen Paketen; ausgehende Pakete aus dem Haupt-Thread werden gleich beim Senden geliefert
 * ({@link PaketAusgang}). {@code VOR} und {@code NACH} kommen dort unmittelbar nacheinander ({@link PaketKopf#direkt()}).
 *
 * <p>Buendel: Hypixel schickt viele Pakete als {@code ClientboundBundlePacket}. Das Spiel ruft deren Teilpakete direkt
 * auf, ohne die zentrale Stelle zu passieren. Der Abgriff loest ein Buendel deshalb auf und meldet jedes Teilpaket mit
 * eigener laufender Nummer: {@code VOR} aller Teilpakete vor dem Buendel, {@code NACH} aller danach. Das Buendel selbst und
 * die Trennpakete ({@code BundleDelimiter}) erscheinen nicht.
 *
 * <p>Alles hier laeuft im Haupt-Thread. Fehler werden gefangen; ein Fehler im Abgriff darf nie die Verarbeitung eines
 * Pakets durch das Spiel stoeren.
 */
public final class PaketAbgriff {
    /** Das Paket (oder Buendel), das gerade bearbeitet wird; null, wenn nichts gemeldet wurde. */
    private static Packet<?> aktuell;
    private static ConnectionProtocol aktuellesProtokoll;
    private static Packet<?>[] teile = new Packet<?>[8];
    /** Die vergebenen Nummern je Teilpaket: Sendet ein Abonnent beim {@code VOR} selbst ein Paket, steht dessen Nummer mittendrin. */
    private static long[] nummern = new long[8];
    /** Die Einordnung je Teilpaket, wie das {@code VOR} sie geliefert hat (null, wenn es keinen Empfaenger gab). */
    private static Einordnung[] einordnungen = new Einordnung[8];
    private static int anzahl;

    private PaketAbgriff() {
    }

    /**
     * Das Spiel bearbeitet gleich dieses Paket (Weg 1). Meldet nur Pakete, die ein Client-Empfaenger bearbeitet; Eintraege
     * des Servers im Einzelspieler und die eigenen Eintraege von {@link PaketDirekt} werden nicht gemeldet.
     */
    public static void vor(Packet<?> paket, PacketListener empfaenger) {
        ConnectionProtocol protokoll = null;
        boolean gemeldet = false;
        try {
            if (empfaenger != null && empfaenger.flow() == PacketFlow.CLIENTBOUND && !(paket instanceof PaketDirekt.Aufgabe)) {
                protokoll = empfaenger.protocol();
                gemeldet = true;
            }
        } catch (Throwable t) {
            PaketBus.abgriffFehler(t);
        }
        vorIntern(paket, gemeldet, protokoll);
    }

    /**
     * Wie {@link #vor(Packet, PacketListener)}, wenn der Aufrufer schon weiss, ob es ein Paket der Spiel-Phase des Clients
     * ist (andere werden nicht gemeldet).
     */
    public static void vor(Packet<?> paket, boolean spielPhase) {
        vorIntern(paket, spielPhase, ConnectionProtocol.PLAY);
    }

    private static void vorIntern(Packet<?> paket, boolean melden, ConnectionProtocol protokoll) {
        aktuell = null;
        if (!melden || paket == null || PaketBus.ausgeschaltet()) {
            return;
        }
        try {
            anzahl = 0;
            if (paket instanceof BundlePacket<?> buendel) {
                for (Packet<?> teil : buendel.subPackets()) {
                    hinzufuegen(teil);
                }
            } else {
                hinzufuegen(paket);
            }
            if (anzahl == 0) {
                return;
            }
            aktuell = paket;
            aktuellesProtokoll = protokoll;
            // Anzahl und Teile stehen fest, bevor ein Abonnent laeuft; ein Abonnent darf also senden, ohne die Liste zu stoeren
            int n = anzahl;
            Packet<?>[] meine = teile;
            long[] nr = nummern;
            Einordnung[] eo = einordnungen;
            for (int i = 0; i < n; i++) {
                nr[i] = PaketBus.vor(meine[i], PaketRichtung.EINGEHEND, protokoll, false);
                eo[i] = PaketBus.letzteEinordnung(); // fuer das NACH: VOR und NACH tragen dieselbe Einordnung
            }
        } catch (Throwable t) {
            aktuell = null;
            leeren();
            PaketBus.abgriffFehler(t);
        }
    }

    /** Das Spiel hat das Paket bearbeitet. Wird nicht gemeldet, wenn die Bearbeitung einen Fehler geworfen hat. */
    public static void nach(Packet<?> paket) {
        if (aktuell == null || aktuell != paket) {
            return;
        }
        aktuell = null;
        try {
            for (int i = 0; i < anzahl; i++) {
                PaketBus.nach(teile[i], nummern[i], PaketRichtung.EINGEHEND, aktuellesProtokoll, false, einordnungen[i]);
            }
        } catch (Throwable t) {
            PaketBus.abgriffFehler(t);
        } finally {
            leeren();
        }
    }

    /**
     * Weg 2: Liefert ein Paket, das das Spiel nicht im Haupt-Thread um den Bus herum bearbeitet, mit {@code VOR} und
     * {@code NACH} unmittelbar nacheinander. Nur im Haupt-Thread aufrufen. Wirft nie.
     *
     * @param protokoll Verbindungsphase des Pakets, {@code null} wenn unbekannt
     */
    static void direkt(Packet<?> paket, PaketRichtung richtung, ConnectionProtocol protokoll) {
        if (paket == null || PaketBus.ausgeschaltet()) {
            return;
        }
        try {
            if (richtung == PaketRichtung.AUSGEHEND && paket instanceof ClientIntentionPacket) {
                PaketBus.neueVerbindung(); // das Handshake-Paket eroeffnet eine Verbindung und traegt wieder die Nummer 1
            }
            long nummer = PaketBus.vor(paket, richtung, protokoll, true);
            PaketBus.nach(paket, nummer, richtung, protokoll, true, PaketBus.letzteEinordnung());
        } catch (Throwable t) {
            PaketBus.abgriffFehler(t);
        }
    }

    private static void hinzufuegen(Packet<?> teil) {
        if (anzahl == teile.length) {
            teile = Arrays.copyOf(teile, teile.length * 2);
            nummern = Arrays.copyOf(nummern, nummern.length * 2);
            einordnungen = Arrays.copyOf(einordnungen, einordnungen.length * 2);
        }
        teile[anzahl++] = teil;
    }

    /** Gibt die gemerkten Pakete frei, damit der Abgriff keines festhaelt. */
    private static void leeren() {
        Arrays.fill(teile, 0, anzahl, null);
        Arrays.fill(einordnungen, 0, anzahl, null);
        anzahl = 0;
    }
}
