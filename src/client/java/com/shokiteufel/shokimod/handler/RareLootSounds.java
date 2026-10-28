package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.util.AlertVolume;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Toene des Rare-Loot-Alarms, wenn sie nacheinander laufen sollen (Rare Loot > Several sounds).
 *
 * Ein Beutebuendel oder zwei Zeilen kurz hintereinander loesen mehrere Toene aus. Hier wird erst einen Augenblick gesammelt - aus einem
 * Buendel kommen die Funde dicht hintereinander, aber nicht im selben Moment, und der erste soll nicht schon laufen, waehrend die anderen noch
 * eintreffen (dieselbe Pause wie bei den Bannern) -, dann kommt die hoechste Stufe zuerst und die naechste erst, wenn die laufende zu Ende ist.
 * Wer waehrend eines laufenden Tons dazukommt, steht an und wird eingereiht: der hoechste der Wartenden ist als Naechster dran.
 *
 * Dieselbe Stufe steht nur einmal an: Fuenf gleiche Funde aus einem Buendel sind nicht fuenfmal derselbe Ton hintereinander.
 */
final class RareLootSounds {

    /** So lange wird nach dem letzten Zugang noch gesammelt, bevor der erste Ton anfaengt */
    private static final long GATHER_MILLIS = 200L;
    /** Notausgang: Meldet sich ein Ton so lange nicht als beendet, laeuft die Schlange weiter - sonst bliebe sie fuer immer stehen */
    private static final long MAX_PLAY_MILLIS = 20_000L;

    private record Entry(String file, String channel, double rank, double coins, long arrivedAt) {
    }

    /** Der Rueckruf des Players kommt von einem anderen Faden als der Tick: alles hier geht durch diese Sperre */
    private static final Object LOCK = new Object();
    private static final List<Entry> waiting = new ArrayList<>();
    private static boolean playing = false;
    private static long playingSince = 0L;
    private static long lastArrival = 0L;
    /** Zaehlt die gestarteten Toene: Ein Rueckruf eines Tons, den der Notausgang schon abgeloest hat, gilt nicht mehr */
    private static long generation = 0L;

    private RareLootSounds() {
    }

    /**
     * Reiht einen Ton ein.
     *
     * @param rank  wonach gereiht wird: die Schwelle der Stufe, beim Farb-Alarm unendlich
     * @param coins der Wert des Fundes - entscheidet zwischen zwei Toenen gleichen Rangs
     */
    static void add(String file, String channel, double rank, double coins) {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            lastArrival = now;
            for (int i = 0; i < waiting.size(); i++) {
                Entry old = waiting.get(i);
                if (!old.channel().equals(channel)) continue;
                // Dieselbe Stufe steht schon an: der wertvollere Fund haelt den Platz
                if (coins > old.coins()) waiting.set(i, new Entry(file, channel, rank, coins, old.arrivedAt()));
                return;
            }
            waiting.add(new Entry(file, channel, rank, coins, now));
        }
    }

    /** Jeden Tick: fangen die Wartenden an, wenn nichts mehr nachkam und gerade nichts laeuft? */
    static void tick() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (playing && now - playingSince > MAX_PLAY_MILLIS) playing = false;
            if (!playing && !waiting.isEmpty() && now - lastArrival >= GATHER_MILLIS) startNext(now);
        }
    }

    /** Nach einem Weltwechsel gilt nichts Wartendes mehr. Ein laufender Ton laeuft zu Ende */
    static void clear() {
        synchronized (LOCK) {
            waiting.clear();
            playing = false;
            generation++;
        }
    }

    /** Spielt den besten der Wartenden. Die Sperre muss gehalten werden */
    private static void startNext(long now) {
        Entry best = waiting.get(0);
        for (int i = 1; i < waiting.size(); i++) {
            if (better(waiting.get(i), best)) best = waiting.get(i);
        }
        waiting.remove(best);
        playing = true;
        playingSince = now;
        long mine = ++generation;
        // Fehlt die Datei oder scheitert der Ton, ruft der Player den Rueckruf sofort: dann geht es mit dem naechsten weiter
        CustomSoundPlayer.playThen(best.file(), AlertVolume.factor(), best.channel(), () -> finished(mine));
    }

    private static void finished(long mine) {
        synchronized (LOCK) {
            if (mine != generation) return;
            playing = false;
            // Wer waehrend des Tons dazukam, kommt ohne weitere Pause dran
            if (!waiting.isEmpty()) startNext(System.currentTimeMillis());
        }
    }

    /** Hoeherer Rang zuerst, dann der wertvollere Fund, dann der frueher eingetroffene */
    private static boolean better(Entry a, Entry b) {
        int byRank = Double.compare(a.rank(), b.rank());
        if (byRank != 0) return byRank > 0;
        int byCoins = Double.compare(a.coins(), b.coins());
        if (byCoins != 0) return byCoins > 0;
        return a.arrivedAt() < b.arrivedAt();
    }
}
