package com.shokiteufel.shokimod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Die Tabellen, nach denen Estimated Item Value rechnet: Umschmiedungen, Kosten der Sterne, Kosten der Edelstein-Plaetze und die Regeln fuer Buecher.
 *
 * Herkunft: die Tabellen von NotEnoughUpdates (Umschmiedesteine, Essenz-Kosten, Edelsteinplaetze; NEU-REPO) und SkyHanni (Items.json, Enchants.json,
 * SkyHanni-Repo). Es sind Tatsachen ueber das Spiel; sie werden wie die Item-Namen aus dem Netz geholt ({@link RemoteMap}: nur wenn jemand fragt, den
 * letzten Stand auf der Platte) und nicht in die Mod geschrieben, damit ein neues Spiel-Update sie nicht veralten laesst.
 */
public final class EivData {

    private static final String NEU = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/";
    private static final String SKYHANNI = "https://raw.githubusercontent.com/hannibal002/SkyHanni-Repo/main/constants/";
    private static final long DAY = 24L * 60 * 60 * 1000;

    /** Ein Umschmiedestein: der Name der Umschmiedung, ihre Kennung im Gegenstand, der Stein und was das Anbringen je Seltenheit kostet */
    public record Reforge(String name, String modifier, String stone, Map<String, Long> costs) {
    }

    /** Was eine Stufe (oder mehrere zusammen) an Essenz, Coins und Gegenstaenden kosten */
    public record Upgrade(String essenceType, int essence, long coins, Map<String, Integer> items) {

        public Upgrade plus(Upgrade other) {
            Map<String, Integer> sum = new LinkedHashMap<>(items);
            other.items.forEach((id, amount) -> sum.merge(id, amount, Integer::sum));
            return new Upgrade(essenceType, essence + other.essence, coins + other.coins, sum);
        }
    }

    /** Ein Verzauberungs-Abschluss: ab dieser Stufe kommt ein Gegenstand dazu */
    public record Endcap(int requiredLevel, String item) {
    }

    /** Eine Verzauberung, die ein Gegenstand von sich aus hat (nicht mitgerechnet) */
    public record AlwaysActive(int level, Set<String> items) {
    }

    /** Die Regeln fuer den Wert von Verzauberungen und andere Zahlen aus SkyHannis Items.json */
    public record ValueData(Map<String, AlwaysActive> alwaysActive, Set<String> onlyTierOne, Set<String> onlyTierFive,
                            Map<String, List<Endcap>> endcaps, Map<String, Integer> bookBundleAmount,
                            Map<String, Map<String, Integer>> crimsonPrestige, Set<String> legacyGemSlots) {
    }

    public static final RemoteMap<Reforge> REFORGES = new RemoteMap<>(
            "reforge stones", NEU + "reforgestones.json", "eiv-reforgestones.json", DAY, EivData::parseReforges);
    public static final RemoteMap<Map<Integer, Upgrade>> ESSENCE = new RemoteMap<>(
            "essence costs", NEU + "essencecosts.json", "eiv-essencecosts.json", DAY, EivData::parseEssence);
    public static final RemoteMap<Map<String, List<String>>> GEM_SLOTS = new RemoteMap<>(
            "gemstone slots", NEU + "gemstonecosts.json", "eiv-gemstonecosts.json", DAY, EivData::parseGemSlots);
    public static final RemoteMap<ValueData> VALUES = new RemoteMap<>(
            "item value rules", SKYHANNI + "Items.json", "eiv-items.json", DAY, EivData::parseValues);
    public static final RemoteMap<Boolean> STACKING = new RemoteMap<>(
            "stacking enchants", SKYHANNI + "Enchants.json", "eiv-enchants.json", DAY, EivData::parseStacking);

    private EivData() {
    }

    /** Alles anstossen, was gebraucht wird (holt im Hintergrund; macht nichts, solange der Stand frisch ist) */
    public static void prefetch() {
        REFORGES.prefetch();
        ESSENCE.prefetch();
        GEM_SLOTS.prefetch();
        VALUES.prefetch();
        STACKING.prefetch();
    }

    /** Eine Zeile je Tabelle fuer den Diagnosebericht */
    public static String status() {
        return REFORGES.status() + "\n" + ESSENCE.status() + "\n" + GEM_SLOTS.status() + "\n" + VALUES.status() + "\n" + STACKING.status();
    }

    // ------------------------------------------------------------------ Lesen (rein, fuer die Tests)

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return 0L;
        try {
            return e.getAsLong();
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /** Die Kennung der Umschmiedung im Gegenstand: das Feld "nbtModifier" oder der Name klein, ohne Sonderzeichen, Leerzeichen und Striche als Unterstrich */
    static String modifierOf(String reforgeName, String rawModifier) {
        if (rawModifier != null && !rawModifier.isBlank()) return rawModifier;
        String s = reforgeName == null ? "" : reforgeName.toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9\\s_-]", "");
        return s.replaceAll("[\\s-]", "_");
    }

