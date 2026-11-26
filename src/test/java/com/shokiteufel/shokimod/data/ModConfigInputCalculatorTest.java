package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI & Visuals > Input Calculator (nach Skyblocker): von Haus aus aus, speichern und laden, eine Datei von vorher ohne die Unterkategorie, das Tor in {@link FeatureGate}
 * (mit dem Ort) und die Namen im Editor.
 */
class ModConfigInputCalculatorTest {

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

    @Test
    void theCalculatorIsOffFromTheStart() {
        ModConfig c = new ModConfig();
        assertNotNull(c.uiVisuals.inputCalculator);
        assertFalse(c.uiVisuals.inputCalculator.enabled, "alles Neue ist aus");
        with(x -> {
        }, () -> assertFalse(FeatureGate.inputCalculator()));
    }

    @Test
    void theSwitchOpensTheGateAndTheLocation() {
        with(c -> c.uiVisuals.inputCalculator.enabled = true, () -> {
            assertTrue(FeatureGate.inputCalculator());
            assertTrue(FeatureGate.location(), "das Rechnen gilt nur in SkyBlock: die Mod muss den Ort lesen");
        });
    }

    @Test
    void theChoiceSurvivesSavingAndLoadingAndIsIndependentOfTheEquipmentSwitch() {
        ModConfig c = new ModConfig();
        c.uiVisuals.inputCalculator.enabled = true;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.uiVisuals.inputCalculator.enabled);
        assertFalse(again.uiVisuals.inventoryScreen.showEquipment, "der andere Schalter bleibt aus");
        ModConfig off = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(new ModConfig()), ModConfig.class);
        assertFalse(off.uiVisuals.inputCalculator.enabled);
    }

    @Test
    void aFileFromBeforeHasNoSubCategoryAndGetsItFromTheRepair() {
        // Eine Datei von 2.5.7 und frueher kennt nur Skyblock Inventory Screen
        ModConfig loaded = ModConfig.GSON.fromJson("{\"uiVisuals\":{\"inventoryScreen\":{\"showEquipment\":true}}}", ModConfig.class);
        assertNotNull(loaded.uiVisuals.inputCalculator, "Gson laesst das Feld der Vorgabe");
        assertFalse(loaded.uiVisuals.inputCalculator.enabled);
        assertTrue(loaded.uiVisuals.inventoryScreen.showEquipment, "ein eigenes Ja bleibt");

        ModConfig broken = ModConfig.GSON.fromJson("{\"uiVisuals\":{\"inputCalculator\":null}}", ModConfig.class);
        assertNull(broken.uiVisuals.inputCalculator, "Gson uebernimmt das null");
        ModConfig.repairUiVisuals(broken);
        assertNotNull(broken.uiVisuals.inputCalculator);
        assertFalse(broken.uiVisuals.inputCalculator.enabled);
        assertDoesNotThrow(() -> ModConfig.repairUiVisuals(null));
    }

    @Test
    void theNamesAreTheOnesAskedFor() throws NoSuchFieldException {
        Category sub = ModConfig.UiVisualsCategory.class.getField("inputCalculator").getAnnotation(Category.class);
        assertNotNull(sub);
        assertEquals("Input Calculator", sub.name());
        assertTrue(sub.desc().contains("Idea from Skyblocker."), sub.desc());

        ConfigOption option = ModConfig.InputCalculatorCategory.class.getField("enabled").getAnnotation(ConfigOption.class);
        assertNotNull(option);
        assertEquals("Calculate in Number Signs", option.name());
        assertTrue(option.desc().contains("Idea from Skyblocker."), option.desc());
        assertTrue(option.desc().contains("45656445465 = 45,656,445,465"), "das Beispiel des Users steht in der Beschreibung");
    }
}
