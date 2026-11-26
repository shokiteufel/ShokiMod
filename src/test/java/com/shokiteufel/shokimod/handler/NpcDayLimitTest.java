package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig.NpcDayLimitCategory;
import com.shokiteufel.shokimod.data.ModConfig.NpcNumberFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Misc > NPC Daily Limit Tracker (2.4.16): die Verkaufszeile lesen, den Tag wechseln, die Zahlen schreiben. */
class NpcDayLimitTest {

    @Test
    void aSaleLineGivesItsCoins() {
        assertEquals(9_600_000L, NpcDayLimit.parseSale("You sold Evergreen Chip x64 for 9,600,000 Coins!"));
        assertEquals(50_000L, NpcDayLimit.parseSale("You sold Cicada Symphony Vinyl x1 for 50,000 Coins!"));
        assertEquals(7L, NpcDayLimit.parseSale("You sold Rotten Flesh x1 for 7 Coins!"));
        assertEquals(12_345L, NpcDayLimit.parseSale("  You sold Enchanted Coal x3 for 12,345 Coins!  "));
    }

    @Test
    void otherLinesAreNoSales() {
        assertEquals(-1L, NpcDayLimit.parseSale(null));
        assertEquals(-1L, NpcDayLimit.parseSale(""));
        assertEquals(-1L, NpcDayLimit.parseSale("You bought Wheat x1 for 5 Coins!"));
        assertEquals(-1L, NpcDayLimit.parseSale("[Bazaar] Sold 64x Wheat for 640 coins!"));
        assertEquals(-1L, NpcDayLimit.parseSale("Someone: You sold Wheat x1 for 5 Coins! lol"));
        assertEquals(-1L, NpcDayLimit.parseSale("You sold Wheat for many Coins!"));
    }

    @Test
    void theShortFormCutsInsteadOfRounding() {
        assertEquals("0", NpcDayLimit.shorten(0));
        assertEquals("999", NpcDayLimit.shorten(999));
        assertEquals("1k", NpcDayLimit.shorten(1_000));
        assertEquals("1.5k", NpcDayLimit.shorten(1_500));
        assertEquals("400k", NpcDayLimit.shorten(400_000));
        assertEquals("12.3m", NpcDayLimit.shorten(12_300_000));
        assertEquals("500m", NpcDayLimit.shorten(500_000_000));
        assertEquals("499.9m", NpcDayLimit.shorten(499_999_999), "nicht 500m: die Grenze ist noch nicht erreicht");
        assertEquals("1.5b", NpcDayLimit.shorten(1_500_000_000L));
    }

    @Test
    void theTwoFormatsOfTheLine() {
        assertEquals("§6400k§f/§6500m", NpcDayLimit.line(400_000, NpcNumberFormat.SHORT));
        assertEquals("§6400,000§f/§6500,000,000", NpcDayLimit.line(400_000, NpcNumberFormat.LONG));
        assertEquals("§60§f/§6500m", NpcDayLimit.line(0, NpcNumberFormat.SHORT));
    }

    @Test
    void salesAddUpWithinADay() {
        NpcDayLimitCategory c = new NpcDayLimitCategory();
        NpcDayLimit.add(c, 100L, 20_000L);
        NpcDayLimit.add(c, 250L, 20_000L);
        assertEquals(350L, NpcDayLimit.soldToday(c, 20_000L));
        assertEquals(20_000L, c.gmtEpochDay);
    }

    @Test
    void aNewDayStartsAtZero() {
        NpcDayLimitCategory c = new NpcDayLimitCategory();
        NpcDayLimit.add(c, 5_000L, 20_000L);
        assertEquals(0L, NpcDayLimit.soldToday(c, 20_001L), "Mitternacht GMT hat den Zaehler geleert");
        NpcDayLimit.add(c, 7L, 20_001L);
        assertEquals(7L, NpcDayLimit.soldToday(c, 20_001L));
        assertEquals(20_001L, c.gmtEpochDay);
    }

    @Test
    void theFirstSaleOfAFreshFileCountsFromZero() {
        NpcDayLimitCategory c = new NpcDayLimitCategory();
        assertEquals(0L, c.gmtEpochDay);
        NpcDayLimit.add(c, 42L, 20_500L);
        assertEquals(42L, NpcDayLimit.soldToday(c, 20_500L));
    }

    @Test
    void whatIsLeftNeverGoesBelowZero() {
        assertEquals(500_000_000L, NpcDayLimit.remaining(0));
        assertEquals(100_000_000L, NpcDayLimit.remaining(400_000_000L));
        assertEquals(0L, NpcDayLimit.remaining(600_000_000L));
    }
}
