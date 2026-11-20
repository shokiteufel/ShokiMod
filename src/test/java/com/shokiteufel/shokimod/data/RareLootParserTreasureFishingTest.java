package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meldung des Users (19.11.): Beim Treasure-Fischen gehen die Shards nicht in den Tracker. Hypixel meldet sie als "GOOD CATCH! You caught a
 * Stingray Shard x3!" (auch GREAT und OUTSTANDING), mit der Menge hinter dem Namen und manchmal einem Zaehler "(2)".
 */
class RareLootParserTreasureFishingTest {

    private static void assertShard(String zeile, String name, int menge, String kennung) {
        Drop drop = RareLootParser.parse(zeile);
        assertNotNull(drop, zeile);
        assertEquals(name, drop.displayName(), zeile);
        assertEquals(menge, drop.amount(), zeile);
        assertEquals(kennung, drop.itemIdCandidates().get(0), zeile);
    }

    @Test
    void readsTheShardsOfTreasureFishing() {
        assertShard("GOOD CATCH! You caught a Stingray Shard x3!", "Stingray Shard", 3, "SHARD_STINGRAY");
        assertShard("GREAT CATCH! You caught a Sepialot Shard x3!", "Sepialot Shard", 3, "SHARD_SEPIALOT");
        assertShard("OUTSTANDING CATCH! You caught a Solar Shard x5!", "Solar Shard", 5, "SHARD_SOLAR");
    }

    @Test
    void readsItWithTheCounterAChatModAppends() {
        assertShard("GOOD CATCH! You caught a Stingray Shard x3! (2)", "Stingray Shard", 3, "SHARD_STINGRAY");
    }

    @Test
    void aSingleShardHasNoAmount() {
        assertShard("GOOD CATCH! You caught a Stingray Shard!", "Stingray Shard", 1, "SHARD_STINGRAY");
        assertShard("GOOD CATCH! You caught an Abyssal Lanternfish Shard!", "Abyssal Lanternfish Shard", 1, "SHARD_ABYSSAL_LANTERNFISH");
    }

    @Test
    void readsPluralAndThousandsSeparators() {
        assertShard("GREAT CATCH! You caught a Stingray Shards x1,234!", "Stingray Shard", 1234, "SHARD_STINGRAY");
        assertShard("GOOD CATCH! You caught a Stingray Shard 3x!", "Stingray Shard", 3, "SHARD_STINGRAY");
    }

    @Test
    void aSymbolBeforeTheLevelWordDoesNotMatter() {
        assertShard(" GOOD CATCH! You caught a Stingray Shard x3!", "Stingray Shard", 3, "SHARD_STINGRAY");
        assertShard("⚓ GREAT CATCH! You caught a Solar Shard x3! (2)", "Solar Shard", 3, "SHARD_SOLAR");
    }

    @Test
    void ordinaryGoodsStayOrdinaryGoods() {
        Drop pilz = RareLootParser.parse("GOOD CATCH! You caught an Enchanted Ruby Veilshroom!");
        assertNotNull(pilz);
        assertEquals("Enchanted Ruby Veilshroom", pilz.displayName());
        assertFalse(pilz.itemIdCandidates().get(0).startsWith("SHARD_"));
        Drop stiefel = RareLootParser.parse("JUNK CATCH! You caught an Old Leather Boot!");
        assertNotNull(stiefel);
        assertEquals("Old Leather Boot", stiefel.displayName());
    }

    @Test
    void goodsThatAreNoHuntingShardsKeepTheirOwnId() {
        // Prismarine und Earth Shard sind Waren mit Preis, keine Jagd-Shards (siehe auch die Sack-Meldung)
        assertShard("TREASURE CATCH! You caught a Prismarine Shard x2!", "Prismarine Shard", 2, "PRISMARINE_SHARD");
        assertShard("GOOD CATCH! You caught an Earth Shard!", "Earth Shard", 1, "EARTH_SHARD");
        assertTrue(RareLootParser.parse("TREASURE CATCH! You caught a Prismarine Shard x2!").itemIdCandidates().stream()
                .noneMatch(id -> id.startsWith("SHARD_")));
    }

    @Test
    void linesOfOtherChannelsAreNoCatches() {
        assertNull(RareLootParser.parse("Party > [MVP+] Anna: GOOD CATCH! You caught a Stingray Shard x3!"));
        assertNull(RareLootParser.parse("Guild > [VIP] Bob: GOOD CATCH! You caught a Stingray Shard x3!"));
        assertNull(RareLootParser.parse("[MVP+] Anna: GOOD CATCH! You caught a Stingray Shard x3!"));
    }
}
