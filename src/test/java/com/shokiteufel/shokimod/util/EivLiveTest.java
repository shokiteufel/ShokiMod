package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig.EivPriceSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Estimated Item Value (2.4.18): die Namen und die festen Preise des Spiels (ohne Netz). */
class EivLiveTest {

    @Test
    void booksAreNamedWithRomanLevelsInBlueAndUltimatesInPink() {
        assertEquals("§9Sharpness VI", EivLive.coloured("ENCHANTMENT_SHARPNESS_6"));
        assertEquals("§d§lWise V", EivLive.coloured("ENCHANTMENT_ULTIMATE_WISE_5"));
        assertEquals("§9Turbo Wheat V", EivLive.coloured("ENCHANTMENT_TURBO_WHEAT_5"));
    }

    @Test
    void essencesAreNamedInPink() {
        assertEquals("§dWither Essence", EivLive.coloured("ESSENCE_WITHER"));
        assertEquals("§dGold Essence", EivLive.coloured("ESSENCE_GOLD"));
    }

    @Test
    void coinsHaveTheirOwnName() {
        assertEquals("§6Coins", EivLive.coloured("SKYBLOCK_COIN"));
    }

    @Test
    void aCoinIsWorthOneCoinWhateverTheSource() {
        for (EivPriceSource source : EivPriceSource.values()) {
            assertEquals(1.0, EivLive.INSTANCE.price("SKYBLOCK_COIN", source));
        }
    }

    @Test
    void someItemsHaveAFixedPrice() {
        assertEquals(0.0, EivLive.INSTANCE.price("JASPER_CRYSTAL", EivPriceSource.INSTANT_SELL));
        assertEquals(0.0, EivLive.INSTANCE.price("RUBY_CRYSTAL", EivPriceSource.NPC_SELL));
        assertEquals(20_000.0, EivLive.INSTANCE.price("WISP_POTION", EivPriceSource.INSTANT_BUY));
        assertEquals(7_776.0, EivLive.INSTANCE.price("ENCHANTED_HAY_BLOCK", EivPriceSource.INSTANT_SELL));
        assertEquals(1_119_744.0, EivLive.INSTANCE.price("TIGHTLY_TIED_HAY_BALE", EivPriceSource.INSTANT_SELL));
    }
}
