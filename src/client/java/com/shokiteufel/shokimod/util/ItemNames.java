package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Vom Anzeigenamen zur Kennung, nach Hypixels eigener Liste.
 *
 * Der Chat nennt "Ghostly Boots", die Preislisten kennen nur GHOST_BOOTS. Aus dem
 * Namen laesst sich das nicht raten - deshalb die offizielle Item-Liste, die neun
 * Mods dieses Profils ohnehin abrufen. Sie aendert sich selten und wird einmal am
 * Tag geholt.
 *
 * Manche Namen stehen mehrfach darin, etwa "Adaptive Boots" als gewoehnliche und
 * als STARRED-Fassung. Dann gibt es alle Kennungen zurueck; wer den Preis sucht,
 * nimmt die erste, die eine Liste kennt.
 */
public final class ItemNames {

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    public static final RemoteMap<List<String>> FEED = new RemoteMap<>(
            "item names",
            "https://api.hypixel.net/v2/resources/skyblock/items",
            "item-names.json",
            24 * 60 * 60 * 1000L,
            ItemNames::parse);

    private ItemNames() {
    }

    /** Alle Kennungen zu einem Anzeigenamen. Leer, wenn die Liste ihn nicht kennt */
    public static List<String> idsFor(String displayName) {
        List<String> ids = FEED.get(normalize(displayName));
        return ids == null ? List.of() : ids;
    }

    public static void prefetch() {
        FEED.prefetch();
    }

    /** Ohne Farbcodes, ohne Gross/Klein, ohne Rand - so werden zwei Schreibweisen eine */
    static String normalize(String displayName) {
        if (displayName == null) return "";
        return COLOUR_CODE.matcher(displayName).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<String>> parse(JsonObject root) {
        Map<String, List<String>> out = new HashMap<>();
        if (!root.has("items") || !root.get("items").isJsonArray()) return out;

        JsonArray items = root.getAsJsonArray("items");
        for (JsonElement element : items) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!item.has("id") || !item.has("name")) continue;

            String id = item.get("id").getAsString();
            String name = normalize(item.get("name").getAsString());
            if (id.isBlank() || name.isBlank()) continue;

            out.computeIfAbsent(name, key -> new ArrayList<>(1)).add(id);
        }
        return out;
    }
}
