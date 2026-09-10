package com.shokiteufel.shokimod.scanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

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
    }

    /** Bilder je Sekunde, wie Minecraft sie zaehlt */
    public static int fps() {
        return Minecraft.getInstance().getFps();
    }

    /** Antwortzeit in Millisekunden, oder -1 wenn die Spielerliste sie nicht fuehrt */
    public static int ping() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return -1;
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        return info == null ? -1 : info.getLatency();
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
