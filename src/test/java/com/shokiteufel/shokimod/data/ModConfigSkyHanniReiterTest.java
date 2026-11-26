package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wie der Editor die Reiter sieht, die seit 2.4.10 nach SkyHanni dazukamen (Fishing: Hook display, Bait display, Cocoon settings; Slayer: Cocoon und
 * Spawn particles; Skill progress mit Progress bar und Overflow; Inventory mit Auction house und Bazaar). Derselbe Weg wie beim Oeffnen des Fensters:
 * Ein Fehler in den Annotationen (doppelte Gruppe, falscher Feldtyp, eine dritte Ebene) wuerde dort das Fenster zerbrechen - hier faellt er vorher auf.
 */
class ModConfigSkyHanniReiterTest {

    private static final class Ergebnis {
        LinkedHashMap<String, ? extends ProcessedCategory> kategorien;
        String fehlerausgabe;
    }

    private static Ergebnis verarbeiten() {
        ModConfig config = new ModConfig();
        PrintStream alt = System.err;
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        Ergebnis e = new Ergebnis();
        try {
            System.setErr(new PrintStream(puffer, true));
            MoulConfigProcessor<ModConfig> prozessor = MoulConfigProcessor.withDefaults(config);
            new ConfigProcessorDriver(prozessor).processConfig(config);
            e.kategorien = prozessor.getAllCategories();
        } finally {
            System.setErr(alt);
        }
        e.fehlerausgabe = puffer.toString();
        return e;
    }

    private static ProcessedCategory nach(Ergebnis e, String teilDesFeldnamens) {
        for (var k : e.kategorien.entrySet()) {
            if (k.getKey().contains(teilDesFeldnamens)) return k.getValue();
        }
        return null;
    }

    @Test
    void theEditorReadsTheWholeConfigWithoutWarnings() {
        Ergebnis e = verarbeiten();
        assertFalse(e.fehlerausgabe.contains("double recursive"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("sub categories inside of an accordion"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("Cannot mix"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.toLowerCase().contains("exception"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.toLowerCase().contains("duplicate"), e.fehlerausgabe);
    }

    @Test
    void theNewFishingTabsAreSubCategoriesOfFishing() {
        Ergebnis e = verarbeiten();
        ProcessedCategory fishing = nach(e, "ModConfig.fishing");
        assertNotNull(fishing, "der Reiter Fishing");
        for (String name : new String[] {"FishingCategory.hookDisplay", "FishingCategory.baitDisplay", "FishingCategory.cocoon"}) {
            ProcessedCategory c = nach(e, name);
            assertNotNull(c, name);
            assertEquals(fishing.getIdentifier(), c.getParentCategoryId(), name + " haengt unter Fishing");
        }
    }

    @Test
    void skillProgressHasTheBarAndOverflowBelowIt() {
        Ergebnis e = verarbeiten();
        ProcessedCategory skill = nach(e, "ModConfig.skillProgress");
        assertNotNull(skill, "der Reiter Skill progress");
        for (String name : new String[] {"SkillProgressCategory.bar", "SkillProgressCategory.overflow"}) {
            ProcessedCategory c = nach(e, name);
            assertNotNull(c, name);
            assertEquals(skill.getIdentifier(), c.getParentCategoryId(), name + " haengt unter Skill progress");
        }
    }

    @Test
    void inventoryHasAuctionHouseBazaarAndTheFlipsBelowIt() {
        Ergebnis e = verarbeiten();
        ProcessedCategory inventory = nach(e, "ModConfig.inventory");
        assertNotNull(inventory, "der Reiter Inventory");
        for (String name : new String[] {"InventoryCategory.auctions", "InventoryCategory.bazaar", "InventoryCategory.flips", "InventoryCategory.itemValue"}) {
            ProcessedCategory c = nach(e, name);
            assertNotNull(c, name);
            assertEquals(inventory.getIdentifier(), c.getParentCategoryId(), name + " haengt unter Inventory");
        }
    }

    @Test
    void miscHasTheNpcTrackerBelowIt() {
        Ergebnis e = verarbeiten();
        ProcessedCategory misc = nach(e, "ModConfig.misc");
        assertNotNull(misc, "der Reiter Misc");
        ProcessedCategory npc = nach(e, "MiscCategory.npcLimit");
        assertNotNull(npc, "MiscCategory.npcLimit");
        assertEquals(misc.getIdentifier(), npc.getParentCategoryId(), "der Tracker haengt unter Misc");
        assertTrue(misc.getOptions().size() >= 4, "Info, Shorten /warp, Replace /warp is, Fix item double clicks");
    }

    private static int optionsInFoldOut(ProcessedCategory category, int accordionId) {
        int n = 0;
        for (ProcessedOption o : category.getOptions()) {
            if (o.getAccordionId() == accordionId) n++;
        }
        return n;
    }

    @Test
    void chatHasChatFilterTypesBelowItAndFoldOutsThatHoldTheirOptions() {
        Ergebnis e = verarbeiten();
        ProcessedCategory chat = nach(e, "ModConfig.chatTab");
        assertNotNull(chat, "der Reiter Chat (nicht Alerts: das ist ModConfig.chat)");
        ProcessedCategory types = nach(e, "ChatTabCategory.filterTypes");
        assertNotNull(types, "ChatTabCategory.filterTypes");
        assertEquals(chat.getIdentifier(), types.getParentCategoryId(), "Chat Filter Types haengt unter Chat");
        // Dungeon Filters: ein Akkordeon mit den elf Arten
        assertEquals(11, optionsInFoldOut(chat, 130), "Dungeon Filters: elf Arten");
        // Chat Filter Types: Powder Mining (Hauptschalter, zwei Schieber, zehn Listeneintraege, Goblin Egg, Stronger Tool, sieben Edelsteine)
        assertEquals(22, optionsInFoldOut(types, 131), "Powder Mining");
        assertEquals(9, optionsInFoldOut(types, 132), "Crystal Nucleus: Hauptschalter und acht Arten");
        assertEquals(1, optionsInFoldOut(types, 133), "Foraging");
        assertEquals(2, optionsInFoldOut(types, 134), "Hunting");
        assertEquals(7, optionsInFoldOut(types, 135), "Stash Messages");
        // Die Alerts-Kategorie (Feld chat) ist unberuehrt und eine andere
        ProcessedCategory alerts = null;
        for (var k : e.kategorien.entrySet()) {
            if (k.getKey().contains("ModConfig.chat") && !k.getKey().contains("ModConfig.chatTab")) alerts = k.getValue();
        }
        assertNotNull(alerts, "der Reiter Alerts (ModConfig.chat) bleibt");
    }

    @Test
    void theSlayerGroupsForCocoonAndSpawnParticlesHoldTheirOptions() {
        Ergebnis e = verarbeiten();
        ProcessedCategory slayer = nach(e, "ModConfig.slayer");
        assertNotNull(slayer, "der Reiter Slayer");
        int cocoonOptions = 0;
        int particleOptions = 0;
        for (ProcessedOption o : slayer.getOptions()) {
            if (o.getAccordionId() == 121) cocoonOptions++;
            if (o.getAccordionId() == 122) particleOptions++;
        }
        assertTrue(cocoonOptions >= 4, "Cocoon: Titel, Ton, Datei, Test - gezaehlt " + cocoonOptions);
        assertEquals(3, particleOptions, "drei Arten Spawn-Partikel");
    }
}
