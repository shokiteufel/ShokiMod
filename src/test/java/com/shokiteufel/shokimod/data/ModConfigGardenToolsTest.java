package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.render.hud.SafariHud;

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
 * Garden > Visitor Shopping List, Visitor Reward Warning und Garden Plots Widget: von Haus aus aus, die Zahlen von SkyHanni, Schalter und Tore, speichern und laden, eine Datei von
 * vorher, beschaedigte Werte, die drei Kaesten des HUD-Editors - und wie der Editor die Reiter und das Akkordeon liest.
 */
class ModConfigGardenToolsTest {

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

    // ---- Vorgaben ----

    @Test
    void everythingStartsOff() {
        ModConfig c = new ModConfig();
        assertFalse(c.garden.shoppingList.enabled);
        assertFalse(c.garden.rewardWarning.notifyInChat, "bei SkyHanni an, hier aus");
        assertFalse(c.garden.rewardWarning.showOverName, "bei SkyHanni an, hier aus");
        assertFalse(c.garden.rewardWarning.preventRefusing, "bei SkyHanni an, hier aus");
        assertFalse(c.garden.rewardWarning.preventRefusingNew, "bei SkyHanni an, hier aus");
        assertFalse(c.garden.rewardWarning.preventRefusingCopper);
        assertFalse(c.garden.rewardWarning.preventAcceptingCopper);
        assertFalse(c.garden.rewardWarning.preventRefusingLowLoss);
        assertFalse(c.garden.rewardWarning.preventAcceptingHighLoss);
        assertFalse(c.garden.plots.enabled, "bei Skyblocker an, hier aus");
        with(x -> {
        }, () -> {
            assertFalse(FeatureGate.visitorShoppingList());
            assertFalse(FeatureGate.visitorRewardWarning());
            assertFalse(FeatureGate.visitorBlocking());
            assertFalse(FeatureGate.visitorBook());
            assertFalse(FeatureGate.gardenPlots());
        });
    }

    @Test
    void theSubOptionsKeepTheirSensibleDefaults() {
        ModConfig c = new ModConfig();
        assertTrue(c.garden.shoppingList.showPrice);
        assertTrue(c.garden.shoppingList.inBazaarAlley);
        assertFalse(c.garden.shoppingList.inFarmingAreas);
        assertFalse(c.garden.shoppingList.onlyWhenClose);
        assertTrue(c.garden.rewardWarning.optionOutline);
        assertFalse(c.garden.plots.closeScreenOnPlotClick);
    }

    @Test
    void theNumbersAreSkyHannis() {
        ModConfig.GardenRewardWarningCategory w = new ModConfig().garden.rewardWarning;
        assertEquals(6000, w.coinsPerCopperPrice);
        assertEquals(150_000, w.coinsLossThreshold);
        assertEquals(180, w.transparency);
        assertEquals(341, w.bypassKey, "Strg links");
    }

    // ---- Tore ----

    @Test
    void theShoppingListOpensTheBookAndTheLocation() {
        with(c -> c.garden.shoppingList.enabled = true, () -> {
            assertTrue(FeatureGate.visitorShoppingList());
            assertTrue(FeatureGate.visitorBook());
            assertTrue(FeatureGate.location(), "die Liste gilt nur in SkyBlock: die Mod muss den Ort lesen");
            assertFalse(FeatureGate.visitorRewardWarning());
            assertFalse(FeatureGate.visitorBlocking());
            assertFalse(FeatureGate.gardenPlots());
        });
    }

    @Test
    void notifyAndShowOverNameOpenTheBookButBlockNothing() {
        with(c -> c.garden.rewardWarning.notifyInChat = true, () -> {
            assertTrue(FeatureGate.visitorRewardWarning());
            assertTrue(FeatureGate.visitorBook());
            assertTrue(FeatureGate.location());
            assertFalse(FeatureGate.visitorBlocking());
            assertFalse(FeatureGate.visitorShoppingList());
        });
        with(c -> c.garden.rewardWarning.showOverName = true, () -> {
            assertTrue(FeatureGate.visitorRewardWarning());
            assertTrue(FeatureGate.visitorBook());
            assertFalse(FeatureGate.visitorBlocking());
        });
    }

