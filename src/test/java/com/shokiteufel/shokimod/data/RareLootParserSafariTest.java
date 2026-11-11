package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Der Fang in der Safari: die Shards kommen nur ueber diese Zeile, sie gehoeren in den Profit Tracker */
class RareLootParserSafariTest {

    @Test
    void theCaptureLineFromTheSafariGivesTheShardsWithTheirAmount() {
        // Wortlaut vom Bildschirm des Users (11.11.2026)
        Drop drop = RareLootParser.parse("CAPTURE! You caught a Bloodbat and gained 2x Bloodbat Shard! (3)");
        assertNotNull(drop);
        assertEquals(2, drop.amount(), "die 2 aus '2x', nicht die 3 in der Klammer");
        assertEquals("Bloodbat Shard", drop.displayName());
        assertEquals("SHARD_BLOODBAT", drop.itemIdCandidates().get(0));
    }

    @Test
    void aSingleShardAndThePluralAreRead() {
        Drop eins = RareLootParser.parse("CAPTURE! You caught a Rockmite and gained a Rockmite Shard!");
        assertNotNull(eins);
        assertEquals(1, eins.amount());
        assertEquals("SHARD_ROCKMITE", eins.itemIdCandidates().get(0));

        Drop viele = RareLootParser.parse("CAPTURE! You caught a Rockmite and gained 3x Rockmite Shards!");
        assertNotNull(viele);
        assertEquals(3, viele.amount());
        assertEquals("SHARD_ROCKMITE", viele.itemIdCandidates().get(0));
    }

    @Test
    void anAdditionInFrontOfTheCreatureDoesNotMatter() {
        Drop drop = RareLootParser.parse("CAPTURE! You caught a SPARKLING Bloodbat and gained 4x Bloodbat Shard! (7)");
        assertNotNull(drop);
        assertEquals(4, drop.amount());
        assertEquals("SHARD_BLOODBAT", drop.itemIdCandidates().get(0));
    }

    @Test
    void aCatchWithoutShardsIsNoDrop() {
        assertNull(RareLootParser.parse("CAPTURE! You caught a Bloodbat!"));
    }

    @Test
    void aPlayerWritingTheLineIsNotACapture() {
        // Die Zeile muss mit CAPTURE! anfangen; im Chat steht davor der Name
        assertNull(RareLootParser.parse("[MVP+] Anna: CAPTURE! You caught a Bloodbat and gained 2x Bloodbat Shard!"));
    }
}
