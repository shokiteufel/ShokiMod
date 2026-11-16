package com.shokiteufel.shokimod.handler;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Schickt Befehle nacheinander, mit Abstand.
 *
 * Ein Klick im Chat fuehrt nur einen Befehl aus. Wo mehrere gebraucht werden - erst
 * {@code /gfs OIL_BARREL 5}, eine Sekunde spaeter {@code /call ...} - uebernimmt diese Folge:
 * Der erste Befehl geht im naechsten Tick, jeder weitere erst {@link #GAP_MILLIS} nach dem
 * vorigen. Der Abstand gilt auch zwischen zwei Folgen, die kurz hintereinander kommen, damit
 * Hypixel keine Befehle verschluckt oder bremst.
 *
 * Der Takt kommt aus dem Tick, nicht aus einem Schlaf oder einem eigenen Thread: Es laeuft alles
 * auf dem Hauptthread, und wer die Welt verlaesst, laesst die Reste verfallen.
 */
public final class CommandSequence {

    /** So lange nach einem Befehl wartet der naechste. Eine Sekunde, wie vom User fuer das Auftanken gewuenscht */
    public static final long GAP_MILLIS = 1000L;

    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    /** Fruehestens dann darf der naechste Befehl hinaus */
    private static long nextAt = 0L;

    private CommandSequence() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CommandSequence::tick);
    }

    /** Haengt Befehle hinten an. Mit oder ohne fuehrenden Schraegstrich - gesendet wird ohne */
    public static void enqueue(List<String> commands) {
        for (String command : commands) {
            if (command == null) continue;
            String clean = command.trim();
            if (clean.startsWith("/")) clean = clean.substring(1);
            if (!clean.isEmpty()) queue.add(clean);
        }
    }

    /** Wie viele Befehle noch warten */
    public static int pending() {
        return queue.size();
    }

    public static void clear() {
        queue.clear();
    }

    private static void tick(Minecraft client) {
        if (queue.isEmpty()) return;
        // Ohne Verbindung (Welt verlassen, Verbindung weg) hat der Rest keinen Sinn mehr
        if (client.player == null || client.getConnection() == null) {
            queue.clear();
            return;
        }
        step(System.currentTimeMillis(), client.getConnection()::sendCommand);
    }

    /** Ein Schritt der Folge: sendet den naechsten Befehl, wenn der Abstand vorbei ist. Getrennt vom Tick, damit es sich ohne Spiel pruefen laesst */
    static void step(long now, Consumer<String> sender) {
        if (queue.isEmpty() || now < nextAt) return;
        sender.accept(queue.poll());
        nextAt = now + GAP_MILLIS;
    }

    /** Nur fuer die Tests: leer und ohne Wartezeit */
    static void resetForTests() {
        queue.clear();
        nextAt = 0L;
    }
}