    @Test
    void eachBlockSwitchOpensTheBlockingGate() {
        List<Consumer<ModConfig>> blockers = List.of(c -> c.garden.rewardWarning.preventRefusing = true, c -> c.garden.rewardWarning.preventRefusingNew = true,
                c -> c.garden.rewardWarning.preventRefusingCopper = true, c -> c.garden.rewardWarning.preventAcceptingCopper = true,
                c -> c.garden.rewardWarning.preventRefusingLowLoss = true, c -> c.garden.rewardWarning.preventAcceptingHighLoss = true);
        for (int i = 0; i < blockers.size(); i++) {
            int index = i;
            with(blockers.get(i), () -> {
                assertTrue(FeatureGate.visitorBlocking(), "Sperre " + index);
                assertTrue(FeatureGate.visitorRewardWarning(), "Sperre " + index);
                assertTrue(FeatureGate.visitorBook(), "Sperre " + index);
                assertTrue(FeatureGate.location(), "Sperre " + index);
            });
        }
    }

    @Test
    void theNumbersAndTheKeyAloneSwitchNothingOn() {
        with(c -> {
            c.garden.rewardWarning.coinsPerCopperPrice = 1;
            c.garden.rewardWarning.coinsLossThreshold = 1;
            c.garden.rewardWarning.transparency = 0;
            c.garden.rewardWarning.bypassKey = 100;
            c.garden.rewardWarning.optionOutline = false;
            c.garden.rewardWarning.rewardDelicate = true;
            c.garden.shoppingList.showPrice = false;
            c.garden.shoppingList.onlyWhenClose = true;
            c.garden.plots.closeScreenOnPlotClick = true;
        }, () -> {
            assertFalse(FeatureGate.visitorBook());
            assertFalse(FeatureGate.visitorBlocking());
            assertFalse(FeatureGate.gardenPlots());
        });
    }

    @Test
    void thePlotsWidgetOpensItsGateAndTheLocation() {
        with(c -> c.garden.plots.enabled = true, () -> {
            assertTrue(FeatureGate.gardenPlots());
            assertTrue(FeatureGate.location(), "die Befallenen stehen in der Tab-Liste: die Mod muss sie lesen");
            assertFalse(FeatureGate.visitorBook());
        });
    }

