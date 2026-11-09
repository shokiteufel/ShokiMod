package com.shokiteufel.shokimod.paket;

import com.mojang.logging.LogUtils;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Bringt Pakete in den {@link PaketBus}, die das Spiel <b>nicht</b> im Haupt-Thread bearbeitet (Weg 2, siehe
 * {@link PaketAbgriff}), in die richtige Reihenfolge.
 *
 * <p>Das Problem: Vanilla ruft bei jedem eingehenden Paket erst im Netzwerk-Thread {@code handle} auf. Die meisten Handler
 * stellen das Paket dabei in die Warteschlange des {@code PacketProcessor} (dort greift {@link PaketAbgriff} ein). Einige
 * tun das nicht und erledigen alles gleich im Netzwerk-Thread: ChunkBatchStart/Finished, KeepAlive, PongResponse,
 * DebugSample, PlayerCombatEnter/End, LowDiskSpaceWarning, Disconnect, Custom Payloads unbekannter Kanaele und die meisten
 * Pakete der Login- und Konfigurationsphase. Der Bus sieht sie auf dem ueblichen Weg nie.
 *
 * <p>Die Loesung, ohne das Verhalten des Spiels zu aendern: Zwei Mixins ({@code ConnectionMixin}, {@code PacketProcessorMixin})
 * merken sich im Netzwerk-Thread je Paket, ob das Spiel es eingereiht hat. Wenn nicht, stellt {@link #ende} einen
 * <b>eigenen</b> Eintrag ({@link Aufgabe}) in dieselbe Warteschlange. Er traegt das Paket nur als Nutzlast; wenn der
 * Haupt-Thread ihn abarbeitet, meldet er es dem Bus. Weil die Warteschlange eine FIFO ist, steht der Eintrag genau dort,
 * wo das Paket angekommen ist, zwischen den uebrigen Paketen. Das Paket selbst wird nicht noch einmal bearbeitet und nichts
 * wird verzoegert oder verschluckt.
 *
 * <p>Absturzschutz: Die Hooks fangen alles. Der Eintrag wirft nie (Vanilla macht aus einer Ausnahme in einem Paket-Handler
 * einen Absturzbericht). Nach {@value #MAX_FEHLER} Fehlern schaltet sich dieser Weg ab; der Rest laeuft weiter.
 */
public final class PaketDirekt {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_FEHLER = 10;
    private static final AtomicInteger FEHLER = new AtomicInteger();
    private static volatile boolean aus;
    private static final Hoerer HOERER = new Hoerer();

    /** Je Netzwerk-Thread: Wurde das Paket eingereiht, und in welcher Phase war der Empfaenger, bevor er es bearbeitete? */
    private static final class Zustand {
        Packet<?> eingereiht;
        ConnectionProtocol protokoll;
    }

    private static final ThreadLocal<Zustand> ZUSTAND = ThreadLocal.withInitial(Zustand::new);

    /** Wohin die eigenen Eintraege gehen. Im Spiel immer die Warteschlange des Haupt-Threads; nur Tests ersetzen das. */
    @FunctionalInterface
    interface Warteschlange {
        void einreihen(Aufgabe aufgabe);
    }

    private static volatile Warteschlange warteschlange =
            aufgabe -> Minecraft.getInstance().packetProcessor().scheduleIfPossible(HOERER, aufgabe);
    private static volatile BooleanSupplier hauptThread =
            () -> Minecraft.getInstance().packetProcessor().isSameThread();

    private PaketDirekt() {
    }

    /** Ob der aufrufende Thread der Haupt-Thread ist (der Thread, der die Warteschlange abarbeitet). */
    static boolean imHauptThread() {
        return hauptThread.getAsBoolean();
    }

    /** Nur fuer Tests: ersetzt Warteschlange und Thread-Pruefung (null = wieder die des Spiels). */
    static void ersetzenFuerTests(Warteschlange neueWarteschlange, BooleanSupplier neuerHauptThread) {
        warteschlange = neueWarteschlange != null ? neueWarteschlange
                : aufgabe -> Minecraft.getInstance().packetProcessor().scheduleIfPossible(HOERER, aufgabe);
        hauptThread = neuerHauptThread != null ? neuerHauptThread
                : () -> Minecraft.getInstance().packetProcessor().isSameThread();
    }

    /** Netzwerk-Thread, Beginn von {@code Connection.channelRead0}: merkt die Phase des Empfaengers vor der Bearbeitung. */
    public static void beginn(PacketListener empfaenger) {
        try {
            Zustand z = ZUSTAND.get();
            z.eingereiht = null;
            z.protokoll = empfaenger == null ? null : empfaenger.protocol();
        } catch (Throwable t) {
            fehler(t);
        }
    }

    /** Netzwerk-Thread, Beginn von {@code PacketProcessor.scheduleIfPossible}: Das Spiel reiht dieses Paket ein. */
    public static void eingereiht(Packet<?> paket) {
        try {
            ZUSTAND.get().eingereiht = paket;
        } catch (Throwable t) {
            fehler(t);
        }
    }

    /**
     * Netzwerk-Thread, Ende von {@code Connection.channelRead0}: Hat das Spiel das Paket nicht eingereiht, hat es das Paket
     * schon bearbeitet, und es kommt als eigener Eintrag in die Warteschlange.
     */
    public static void ende(Packet<?> paket) {
        try {
            Zustand z = ZUSTAND.get();
            boolean eingereiht = z.eingereiht == paket;
            ConnectionProtocol protokoll = z.protokoll;
            z.eingereiht = null;
            z.protokoll = null;
            if (eingereiht || paket == null || aus || PaketBus.ausgeschaltet()) {
                return;
            }
            einreihen(paket, PaketRichtung.EINGEHEND, protokoll);
        } catch (Throwable t) {
            fehler(t);
        }
    }

    /**
     * Reiht ein Paket als eigenen Eintrag in die Warteschlange des Haupt-Threads ein (von jedem Thread aus). Wirft nie.
     * Auch fuer ausgehende Pakete, die nicht der Haupt-Thread sendet (zum Beispiel der Verbindungsaufbau).
     */
    static void einreihen(Packet<?> paket, PaketRichtung richtung, ConnectionProtocol protokoll) {
        try {
            if (aus || PaketBus.ausgeschaltet()) {
                return;
            }
            warteschlange.einreihen(new Aufgabe(paket, richtung, protokoll));
        } catch (RejectedExecutionException e) {
            // Das Spiel faehrt herunter: nichts mehr zu melden
        } catch (Throwable t) {
            fehler(t);
        }
    }

    /** Zaehlt einen Fehler dieses Wegs (von jedem Thread aus); ab {@value #MAX_FEHLER} wird er abgeschaltet. */
    static void fehler(Throwable t) {
        int n = FEHLER.incrementAndGet();
        if (n <= 3) {
            LOG.error("ShokiMod Pakete: Fehler im Direktweg (das Spiel bearbeitet das Paket unveraendert weiter), Fehler {}", n, t);
        }
        if (n >= MAX_FEHLER && !aus) {
            aus = true;
            LOG.error("ShokiMod Pakete: Direktweg nach {} Fehlern abgeschaltet; eingehende Pakete des Haupt-Threads und der Bus laufen weiter", n);
        }
    }

    /** Ob dieser Weg wegen Fehlern abgeschaltet wurde. */
    public static boolean abgeschaltet() {
        return aus;
    }

    /** Anzahl bisheriger Fehler dieses Wegs. */
    public static int fehlerAnzahl() {
        return FEHLER.get();
    }

    /** Nur fuer Tests. */
    static void zuruecksetzenFuerTests() {
        aus = false;
        FEHLER.set(0);
    }

    // ------------------------------------------------------------------ der eigene Warteschlangeneintrag

    /**
     * Der Empfaenger des eigenen Eintrags. Tut nichts ausser: nie einen Fehler weitergeben. (Vanilla wirft sonst bei einer
     * Ausnahme im Handler einen Absturzbericht.)
     */
    public static final class Hoerer implements PacketListener {
        @Override
        public PacketFlow flow() {
            return PacketFlow.CLIENTBOUND;
        }

        @Override
        public ConnectionProtocol protocol() {
            return ConnectionProtocol.PLAY;
        }

        @Override
        public void onDisconnect(DisconnectionDetails details) {
        }

        @Override
        public boolean isAcceptingMessages() {
            return true;
        }

        @SuppressWarnings("rawtypes")
        @Override
        public void onPacketError(Packet paket, Exception ausnahme) {
            PaketDirekt.fehler(ausnahme); // verschlucken statt einen Absturzbericht zu erzeugen
        }
    }

    /** Der eigene Eintrag: traegt ein bereits bearbeitetes oder ausgehendes Paket zum Bus. */
    public static final class Aufgabe implements Packet<Hoerer> {
        private static final PacketType<Aufgabe> TYP =
                new PacketType<>(PacketFlow.CLIENTBOUND, Identifier.fromNamespaceAndPath("shokimod", "verkehr_direkt"));

        private final Packet<?> paket;
        private final PaketRichtung richtung;
        private final ConnectionProtocol protokoll;

        Aufgabe(Packet<?> paket, PaketRichtung richtung, ConnectionProtocol protokoll) {
            this.paket = paket;
            this.richtung = richtung;
            this.protokoll = protokoll;
        }

        @Override
        public PacketType<Aufgabe> type() {
            return TYP;
        }

        @Override
        public void handle(Hoerer hoerer) {
            try {
                PaketAbgriff.direkt(this.paket, this.richtung, this.protokoll);
            } catch (Throwable t) {
                fehler(t); // nie weiterwerfen
            }
        }
    }
}
