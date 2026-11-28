package com.shokiteufel.shokimod.data;

import com.google.gson.annotations.Expose;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der 26.3-Schalter (seit 2.6.2): ein Schalter fuer alle Funktionen, die seit der Umstellung auf 26.3 (ab 2.4.10) dazukamen. Aus heisst: keine tut etwas; die Schalter in
 * den Reitern bleiben dabei, wie sie sind - wer ihn wieder einschaltet, hat genau das zurueck, was an war.
 */
class Update263SwitchTest {

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

    /** Alles in der Datei einschalten, was ein Schalter ist (boolean mit @Expose), in allen Kategorien; dazu die zwei Einstellungen, die keiner sind (Taste, Auswahl) */
    static void everythingOn(ModConfig c) {
        try {
            turnOn(c, new IdentityHashMap<>());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        c.chatTab.peekChat = 80;
        c.slayer.spawnWhite = ModConfig.SlayerCategory.SpawnParticleMode.HIDE;
    }

    private static void turnOn(Object node, Map<Object, Boolean> seen) throws IllegalAccessException {
        if (node == null || seen.put(node, Boolean.TRUE) != null) return;
        for (Field f : node.getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
            if (f.getType() == boolean.class) {
                if (f.isAnnotationPresent(Expose.class)) f.setBoolean(node, true);
            } else if (f.getType().getEnclosingClass() == ModConfig.class) {
                turnOn(f.get(node), seen);
            }
        }
    }

    /** Alle Abfragen des FeatureGate, die zu einer Funktion seit 2.4.10 gehoeren */
    private static Map<String, BooleanSupplier> gates() {
        Map<String, BooleanSupplier> g = new LinkedHashMap<>();
        g.put("visitorShoppingList", FeatureGate::visitorShoppingList);
        g.put("visitorBlocking", FeatureGate::visitorBlocking);
        g.put("visitorRewardWarning", FeatureGate::visitorRewardWarning);
        g.put("visitorBook", FeatureGate::visitorBook);
        g.put("gardenPlots", FeatureGate::gardenPlots);
        g.put("slayerCocoon", FeatureGate::slayerCocoon);
        g.put("slayerSpawnParticles", FeatureGate::slayerSpawnParticles);
        g.put("skillProgress", FeatureGate::skillProgress);
        g.put("hideSkillGain", FeatureGate::hideSkillGain);
        g.put("auctionHighlights", FeatureGate::auctionHighlights);
        g.put("auctionUnderbid", FeatureGate::auctionUnderbid);
        g.put("bazaarHelpers", FeatureGate::bazaarHelpers);
        g.put("estimatedItemValue", FeatureGate::estimatedItemValue);
        g.put("fishingLines", FeatureGate::fishingLines);
        g.put("hookDisplay", FeatureGate::hookDisplay);
        g.put("baitDisplay", FeatureGate::baitDisplay);
        g.put("seaCocoon", FeatureGate::seaCocoon);
        g.put("npcDayLimit", FeatureGate::npcDayLimit);
        g.put("legionBobbin", FeatureGate::legionBobbin);
        g.put("warpCommands", FeatureGate::warpCommands);
        g.put("fixDoubleClicks", FeatureGate::fixDoubleClicks);
        g.put("inputCalculator", FeatureGate::inputCalculator);
        g.put("chatFilters", FeatureGate::chatFilters);
        g.put("chatFiltersLocation", FeatureGate::chatFiltersLocation);
        g.put("chatFiltersDungeon", FeatureGate::chatFiltersDungeon);
        g.put("skyblockXpInChat", FeatureGate::skyblockXpInChat);
        g.put("chatTick", FeatureGate::chatTick);
        g.put("farDeaths", FeatureGate::farDeaths);
        g.put("chatPeek", FeatureGate::chatPeek);
        g.put("copyChat", FeatureGate::copyChat);
        g.put("currentChatDisplay", FeatureGate::currentChatDisplay);
        g.put("chatSoundResponses", FeatureGate::chatSoundResponses);
        g.put("chatTabComplete", FeatureGate::chatTabComplete);
        g.put("chatRosters", FeatureGate::chatRosters);
        g.put("chatPlayerMessages", FeatureGate::chatPlayerMessages);
        g.put("chatTranslator", FeatureGate::chatTranslator);
        return g;
    }

    // ------------------------------------------------------------------ von Haus aus

    @Test
    void theSwitchIsOnFromTheStartSoNobodyLosesWhatTheyAlreadySet() {
        assertTrue(new ModConfig().update263.enabled);
        withConfig(c -> {
        }, () -> assertTrue(FeatureGate.update263()));
    }

    @Test
    void aMissingEntryCountsAsOn() {
        withConfig(c -> c.update263 = null, () -> assertTrue(FeatureGate.update263()));
        ModConfig saved = ModConfig.INSTANCE;
        try {
            ModConfig.INSTANCE = null;
            assertTrue(FeatureGate.update263(), "ohne Einstellungen (Start, Tests) gilt: an");
        } finally {
            ModConfig.INSTANCE = saved;
        }
    }

    @Test
    void aFileFromBeforeKeepsTheDefaultAndAStoredOffStaysOff() {
        ModConfig old = ModConfig.GSON.fromJson("{}", ModConfig.class);
        ModConfig.repairUpdate263(old);
        assertNotNull(old.update263);
        assertTrue(old.update263.enabled, "eine Datei ohne den Eintrag bleibt an");

        ModConfig off = ModConfig.GSON.fromJson("{\"update263\":{\"enabled\":false}}", ModConfig.class);
        ModConfig.repairUpdate263(off);
        assertFalse(off.update263.enabled, "ein gespeichertes aus bleibt aus");

        ModConfig nulled = ModConfig.GSON.fromJson("{\"update263\":null}", ModConfig.class);
        ModConfig.repairUpdate263(nulled);
        assertNotNull(nulled.update263, "ein null aus der Hand wird ersetzt");
        assertTrue(nulled.update263.enabled);
    }

    @Test
    void theSwitchIsSavedAndReadBack() {
        ModConfig c = new ModConfig();
        c.update263.enabled = false;
        String json = ModConfig.GSON.toJson(c);
        assertTrue(json.contains("\"update263\""), json.substring(0, Math.min(200, json.length())));
        assertFalse(ModConfig.GSON.fromJson(json, ModConfig.class).update263.enabled);
    }

    // ------------------------------------------------------------------ die Funktionen

    @Test
    void everyFunctionRunsWhenTheSwitchIsOnAndTheirOwnSwitchesAreOn() {
        withConfig(Update263SwitchTest::everythingOn, () -> {
            List<String> idle = new ArrayList<>();
            for (Map.Entry<String, BooleanSupplier> gate : gates().entrySet()) {
                if (!gate.getValue().getAsBoolean()) idle.add(gate.getKey());
            }
            // Der Beweis, dass die Pruefung unten nicht leer laeuft: Mit dem Schalter an und allem eingeschaltet arbeitet jede
            assertTrue(idle.isEmpty(), "diese laufen trotz allem nicht (Einrichtung des Tests): " + idle);
        });
    }

    @Test
    void withTheSwitchOffNoFunctionDoesAnythingEvenWithAllTheirSwitchesOn() {
        withConfig(c -> {
            everythingOn(c);
            c.update263.enabled = false;
        }, () -> {
            List<String> running = new ArrayList<>();
            for (Map.Entry<String, BooleanSupplier> gate : gates().entrySet()) {
                if (gate.getValue().getAsBoolean()) running.add(gate.getKey());
            }
            assertTrue(running.isEmpty(), "diese arbeiten mit dem 26.3-Schalter aus weiter: " + running);
        });
    }

    @Test
    void turningTheSwitchOffAndOnTouchesNoSettingOfTheFunctions() {
        withConfig(Update263SwitchTest::everythingOn, () -> {
            String before = ModConfig.GSON.toJson(ModConfig.INSTANCE);
            ModConfig.INSTANCE.update263.enabled = false;
            String off = ModConfig.GSON.toJson(ModConfig.INSTANCE);
            assertTrue(ModConfig.INSTANCE.chatTab.copyChat, "die Schalter der Reiter bleiben, wie sie waren");
            assertTrue(ModConfig.INSTANCE.fishing.hookDisplay.enabled);
            assertTrue(ModConfig.INSTANCE.inventory.itemValue.enabled);
            assertFalse(before.equals(off), "nur der 26.3-Schalter unterscheidet");
            ModConfig.INSTANCE.update263.enabled = true;
            assertEquals(before, ModConfig.GSON.toJson(ModConfig.INSTANCE), "wieder an: genau der Stand von vorher");
        });
    }

    @Test
    void theFunctionsOfBeforeThe263ErasKeepWorkingWithTheSwitchOff() {
        // Der Gegenbeweis: Der Schalter sperrt nicht alles. Funktionen von vor 2.4.10 (hier der Kasten der Safari, der Profit Tracker, der Dark-Auction-Alarm) bleiben
        withConfig(c -> {
            everythingOn(c);
            c.update263.enabled = false;
        }, () -> {
            assertTrue(FeatureGate.cakeReminder());
            assertTrue(FeatureGate.darkAuction());
            assertTrue(FeatureGate.hotspots());
            assertTrue(FeatureGate.slayerKillTime(), "Slayer Kill time ist von 2.4.5 und bleibt");
            assertTrue(FeatureGate.hideOtherHooks());
            assertTrue(FeatureGate.mute());
        });
    }

    @Test
    void aSleepingFunctionDoesNotMakeTheModReadTheLocation() {
        // Der Ort (die Tab-Liste lesen, das teuerste, was die Mod tut) wird fuer eine schlafende Funktion nicht gebraucht
        boolean[] baseline = new boolean[1];
        withConfig(c -> {
        }, () -> baseline[0] = FeatureGate.location());
        withConfig(c -> {
            c.fishing.hookDisplay.enabled = true;
            c.misc.legionBobbin.enabled = true;
            c.update263.enabled = false;
        }, () -> assertEquals(baseline[0], FeatureGate.location(), "mit dem 26.3-Schalter aus aendern zwei eingeschaltete Funktionen am Ort nichts"));
    }

    // ------------------------------------------------------------------ die Kaesten

    @Test
    void theElevenPanelsOfThe263FunctionsAreTheOnesThatRest() {
        Set<SafariHud.Panel> expected = EnumSet.of(SafariHud.Panel.FISHING_HOOK, SafariHud.Panel.FISHING_BAIT, SafariHud.Panel.SKILL_PROGRESS,
                SafariHud.Panel.SKILL_BAR, SafariHud.Panel.BAZAAR_SELL, SafariHud.Panel.NPC_LIMIT, SafariHud.Panel.ITEM_VALUE, SafariHud.Panel.CURRENT_CHAT,
                SafariHud.Panel.VISITOR_LIST, SafariHud.Panel.GARDEN_PLOTS, SafariHud.Panel.LEGION_BOBBIN);
        Set<SafariHud.Panel> actual = EnumSet.noneOf(SafariHud.Panel.class);
        for (SafariHud.Panel panel : SafariHud.Panel.values()) if (panel.from263()) actual.add(panel);
        assertEquals(expected, actual);
        assertEquals(11, actual.size());
    }

    @Test
    void withTheSwitchOffThosePanelsAreNotShownWhateverTheirOwnSwitchesSay() {
        withConfig(c -> {
            everythingOn(c);
            c.update263.enabled = false;
        }, () -> {
            for (SafariHud.Panel panel : SafariHud.Panel.values()) {
                if (panel.from263()) assertFalse(panel.visible(), panel + " darf mit dem 26.3-Schalter aus nicht erscheinen");
            }
        });
    }

    @Test
    void aNewPanelIsNotCountedAs263WithoutSayingSo() {
        // Wacht darueber, dass ein neuer Kasten bewusst eingeordnet wird: Die Liste der Kaesten ist bekannt, jeder hier zaehlt zu einer Seite
        // 30: NPC Shop Values (SHOP_VALUE) kam dazu - eine eigene Funktion, keine seit 2.4.10, also bewusst nicht in from263()
        int known = 30;
        assertEquals(known, SafariHud.Panel.values().length,
                "ein neuer Kasten: gehoert er zu einer Funktion seit 2.4.10? Dann in SafariHud.Panel#from263() eintragen und hier die Zahl und den Test oben nachfuehren");
    }
}
