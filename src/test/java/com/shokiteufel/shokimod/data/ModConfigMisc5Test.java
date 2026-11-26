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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drei kleine Funktionen nach SkyHanni: Fishing > Shorten Fishing Message / Compact Double Hook (/ Position), Inventory > Auction house > Highlight Auctions /
 * Highlight Underbid Auctions (mit drei Farben), Skill progress > Hide in action bar. Von Haus aus aus (ausser dem letzten), speichern und laden, eine Datei von
 * vorher, beschaedigte Werte, die Schalter der Gates - und wie der Editor die Reiter liest.
 */
class ModConfigMisc5Test {

    private static void with(Consumer<ModConfig> change, Runnable then) {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            change.accept(ModConfig.INSTANCE);
            then.run();
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    // ---- Fishing ----

    @Test
    void theFishingLinesAreOffFromTheStart() {
        ModConfig c = new ModConfig();
        assertFalse(c.fishing.shortenFishingMessage);
        assertFalse(c.fishing.compactDoubleHook, "bei SkyHanni an, hier aus");
        assertEquals(ModConfig.CompactDoubleHookPosition.LEFT, c.fishing.compactDoubleHookPosition);
        assertFalse(c.fishing.anyChatLine());
        with(x -> {
        }, () -> {
            assertFalse(FeatureGate.fishingLines());
            assertEquals(ModConfig.INSTANCE.chatTab.anyFilter(), FeatureGate.chatFilters(), "die Fishing-Schalter aus: der Chat-Weg haengt nur an Chat");
        });
    }

    @Test
    void eachFishingSwitchOpensTheChatWayAndTheLocation() {
        with(c -> c.fishing.shortenFishingMessage = true, () -> {
            assertTrue(FeatureGate.fishingLines());
            assertTrue(FeatureGate.chatFilters());
            assertTrue(FeatureGate.location(), "Shorten gilt nur in SkyBlock: die Mod muss den Ort lesen");
        });
        with(c -> c.fishing.compactDoubleHook = true, () -> {
            assertTrue(FeatureGate.fishingLines());
            assertTrue(FeatureGate.chatFilters());
            assertTrue(FeatureGate.location());
        });
        // Die Position allein ist kein Schalter
        with(c -> c.fishing.compactDoubleHookPosition = ModConfig.CompactDoubleHookPosition.RIGHT, () -> assertFalse(FeatureGate.fishingLines()));
    }

    @Test
    void theFishingChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.fishing.shortenFishingMessage = true;
        c.fishing.compactDoubleHook = true;
        c.fishing.compactDoubleHookPosition = ModConfig.CompactDoubleHookPosition.RIGHT;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.fishing.shortenFishingMessage);
        assertTrue(again.fishing.compactDoubleHook);
        assertEquals(ModConfig.CompactDoubleHookPosition.RIGHT, again.fishing.compactDoubleHookPosition);
    }

    @Test
    void aFishingFileFromBeforeKeepsTheDefaults() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"fishing\":{\"hookDisplay\":null}}", ModConfig.class);
        ModConfig.repairFishingExtras(loaded.fishing);
        assertFalse(loaded.fishing.shortenFishingMessage);
        assertFalse(loaded.fishing.compactDoubleHook);
        assertEquals(ModConfig.CompactDoubleHookPosition.LEFT, loaded.fishing.compactDoubleHookPosition);
    }

    @Test
    void anUnknownPositionNameBecomesLeft() {
        // Gson macht einen Namen, den es nicht kennt, zu null: Der Editor bekaeme null und die Zeile eine Ausnahme
        ModConfig loaded = ModConfig.GSON.fromJson("{\"fishing\":{\"compactDoubleHookPosition\":\"MIDDLE\"}}", ModConfig.class);
        assertEquals(null, loaded.fishing.compactDoubleHookPosition);
        ModConfig.repairFishingExtras(loaded.fishing);
        assertEquals(ModConfig.CompactDoubleHookPosition.LEFT, loaded.fishing.compactDoubleHookPosition);
        ModConfig right = ModConfig.GSON.fromJson("{\"fishing\":{\"compactDoubleHookPosition\":\"RIGHT\"}}", ModConfig.class);
        ModConfig.repairFishingExtras(right.fishing);
        assertEquals(ModConfig.CompactDoubleHookPosition.RIGHT, right.fishing.compactDoubleHookPosition);
    }

    @Test
    void thePositionIsShownAsLeftAndRight() {
        assertEquals("Left", ModConfig.CompactDoubleHookPosition.LEFT.toString());
        assertEquals("Right", ModConfig.CompactDoubleHookPosition.RIGHT.toString());
    }

    // ---- Auction house ----

    @Test
    void theAuctionHighlightsAreOffFromTheStartWithSkyHannisColours() {
        ModConfig.AuctionHouseHelpersCategory a = new ModConfig().inventory.auctions;
        assertFalse(a.highlightAuctions, "bei SkyHanni an, hier aus");
        assertFalse(a.highlightUnderbid);
        assertEquals(0x55FF55, ColourText.rgb(a.soldColour, 0), "gruen");
        assertEquals(0xFF5555, ColourText.rgb(a.expiredColour, 0), "rot");
        assertEquals(0xFFAA00, ColourText.rgb(a.underbidColour, 0), "gold");
        with(x -> {
        }, () -> assertFalse(FeatureGate.auctionHighlights()));
    }

    @Test
    void eachAuctionSwitchOpensTheGateAndTheLocation() {
        with(c -> c.inventory.auctions.highlightAuctions = true, () -> {
            assertTrue(FeatureGate.auctionHighlights());
            assertTrue(FeatureGate.location());
            assertFalse(FeatureGate.auctionUnderbid(), "das Kopieren des Preises ist eine andere Funktion");
        });
        with(c -> c.inventory.auctions.highlightUnderbid = true, () -> {
            assertTrue(FeatureGate.auctionHighlights());
            assertTrue(FeatureGate.location());
        });
    }

    @Test
    void theAuctionChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.inventory.auctions.highlightAuctions = true;
        c.inventory.auctions.highlightUnderbid = true;
        c.inventory.auctions.soldColour = ColourText.ofRgb(0x112233);
        c.inventory.auctions.expiredColour = ColourText.ofRgb(0x445566);
        c.inventory.auctions.underbidColour = ColourText.ofRgb(0x778899);
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        ModConfig.repairInventory(again);
        assertTrue(again.inventory.auctions.highlightAuctions);
        assertTrue(again.inventory.auctions.highlightUnderbid);
        assertEquals(0x112233, ColourText.rgb(again.inventory.auctions.soldColour, 0));
        assertEquals(0x445566, ColourText.rgb(again.inventory.auctions.expiredColour, 0));
        assertEquals(0x778899, ColourText.rgb(again.inventory.auctions.underbidColour, 0));
    }

    @Test
    void anAuctionFileFromBeforeGetsTheDefaultColours() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"auctions\":{\"autoCopyUnderbid\":true}}}", ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertTrue(loaded.inventory.auctions.autoCopyUnderbid, "was in der Datei stand, bleibt");
        assertEquals(0x55FF55, ColourText.rgb(loaded.inventory.auctions.soldColour, 0));
        assertTrue(loaded.inventory.auctions.soldColour.indexOf(':') >= 0, "im Format des Waehlers");
        assertTrue(loaded.inventory.auctions.expiredColour.indexOf(':') >= 0);
        assertTrue(loaded.inventory.auctions.underbidColour.indexOf(':') >= 0);
    }

    @Test
    void damagedAndOldColoursAreStraightened() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"inventory\":{\"auctions\":{\"soldColour\":null,\"expiredColour\":\"00FF00\",\"underbidColour\":\"???\"}}}",
                ModConfig.class);
        ModConfig.repairInventory(loaded);
        assertEquals(0x55FF55, ColourText.rgb(loaded.inventory.auctions.soldColour, 0), "null ergibt die Vorgabe");
        assertEquals(0x00FF00, ColourText.rgb(loaded.inventory.auctions.expiredColour, 0), "ein alter Hexwert bleibt der Wert");
        assertTrue(loaded.inventory.auctions.expiredColour.indexOf(':') >= 0, "und wird fuer den Waehler umgeschrieben");
        assertEquals(0xFFAA00, ColourText.rgb(loaded.inventory.auctions.underbidColour, 0), "Unlesbares ergibt die Vorgabe");
    }

    // ---- Skill progress ----

    @Test
    void hideInActionBarIsOnFromTheStartButOnlyWorksWithSkillProgress() {
        ModConfig c = new ModConfig();
        assertTrue(c.skillProgress.hideInActionBar);
        assertFalse(c.skillProgress.enabled);
        with(x -> {
        }, () -> assertFalse(FeatureGate.hideSkillGain(), "Skill progress aus: nichts wird versteckt"));
        with(x -> x.skillProgress.enabled = true, () -> assertTrue(FeatureGate.hideSkillGain()));
        with(x -> {
            x.skillProgress.enabled = true;
            x.skillProgress.hideInActionBar = false;
        }, () -> assertFalse(FeatureGate.hideSkillGain()));
    }

    @Test
    void theHideChoiceSurvivesSavingAndAFileWithoutItKeepsTheDefault() {
        ModConfig c = new ModConfig();
        c.skillProgress.hideInActionBar = false;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertFalse(again.skillProgress.hideInActionBar, "ein eigenes Nein bleibt");
        ModConfig old = ModConfig.GSON.fromJson("{\"skillProgress\":{\"enabled\":true}}", ModConfig.class);
        ModConfig.repairSkillProgress(old);
        assertTrue(old.skillProgress.hideInActionBar, "eine Datei von vorher kennt den Schalter nicht: die Vorgabe (an) gilt");
    }

    // ---- wie der Editor die Reiter liest ----

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

    private static List<String> optionNames(ProcessedCategory category) {
        List<String> names = new ArrayList<>();
        // Der Name ist ein StructuredText; sein toString() ist "MoulConfigText(text=Name)"
        for (ProcessedOption o : category.getOptions()) {
            String shown = String.valueOf(o.getName());
            names.add(shown.startsWith("MoulConfigText(text=") && shown.endsWith(")") ? shown.substring("MoulConfigText(text=".length(), shown.length() - 1) : shown);
        }
        return names;
    }

    private static ProcessedOption option(ProcessedCategory category, String name) {
        int at = optionNames(category).indexOf(name);
        assertTrue(at >= 0, name + " fehlt: " + optionNames(category));
        return category.getOptions().get(at);
    }

    private static void assertInOrder(List<String> names, String... expected) {
        int last = -1;
        for (String name : expected) {
            int at = names.indexOf(name);
            assertTrue(at > last, name + " steht hinter dem vorigen (Stelle " + at + ", vorige " + last + "): " + names);
            last = at;
        }
    }

    @Test
    void theEditorReadsTheNewOptionsWithoutWarnings() {
        Ergebnis e = verarbeiten();
        assertFalse(e.fehlerausgabe.toLowerCase().contains("exception"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.toLowerCase().contains("duplicate"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("double recursive"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("Cannot mix"), e.fehlerausgabe);
    }

    @Test
    void fishingHasTheThreeOptionsAndKeepsItsSubTabs() {
        Ergebnis e = verarbeiten();
        ProcessedCategory fishing = nach(e, "ModConfig.fishing");
        assertNotNull(fishing, "der Reiter Fishing");
        assertInOrder(optionNames(fishing), "Fishing", "Shorten Fishing Message", "Compact Double Hook", "Compact Double Hook Position");
        assertEquals("GuiOptionEditorBoolean", option(fishing, "Shorten Fishing Message").getEditor().getClass().getSimpleName());
        assertEquals("GuiOptionEditorBoolean", option(fishing, "Compact Double Hook").getEditor().getClass().getSimpleName());
        ProcessedOption position = option(fishing, "Compact Double Hook Position");
        assertEquals("GuiOptionEditorDropdown", position.getEditor().getClass().getSimpleName());
        assertEquals(ModConfig.CompactDoubleHookPosition.LEFT, position.get());
        // Die Unterreiter bleiben unter Fishing
        for (String name : new String[] {"FishingCategory.hookDisplay", "FishingCategory.baitDisplay", "FishingCategory.cocoon", "FishingCategory.hotspot"}) {
            ProcessedCategory sub = nach(e, name);
            assertNotNull(sub, name);
            assertEquals(fishing.getIdentifier(), sub.getParentCategoryId(), name + " haengt unter Fishing");
        }
    }

    @Test
    void theAuctionHouseHasTheNewOptionsBeforeTheOldOnesInSkyHannisOrder() {
        Ergebnis e = verarbeiten();
        ProcessedCategory auctions = nach(e, "InventoryCategory.auctions");
        assertNotNull(auctions, "Inventory > Auction house");
        assertInOrder(optionNames(auctions), "Auction house", "Highlight Auctions", "Sold Color", "Expired Color", "Highlight Underbid Auctions", "Underbid Color",
                "Auto copy underbid price", "Copy underbid key");
        for (String name : new String[] {"Sold Color", "Expired Color", "Underbid Color"}) {
            assertEquals("GuiOptionEditorColour", option(auctions, name).getEditor().getClass().getSimpleName(), name);
        }
        assertEquals("GuiOptionEditorBoolean", option(auctions, "Highlight Auctions").getEditor().getClass().getSimpleName());
        assertEquals("GuiOptionEditorBoolean", option(auctions, "Highlight Underbid Auctions").getEditor().getClass().getSimpleName());
    }

    @Test
    void skillProgressHasHideInActionBarBeforeAlwaysShow() {
        Ergebnis e = verarbeiten();
        ProcessedCategory skill = nach(e, "ModConfig.skillProgress");
        assertNotNull(skill, "der Reiter Skill progress");
        assertInOrder(optionNames(skill), "Enabled", "Hide in action bar", "Always show");
        assertEquals("GuiOptionEditorBoolean", option(skill, "Hide in action bar").getEditor().getClass().getSimpleName());
        assertEquals(Boolean.TRUE, option(skill, "Hide in action bar").get());
    }
}
