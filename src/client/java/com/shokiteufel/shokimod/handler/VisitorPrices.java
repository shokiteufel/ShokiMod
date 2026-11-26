package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.util.BazaarIds;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;

import java.util.List;

/**
 * Die Preise, mit denen die Besucher-Funktionen im Garden rechnen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorPriceCalculator}). Von dort stammt die Regel: Ein verlangter Gegenstand kostet, was man fuer ihn zahlt (der Preis zum
 * Kaufen), und ein Copper ist so viel wert wie Green Thumb I durch 1500. Die Umsetzung ist eigene Arbeit: Java, die Preise kommen aus den Listen, die die Mod ohnehin
 * holt ({@link ItemValue}); der Name des Gegenstands wird ueber {@link BazaarIds} zu den Kennungen des Basars (auch Buecher und Shards), der Kaufpreis ist das billigste
 * Angebot im Basar - fehlt es, der Sofortverkauf -, dann der Tiefstpreis aus dem Auktionshaus. Ein Gegenstand ohne Preis ist unbekannt (eine Zahl unter null), nicht null Coins.
 */
public final class VisitorPrices {

    /** Der Basar fuehrt Green Thumb I unter dieser Kennung (bei SkyHanni GREEN_THUMB;1) */
    static final String GREEN_THUMB = "ENCHANTMENT_GREEN_THUMB_1";
    /** So viele Copper bringt Green Thumb I */
    static final double COPPER_PER_GREEN_THUMB = 1500.0;

    private VisitorPrices() {
    }

    /** Der Kaufpreis eines Gegenstands nach Namen, oder eine Zahl unter null, wenn keine Liste ihn kennt */
    public static double buyPrice(String name) {
        return buyPrice(BazaarIds.candidates(name));
    }

    /** Der Kaufpreis des ersten Kandidaten, den der Basar oder das Auktionshaus kennt */
    static double buyPrice(List<String> candidates) {
        for (String id : candidates) {
            if (id == null || id.isBlank()) continue;
            BazaarPrice bazaar = ItemValue.bazaarPrice(id);
            if (bazaar != null) {
                double price = bazaar.cheapestOffer() > 0 ? bazaar.cheapestOffer() : bazaar.instantSell();
                if (price > 0) return price;
            }
            Double bin = ItemValue.LOWEST_BIN.get(id);
            if (bin != null && bin > 0) return bin;
        }
        return -1.0;
    }

    /** Was ein Copper in Coins wert ist, oder eine Zahl unter null, wenn der Preis von Green Thumb I nicht bekannt ist */
    public static double copperValue() {
        double price = buyPrice(List.of(GREEN_THUMB));
        return price > 0 ? price / COPPER_PER_GREEN_THUMB : -1.0;
    }
}
