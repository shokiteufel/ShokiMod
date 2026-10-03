package com.shokiteufel.shokimod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Das SkyBlock-Jahr zu einem Zeitpunkt - und wer es regiert.
 *
 * Ein Jahr dauert 124 Stunden echter Zeit: zwoelf Monate zu 31 Tagen, ein Tag zwanzig
 * Minuten. Gerechnet wird vom Anfang des ersten Jahres aus, und das liegt fest; Hypixel
 * verschiebt es nicht. Gegengerechnet am 03.10.2026 mit der Wahl-Schnittstelle: Die
 * Formel ergibt Jahr 517, und dort steht ein Buergermeister, der im Jahr 516 gewaehlt
 * wurde - gewaehlt wird im Spaetsommer, angetreten wird im Jahr darauf. Beides passt.
 *
 * Wer gerade regiert, steht in einer Liste, die ohne Schluessel abrufbar ist. Vergangene
 * Buergermeister fuehrt sie nicht; deshalb schreibt die Mod sie beim Tageswechsel selbst
 * mit, solange sie laeuft.
 */
public final class SkyBlockYear {

    /** Anfang des ersten SkyBlock-Jahres, in Millisekunden */
    private static final long EPOCH = 1_560_275_700_000L;
    /** Ein Jahr: 124 Stunden */
    private static final long YEAR_MILLIS = 446_400_000L;

    public static final RemoteMap<String> ELECTION = new RemoteMap<>(
            "mayor",
            "https://api.hypixel.net/v2/resources/skyblock/election",
            "mayor.json",
            20 * 60 * 1000L,
            SkyBlockYear::parse);

    private SkyBlockYear() {
    }

    /** Das Jahr zu einem Zeitpunkt. Vor dem Anfang gibt es kein Jahr, dann steht hier 0 */
    public static int yearOf(long millis) {
        if (millis < EPOCH) return 0;
        return (int) ((millis - EPOCH) / YEAR_MILLIS) + 1;
    }

    /** Wann ein Jahr angefangen hat */
    public static long startOf(int year) {
        return EPOCH + (long) (year - 1) * YEAR_MILLIS;
    }

    /** Der Buergermeister, der gerade regiert - oder "" */
    public static String mayor() {
        String name = ELECTION.get("mayor");
        return name == null ? "" : name;
    }

    /** Die Vergünstigungen des laufenden Buergermeisters, mit Komma getrennt - oder "" */
    public static String perks() {
        String perks = ELECTION.get("perks");
        return perks == null ? "" : perks;
    }

    public static boolean ready() {
        ELECTION.prefetch();
        return ELECTION.ready();
    }

    private static Map<String, String> parse(JsonObject root) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonObject mayor = root.getAsJsonObject("mayor");
        if (mayor == null) return out;

        if (mayor.has("name")) out.put("mayor", mayor.get("name").getAsString());
        if (mayor.has("key")) out.put("key", mayor.get("key").getAsString());

        JsonObject election = mayor.getAsJsonObject("election");
        if (election != null && election.has("year")) {
            // Gewaehlt wird im Spaetsommer, regiert wird das Jahr darauf
            out.put("year", String.valueOf(election.get("year").getAsInt() + 1));
        }

        StringBuilder perks = new StringBuilder();
        JsonElement liste = mayor.get("perks");
        if (liste != null && liste.isJsonArray()) {
            for (JsonElement perk : liste.getAsJsonArray()) {
                if (!perk.isJsonObject()) continue;
                JsonObject p = perk.getAsJsonObject();
                if (!p.has("name")) continue;
                if (!perks.isEmpty()) perks.append(", ");
                perks.append(p.get("name").getAsString());
            }
        }
        if (!perks.isEmpty()) out.put("perks", perks.toString());
        return out;
    }
}
