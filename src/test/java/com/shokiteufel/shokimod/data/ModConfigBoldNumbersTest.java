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
 * HUD > Bold numbers: ein Schalter im Reiter HUD, von Haus aus an, er ueberlebt Speichern und Laden, und eine Datei von vorher (ohne das Feld) bekommt die Vorgabe -
 * ein boolean braucht keine Reparatur.
 */
class ModConfigBoldNumbersTest {

    @Test
    void theSwitchIsOnFromTheStart() {
        assertTrue(new ModConfig().hud.boldNumbers);
    }

    @Test
    void aFileFromBeforeKeepsTheDefault() {
        ModConfig noHud = ModConfig.GSON.fromJson("{}", ModConfig.class);
        assertTrue(noHud.hud.boldNumbers);
        ModConfig oldHud = ModConfig.GSON.fromJson("{\"hud\":{\"glowingHudText\":false,\"editorShowsAll\":true}}", ModConfig.class);
        assertTrue(oldHud.hud.boldNumbers, "das Feld fehlt in der alten Datei: es gilt die Vorgabe (an)");
        assertFalse(oldHud.hud.glowingHudText, "was in der Datei stand, bleibt");
    }

    @Test
    void anOwnNoSurvivesSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.hud.boldNumbers = false;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertFalse(again.hud.boldNumbers);
        ModConfig on = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(new ModConfig()), ModConfig.class);
        assertTrue(on.hud.boldNumbers);
    }

    @Test
    void theFieldIsWrittenToTheFile() {
        assertTrue(ModConfig.GSON.toJson(new ModConfig()).contains("\"boldNumbers\": true"), "@Expose: das Feld steht in der Datei");
    }

    // ---- wie der Editor den Reiter liest ----

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
    void theOptionStandsInTheHudTabBehindGlowingTextAndIsASwitch() {
        Ergebnis e = verarbeiten();
        assertFalse(e.fehlerausgabe.toLowerCase().contains("exception"), e.fehlerausgabe);
        assertFalse(e.fehlerausgabe.toLowerCase().contains("duplicate"), e.fehlerausgabe);

        ProcessedCategory hud = null;
        for (var k : e.kategorien.entrySet()) {
            if (hud == null && k.getKey().contains("ModConfig.hud")) hud = k.getValue();
        }
        assertNotNull(hud, "der Reiter HUD");
        List<String> names = optionNames(hud);
        int glow = names.indexOf("Glowing text");
        int bold = names.indexOf("Bold numbers");
        assertTrue(glow >= 0, "Glowing text: " + names);
        assertTrue(bold > glow, "Bold numbers steht hinter Glowing text: " + names);

        ProcessedOption option = hud.getOptions().get(bold);
        assertEquals("GuiOptionEditorBoolean", option.getEditor().getClass().getSimpleName());
        assertEquals(Boolean.TRUE, option.get(), "im Editor von Haus aus an");
    }
}
