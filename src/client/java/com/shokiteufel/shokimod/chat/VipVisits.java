package com.shokiteufel.shokimod.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.util.RemoteMap;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Die Spieler, deren Insel man besuchen kann, um Kuchenseelen zu holen - die Vorschlaege hinter {@code /visit} (Chat > Tab Complete > VIP Visits).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code PlayerTabComplete}, {@code VipVisitsJson}). SkyHanni liest die Namen aus seinen Repo-Daten
 * ({@code constants/VipVisits.json}, Schluessel {@code vipVisits}); sie wechseln, wenn jemand die Seelen verteilt. Die Umsetzung ist eigene Arbeit: Java.
 *
 * <p>Zwei Quellen, wie bei {@link SackItems}: die mitgelieferten Namen (Stand 28.11.2026), damit gleich beim ersten Mal etwas da ist, und dieselbe Datei aus dem Netz,
 * einmal am Tag aufgefrischt ({@link RemoteMap}); beide zusammen, ohne Doppelte, die Schreibweise bleibt, wie sie dort steht ("PortalHub"). Versagt das Netz-Teil,
 * gelten die mitgelieferten allein.
 */
public final class VipVisits {

    /** Stand 28.11.2026, aus {@code constants/VipVisits.json} im SkyHanni-REPO */
    static final List<String> BUNDLED = List.of("prtlhub", "PortalHub", "cake_hub", "Hubportal", "MelonKingDe", "Minikloon");

    private static final String SKYHANNI_REPO = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/VipVisits.json";

    /** Die Namen aus dem Netz (nur die Schluessel zaehlen); der letzte Stand bleibt auf der Platte */
    static final RemoteMap<Boolean> FEED = new RemoteMap<>("vip visits", SKYHANNI_REPO, "vip-visits.json", 24 * 60 * 60 * 1000L, VipVisits::parse);

    private static boolean warned = false;

    private VipVisits() {
    }

    /** Aus der Antwort ({@code {"vipVisits": ["prtlhub", ...]}}) die Namen */
    static Map<String, Boolean> parse(JsonObject root) {
        Map<String, Boolean> out = new HashMap<>();
        JsonElement names = root.get("vipVisits");
        if (names == null || !names.isJsonArray()) return out;
        for (JsonElement name : names.getAsJsonArray()) {
            if (name.isJsonPrimitive() && !name.getAsString().isBlank()) out.put(name.getAsString().trim(), Boolean.TRUE);
        }
        return out;
    }

    /** Die Namen jetzt: erst die mitgelieferten in ihrer Reihenfolge, dann was das Netz sonst noch nennt, alphabetisch; ohne Doppelte (Gross und Klein zaehlen als gleich) */
    public static List<String> names() {
        Set<String> all = new LinkedHashSet<>(BUNDLED);
        try {
            FEED.prefetch();
            Set<String> extra = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            extra.addAll(FEED.keys());
            for (String name : BUNDLED) extra.remove(name);
            all.addAll(extra);
        } catch (RuntimeException e) {
            // Die Vorschlaege duerfen nie am Nachgeladenen haengen
            if (!warned) {
                warned = true;
                ShokiMod.LOGGER.warn("[Chat] /visit suggestions go on without the downloaded VIP list: {}", e.toString());
            }
        }
        return List.copyOf(all);
    }
}