    // ---- speichern und laden ----

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.garden.shoppingList.enabled = true;
        c.garden.shoppingList.onlyWhenClose = true;
        c.garden.shoppingList.inBazaarAlley = false;
        c.garden.shoppingList.inFarmingAreas = true;
        c.garden.shoppingList.showPrice = false;
        c.garden.shoppingList.hudX = 0.4f;
        c.garden.shoppingList.hudScale = 2.0f;
        c.garden.rewardWarning.notifyInChat = true;
        c.garden.rewardWarning.showOverName = true;
        c.garden.rewardWarning.preventRefusing = true;
        c.garden.rewardWarning.preventRefusingNew = true;
        c.garden.rewardWarning.bypassKey = 345;
        c.garden.rewardWarning.rewardDelicate = true;
        c.garden.rewardWarning.rewardDedication = false;
        c.garden.rewardWarning.coinsPerCopperPrice = 7500;
        c.garden.rewardWarning.coinsLossThreshold = 200_000;
        c.garden.rewardWarning.preventRefusingCopper = true;
        c.garden.rewardWarning.preventAcceptingCopper = true;
        c.garden.rewardWarning.preventRefusingLowLoss = true;
        c.garden.rewardWarning.preventAcceptingHighLoss = true;
        c.garden.rewardWarning.transparency = 90;
        c.garden.rewardWarning.optionOutline = false;
        c.garden.plots.enabled = true;
        c.garden.plots.closeScreenOnPlotClick = true;
        c.garden.plots.hudY = 0.6f;

        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        ModConfig.repairGardenTools(again.garden);
        assertTrue(again.garden.shoppingList.enabled);
        assertTrue(again.garden.shoppingList.onlyWhenClose);
        assertFalse(again.garden.shoppingList.inBazaarAlley, "ein eigenes Nein bleibt");
        assertTrue(again.garden.shoppingList.inFarmingAreas);
        assertFalse(again.garden.shoppingList.showPrice);
        assertEquals(0.4f, again.garden.shoppingList.hudX);
        assertEquals(2.0f, again.garden.shoppingList.hudScale);
        assertTrue(again.garden.rewardWarning.notifyInChat);
        assertTrue(again.garden.rewardWarning.showOverName);
        assertTrue(again.garden.rewardWarning.preventRefusing);
        assertTrue(again.garden.rewardWarning.preventRefusingNew);
        assertEquals(345, again.garden.rewardWarning.bypassKey);
        assertTrue(again.garden.rewardWarning.rewardDelicate);
        assertFalse(again.garden.rewardWarning.rewardDedication, "eine abgewaehlte Belohnung bleibt abgewaehlt");
        assertEquals(7500, again.garden.rewardWarning.coinsPerCopperPrice);
        assertEquals(200_000, again.garden.rewardWarning.coinsLossThreshold);
        assertTrue(again.garden.rewardWarning.preventRefusingCopper);
        assertTrue(again.garden.rewardWarning.preventAcceptingCopper);
        assertTrue(again.garden.rewardWarning.preventRefusingLowLoss);
        assertTrue(again.garden.rewardWarning.preventAcceptingHighLoss);
        assertEquals(90, again.garden.rewardWarning.transparency);
        assertFalse(again.garden.rewardWarning.optionOutline);
        assertTrue(again.garden.plots.enabled);
        assertTrue(again.garden.plots.closeScreenOnPlotClick);
        assertEquals(0.6f, again.garden.plots.hudY);
    }

    @Test
    void aFileFromBeforeKeepsTheDefaults() {
        // Eine Datei von vor 2.5.4 kennt die drei Teile nicht; Gson legt sie mit den Vorgaben an, oder sie stehen als null da
        ModConfig loaded = ModConfig.GSON.fromJson("{\"garden\":{\"traps\":{\"warnAt\":2}}}", ModConfig.class);
        assertFalse(ModConfig.repairGardenTools(loaded.garden), "nichts war beschaedigt");
        assertEquals(2, loaded.garden.traps.warnAt, "was in der Datei stand, bleibt");
        assertFalse(loaded.garden.shoppingList.enabled);
        assertEquals(150_000, loaded.garden.rewardWarning.coinsLossThreshold);
        assertFalse(loaded.garden.plots.enabled);

        ModConfig nulls = ModConfig.GSON.fromJson("{\"garden\":{\"shoppingList\":null,\"rewardWarning\":null,\"plots\":null}}", ModConfig.class);
        assertEquals(null, nulls.garden.shoppingList);
        ModConfig.repairGardenTools(nulls.garden);
        assertNotNull(nulls.garden.shoppingList);
        assertNotNull(nulls.garden.rewardWarning);
        assertNotNull(nulls.garden.plots);
        assertEquals(6000, nulls.garden.rewardWarning.coinsPerCopperPrice);
        assertEquals(0.80f, nulls.garden.plots.hudX);
    }

    @Test
    void damagedNumbersAreStraightenedOnce() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"garden\":{\"rewardWarning\":{\"coinsPerCopperPrice\":-5,\"coinsLossThreshold\":0,\"transparency\":999},"
                + "\"shoppingList\":{\"hudX\":7,\"hudY\":-2,\"hudScale\":0,\"hudAlpha\":3},\"plots\":{\"hudScale\":50}}}", ModConfig.class);
        assertTrue(ModConfig.repairGardenTools(loaded.garden), "etwas wurde zurechtgerueckt: speichern");
        assertEquals(1, loaded.garden.rewardWarning.coinsPerCopperPrice);
        assertEquals(1, loaded.garden.rewardWarning.coinsLossThreshold);
        assertEquals(255, loaded.garden.rewardWarning.transparency);
        assertEquals(1.0f, loaded.garden.shoppingList.hudX);
        assertEquals(0.0f, loaded.garden.shoppingList.hudY);
        assertEquals(0.5f, loaded.garden.shoppingList.hudScale);
        assertEquals(1.0f, loaded.garden.shoppingList.hudAlpha);
        assertEquals(3.0f, loaded.garden.plots.hudScale);
        assertFalse(ModConfig.repairGardenTools(loaded.garden), "ein zweites Mal gibt es nichts mehr");

        ModConfig big = ModConfig.GSON.fromJson("{\"garden\":{\"rewardWarning\":{\"coinsPerCopperPrice\":99999999,\"coinsLossThreshold\":99999999,\"transparency\":-1}}}", ModConfig.class);
        ModConfig.repairGardenTools(big.garden);
        assertEquals(50_000, big.garden.rewardWarning.coinsPerCopperPrice);
        assertEquals(500_000, big.garden.rewardWarning.coinsLossThreshold);
        assertEquals(0, big.garden.rewardWarning.transparency);
    }

    @Test
    void anUnreadableHudValueBecomesTheDefault() {
        assertEquals(0.5f, ModConfig.clampHud(Float.NaN, 0f, 1f, 0.5f));
        assertEquals(0.5f, ModConfig.clampHud(Float.POSITIVE_INFINITY, 0f, 1f, 0.5f));
        assertEquals(0.25f, ModConfig.clampHud(0.25f, 0f, 1f, 0.5f));
    }

    // ---- die Kaesten des HUD-Editors ----

    @Test
    void theTwoNewBoxesAreBoxesOfTheHudEditor() {
        with(c -> {
        }, () -> {
            SafariHud.Panel list = SafariHud.Panel.VISITOR_LIST;
            assertEquals("Visitor Shopping List", list.settingsSearch());
            assertEquals(0.12f, list.x());
            assertEquals(0.20f, list.y());
            assertEquals(1.0f, list.scale());
            assertEquals(1.0f, list.alpha());
            assertFalse(list.visible(), "ohne den Schalter steht er nie");

            list.setPosition(0.5f, 0.25f);
            list.setScale(2.0f);
            list.setAlpha(0.5f);
            assertEquals(0.5f, ModConfig.INSTANCE.garden.shoppingList.hudX);
            assertEquals(0.25f, ModConfig.INSTANCE.garden.shoppingList.hudY);
            assertEquals(2.0f, ModConfig.INSTANCE.garden.shoppingList.hudScale);
            assertEquals(0.5f, ModConfig.INSTANCE.garden.shoppingList.hudAlpha);
            list.setScale(9.0f);
            assertEquals(3.0f, list.scale(), "die Grenze des Editors");
            list.setAlpha(7.0f);
            assertEquals(1.0f, list.alpha());

            SafariHud.Panel plots = SafariHud.Panel.GARDEN_PLOTS;
            assertEquals("Garden Plots Widget", plots.settingsSearch());
            assertEquals(0.80f, plots.x());
            assertEquals(0.35f, plots.y());
            assertFalse(plots.visible(), "ohne den Schalter steht er nie");
            plots.setPosition(0.1f, 0.9f);
            plots.setScale(0.1f);
            plots.setAlpha(-1f);
            assertEquals(0.1f, ModConfig.INSTANCE.garden.plots.hudX);
            assertEquals(0.9f, ModConfig.INSTANCE.garden.plots.hudY);
            assertEquals(0.5f, plots.scale(), "die Grenze des Editors");
            assertEquals(0.0f, plots.alpha());
        });
    }

    @Test
    void thePlotsBoxStandsInTheGardenAndInTheEditorOnly() {
        String saved = GameState.Server.map;
        try {
            with(c -> c.garden.plots.enabled = true, () -> {
                GameState.Server.map = "Garden";
                assertTrue(SafariHud.Panel.GARDEN_PLOTS.visible());
                GameState.Server.map = "Hub";
                assertFalse(SafariHud.Panel.GARDEN_PLOTS.visible(), "ausserhalb des Gartens nicht");
                GameState.Server.map = "Unknown";
                assertFalse(SafariHud.Panel.GARDEN_PLOTS.visible());
            });
        } finally {
            GameState.Server.map = saved;
        }
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

    private static int optionsInFoldOut(ProcessedCategory category, int accordionId) {
        int n = 0;
        for (ProcessedOption o : category.getOptions()) {
            if (o.getAccordionId() == accordionId) n++;
        }
        return n;
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
    void theGardenHasTheThreeNewSubTabsNextToTheOldOnes() {
        Ergebnis e = verarbeiten();
        ProcessedCategory garden = nach(e, "ModConfig.garden");
        assertNotNull(garden, "der Reiter Garden");
        for (String name : new String[] {"GardenCategory.traps", "GardenCategory.visitors", "GardenCategory.pests", "GardenCategory.shoppingList", "GardenCategory.rewardWarning",
                "GardenCategory.plots"}) {
            ProcessedCategory sub = nach(e, name);
            assertNotNull(sub, name);
            assertEquals(garden.getIdentifier(), sub.getParentCategoryId(), name + " haengt unter Garden");
        }
    }

    @Test
    void theShoppingListHasTheOptionsOfSkyHanniThatTheModCanDo() {
        Ergebnis e = verarbeiten();
        ProcessedCategory list = nach(e, "GardenCategory.shoppingList");
        assertNotNull(list);
        assertEquals(List.of("Visitor Shopping List", "Enable", "Only when Close", "Bazaar Alley", "Farming Areas", "Show Price"), optionNames(list),
                "ohne Show Sack Count, Show Super Craft und Item Preview: dafuer gibt es keine Quelle");
        for (String name : new String[] {"Enable", "Only when Close", "Bazaar Alley", "Farming Areas", "Show Price"}) {
            assertEquals("GuiOptionEditorBoolean", option(list, name).getEditor().getClass().getSimpleName(), name);
        }
        assertEquals(Boolean.FALSE, option(list, "Enable").get());
        assertEquals(Boolean.TRUE, option(list, "Show Price").get());
    }

    @Test
    void theRewardWarningHasSkyHannisOptionsInTheirOrderAndAFoldOutWithEveryReward() {
        Ergebnis e = verarbeiten();
        ProcessedCategory warning = nach(e, "GardenCategory.rewardWarning");
        assertNotNull(warning);
        List<String> names = optionNames(warning);
        assertInOrder(names, "Visitor Reward Warning", "Notify in Chat", "Show over Name", "Block Refusing Reward", "Bypass Key", "Items", "Coins per Copper",
                "Block Refusing Copper", "Block Accepting Copper", "Acceptable Coin Loss", "Block Refusing Low Loss", "Block Accepting High Loss",
                "Block Refusing New Visitors", "Transparency", "Outline");
        assertEquals(32, optionsInFoldOut(warning, 140), "ein Schalter je Belohnung");
        for (String reward : new String[] {"Overgrown Grass", "Dedication IV", "Copper Dye", "Jelly Garden Greenhouse Skin"}) {
            assertTrue(names.contains(reward), reward);
            assertEquals(140, option(warning, reward).getAccordionId(), reward + " steht im Akkordeon");
        }
        assertTrue(option(warning, "Bypass Key").getEditor().getClass().getSimpleName().contains("Keybind"), "ein Tastenwaehler");
        assertEquals(341, option(warning, "Bypass Key").get());
        for (String slider : new String[] {"Coins per Copper", "Acceptable Coin Loss", "Transparency"}) {
            assertEquals("GuiOptionEditorSlider", option(warning, slider).getEditor().getClass().getSimpleName(), slider);
        }
        assertEquals(6000, option(warning, "Coins per Copper").get());
        assertEquals(150_000, option(warning, "Acceptable Coin Loss").get());
        assertEquals(180, option(warning, "Transparency").get());
        assertEquals(Boolean.FALSE, option(warning, "Notify in Chat").get());
        assertEquals(Boolean.TRUE, option(warning, "Overgrown Grass").get(), "SkyHannis Vorauswahl");
        assertEquals(Boolean.FALSE, option(warning, "Delicate V").get());
    }

    @Test
    void theAccordionIdOfTheRewardsIsNotUsedAnywhereElse() {
        Ergebnis e = verarbeiten();
        int owners = 0;
        for (var k : e.kategorien.entrySet()) {
            if (optionsInFoldOut(k.getValue(), 140) > 0) owners++;
        }
        assertEquals(1, owners, "nur der Reiter Visitor Reward Warning hat ein Akkordeon 140");
    }

    @Test
    void thePlotsWidgetHasItsOptions() {
        Ergebnis e = verarbeiten();
        ProcessedCategory plots = nach(e, "GardenCategory.plots");
        assertNotNull(plots);
        assertEquals(List.of("Garden Plots Widget", "Enable Garden Plots Widget", "Close Inventory on Teleport"), optionNames(plots));
        assertEquals(Boolean.FALSE, option(plots, "Enable Garden Plots Widget").get());
    }
}