    /** reforgestones.json: Kennung der Umschmiedung -> Umschmiedung. Eintraege ohne Stein zaehlen nicht */
    static Map<String, Reforge> parseReforges(JsonObject root) {
        Map<String, Reforge> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject o = entry.getValue().getAsJsonObject();
            String stone = text(o, "internalName");
            String name = text(o, "reforgeName");
            if (stone == null || stone.isBlank() || name == null) continue;
            Map<String, Long> costs = new HashMap<>();
            if (o.has("reforgeCosts") && o.get("reforgeCosts").isJsonObject()) {
                for (Map.Entry<String, JsonElement> cost : o.getAsJsonObject("reforgeCosts").entrySet()) {
                    if (cost.getValue().isJsonPrimitive()) costs.put(cost.getKey().toUpperCase(Locale.ROOT), cost.getValue().getAsLong());
                }
            }
            String modifier = modifierOf(name, text(o, "nbtModifier"));
            out.put(modifier, new Reforge(name, modifier, stone, costs));
        }
        return out;
    }

    /**
     * essencecosts.json: Kennung des Gegenstands -> Stern -> Kosten dieses einen Sterns. "type" ist die Essenz, "1" bis "15" die Menge je Stern, und unter
     * "items" stehen je Stern weitere Zutaten als "KENNUNG:Anzahl" (SKYBLOCK_COIN sind Coins).
     */
    static Map<String, Map<Integer, Upgrade>> parseEssence(JsonObject root) {
        Map<String, Map<Integer, Upgrade>> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject o = entry.getValue().getAsJsonObject();
            String type = text(o, "type");
            if (type == null) continue;
            Map<Integer, Upgrade> tiers = new TreeMap<>();
            for (int star = 1; star <= 15; star++) {
                JsonElement amount = o.get(Integer.toString(star));
                if (amount == null || !amount.isJsonPrimitive()) continue;
                long coins = 0L;
                Map<String, Integer> items = new LinkedHashMap<>();
                if (o.has("items") && o.get("items").isJsonObject()) {
                    JsonElement extra = o.getAsJsonObject("items").get(Integer.toString(star));
                    if (extra != null && extra.isJsonArray()) {
                        for (JsonElement raw : (JsonArray) extra) {
                            String[] parts = raw.getAsString().split(":");
                            if (parts.length != 2) continue;
                            try {
                                long count = Long.parseLong(parts[1]);
                                if (parts[0].equals("SKYBLOCK_COIN")) coins += count;
                                else items.merge(parts[0], (int) count, Integer::sum);
                            } catch (NumberFormatException e) {
                                // eine kaputte Zeile zaehlt nicht
                            }
                        }
                    }
                }
                tiers.put(star, new Upgrade(type, amount.getAsInt(), coins, items));
            }
            if (!tiers.isEmpty()) out.put(entry.getKey(), tiers);
        }
        return out;
    }

    /** gemstonecosts.json: Kennung -> Platz ("SAPPHIRE_1") -> Zutaten "KENNUNG:Anzahl" */
    static Map<String, Map<String, List<String>>> parseGemSlots(JsonObject root) {
        Map<String, Map<String, List<String>>> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            Map<String, List<String>> slots = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> slot : entry.getValue().getAsJsonObject().entrySet()) {
                if (!slot.getValue().isJsonArray()) continue;
                List<String> ingredients = new ArrayList<>();
                for (JsonElement raw : slot.getValue().getAsJsonArray()) ingredients.add(raw.getAsString());
                slots.put(slot.getKey(), ingredients);
            }
            out.put(entry.getKey(), slots);
        }
        return out;
    }

    private static Set<String> stringSet(JsonObject o, String key) {
        Set<String> out = new HashSet<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        }
        return out;
    }

    /** Items.json (nur was gebraucht wird), unter dem Schluessel "data" */
    static Map<String, ValueData> parseValues(JsonObject root) {
        if (!root.has("value_calculation_data") || !root.get("value_calculation_data").isJsonObject()) return Map.of();
        JsonObject calc = root.getAsJsonObject("value_calculation_data");

        Map<String, AlwaysActive> alwaysActive = new HashMap<>();
        if (calc.has("always_active_enchants") && calc.get("always_active_enchants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : calc.getAsJsonObject("always_active_enchants").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                alwaysActive.put(e.getKey(), new AlwaysActive((int) number(o, "level"), stringSet(o, "items")));
            }
        }
        Map<String, List<Endcap>> endcaps = new HashMap<>();
        if (calc.has("endcap_enchants_new") && calc.get("endcap_enchants_new").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : calc.getAsJsonObject("endcap_enchants_new").entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<Endcap> list = new ArrayList<>();
                for (JsonElement raw : e.getValue().getAsJsonArray()) {
                    if (!raw.isJsonObject()) continue;
                    JsonObject o = raw.getAsJsonObject();
                    String item = text(o, "endcap_item");
                    if (item != null) list.add(new Endcap((int) number(o, "required_level"), item));
                }
                if (!list.isEmpty()) endcaps.put(e.getKey(), list);
            }
        }
        Map<String, Integer> bundles = new HashMap<>();
        if (root.has("book_bundle_amount") && root.get("book_bundle_amount").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("book_bundle_amount").entrySet()) {
                if (e.getValue().isJsonPrimitive()) bundles.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        Map<String, Map<String, Integer>> prestige = new HashMap<>();
        if (root.has("crimson_prestige_costs") && root.get("crimson_prestige_costs").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("crimson_prestige_costs").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                Map<String, Integer> costs = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> c : e.getValue().getAsJsonObject().entrySet()) {
                    if (c.getValue().isJsonPrimitive()) costs.put(c.getKey(), c.getValue().getAsInt());
                }
                prestige.put(e.getKey(), costs);
            }
        }
        ValueData data = new ValueData(alwaysActive, stringSet(calc, "only_tier_one_prices"), stringSet(calc, "only_tier_five_prices"),
                endcaps, bundles, prestige, stringSet(root, "has_legacy_gemstone_slots"));
        return Map.of("data", data);
    }

    /** Enchants.json: die Verzauberungen unter "STACKING" - bei ihnen zaehlt nur die erste Stufe */
    static Map<String, Boolean> parseStacking(JsonObject root) {
        Map<String, Boolean> out = new HashMap<>();
        if (root.has("STACKING") && root.get("STACKING").isJsonObject()) {
            for (String key : root.getAsJsonObject("STACKING").keySet()) out.put(key, Boolean.TRUE);
        }
        return out;
    }
}
