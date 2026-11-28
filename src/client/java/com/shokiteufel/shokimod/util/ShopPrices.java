package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;

import java.util.List;

/**
 * Die Preise fuer NPC Shop Values ({@link ShopValue}): Basar, sonst die niedrigste BIN - nie der Preis beim NPC, ein Stueck, das nur ein NPC abnimmt, hat hier keinen Wert.
 *
 * Basar: je nach Wahl der Sofortverkauf (was man jetzt bekommt) oder die Verkaufsorder; fehlt die gewaehlte Seite, gilt die andere. Die Listen holt {@link ItemValue}
 * ohnehin, hier wird nur gelesen.
 */
public final class ShopPrices implements ShopValue.Prices {

    private final boolean sellOffer;

    public ShopPrices(boolean sellOffer) {
        this.sellOffer = sellOffer;
    }

    @Override
    public double valueOfItem(List<String> candidates) {
        if (candidates == null) return -1;
        for (String id : candidates) {
            if (id == null || id.isBlank()) continue;
            BazaarPrice bazaar = ItemValue.bazaarPrice(id);
            if (bazaar != null) {
                double chosen = sellOffer ? bazaar.sellOrder() : bazaar.instantSell();
                double other = sellOffer ? bazaar.instantSell() : bazaar.sellOrder();
                double price = chosen > 0 ? chosen : other;
                if (price > 0) return price;
            }
            Double bin = ItemValue.LOWEST_BIN.get(id);
            if (bin != null && bin > 0) return bin;
        }
        return -1;
    }

    @Override
    public double valueOfName(String name) {
        return valueOfItem(BazaarIds.candidates(name));
    }
}
