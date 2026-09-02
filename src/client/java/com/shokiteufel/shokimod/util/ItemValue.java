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
 * Der Basar handelt Waren wie Enchanted Diamonds oder verzauberte Buecher; das
 * Auktionshaus alles andere, vom Hyperion bis zum Pet. Wer in keiner steht, hat
 * keinen Wert - nicht "null Coins", sondern unbekannt.
 *
 * Reihenfolge und Quellen entsprechen Skysofts RareLootValueResolver (LGPL-3.0,
 * Akinsoft). Abweichung: Skysoft nimmt zuerst den Sell-Order-Preis seines eigenen
 * Spiegels; hier gilt der Sofortverkaufspreis direkt von Hypixel - das ist, was
 * man fuer einen Fund bekommt, wenn man ihn ohne Warten abgibt.
 */
public final class ItemValue {

    /** Derselbe Endpunkt, den zehn Mods dieses Profils nutzen - schluesselfrei */
    public static final PriceFeed BAZAAR = new PriceFeed(
            "bazaar prices",
            "https://api.hypixel.net/v2/skyblock/bazaar",
            "bazaar-prices.json",
            10 * 60 * 1000L,
            ItemValue::parseBazaar);

    /** Skyblockers Backend, in diesem Profil ohnehin schon in Gebrauch */
    public static final PriceFeed LOWEST_BIN = new PriceFeed(
            "lowest BIN prices",
            "https://hysky.de/api/auctions/lowestbins",
            "lowest-bins.json",
            10 * 60 * 1000L,
            ItemValue::parseLowestBins);

    private ItemValue() {
    }

    /** Ein aufgeloester Wert: Coins fuer den ganzen Stapel, und welche Kennung getroffen hat */
    public record Value(double coins, String itemId, Source source) {
    }

    public enum Source {
        BAZAAR_INSTANT_SELL,
        LOWEST_BIN
    }

    /** Beide Listen nachladen, bevor der erste Fund sie braucht */
    public static void prefetch() {
        BAZAAR.prefetch();
        LOWEST_BIN.prefetch();
    }

    /**
     * Der Wert des ersten Kandidaten, den eine der Listen kennt.
     *
     * Aus einer Chatzeile lassen sich oft mehrere Kennungen ableiten - ein Buch
     * "Wise V" kann ENCHANTMENT_WISE_5 oder ENCHANTMENT_ULTIMATE_WISE_5 sein.
     * Genommen wird der erste, fuer den es einen Preis gibt. Keiner: null.
     */
    public static Value resolve(List<String> candidates, int amount) {
        if (candidates == null) return null;
        int multiplier = Math.max(1, amount);

        for (String candidate : candidates) {
            if (candidate == null) continue;
            String itemId = candidate.trim();
            if (itemId.isEmpty()) continue;

            double bazaar = BAZAAR.price(itemId);
            if (bazaar > 0) return new Value(bazaar * multiplier, itemId, Source.BAZAAR_INSTANT_SELL);

            double bin = LOWEST_BIN.price(itemId);
            if (bin > 0) return new Value(bin * multiplier, itemId, Source.LOWEST_BIN);
        }
        return null;
    }

    /** products.<ID>.quick_status.sellPrice - was der Sofortverkauf gerade zahlt */
    private static Map<String, Double> parseBazaar(JsonObject root) {
        Map<String, Double> out = new HashMap<>();
        if (!root.has("products")) return out;

        JsonObject products = root.getAsJsonObject("products");
        for (String id : products.keySet()) {
            JsonElement productElement = products.get(id);
            if (productElement == null || !productElement.isJsonObject()) continue;

            JsonObject product = productElement.getAsJsonObject();
            if (!product.has("quick_status")) continue;
            JsonObject status = product.getAsJsonObject("quick_status");
            if (status == null || !status.has("sellPrice")) continue;

            double price = status.get("sellPrice").getAsDouble();
            if (price > 0) out.put(id, price);
        }
        return out;
    }

    /**
     * Eine flache Karte: Kennung auf Preis.
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

        // "1,000,000" ist ein Tausendertrenner, "1,2" ein Dezimalkomma: entscheidet
        // die Stellenzahl hinter dem letzten Komma
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
