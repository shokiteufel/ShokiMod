package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import com.shokiteufel.shokimod.data.ModConfig.CompactPotionCategory;
import com.shokiteufel.shokimod.data.ModConfig.RareDropMessagesCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 2): von Haus aus aus, speichern und laden, eine Datei von vorher, kaputte Datei, und was den Chat-Weg, den Ort und den Tick einschaltet. Der
 * Teil-1-Test {@code ModConfigChatTabTest} prueft ueber alle Schalter von {@code ChatTabCategory}, dass jeder aus ist - die neuen sind dabei.
 */
class ModConfigChatTab2Test {

    private static List<Field> booleans(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) out.add(f);
        }
        return out;
    }

    private static void withConfig(Consumer<ModConfig> change, Runnable then) {
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
    void everyNewSwitchIsOffFromTheStartExceptTheClickOfThePotionLine() throws Exception {
        ModConfig c = new ModConfig();
        for (Field f : booleans(RareDropMessagesCategory.class)) assertFalse(f.getBoolean(c.chatTab.rareDrop), "Rare Drop Messages: " + f.getName());
        assertEquals(3, booleans(RareDropMessagesCategory.class).size());
        assertFalse(c.chatTab.compactPotion.enabled, "Compact Potion Messages: Enabled");
        // Wirkt erst, wenn "Enabled" an ist: SkyHannis Vorgabe bleibt
        assertTrue(c.chatTab.compactPotion.clickableChatMessage);
        assertEquals(2, booleans(CompactPotionCategory.class).size());
        for (String name : new String[] {"compactBestiary", "compactExperiments", "compactJacobClaim", "skyBlockXpInChat", "showEffectivePowder", "shortenCoinAmounts",
                "hideClickableHints", "darkAuctionItemDisplay"}) {
            assertFalse(ChatTabCategory.class.getField(name).getBoolean(c.chatTab), name);
        }
        assertFalse(c.chatTab.anyRewrite());
        assertFalse(c.chatTab.anyCompact());
        assertFalse(c.chatTab.needsTick());
        withConfig(x -> {
        }, () -> {
            assertFalse(FeatureGate.chatFilters());
            assertFalse(FeatureGate.chatFiltersLocation());
            assertFalse(FeatureGate.chatTick());
            assertFalse(FeatureGate.skyblockXpInChat());
        });
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.chatTab.compactBestiary = true;
        c.chatTab.compactJacobClaim = true;
        c.chatTab.skyBlockXpInChat = true;
        c.chatTab.shortenCoinAmounts = true;
        c.chatTab.hideClickableHints = true;
        c.chatTab.compactPotion.enabled = true;
        c.chatTab.compactPotion.clickableChatMessage = false;
        c.chatTab.rareDrop.petRarity = true;
        c.chatTab.rareDrop.enchantedBookMissingMessage = true;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.chatTab.compactBestiary);
        assertFalse(again.chatTab.compactExperiments);
        assertTrue(again.chatTab.compactJacobClaim);
        assertTrue(again.chatTab.skyBlockXpInChat);
        assertTrue(again.chatTab.shortenCoinAmounts);
        assertTrue(again.chatTab.hideClickableHints);
        assertFalse(again.chatTab.showEffectivePowder);
        assertFalse(again.chatTab.darkAuctionItemDisplay);
        assertTrue(again.chatTab.compactPotion.enabled);
        assertFalse(again.chatTab.compactPotion.clickableChatMessage);
        assertTrue(again.chatTab.rareDrop.petRarity);
        assertFalse(again.chatTab.rareDrop.enchantedBook);
        assertTrue(again.chatTab.rareDrop.enchantedBookMissingMessage);
    }

    @Test
    void theKeysInTheFileAreTheOnesTheModWrites() {
        String json = ModConfig.GSON.toJson(new ModConfig());
        for (String key : new String[] {"\"compactBestiary\"", "\"compactExperiments\"", "\"compactJacobClaim\"", "\"skyBlockXpInChat\"", "\"showEffectivePowder\"",
                "\"shortenCoinAmounts\"", "\"hideClickableHints\"", "\"darkAuctionItemDisplay\"", "\"rareDrop\"", "\"compactPotion\"", "\"petRarity\"",
                "\"enchantedBook\"", "\"enchantedBookMissingMessage\"", "\"clickableChatMessage\""}) {
            assertTrue(json.contains(key), key);
        }
        // Die Infotexte der Unterkategorien werden nicht gespeichert
        assertFalse(json.contains("\"about\""));
    }

    @Test
    void aFileFromBeforeKeepsTheDefaultsOfTheNewParts() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"dungeonAmbience\":true}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        assertTrue(loaded.chatTab.dungeonAmbience);
        assertNotNull(loaded.chatTab.rareDrop);
        assertNotNull(loaded.chatTab.compactPotion);
        assertTrue(loaded.chatTab.compactPotion.clickableChatMessage);
        assertFalse(loaded.chatTab.anyRewrite());
    }

    @Test
    void aBrokenFileGetsTheMissingPartsBack() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"rareDrop\":null,\"compactPotion\":null}}", ModConfig.class);
        assertNull(loaded.chatTab.rareDrop);
        ModConfig.repairChatTab(loaded);
        assertNotNull(loaded.chatTab.rareDrop);
        assertNotNull(loaded.chatTab.compactPotion);
        assertFalse(loaded.chatTab.rareDrop.any());
        assertFalse(loaded.chatTab.compactPotion.enabled);
        // Auch die ganze Kategorie fehlt
        ModConfig empty = ModConfig.GSON.fromJson("{\"chatTab\":null}", ModConfig.class);
        ModConfig.repairChatTab(empty);
        assertNotNull(empty.chatTab.rareDrop);
        assertNotNull(empty.chatTab.compactPotion);
        // Ein Schalter mit einem falschen Wert (kein Wahrheitswert) bricht das Lesen nicht: er bleibt aus
        ModConfig weird = ModConfig.GSON.fromJson("{\"chatTab\":{\"rareDrop\":{\"petRarity\":true}}}", ModConfig.class);
        assertTrue(weird.chatTab.rareDrop.petRarity);
        assertFalse(weird.chatTab.rareDrop.enchantedBook);
    }

    @Test
    void everySwitchThatRewritesALineTurnsOnTheChatPathAndTheLocationReader() {
        List<Consumer<ModConfig>> rewriting = List.of(
                c -> c.chatTab.compactBestiary = true, c -> c.chatTab.compactExperiments = true, c -> c.chatTab.compactJacobClaim = true,
                c -> c.chatTab.showEffectivePowder = true, c -> c.chatTab.shortenCoinAmounts = true, c -> c.chatTab.darkAuctionItemDisplay = true,
                c -> c.chatTab.compactPotion.enabled = true, c -> c.chatTab.rareDrop.petRarity = true, c -> c.chatTab.rareDrop.enchantedBook = true,
                c -> c.chatTab.rareDrop.enchantedBookMissingMessage = true);
        for (int i = 0; i < rewriting.size(); i++) {
            int index = i;
            withConfig(rewriting.get(i), () -> {
                assertTrue(FeatureGate.chatFilters(), "Schalter " + index + " schaltet den Chat-Weg ein");
                assertTrue(FeatureGate.chatFiltersLocation(), "Schalter " + index + " braucht den Ort (nur in SkyBlock)");
                assertTrue(FeatureGate.location(), "Schalter " + index + " liest die Tab-Liste");
            });
        }
        withConfig(c -> c.chatTab.compactPotion.clickableChatMessage = true, () -> assertFalse(FeatureGate.chatFilters(), "the click alone does nothing"));
    }

    @Test
    void skyBlockXpInChatReadsTheActionBarNotTheChatLines() {
        withConfig(c -> c.chatTab.skyBlockXpInChat = true, () -> {
            assertTrue(FeatureGate.skyblockXpInChat());
            assertFalse(FeatureGate.chatFilters(), "no chat line is read for it");
            assertFalse(FeatureGate.chatTick());
            assertTrue(FeatureGate.location(), "it needs to know whether you are in SkyBlock");
        });
    }

    @Test
    void hideClickableHintsTouchesNoGate() {
        // Andere Reiter koennen den Ort von Haus aus brauchen: verglichen wird mit einer frischen Einstellung, nicht mit "aus"
        boolean[] baseline = new boolean[1];
        withConfig(c -> {
        }, () -> baseline[0] = FeatureGate.location());
        withConfig(c -> c.chatTab.hideClickableHints = true, () -> {
            assertFalse(FeatureGate.chatFilters());
            assertFalse(FeatureGate.chatFiltersLocation());
            assertFalse(FeatureGate.chatTick());
            assertFalse(FeatureGate.skyblockXpInChat());
            assertEquals(baseline[0], FeatureGate.location(), "no place needed for it");
        });
    }

    @Test
    void theTickRunsOnlyForTheSwitchesThatWriteLater() {
        for (Consumer<ModConfig> on : List.<Consumer<ModConfig>>of(c -> c.chatTab.compactExperiments = true, c -> c.chatTab.compactJacobClaim = true,
                c -> c.chatTab.rareDrop.enchantedBook = true, c -> c.chatTab.rareDrop.enchantedBookMissingMessage = true, c -> c.chatTab.hideFarDeaths = true)) {
            withConfig(on, () -> assertTrue(FeatureGate.chatTick()));
        }
        for (Consumer<ModConfig> off : List.<Consumer<ModConfig>>of(c -> c.chatTab.compactBestiary = true, c -> c.chatTab.compactPotion.enabled = true,
                c -> c.chatTab.shortenCoinAmounts = true, c -> c.chatTab.showEffectivePowder = true, c -> c.chatTab.darkAuctionItemDisplay = true,
                c -> c.chatTab.rareDrop.petRarity = true)) {
            withConfig(off, () -> assertFalse(FeatureGate.chatTick()));
        }
    }
}
