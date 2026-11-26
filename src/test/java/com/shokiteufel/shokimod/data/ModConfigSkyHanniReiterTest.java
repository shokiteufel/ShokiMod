package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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
    void chatHasRareDropMessagesAndCompactPotionMessagesBelowItInTheOrderOfSkyHanni() {
        Ergebnis e = verarbeiten();
        ProcessedCategory chat = nach(e, "ModConfig.chatTab");
        assertNotNull(chat, "der Reiter Chat");
        ProcessedCategory rare = nach(e, "ChatTabCategory.rareDrop");
        ProcessedCategory potion = nach(e, "ChatTabCategory.compactPotion");
        assertNotNull(rare, "ChatTabCategory.rareDrop");
        assertNotNull(potion, "ChatTabCategory.compactPotion");
        assertEquals(chat.getIdentifier(), rare.getParentCategoryId(), "Rare Drop Messages haengt unter Chat");
        assertEquals(chat.getIdentifier(), potion.getParentCategoryId(), "Compact Potion Messages haengt unter Chat");
        assertEquals(4, rare.getOptions().size(), "Info und drei Schalter");
        assertEquals(3, potion.getOptions().size(), "Info und zwei Schalter");

        // Die Unterreiter stehen wie bei SkyHanni: Chat Filter Types, dann Rare Drop Messages, dann Compact Potion Messages
        List<String> subTabs = new ArrayList<>();
        for (var k : e.kategorien.entrySet()) {
            if (chat.getIdentifier().equals(k.getValue().getParentCategoryId())) subTabs.add(k.getKey());
        }
        int types = indexOfKeyContaining(subTabs, "ChatTabCategory.filterTypes");
        int rareAt = indexOfKeyContaining(subTabs, "ChatTabCategory.rareDrop");
        int potionAt = indexOfKeyContaining(subTabs, "ChatTabCategory.compactPotion");
        assertTrue(types >= 0 && types < rareAt && rareAt < potionAt, "Reihenfolge der Unterreiter: " + subTabs);

        // Die Schalter von Chat stehen an der Stelle, die zu SkyHannis Reihenfolge passt; die Dungeon Filters sind weiter ein Akkordeon mit elf Arten
        assertEquals(11, optionsInFoldOut(chat, 130), "Dungeon Filters: elf Arten");
        List<String> names = new ArrayList<>();
        // Der Name ist ein StructuredText; sein toString() ist "MoulConfigText(text=Name)"
        for (ProcessedOption o : chat.getOptions()) {
            String shown = String.valueOf(o.getName());
            names.add(shown.startsWith("MoulConfigText(text=") && shown.endsWith(")") ? shown.substring("MoulConfigText(text=".length(), shown.length() - 1) : shown);
        }
        String[] skyHanniOrder = {"Dungeon Boss Messages", "Hide Far Deaths", "Compact Bestiary Messages", "Compact Enchanting Rewards", "Compact Jacob Claim",
                "Arachne Hider", "Sack Change Hider", "Only Hide on Garden", "SkyBlock XP in Chat", "Anita's Accessories", "Sky Mall Messages",
                "Show Actual Powder Gained", "Hide Foraging Buff Messages", "Shorten Coin Amounts", "Hide Clickable Hints", "Dark Auction Item Display"};
        int last = -1;
        for (String name : skyHanniOrder) {
            int at = names.indexOf(name);
            assertTrue(at > last, name + " steht hinter dem vorigen (Stelle " + at + ", vorige " + last + "): " + names);
            last = at;
        }
    }

    private static int indexOfKeyContaining(List<String> keys, String part) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).contains(part)) return i;
        }
        return -1;
    }

    private static List<String> optionNames(ProcessedCategory category) {
        List<String> names = new ArrayList<>();
        // Der Name ist ein StructuredText; sein toString() ist "MoulConfigText(text=Name)"
        for (ProcessedOption o : category.getOptions()) {
            String shown = String.valueOf(o.getName());
            names.add(shown.startsWith("MoulConfigText(text=") && shown.endsWith(")") ? shown.substring("MoulConfigText(text=".length(), shown.length() - 1) : shown);
        }
        return names;
    }

    @Test
    void chatPart3PeekChatOnTopSoundResponsesBeforeRareDropAndTabCompleteAtTheEnd() {
        Ergebnis e = verarbeiten();
        assertFalse(e.fehlerausgabe.contains("sub categories inside of an accordion"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.toLowerCase().contains("duplicate"), e.fehlerausgabe);
        ProcessedCategory chat = nach(e, "ModConfig.chatTab");
        assertNotNull(chat, "der Reiter Chat");

        // Die Unterreiter in der Reihenfolge von SkyHanni; Tab Complete (bei SkyHanni "Commands") ganz hinten
        List<String> subTabs = new ArrayList<>();
        for (var k : e.kategorien.entrySet()) {
            if (chat.getIdentifier().equals(k.getValue().getParentCategoryId())) subTabs.add(k.getKey());
        }
        int types = indexOfKeyContaining(subTabs, "ChatTabCategory.filterTypes");
        int sound = indexOfKeyContaining(subTabs, "ChatTabCategory.soundResponse");
        int rare = indexOfKeyContaining(subTabs, "ChatTabCategory.rareDrop");
        int potion = indexOfKeyContaining(subTabs, "ChatTabCategory.compactPotion");
        int tab = indexOfKeyContaining(subTabs, "ChatTabCategory.tabComplete");
        assertTrue(types >= 0 && types < sound && sound < rare && rare < potion && potion < tab, "Reihenfolge der Unterreiter: " + subTabs);

        // Peek Chat steht ganz oben, Copy Chat nach den Dungeon Filters, Current Chat Display nach Hide Far Deaths
        List<String> names = optionNames(chat);
        assertEquals("Peek Chat", names.get(0), "ganz oben: " + names);
        int dungeonBoss = names.indexOf("Dungeon Boss Messages");
        int copy = names.indexOf("Copy Chat");
        int lastDungeonSwitch = names.indexOf("End (End of run spam)");
        assertTrue(lastDungeonSwitch >= 0 && lastDungeonSwitch < copy && copy < dungeonBoss, "Copy Chat zwischen den Dungeon Filters und Dungeon Boss Messages: " + names);
        int farDeaths = names.indexOf("Hide Far Deaths");
        int display = names.indexOf("Current Chat Display");
        int bestiary = names.indexOf("Compact Bestiary Messages");
        assertTrue(farDeaths >= 0 && farDeaths < display && display < bestiary, "Current Chat Display nach Hide Far Deaths: " + names);
        assertEquals(11, optionsInFoldOut(chat, 130), "Dungeon Filters: weiter elf Arten");

        // Sound Responses: Enabled und ein Akkordeon mit fuenfzehn Arten
        ProcessedCategory soundCategory = nach(e, "ChatTabCategory.soundResponse");
        assertNotNull(soundCategory);
        assertEquals(chat.getIdentifier(), soundCategory.getParentCategoryId(), "Sound Responses haengt unter Chat");
        assertEquals(15, optionsInFoldOut(soundCategory, 136), "fuenfzehn Tierlaute");
        assertTrue(optionNames(soundCategory).contains("Enabled"));

        // Tab Complete: der Infotext und elf Schalter (Carry Customers und VIP Visits fehlen mit Absicht)
        ProcessedCategory tabCategory = nach(e, "ChatTabCategory.tabComplete");
        assertNotNull(tabCategory);
        assertEquals(chat.getIdentifier(), tabCategory.getParentCategoryId(), "Tab Complete haengt unter Chat");
        assertEquals(12, tabCategory.getOptions().size(), "Info und elf Schalter: " + optionNames(tabCategory));
        assertEquals(List.of("Tab Complete", "Warps", "Island Players", "Friends", "Only Best Friends", "Party", "Guild", "/gfs Sack", "View Recipe", "Show Item", "Call",
                "Tab Complete Stash Command"), optionNames(tabCategory), "in der Reihenfolge von SkyHanni");
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
