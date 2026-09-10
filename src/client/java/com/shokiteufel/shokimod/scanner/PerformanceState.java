package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

/**
 * Bilder je Sekunde, Server-Takt und Antwortzeit.
 *
 * Zwei der drei Zahlen liegen bereit: Die Bildrate fuehrt Minecraft selbst, die
 * Antwortzeit steht in der Spielerliste. Der Server-Takt steht nirgends - er wird
 * gemessen.
 *
 * <p><b>Wie der Takt gemessen wird:</b> Der Server schickt seine Uhrzeit alle zwanzig
 * Ticks, also bei vollem Takt einmal je Sekunde. Kommt sie spaeter, laeuft der Server
 * langsamer: Aus dem Abstand zwischen zwei Zeitangaben ergibt sich der Takt. Gemittelt
 * wird ueber die letzten fuenf, sonst zappelt die Zahl bei jedem Netzwerk-Schluckauf.
 */
public final class PerformanceState {

    /** Der Server schickt seine Uhrzeit alle zwanzig Ticks */
    private static final int TICKS_PER_UPDATE = 20;
    private static final int SAMPLES = 5;
    /** Mehr als das ist kein Takt mehr, sondern ein Aussetzer - solche Werte verzerren */
    private static final long MAX_GAP_MILLIS = 10_000L;
    /** Ohne frische Zeitangabe ist die Zahl von gestern */
    private static final long STALE_MILLIS = 15_000L;

    private static final double[] samples = new double[SAMPLES];
    private static int filled = 0;
    private static int next = 0;
    private static long lastUpdateAt = 0L;
    /** Wie oft eine Ping-Anfrage losgeht - oefter braucht es nicht */
    private static final long PING_GAP_MILLIS = 2_000L;
    /** Gemittelt wird ueber so viele Antworten - eine einzelne zappelt zu sehr */
    private static final int PING_SAMPLES = 5;
    private static final long[] pings = new long[PING_SAMPLES];
    private static int pingFilled = 0;
    private static int pingNext = 0;
    private static long lastPingSentAt = 0L;
    private static long lastPingAt = 0L;

    private PerformanceState() {
    }

    /** Aufgerufen, sobald der Server seine Uhrzeit schickt */
    public static void onTimeUpdate() {
        long now = System.currentTimeMillis();
        long vorher = lastUpdateAt;
        lastUpdateAt = now;
        if (vorher == 0L) return;

        long abstand = now - vorher;
        if (abstand <= 0 || abstand > MAX_GAP_MILLIS) return;
        // Zwanzig Ticks in dieser Zeit - daraus der Takt je Sekunde
        double tps = TICKS_PER_UPDATE * 1000.0 / abstand;
        samples[next] = Math.min(tps, TICKS_PER_UPDATE);
        next = (next + 1) % SAMPLES;
        if (filled < SAMPLES) filled++;
    }

    /** Nach einem Weltwechsel gilt die alte Messung nicht mehr */
    public static void reset() {
        filled = 0;
        next = 0;
        lastUpdateAt = 0L;
        pingFilled = 0;
        pingNext = 0;
        lastPingAt = 0L;
        lastPingSentAt = 0L;
    }

    /** Bilder je Sekunde, wie Minecraft sie zaehlt */
    public static int fps() {
        return Minecraft.getInstance().getFps();
    }

    /**
     * Antwortzeit in Millisekunden - selbst gemessen, nicht abgeschrieben.
     *
     * Die Zahl in der Spielerliste taugt dafuer nicht: Hypixel traegt dort etwas ein,
     * das mit der Laufzeit zum Server nichts zu tun hat - im Vergleich mit Odin stand
     * dort 1 ms, waehrend die Strecke tatsaechlich 155 ms brauchte. Gemessen wird
     * deshalb selbst: eine Anfrage hin, die Antwort zurueck, die Spanne dazwischen ist
     * die Umlaufzeit. Gemittelt ueber die letzten fuenf, sonst springt die Zahl.
     */
    public static int ping() {
        if (pingFilled > 0 && Util.getMillis() - lastPingAt < STALE_MILLIS) {
            long summe = 0;
            for (int i = 0; i < pingFilled; i++) summe += pings[i];
            return (int) (summe / pingFilled);
        }
        // Solange noch nichts zurueckkam, lieber die Zahl aus der Spielerliste als gar
        // keine - auf anderen Servern als Hypixel stimmt sie
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return -1;
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    /**
     * Eine Anfrage losschicken, wenn die letzte lange genug her ist.
     *
     * Aufgerufen im Takt des Spiels. Die Zeit steht in der Anfrage selbst, der Server
     * schickt sie unveraendert zurueck - daraus ergibt sich die Umlaufzeit, ohne dass
     * hier etwas gemerkt werden muss.
     *
     * <p>Gestempelt wird mit {@link Util#getMillis()}, nicht mit der Kalenderuhr. Das
     * ist dieselbe Uhr, die Minecraft fuer seine eigenen Anfragen nimmt (die hinter
     * F3) - so passen beide zusammen und deren Antworten zaehlen einfach mit. Die
     * Kalenderuhr waere hier ohnehin falsch: Sie kann waehrend der Messung gestellt
     * werden, die andere laeuft immer vorwaerts.
     */
    public static void tickPing(Minecraft client) {
        if (client == null || client.player == null || client.getConnection() == null) return;
        long now = Util.getMillis();
        if (now - lastPingSentAt < PING_GAP_MILLIS) return;
        lastPingSentAt = now;
        try {
            client.getConnection().send(new ServerboundPingRequestPacket(now));
        } catch (RuntimeException e) {
            // Ein Server, der damit nichts anfangen kann, soll den Takt nicht stoeren
        }
    }

    /** Die Antwort des Servers - sie traegt die Zeit der Anfrage zurueck */
    public static void onPong(long sentAt) {
        long now = Util.getMillis();
        long dauer = now - sentAt;
        // Unsinnige Werte verwerfen: negative Zeiten und alles ueber einer Minute
        if (dauer < 0 || dauer > 60_000L) return;
        pings[pingNext] = dauer;
        pingNext = (pingNext + 1) % PING_SAMPLES;
        if (pingFilled < PING_SAMPLES) pingFilled++;
        lastPingAt = now;
    }

    /** Server-Takt, oder -1 solange noch nicht genug gemessen wurde */
    public static double tps() {
        if (filled == 0) return -1;
        if (System.currentTimeMillis() - lastUpdateAt > STALE_MILLIS) return -1;
        double summe = 0;
        for (int i = 0; i < filled; i++) summe += samples[i];
        return summe / filled;
    }

    /** Eine Zeile fuer den Diagnosebericht */
    public static String status() {
        double tps = tps();
        return "performance: " + fps() + " fps, "
                + (tps < 0 ? "tps unknown" : String.format(java.util.Locale.US, "%.1f tps", tps))
                + ", " + (ping() < 0 ? "ping unknown" : ping() + "ms");
    }
}
