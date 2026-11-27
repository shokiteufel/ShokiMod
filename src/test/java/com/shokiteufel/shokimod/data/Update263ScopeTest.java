package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver;
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor;
import io.github.notenoughupdates.moulconfig.processor.ProcessedCategory;
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Was im Menue zu 26.3 gehoert (seit 2.6.3): mit dem Schalter im Reiter "26.3" aus verschwinden die Reiter und Eintraege der Funktionen seit 2.4.10, damit das Menue
 * wieder aussieht wie vorher. Die Einstellungen selbst bleiben unberuehrt. Geprueft wird am echten Menue-Aufbau von MoulConfig (derselbe Weg wie beim Oeffnen).
 */
class Update263ScopeTest {

    private static MoulConfigProcessor<ModConfig> process(ModConfig config) {
        MoulConfigProcessor<ModConfig> processor = MoulConfigProcessor.withDefaults(config);
        new ConfigProcessorDriver(processor).processConfig(config);
        return processor;
    }

    private static void withSwitch(boolean on, Runnable then) {
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = new ModConfig();
            ModConfig.INSTANCE.update263.enabled = on;
            then.run();
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    private static Set<String> hiddenCategoryNames(MoulConfigProcessor<ModConfig> processor) {
        Set<String> names = new TreeSet<>();
        for (ProcessedCategory category : processor.getAllCategories().values()) {
            if (Update263Scope.categoryHidden(category.getIdentifier(), category.getParentCategoryId())) {
                names.add(category.getDisplayName().getText().trim());
            }
        }
        return names;
    }

    private static List<ProcessedOption> allOptions(MoulConfigProcessor<ModConfig> processor) {
        List<ProcessedOption> all = new ArrayList<>();
        for (ProcessedCategory category : processor.getAllCategories().values()) all.addAll(category.getOptions());
        return all;
    }

    @Test
    void everyEntryOfTheListPointsAtARealField() {
        // Die Zahlen sind eine Wache: Fiele ein Eintrag weg (Feld umbenannt), wuerde still ein Reiter von 26.3 im Menue stehen bleiben
        assertEquals(13, Update263Scope.categoryFields().size(), "Reiter und Unter-Reiter der Liste");
        assertEquals(5, Update263Scope.optionFields().size(), "einzelne Eintraege der Liste");
        for (Field field : Update263Scope.categoryFields()) {
            assertTrue(field.isAnnotationPresent(Category.class), field + " ist kein Reiter");
        }
        for (Field field : Update263Scope.optionFields()) {
            assertTrue(field.isAnnotationPresent(ConfigOption.class), field + " ist kein Eintrag des Menues");
        }
    }

    @Test
    void withTheSwitchOnNothingIsHidden() {
        withSwitch(true, () -> {
            MoulConfigProcessor<ModConfig> processor = process(ModConfig.INSTANCE);
            assertEquals(Set.of(), hiddenCategoryNames(processor));
        });
    }

    @Test
    void withTheSwitchOffExactlyTheTabsOf263AndTheirSubTabsAreHidden() {
        withSwitch(false, () -> {
            MoulConfigProcessor<ModConfig> processor = process(ModConfig.INSTANCE);
            Set<String> expected = new TreeSet<>(Set.of(
                    // ganze Reiter und was darunter liegt
                    "Chat", "Chat Filter Types", "Player Messages", "Sound Responses", "Rare Drop Messages", "Compact Potion Messages", "Translator", "Tab Complete",
                    "Skill progress", "Progress bar", "Overflow",
                    "Misc", "NPC Daily Limit Tracker", "Legion/Bobbin Overlay",
                    "UI & Visuals", "Input Calculator",
                    // Unter-Reiter in Reitern, die Aelteres enthalten
                    "Hook display", "Bait display", "Cocoon settings",
                    "Visitor Shopping List", "Visitor Reward Warning", "Garden Plots Widget",
                    "Auction house", "Bazaar", "Estimated Item Value"));
            assertEquals(expected, hiddenCategoryNames(processor));
        });
    }

    @Test
    void theTabOfTheSwitchItselfAndTheOlderTabsStay() {
        withSwitch(false, () -> {
            Set<String> hidden = hiddenCategoryNames(process(ModConfig.INSTANCE));
            // Der Schalter muss erreichbar bleiben, sonst kaeme man nie zurueck
            for (String stays : List.of("26.3", "Fishing", "Garden", "Inventory", "Slayer", "Mining", "HUD", "Hotspot", "Visitors", "Pests", "Bazaar flips", "Alerts",
                    "Guild / Party", "Profit Tracker", "Hunting")) {
                assertFalse(hidden.contains(stays), stays + " bleibt im Menue");
            }
        });
    }

    @Test
    void unknownOrMissingIdentifiersAreNeverHidden() {
        withSwitch(false, () -> {
            assertFalse(Update263Scope.categoryHidden(null, null));
            assertFalse(Update263Scope.categoryHidden("irgendwas", null));
            assertFalse(Update263Scope.categoryHidden("irgendwas", "anderes"));
        });
    }

    @Test
    void theSingleEntriesAndTheFoldersOf263AreHiddenWithTheirContents() {
        MoulConfigProcessor<ModConfig> processor = process(new ModConfig());
        Set<ProcessedOption> hide = Update263Scope.optionsToHide(allOptions(processor), processor::getOptionFromField);
        Set<String> names = new TreeSet<>();
        for (ProcessedOption option : hide) names.add(((ProcessedOption.HasField) option).getField().getName());
        assertEquals(new TreeSet<>(Set.of(
                // Fishing
                "shortenFishingMessage", "compactDoubleHook", "compactDoubleHookPosition",
                // Slayer: der Ordner Cocoon mit allem darin, der Ordner Spawn particles mit allem darin
                "cocoonFolder", "cocoonTitle", "cocoonDing", "openCocoonSound", "testCocoon",
                "spawnParticleFolder", "spawnWhite", "spawnPurple", "spawnSlayer")), names);
    }

    @Test
    void otherFoldersOfTheSameTabsStayAsTheyWere() throws NoSuchFieldException {
        MoulConfigProcessor<ModConfig> processor = process(new ModConfig());
        Set<ProcessedOption> hide = Update263Scope.optionsToHide(allOptions(processor), processor::getOptionFromField);
        // Der Ordner "Kill time" im selben Reiter Slayer ist aelter und bleibt
        ProcessedOption killTime = processor.getOptionFromField(ModConfig.SlayerCategory.class.getField("killTimeFolder"));
        assertFalse(hide.contains(killTime), "Kill time bleibt");
        // Auch der Reiter Fishing hat Aelteres, das stehen bleibt
        for (ProcessedOption option : allOptions(processor)) {
            Field field = ((ProcessedOption.HasField) option).getField();
            if (field.getDeclaringClass() == ModConfig.FishingCategory.class && field.getName().equals("about")) {
                assertFalse(hide.contains(option), "die Beschreibung von Fishing bleibt");
            }
        }
    }

    @Test
    void theCocoonMessagesLeaveTheBannerListWithTheSwitchOff() {
        withSwitch(true, () -> {
            List<String> labels = BannerUses.all().stream().map(BannerUses.Use::label).toList();
            assertTrue(labels.contains("Cocoon"));
            assertTrue(labels.contains("Slayer cocoon"));
        });
        withSwitch(false, () -> {
            List<String> labels = BannerUses.all().stream().map(BannerUses.Use::label).toList();
            assertFalse(labels.contains("Cocoon"));
            assertFalse(labels.contains("Slayer cocoon"));
            assertTrue(labels.contains("Spot") || labels.contains("Hotspot gone"), "die aelteren Meldungen bleiben in der Liste");
        });
    }
}
