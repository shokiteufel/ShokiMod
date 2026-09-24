package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Was die Liste je Kennung sonst noch weiss - fuer das Bild eines Items */
    public record Info(String material, String skin, String color, String itemModel) {
    }

    private static final Map<String, Info> byId = new ConcurrentHashMap<>();
    /** Was ein NPC fuer ein Stueck zahlt. Steht in derselben Liste und kostet keinen zweiten Abruf */
    private static final Map<String, Double> npcSell = new ConcurrentHashMap<>();
    /** Der Weg zurueck: Kennung auf den Namen, wie ihn das Spiel schreibt */
    private static final Map<String, String> nameById = new ConcurrentHashMap<>();
    /** Die Seltenheit einer Kennung: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, ... */
    private static final Map<String, String> tierById = new ConcurrentHashMap<>();

    /** Material, Skin, Farbe und Modellverweis einer Kennung, oder null wenn die Liste sie nicht kennt */
    public static Info info(String itemId) {
        if (itemId == null) return null;
        FEED.prefetch();
        return byId.get(itemId);
    }

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

    /**
     * Was der NPC-Haendler je Stueck zahlt, oder -1 wenn die Liste dazu nichts sagt.
     *
     * Fuer alles, was der Basar nicht handelt und wofuer sich im Auktionshaus niemand
     * interessiert, ist das der einzige Preis, den es ueberhaupt gibt - Enchanted
     * Pumpkin etwa.
     */
    /** Der Anzeigename zu einer Kennung, oder null wenn die Liste sie nicht kennt */
    public static String displayName(String itemId) {
        if (itemId == null) return null;
        FEED.prefetch();
        String name = nameById.get(itemId);
        if (name != null) return name;
        // Pets und Buecher tragen ihre Stufe hinter einem Semikolon; die Liste kennt nur den Stamm
        int semicolon = itemId.indexOf(';');
        return semicolon > 0 ? nameById.get(itemId.substring(0, semicolon)) : null;
    }

    /**
     * Die Seltenheit einer Kennung, oder null.
     *
     * Steht in derselben Liste wie Name und NPC-Preis. Bei 4.782 der 5.655 Items ist
     * sie gesetzt; was keine hat, ist gewoehnliches Minecraft-Zeug und damit COMMON.
     */
    public static String tier(String itemId) {
        if (itemId == null) return null;
        FEED.prefetch();
        String tier = tierById.get(itemId);
        if (tier != null) return tier;
        int semicolon = itemId.indexOf(';');
        return semicolon > 0 ? tierById.get(itemId.substring(0, semicolon)) : null;
    }

    public static double npcSellPrice(String itemId) {
        if (itemId == null) return -1;
        FEED.prefetch();
        Double price = npcSell.get(itemId);
        return price == null ? -1 : price;
    }

    /** Ohne Farbcodes, ohne Gross/Klein, ohne Rand - so werden zwei Schreibweisen eine */
    static String normalize(String displayName) {
        if (displayName == null) return "";
        return COLOUR_CODE.matcher(displayName).replaceAll("").trim().toLowerCase(Locale.ROOT);
    }

    private static String text(JsonObject item, String key) {
        JsonElement value = item.get(key);
        return value == null || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    /** Hypixel legt die Kopftextur als Objekt mit "value" ab, gelegentlich direkt als Text */
    private static String skinOf(JsonObject item) {
        JsonElement skin = item.get("skin");
        if (skin == null) return null;
        if (skin.isJsonPrimitive()) return skin.getAsString();
        if (skin.isJsonObject() && skin.getAsJsonObject().has("value")) {
            return skin.getAsJsonObject().get("value").getAsString();
        }
        return null;
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
            String raw = item.get("name").getAsString();
            String name = normalize(raw);
            if (id.isBlank() || name.isBlank()) continue;
            nameById.put(id, COLOUR_CODE.matcher(raw).replaceAll("").trim());

            out.computeIfAbsent(name, key -> new ArrayList<>(1)).add(id);
            byId.put(id, new Info(text(item, "material"), skinOf(item), text(item, "color"),
                    text(item, "item_model")));

            String tier = text(item, "tier");
            if (tier != null && !tier.isBlank()) tierById.put(id, tier);

            JsonElement npc = item.get("npc_sell_price");
            if (npc != null && npc.isJsonPrimitive()) {
                double coins = npc.getAsDouble();
                if (coins > 0) npcSell.put(id, coins);
            }
        }
        return out;
    }
}
