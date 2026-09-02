package com.shokiteufel.shokimod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Was ein Fund wert ist - und woher die Zahl stammt.
 *
 * Zwei Listen, in dieser Reihenfolge: erst der Basar, dann das Auktionshaus.
 * Der Basar handelt Waren wie Enchanted Diamonds, Buecher oder Shards; das
 * Auktionshaus alles andere, vom Hyperion bis zum Pet. Wer in keiner steht, hat
 * keinen Wert - nicht "null Coins", sondern unbekannt.
 *
 * Im Basar gibt es zwei Preise: was der Sofortverkauf gerade zahlt, und was eine
 * Verkaufsorder bringt, wenn man auf den Kaeufer wartet. Beim Ghost Shard liegen
 * 3,5k und 9k dazwischen. Welcher zaehlt, darf man getrennt fuer Shards und fuer
 * alle anderen Waren waehlen - Shards verkauft man anders als Enchanted Diamonds.
 *
 * Reihenfolge und Quellen entsprechen Skysofts RareLootValueResolver (LGPL-3.0,
 * Akinsoft).
 */
public final class ItemValue {

    /** Welcher Basarpreis zaehlt */
    public enum PriceMode {
        INSTANT_SELL("Instant Sell"),
        SELL_ORDER("Sell Order");

        private final String label;

        PriceMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Beide Basarpreise einer Ware, so wie Hypixel sie in quick_status liefert */
    public record BazaarPrice(double instantSell, double sellOrder) {
        double pick(PriceMode mode) {
            return mode == PriceMode.SELL_ORDER ? sellOrder : instantSell;
        }
    }

    /** Derselbe Endpunkt, den zehn Mods dieses Profils nutzen - schluesselfrei */
    public static final RemoteMap<BazaarPrice> BAZAAR = new RemoteMap<>(
            "bazaar prices",
            "https://api.hypixel.net/v2/skyblock/bazaar",
            "bazaar-prices.json",
            10 * 60 * 1000L,
            ItemValue::parseBazaar);

    /** Skyblockers Backend, in diesem Profil ohnehin schon in Gebrauch */
    public static final RemoteMap<Double> LOWEST_BIN = new RemoteMap<>(
            "lowest BIN prices",
            "https://hysky.de/api/auctions/lowestbins",
            "lowest-bins.json",
            10 * 60 * 1000L,
            ItemValue::parseLowestBins);

    private static final String SHARD_PREFIX = "SHARD_";

    private ItemValue() {
    }

    /** Ein aufgeloester Wert: Coins fuer den ganzen Stapel, und welche Kennung getroffen hat */
    public record Value(double coins, String itemId, Source source) {
    }

    public enum Source {
        BAZAAR_INSTANT_SELL,
        BAZAAR_SELL_ORDER,
        LOWEST_BIN
    }

    /** Einmal aufgeloeste Shard-Kennungen, damit der Abgleich nicht bei jedem Fang laeuft */
    private static final Map<String, String> shardAliases = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Die Kennung, unter der der Basar einen Shard wirklich fuehrt.
     *
     * Der Chat sagt "Wither Spectre", der Basar schreibt SHARD_WITHER_SPECTER. Aus dem
     * Namen laesst sich das nicht raten - aber die Produktliste des Basars kennt alle
     * 320 Shards. Gibt es die geratene Kennung dort nicht, gewinnt das Produkt mit
     * dem kleinsten Buchstabenabstand, sofern der hoechstens zwei betraegt. Ohne
     * Treffer bleibt es bei der geratenen Kennung.
     */
    public static String canonicalShard(String guessed) {
        if (guessed == null || !guessed.startsWith(SHARD_PREFIX)) return guessed;
        if (BAZAAR.get(guessed) != null) return guessed;

        String cached = shardAliases.get(guessed);
        if (cached != null) return cached;
        if (!BAZAAR.ready()) return guessed;

        String best = guessed;
        int bestDistance = 3;
        for (String product : BAZAAR.keys()) {
            if (!product.startsWith(SHARD_PREFIX)) continue;
            if (Math.abs(product.length() - guessed.length()) > 2) continue;
            int distance = editDistance(product, guessed, bestDistance);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = product;
            }
        }
        if (!best.equals(guessed)) shardAliases.put(guessed, best);
        return best;
    }

