package com.shokiteufel.shokimod.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.notenoughupdates.moulconfig.annotations.Category;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Reiter UI & Visuals (nach Skyblocker): Reparatur einer Datei von vorher, was in der Datei steht, wie der Editor ihn liest - direkt nach Misc, vor ikohS, mit seiner einen
 * Unterkategorie (Input Calculator, dessen Schalter ModConfigInputCalculatorTest prueft).
 *
 * <p>Bis 2.5.13 stand hier auch "Skyblock Inventory Screen" mit "Show Equipment in Inventory" (2.5.7). Das ist auf Wunsch des Users entfernt (eine andere Mod macht es besser);
 * die Pruefungen dazu sind mit entfallen, und {@link #showEquipmentInInventoryIsGoneForGood} haelt fest, dass nichts davon zurueckblieb.
 */
class ModConfigUiVisualsTest {

    // ---- Einstellung ----

    @Test
    void theCategoryHoldsTheCalculatorAndIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        assertNotNull(c.uiVisuals);
        assertNotNull(c.uiVisuals.inputCalculator);
        assertFalse(c.uiVisuals.inputCalculator.enabled, "alles Neue ist aus");
    }

    @Test
    void showEquipmentInInventoryIsGoneForGood() {
        assertThrows(NoSuchFieldException.class, () -> ModConfig.UiVisualsCategory.class.getField("inventoryScreen"), "die Unterkategorie ist entfernt");
        assertThrows(NoSuchMethodException.class, () -> FeatureGate.class.getMethod("equipmentInInventory"), "das Tor ist entfernt");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.shokiteufel.shokimod.equipment.EquipmentInventory"), "die Klassen sind entfernt");
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.shokiteufel.shokimod.paket.mixin.InventoryEquipmentMixin"), "der Einsprung ist entfernt");
    }

    @Test
    void theFileHoldsOnlyTheCalculatorAndNoInfoText() {
        JsonObject root = JsonParser.parseString(ModConfig.GSON.toJson(new ModConfig())).getAsJsonObject();
        JsonObject ui = root.getAsJsonObject("uiVisuals");
        assertNotNull(ui, "uiVisuals steht in der Datei");
        assertEquals(List.of("inputCalculator"), new ArrayList<>(ui.keySet()), "nur die Unterkategorie, kein Infotext");
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
        assertNotNull(loaded.uiVisuals.inputCalculator);
        assertFalse(loaded.uiVisuals.inputCalculator.enabled);
    }

    @Test
    void aFileOfTheEquipmentTimeStillLoadsAndItsLeftoverIsIgnored() {
        // Eine Datei von 2.5.7 bis 2.5.13 kennt noch "inventoryScreen": Gson ueberliest den Eintrag, der Rest der Datei bleibt, und der Schalter des Rechners wird nicht angefasst
        ModConfig loaded = assertDoesNotThrow(() -> ModConfig.GSON.fromJson(
                "{\"misc\":{\"shortenWarp\":true},\"uiVisuals\":{\"inventoryScreen\":{\"showEquipment\":true},\"inputCalculator\":{\"enabled\":true}}}", ModConfig.class));
        ModConfig.repairUiVisuals(loaded);
        assertTrue(loaded.misc.shortenWarp, "der Rest der Datei bleibt");
        assertTrue(loaded.uiVisuals.inputCalculator.enabled, "ein eigenes Ja beim Rechner bleibt");
        // Beim naechsten Speichern ist der alte Eintrag weg
        JsonObject again = JsonParser.parseString(ModConfig.GSON.toJson(loaded)).getAsJsonObject().getAsJsonObject("uiVisuals");
        assertFalse(again.has("inventoryScreen"), "der entfernte Eintrag wird nicht mehr geschrieben");
    }

    @Test
    void aNullCategoryInTheFileIsRepaired() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"uiVisuals\":null}", ModConfig.class);
        assertNull(loaded.uiVisuals, "Gson uebernimmt das null: Der Editor bekaeme es, deshalb raeumt repairUiVisuals es weg");
        ModConfig.repairUiVisuals(loaded);
        assertNotNull(loaded.uiVisuals);
        assertNotNull(loaded.uiVisuals.inputCalculator);
        assertFalse(loaded.uiVisuals.inputCalculator.enabled);

        ModConfig second = ModConfig.GSON.fromJson("{\"uiVisuals\":{\"inputCalculator\":null}}", ModConfig.class);
        assertNull(second.uiVisuals.inputCalculator);
        ModConfig.repairUiVisuals(second);
        assertNotNull(second.uiVisuals.inputCalculator);
        assertFalse(second.uiVisuals.inputCalculator.enabled);
    }

    @Test
    void repairingKeepsAChoiceAndToleratesNull() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"uiVisuals\":{\"inputCalculator\":{\"enabled\":true}}}", ModConfig.class);
        ModConfig.repairUiVisuals(loaded);
        assertTrue(loaded.uiVisuals.inputCalculator.enabled, "ein eigenes Ja bleibt");
        assertDoesNotThrow(() -> ModConfig.repairUiVisuals(null));
    }

    // ---- Namen und Beschreibung (so, wie sie im Editor stehen) ----

    @Test
    void theNameAndTheDescriptionOfTheTabAreTheOnesAskedFor() throws NoSuchFieldException {
        Category top = ModConfig.class.getField("uiVisuals").getAnnotation(Category.class);
        assertNotNull(top);
        assertEquals("UI & Visuals", top.name());
        assertTrue(top.desc().contains("Idea from Skyblocker."), top.desc());
        assertFalse(top.desc().toLowerCase().contains("equipment"), "der Reiter wirbt nicht mit einer entfernten Funktion: " + top.desc());
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
    void theEditorReadsTheCategoryWithoutWarnings() {
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
     * Seit 2.5.14 hat UI & Visuals genau eine Unterkategorie, den Input Calculator (2.5.8 bis 2.5.13: zwei, dazu Skyblock Inventory Screen). Die Zahl ist die der Anforderung von
     * heute: Die entfernte Unterkategorie darf nirgends im Editor stehen, die uebrige behaelt Infotext und einen Schalter, von Haus aus aus.
     */
    @Test
    void uiAndVisualsHasOneSubCategoryWithExactlyOneSwitch() {
        Ergebnis e = verarbeiten();
        ProcessedCategory ui = nach(e, "ModConfig.uiVisuals");
        assertNotNull(ui, "der Reiter UI & Visuals");
        assertNull(nach(e, "UiVisualsCategory.inventoryScreen"), "die Unterkategorie Skyblock Inventory Screen ist entfernt");

        int children = 0;
        for (ProcessedCategory category : e.kategorien.values()) {
            if (ui.getIdentifier().equals(category.getParentCategoryId())) children++;
        }
        assertEquals(1, children, "eine Unterkategorie");

        ProcessedCategory calculator = nach(e, "UiVisualsCategory.inputCalculator");
        assertNotNull(calculator, "die Unterkategorie Input Calculator");
        assertEquals(ui.getIdentifier(), calculator.getParentCategoryId(), "haengt unter UI & Visuals");
        List<String> calculatorNames = optionNames(calculator);
        assertEquals(List.of("Input Calculator", "Calculate in Number Signs"), calculatorNames, "Infotext und ein Schalter");
        ProcessedOption calculatorOption = calculator.getOptions().get(calculatorNames.indexOf("Calculate in Number Signs"));
        assertEquals("GuiOptionEditorBoolean", calculatorOption.getEditor().getClass().getSimpleName());
        assertEquals(Boolean.FALSE, calculatorOption.get(), "von Haus aus aus");

        assertEquals(List.of("UI & Visuals"), optionNames(ui), "im Reiter selbst nur der Infotext");
        for (ProcessedCategory category : e.kategorien.values()) {
            for (String name : optionNames(category)) {
                assertFalse(name.contains("Show Equipment in Inventory"), "kein Schalter der entfernten Funktion: " + name);
            }
        }
    }
}
