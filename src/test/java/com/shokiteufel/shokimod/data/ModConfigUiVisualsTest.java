package com.shokiteufel.shokimod.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI & Visuals > Skyblock Inventory Screen > Show Equipment in Inventory (nach Skyblocker): von Haus aus aus, speichern und laden, eine Datei von vorher, beschaedigte
 * Werte, das Tor in {@link FeatureGate} (mit dem Ort) - und wie der Editor die neue Kategorie liest: direkt nach Misc, vor ikohS, mit ihren Unterkategorien (seit 2.5.8 zwei: dazu Input Calculator).
 */
class ModConfigUiVisualsTest {

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

    // ---- Einstellung ----

    @Test
    void showEquipmentIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        assertNotNull(c.uiVisuals);
        assertNotNull(c.uiVisuals.inventoryScreen);
        assertFalse(c.uiVisuals.inventoryScreen.showEquipment, "bei Skyblocker an, hier aus (Vorgabe des Users: alles Neue ist aus)");
        with(x -> {
        }, () -> assertFalse(FeatureGate.equipmentInInventory()));
    }

    @Test
    void theSwitchOpensTheGateAndTheLocation() {
        with(c -> c.uiVisuals.inventoryScreen.showEquipment = true, () -> {
            assertTrue(FeatureGate.equipmentInInventory());
            assertTrue(FeatureGate.location(), "die Plaetze gelten nur in SkyBlock: die Mod muss den Ort lesen");
        });
    }

    @Test
    void theChoiceSurvivesSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.uiVisuals.inventoryScreen.showEquipment = true;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.uiVisuals.inventoryScreen.showEquipment);
        ModConfig off = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(new ModConfig()), ModConfig.class);
        assertFalse(off.uiVisuals.inventoryScreen.showEquipment);
    }

    @Test
    void theFileHoldsOnlyTheSwitchAndNoInfoText() {
        JsonObject root = JsonParser.parseString(ModConfig.GSON.toJson(new ModConfig())).getAsJsonObject();
        JsonObject ui = root.getAsJsonObject("uiVisuals");
        assertNotNull(ui, "uiVisuals steht in der Datei");
        // Seit 2.5.8 zwei Unterkategorien (dazu Input Calculator); die Pruefung "nur Unterkategorien, kein Infotext" und "nur der Schalter" gilt fuer beide
        assertEquals(List.of("inventoryScreen", "inputCalculator"), new ArrayList<>(ui.keySet()), "nur die Unterkategorien, kein Infotext");
        JsonObject screen = ui.getAsJsonObject("inventoryScreen");
        assertEquals(List.of("showEquipment"), new ArrayList<>(screen.keySet()), "nur der Schalter");
        assertFalse(screen.get("showEquipment").getAsBoolean());
        JsonObject calculator = ui.getAsJsonObject("inputCalculator");
        assertEquals(List.of("enabled"), new ArrayList<>(calculator.keySet()), "nur der Schalter");
        assertFalse(calculator.get("enabled").getAsBoolean());
    }

    @Test
    void aFileFromBeforeKeepsTheDefaults() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"misc\":{\"shortenWarp\":true}}", ModConfig.class);
        ModConfig.repairUiVisuals(loaded);
        assertTrue(loaded.misc.shortenWarp, "was in der Datei stand, bleibt");
        assertNotNull(loaded.uiVisuals);
        assertNotNull(loaded.uiVisuals.inventoryScreen);
        assertFalse(loaded.uiVisuals.inventoryScreen.showEquipment);
    }

    @Test
    void aNullCategoryInTheFileIsRepaired() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"uiVisuals\":null}", ModConfig.class);
        assertNull(loaded.uiVisuals, "Gson uebernimmt das null: Der Editor bekaeme es, deshalb raeumt repairUiVisuals es weg");
        ModConfig.repairUiVisuals(loaded);
        assertNotNull(loaded.uiVisuals);
        assertNotNull(loaded.uiVisuals.inventoryScreen);
        assertFalse(loaded.uiVisuals.inventoryScreen.showEquipment);

        ModConfig second = ModConfig.GSON.fromJson("{\"uiVisuals\":{\"inventoryScreen\":null}}", ModConfig.class);
        assertNull(second.uiVisuals.inventoryScreen);
        ModConfig.repairUiVisuals(second);
        assertNotNull(second.uiVisuals.inventoryScreen);
        assertFalse(second.uiVisuals.inventoryScreen.showEquipment);
    }

    @Test
    void repairingKeepsAChoiceAndToleratesNull() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"uiVisuals\":{\"inventoryScreen\":{\"showEquipment\":true}}}", ModConfig.class);
        ModConfig.repairUiVisuals(loaded);
        assertTrue(loaded.uiVisuals.inventoryScreen.showEquipment, "ein eigenes Ja bleibt");
        assertDoesNotThrow(() -> ModConfig.repairUiVisuals(null));
    }

    // ---- Namen und Beschreibung (so, wie sie im Editor stehen) ----

    @Test
    void theNamesAndTheDescriptionAreTheOnesAskedFor() throws NoSuchFieldException {
        Category top = ModConfig.class.getField("uiVisuals").getAnnotation(Category.class);
        assertNotNull(top);
        assertEquals("UI & Visuals", top.name());
        assertTrue(top.desc().contains("Idea from Skyblocker."), top.desc());

        Category sub = ModConfig.UiVisualsCategory.class.getField("inventoryScreen").getAnnotation(Category.class);
        assertNotNull(sub);
        assertEquals("Skyblock Inventory Screen", sub.name());

        ConfigOption option = ModConfig.SkyblockInventoryScreenCategory.class.getField("showEquipment").getAnnotation(ConfigOption.class);
        assertNotNull(option);
        assertEquals("Show Equipment in Inventory", option.name());
        assertTrue(option.desc().contains("Idea from Skyblocker."), "der Satz steht in der Beschreibung des Schalters: " + option.desc());
        assertTrue(option.desc().toLowerCase().contains("cache"), "und der Hinweis, dass die Anzeige nicht in Echtzeit ist");
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

    private static int stelle(Ergebnis e, String teilDesFeldnamens) {
        int i = 0;
        for (String key : e.kategorien.keySet()) {
            if (key.contains(teilDesFeldnamens)) return i;
            i++;
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
    void theEditorReadsTheNewCategoryWithoutWarnings() {
        Ergebnis e = verarbeiten();
        assertFalse(e.fehlerausgabe.toLowerCase().contains("exception"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.toLowerCase().contains("duplicate"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("double recursive"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("sub categories inside of an accordion"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.contains("Cannot mix"), e.fehlerausgabe);
    }

    @Test
    void uiAndVisualsIsATabRightAfterMiscAndBeforeIkohS() {
        Ergebnis e = verarbeiten();
        int misc = stelle(e, "ModConfig.misc");
        int ui = stelle(e, "ModConfig.uiVisuals");
        int ikohS = stelle(e, "ModConfig.ikohS");
        assertTrue(misc >= 0, "Misc");
        assertTrue(ui >= 0, "UI & Visuals");
        assertTrue(ikohS >= 0, "ikohS");
        assertTrue(misc < ui && ui < ikohS, "Reihenfolge der Reiter (Misc " + misc + ", UI & Visuals " + ui + ", ikohS " + ikohS + "): " + e.kategorien.keySet());

        ProcessedCategory miscCategory = nach(e, "ModConfig.misc");
        ProcessedCategory uiCategory = nach(e, "ModConfig.uiVisuals");
        assertNotNull(miscCategory);
        assertNotNull(uiCategory);
        assertFalse(miscCategory.getIdentifier().equals(uiCategory.getParentCategoryId()), "ein eigener Reiter, nicht unter Misc");

        // Direkt hinter Misc und vor ikohS: Zwischen den dreien stehen nur Unterreiter von Misc beziehungsweise von UI & Visuals, kein anderer Reiter
        int i = 0;
        for (ProcessedCategory category : e.kategorien.values()) {
            if (i > misc && i < ui) {
                assertEquals(miscCategory.getIdentifier(), category.getParentCategoryId(), "zwischen Misc und UI & Visuals steht nur ein Unterreiter von Misc: " + e.kategorien.keySet());
            }
            if (i > ui && i < ikohS) {
                assertEquals(uiCategory.getIdentifier(), category.getParentCategoryId(), "zwischen UI & Visuals und ikohS steht nur ein Unterreiter von UI & Visuals: " + e.kategorien.keySet());
            }
            i++;
        }
    }

    /**
     * Seit 2.5.8 hat UI & Visuals zwei Unterkategorien (Skyblock Inventory Screen und Input Calculator); bis 2.5.7 genau eine. Die Zahl hier ist die der Anforderung von heute,
     * kein Abschwaechen: Der Test heisst nicht mehr "genau eine", prueft aber weiter jede Unterkategorie einzeln (Infotext und ein Schalter, von Haus aus aus).
     */
    @Test
    void uiAndVisualsHasTwoSubCategoriesEachWithExactlyOneSwitch() {
        Ergebnis e = verarbeiten();
        ProcessedCategory ui = nach(e, "ModConfig.uiVisuals");
        ProcessedCategory sub = nach(e, "UiVisualsCategory.inventoryScreen");
        assertNotNull(ui, "der Reiter UI & Visuals");
        assertNotNull(sub, "die Unterkategorie Skyblock Inventory Screen");
        assertEquals(ui.getIdentifier(), sub.getParentCategoryId(), "haengt unter UI & Visuals");

        int children = 0;
        for (ProcessedCategory category : e.kategorien.values()) {
            if (ui.getIdentifier().equals(category.getParentCategoryId())) children++;
        }
        assertEquals(2, children, "zwei Unterkategorien");

        ProcessedCategory calculator = nach(e, "UiVisualsCategory.inputCalculator");
        assertNotNull(calculator, "die Unterkategorie Input Calculator");
        assertEquals(ui.getIdentifier(), calculator.getParentCategoryId(), "haengt unter UI & Visuals");
        List<String> calculatorNames = optionNames(calculator);
        assertEquals(List.of("Input Calculator", "Calculate in Number Signs"), calculatorNames, "Infotext und ein Schalter");
        ProcessedOption calculatorOption = calculator.getOptions().get(calculatorNames.indexOf("Calculate in Number Signs"));
        assertEquals("GuiOptionEditorBoolean", calculatorOption.getEditor().getClass().getSimpleName());
        assertEquals(Boolean.FALSE, calculatorOption.get(), "von Haus aus aus");

        assertEquals(List.of("UI & Visuals"), optionNames(ui), "im Reiter selbst nur der Infotext");
        List<String> names = optionNames(sub);
        assertEquals(List.of("Skyblock Inventory Screen", "Show Equipment in Inventory"), names, "Infotext und ein Schalter");
        ProcessedOption option = sub.getOptions().get(names.indexOf("Show Equipment in Inventory"));
        assertEquals("GuiOptionEditorBoolean", option.getEditor().getClass().getSimpleName());
        assertEquals(Boolean.FALSE, option.get(), "von Haus aus aus");
    }
}
