package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.data.RareLootParser.DyeFind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Farbmeldungen im Chat - die alte Form ("found") und die neue geteilte ("SUPER WOW! ... lootshared ..." samt Beleg-Zeile) */
class RareLootParserDyeTest {

    @Test
    void theOldFormStillReadsAndIsNotShared() {
        DyeFind find = RareLootParser.parseDye("WOW! Shokiteufel found a Necron Dye!");
        assertNotNull(find);
        assertEquals("Shokiteufel", find.player());
        assertEquals("Necron Dye", find.drop().displayName());
        assertFalse(find.shared());
    }

    @Test
    void theOldFormWithRankEmblemAndServerNumberStillReads() {
        DyeFind find = RareLootParser.parseDye("WOW! [MVP+] ❀ Someone found a Pure Black Dye #1,204!");
        assertNotNull(find);
        assertEquals("Someone", find.player());
        assertEquals("Pure Black Dye", find.drop().displayName(), "die laufende Nummer gehoert nicht zum Namen");
    }

    @Test
    void theNewLootsharedLineIsReadFromTheScreenshotOf22November() {
        // Wortlaut vom Bildschirm des Users (22.11.2026)
        DyeFind find = RareLootParser.parseDye("SUPER WOW! [MVP+] dyedicated lootshared an Iceberg Dye!");
        assertNotNull(find);
        assertEquals("dyedicated", find.player());
        assertEquals("Iceberg Dye", find.drop().displayName());
        assertEquals("DYE_ICEBERG", find.drop().itemIdCandidates().get(0));
        assertTrue(find.shared());
    }

    @Test
    void theHeadingAndTheVerbMayBeMixed() {
        DyeFind superFound = RareLootParser.parseDye("SUPER WOW! Someone found a Necron Dye!");
        assertNotNull(superFound);
        assertFalse(superFound.shared());
        DyeFind sharedWow = RareLootParser.parseDye("WOW! Someone lootshared a Necron Dye!");
        assertNotNull(sharedWow);
        assertTrue(sharedWow.shared());
    }

    @Test
    void theLinesBelowTheAnnouncementAreNoDyeFinds() {
        assertNull(RareLootParser.parseDye("An unbelievable 1/62.5k (0.0016%) chance! (+208★ Magic Find)"));
        assertNull(RareLootParser.parseDye("Talk to Vincent in the Artist's Abode to learn more about this dye!"));
        assertNull(RareLootParser.parseDye("LOOT SHARE! Iceberg Dye"));
    }

    @Test
    void otherWowLinesAreNoDyes() {
        assertNull(RareLootParser.parseDye("WOW! You found a Glacite Mineshaft portal!"));
        assertNull(RareLootParser.parseDye("SUPER WOW! Someone lootshared a Hyperion!"));
    }

    @Test
    void theReceiptLineGivesTheDyeThatWasSharedWithMe() {
        Drop drop = RareLootParser.parseDyeShare("LOOT SHARE! Iceberg Dye");
        assertNotNull(drop);
        assertEquals("Iceberg Dye", drop.displayName());
        assertEquals("DYE_ICEBERG", drop.itemIdCandidates().get(0));
        assertEquals(1, drop.amount());
    }

    @Test
    void theReceiptLineWithoutTheExclamationMarkIsReadToo() {
        // Bei den Shard-Zeilen fiel schon einmal das Ausrufezeichen weg bzw. kam hinzu: beide Schreibweisen gelten
        assertNotNull(RareLootParser.parseDyeShare("LOOT SHARE Iceberg Dye"));
    }

    @Test
    void shardLootsharesAreNoDyeReceipts() {
        assertNull(RareLootParser.parseDyeShare("LOOT SHARE You received 3 Silkbreeze Shards for assisting Outyc!"));
        assertNull(RareLootParser.parseDyeShare("LOOT SHARE! You received a Doomspiral Shard from Someone catching a Doomspiral!"));
        assertNull(RareLootParser.parseDyeShare("LOOT SHARE! You received a Hunters Dye for assisting Someone!"));
        assertNull(RareLootParser.parseDyeShare("SUPER WOW! [MVP+] dyedicated lootshared an Iceberg Dye!"));
        assertNull(RareLootParser.parseDyeShare(null));
    }
}
