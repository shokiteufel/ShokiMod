package com.shokiteufel.shokimod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Was George fuer ein Pet zahlt.
 *
 * George steht im Hub und kauft Pets zu festen Preisen - ein Baby Yeti bringt dort eine
 * Million, egal was gerade im Auktionshaus steht. Damit ist das kein Marktpreis, sondern
 * ein Boden: Weniger als das ist ein Pet nie wert, weil man es immer bei ihm abgeben kann.
 *
 * Die Liste kommt aus dem NEU-Repo, das sie aus dem Wiki pflegt - 309 Eintraege, je Pet
 * und Seltenheit ({@code BABY_YETI;0} ist gewoehnlich, {@code ;4} legendaer, {@code ;5}
 * mythisch). Sie wird zur Laufzeit geholt und liegt danach im Config-Ordner: So kommen
 * neue Pets von selbst dazu, und mitgeliefert werden fremde Daten nicht.
 */
public final class GeorgePrices {

    public static final RemoteMap<Double> FEED = new RemoteMap<>(
            "George prices",
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/george.json",
            "george-prices.json",
            24 * 60 * 60 * 1000L,
            GeorgePrices::parse);

    private GeorgePrices() {
    }

    /**
     * Was George zahlt, oder -1.
     *
     * Minus eins heisst "kein Pet, das er kauft" - das ist etwas anderes als null Coins.
     */
    public static double of(String itemId) {
        if (itemId == null || itemId.indexOf(';') < 0) return -1;
        Double preis = FEED.get(itemId);
        return preis == null || preis <= 0 ? -1 : preis;
    }

    public static boolean ready() {
        return FEED.ready();
    }

    private static Map<String, Double> parse(JsonObject root) {
        Map<String, Double> out = new LinkedHashMap<>();
        JsonObject prices = root.getAsJsonObject("prices");
        if (prices == null) return out;

        for (Map.Entry<String, JsonElement> entry : prices.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            try {
                double preis = entry.getValue().getAsDouble();
                if (preis > 0) out.put(entry.getKey(), preis);
            } catch (NumberFormatException e) {
                // Was keine Zahl ist, ist kein Preis
            }
        }
        return out;
    }
}
