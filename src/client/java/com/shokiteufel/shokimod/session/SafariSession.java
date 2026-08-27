package com.shokiteufel.shokimod.session;

import com.shokiteufel.shokimod.data.Critters;
import com.shokiteufel.shokimod.data.Critters.Critter;
import com.shokiteufel.shokimod.data.SafariBiome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Safari-Lauf: wer hat was wie oft gefangen.
 *
 * Gebucht wird pro Spieler und Art, damit sich sowohl die eigene Ausbeute als auch die
 * der ganzen Gruppe ablesen laesst. Was die Gruppe faengt, zaehlt fuer den Critterdex
 * genauso - deshalb sind zwei Sichten noetig und nicht eine.
 */
public class SafariSession {

    private final String self;
    private final long started;
    private long ended = 0L;

    /** Spielername -> (Art -> Anzahl) */
    private final Map<String, Map<Critter, Integer>> caught = new LinkedHashMap<>();
    private int totalShards = 0;
    private int attempts = 0;

    public SafariSession(String self, long started) {
        this.self = self == null ? "You" : self;
        this.started = started;
    }

    public String self() {
        return self;
    }

    public long elapsedMillis(long now) {
        return (ended > 0 ? ended : now) - started;
    }

    public void end(long now) {
        if (ended == 0L) ended = now;
    }

    public boolean isRunning() {
        return ended == 0L;
    }

    public void countAttempt() {
        attempts++;
    }

    public int attempts() {
        return attempts;
    }

    public void record(String player, Critter critter, int shards) {
        caught.computeIfAbsent(player == null ? self : player, p -> new LinkedHashMap<>())
                .merge(critter, 1, Integer::sum);
        totalShards += Math.max(0, shards);
    }

    public int totalShards() {
        return totalShards;
    }

    // ---- eigene Ausbeute ----

    public int ownCount(Critter critter) {
        return caught.getOrDefault(self, Map.of()).getOrDefault(critter, 0);
    }

    public int ownUnique() {
        return (int) Critters.ALL.stream().filter(c -> ownCount(c) > 0).count();
    }

    // ---- Ausbeute der ganzen Gruppe ----

    public int partyCount(Critter critter) {
        int sum = 0;
        for (Map<Critter, Integer> byPlayer : caught.values()) {
            sum += byPlayer.getOrDefault(critter, 0);
        }
        return sum;
    }

    public int partyUnique() {
        return (int) Critters.ALL.stream().filter(c -> partyCount(c) > 0).count();
    }

    public int partyUnique(SafariBiome biome) {
        return (int) Critters.inBiome(biome).stream().filter(c -> partyCount(c) > 0).count();
    }

    /** Wie viele Faenge dieser Art noch fehlen, bis sie erledigt ist */
    public int remaining(Critter critter, boolean firstCatchIsEnough) {
        return Math.max(0, critter.required(firstCatchIsEnough) - partyCount(critter));
    }

    public boolean isComplete(Critter critter, boolean firstCatchIsEnough) {
        return remaining(critter, firstCatchIsEnough) == 0;
    }

    public List<Critter> missingIn(SafariBiome biome, boolean firstCatchIsEnough) {
        List<Critter> out = new ArrayList<>();
        for (Critter critter : Critters.inBiome(biome)) {
            if (!isComplete(critter, firstCatchIsEnough)) out.add(critter);
        }
        return out;
    }

    public boolean biomeComplete(SafariBiome biome, boolean firstCatchIsEnough) {
        return missingIn(biome, firstCatchIsEnough).isEmpty();
    }

    public boolean dexComplete(boolean firstCatchIsEnough) {
        for (SafariBiome biome : SafariBiome.values()) {
            if (!biomeComplete(biome, firstCatchIsEnough)) return false;
        }
        return true;
    }

    /** Fangzahl je Spieler, fuer die Aufschluesselung unter den Balken */
    public Map<String, Integer> uniquePerPlayer() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Critter, Integer>> entry : caught.entrySet()) {
            out.put(entry.getKey(), entry.getValue().size());
        }
        return out;
    }
}