    /** Levenshtein mit Abbruch, sobald der Abstand die Schranke erreicht */
    private static int editDistance(String a, String b, int limit) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin >= limit) return limit;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Alle Listen nachladen, bevor der erste Fund sie braucht */
    public static void prefetch() {
        BAZAAR.prefetch();
        LOWEST_BIN.prefetch();
        ItemNames.prefetch();
    }

    /** Eine Zeile je Liste fuer den Diagnosebericht */
    public static List<String> statusLines() {
        return List.of(BAZAAR.status(), LOWEST_BIN.status(), ItemNames.FEED.status());
    }

    /**
     * Der Wert des ersten Kandidaten, den eine der Listen kennt.
     *
     * Aus einer Chatzeile lassen sich oft mehrere Kennungen ableiten - ein Buch
     * "Wise V" kann ENCHANTMENT_WISE_5 oder ENCHANTMENT_ULTIMATE_WISE_5 sein.
     * Genommen wird der erste, fuer den es einen Preis gibt. Keiner: null.
     *
     * @param shardMode welcher Basarpreis fuer SHARD_-Waren zaehlt
     * @param otherMode welcher Basarpreis fuer alle anderen Waren zaehlt
     */
    public static Value resolve(List<String> candidates, int amount, PriceMode shardMode, PriceMode otherMode) {
        if (candidates == null) return null;
        int multiplier = Math.max(1, amount);

        for (String candidate : candidates) {
            if (candidate == null) continue;
            String itemId = candidate.trim();
            if (itemId.isEmpty()) continue;

            BazaarPrice bazaar = BAZAAR.get(itemId);
            if (bazaar != null) {
                PriceMode mode = itemId.startsWith(SHARD_PREFIX) ? shardMode : otherMode;
                if (mode == null) mode = PriceMode.INSTANT_SELL;
                double price = bazaar.pick(mode);
                // Eine Ware ohne Order auf der gewaehlten Seite faellt auf die andere zurueck,
                // bevor sie als unbekannt gilt
                if (price <= 0) price = bazaar.pick(mode == PriceMode.SELL_ORDER ? PriceMode.INSTANT_SELL : PriceMode.SELL_ORDER);
                if (price > 0) {
                    Source source = mode == PriceMode.SELL_ORDER ? Source.BAZAAR_SELL_ORDER : Source.BAZAAR_INSTANT_SELL;
                    return new Value(price * multiplier, itemId, source);
                }
            }

            Double bin = LOWEST_BIN.get(itemId);
            if (bin != null && bin > 0) return new Value(bin * multiplier, itemId, Source.LOWEST_BIN);
        }
        return null;
    }

    /**
     * quick_status je Ware: sellPrice zahlt der Sofortverkauf, buyPrice bringt eine
     * Verkaufsorder - so nennt es auch Skysofts Tooltip "Bazaar Sell Order".
     */
    private static Map<String, BazaarPrice> parseBazaar(JsonObject root) {
        Map<String, BazaarPrice> out = new HashMap<>();
        if (!root.has("products")) return out;

        JsonObject products = root.getAsJsonObject("products");
        for (String id : products.keySet()) {
            JsonElement productElement = products.get(id);
            if (productElement == null || !productElement.isJsonObject()) continue;

            JsonObject product = productElement.getAsJsonObject();
            if (!product.has("quick_status")) continue;
            JsonObject status = product.getAsJsonObject("quick_status");
            if (status == null) continue;

            double instantSell = status.has("sellPrice") ? status.get("sellPrice").getAsDouble() : 0;
            double sellOrder = status.has("buyPrice") ? status.get("buyPrice").getAsDouble() : 0;
            if (instantSell > 0 || sellOrder > 0) out.put(id, new BazaarPrice(instantSell, sellOrder));
        }
        return out;
    }

    /**
     * Eine flache Karte: Kennung auf Preis, 4.800 Eintraege.
     *
     * Skyblocker liest dieselbe Antwort in eine Object2IntMap; mehr Struktur hat
     * sie nicht. Alles, was keine Zahl ist, wird uebergangen.
     */
    private static Map<String, Double> parseLowestBins(JsonObject root) {
        Map<String, Double> out = new HashMap<>();
        for (String id : root.keySet()) {
            JsonElement element = root.get(id);
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) continue;

            double price = element.getAsDouble();
            if (price > 0) out.put(id.toUpperCase(Locale.ROOT), price);
        }
        return out;
    }

    /** 1234567 wird zu "1.2m" - lesbar statt genau, so wie Hypixel es schreibt */
    public static String format(double coins) {
        double abs = Math.abs(coins);
        if (abs >= 1_000_000_000d) return trim(coins / 1_000_000_000d) + "b";
        if (abs >= 1_000_000d) return trim(coins / 1_000_000d) + "m";
        if (abs >= 1_000d) return trim(coins / 1_000d) + "k";
        return trim(coins);
    }

    private static String trim(double value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    /**
     * "5M", "500k", "1.2B", "1,000,000" oder eine blanke Zahl.
     *
     * Dieselbe Schreibweise, die Hypixel selbst verwendet - wer eine Schwelle
     * eintippt, soll sie nicht in Nullen ausschreiben muessen. Ungueltig: 0.
     */
    public static double parseAmount(String text) {
        if (text == null) return 0;

        String cleaned = text.trim().replace(" ", "");
        if (cleaned.isEmpty()) return 0;

        double factor = 1;
        char last = Character.toLowerCase(cleaned.charAt(cleaned.length() - 1));
        switch (last) {
            case 'k' -> factor = 1_000d;
            case 'm' -> factor = 1_000_000d;
            case 'b' -> factor = 1_000_000_000d;
            default -> {
            }
        }
        if (factor > 1) cleaned = cleaned.substring(0, cleaned.length() - 1);

        int comma = cleaned.lastIndexOf(',');
        if (comma >= 0 && cleaned.length() - comma - 1 == 3) {
            cleaned = cleaned.replace(",", "");
        } else {
            cleaned = cleaned.replace(",", ".");
        }

        try {
            return Double.parseDouble(cleaned) * factor;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
