package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.ModConfig.ChatSoundResponseCategory;
import com.shokiteufel.shokimod.data.ModConfig.TabCompleteCategory;
import com.shokiteufel.shokimod.render.hud.SafariHud;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 3): von Haus aus aus, speichern und laden, eine Datei von vorher, kaputte Werte, und was die Gates einschaltet. Der Test {@code ModConfigChatTabTest}
 * prueft ueber alle Schalter von {@code ChatTabCategory}, dass jeder aus ist - Copy Chat und Current Chat Display sind dabei.
 */
class ModConfigChatTab3Test {

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

    private static boolean baselineLocation() {
        boolean[] out = new boolean[1];
        withConfig(c -> {
        }, () -> out[0] = FeatureGate.location());
        return out[0];
    }

    @Test
    void everythingIsOffFromTheStart() throws Exception {
        ModConfig c = new ModConfig();
        assertEquals(-1, c.chatTab.peekChat);
        assertFalse(c.chatTab.hasPeekKey());
        assertFalse(c.chatTab.copyChat);
        assertFalse(c.chatTab.currentChatDisplay);
        assertFalse(c.chatTab.soundResponse.enabled);
        for (Field f : booleans(TabCompleteCategory.class)) assertFalse(f.getBoolean(c.chatTab.tabComplete), "Tab Complete: " + f.getName());
        assertEquals(12, booleans(TabCompleteCategory.class).size(), "zwoelf Schalter (seit 2.6.8 mit VIP Visits)");
        assertFalse(c.chatTab.tabComplete.any());
        assertTrue(c.chatTab.tabComplete.knownFriends.isEmpty());
        assertTrue(c.chatTab.tabComplete.knownBestFriends.isEmpty());
        assertTrue(c.chatTab.tabComplete.knownGuildMembers.isEmpty());
        withConfig(x -> {
        }, () -> {
            assertFalse(FeatureGate.chatPeek());
            assertFalse(FeatureGate.copyChat());
            assertFalse(FeatureGate.currentChatDisplay());
            assertFalse(FeatureGate.chatSoundResponses());
            assertFalse(FeatureGate.chatTabComplete());
            assertFalse(FeatureGate.chatRosters());
            assertFalse(FeatureGate.chatFilters(), "keiner der neuen Schalter greift in den Chat-Weg des Ausblendens ein");
        });
    }

    @Test
    void theSoundSwitchesStartOnLikeTheWholeListOfSkyHanniButOnlyWorkWithEnabled() throws Exception {
        ChatSoundResponseCategory s = new ModConfig().chatTab.soundResponse;
        assertFalse(s.enabled);
        List<Field> types = booleans(ChatSoundResponseCategory.class);
        assertEquals(16, types.size(), "Enabled und fuenfzehn Arten");
        for (Field f : types) {
            if (f.getName().equals("enabled")) continue;
            assertTrue(f.getBoolean(s), "Art " + f.getName() + " steht von Haus aus an (SkyHannis Liste enthaelt alle)");
        }
        assertTrue(s.anyType());
        withConfig(c -> {
        }, () -> assertFalse(FeatureGate.chatSoundResponses(), "ohne Enabled tut keine Art etwas"));
        withConfig(c -> c.chatTab.soundResponse.enabled = true, () -> assertTrue(FeatureGate.chatSoundResponses()));
    }

    @Test
    void enabledWithNoKindLeftDoesNothing() throws Exception {
        withConfig(c -> {
            c.chatTab.soundResponse.enabled = true;
            try {
                for (Field f : booleans(ChatSoundResponseCategory.class)) f.setBoolean(c.chatTab.soundResponse, false);
                c.chatTab.soundResponse.enabled = true;
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }, () -> {
            assertFalse(ModConfig.INSTANCE.chatTab.soundResponse.anyType());
            assertFalse(FeatureGate.chatSoundResponses());
        });
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.chatTab.peekChat = 75;
        c.chatTab.copyChat = true;
        c.chatTab.currentChatDisplay = true;
        c.chatTab.currentChatHudX = 0.25f;
        c.chatTab.currentChatHudScale = 1.5f;
        c.chatTab.soundResponse.enabled = true;
        c.chatTab.soundResponse.cow = false;
        c.chatTab.tabComplete.call = true;
        c.chatTab.tabComplete.friends = true;
        c.chatTab.tabComplete.stashCommand = true;
        c.chatTab.tabComplete.knownFriends = new ArrayList<>(List.of("Fred", "Frida"));
        c.chatTab.tabComplete.knownBestFriends = new ArrayList<>(List.of("Frida"));
        c.chatTab.tabComplete.knownGuildMembers = new ArrayList<>(List.of("Gus"));
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertEquals(75, again.chatTab.peekChat);
        assertTrue(again.chatTab.hasPeekKey());
        assertTrue(again.chatTab.copyChat);
        assertTrue(again.chatTab.currentChatDisplay);
        assertEquals(0.25f, again.chatTab.currentChatHudX);
        assertEquals(0.93f, again.chatTab.currentChatHudY);
        assertEquals(1.5f, again.chatTab.currentChatHudScale);
        assertTrue(again.chatTab.soundResponse.enabled);
        assertFalse(again.chatTab.soundResponse.cow);
        assertTrue(again.chatTab.soundResponse.cat);
        assertTrue(again.chatTab.tabComplete.call);
        assertTrue(again.chatTab.tabComplete.friends);
        assertTrue(again.chatTab.tabComplete.stashCommand);
        assertFalse(again.chatTab.tabComplete.warps);
        assertEquals(List.of("Fred", "Frida"), again.chatTab.tabComplete.knownFriends);
        assertEquals(List.of("Frida"), again.chatTab.tabComplete.knownBestFriends);
        assertEquals(List.of("Gus"), again.chatTab.tabComplete.knownGuildMembers);
    }

    @Test
    void theKeysInTheFileAreTheOnesTheModWrites() {
        String json = ModConfig.GSON.toJson(new ModConfig());
        for (String key : new String[] {"\"peekChat\"", "\"copyChat\"", "\"currentChatDisplay\"", "\"currentChatHudX\"", "\"currentChatHudY\"", "\"currentChatHudScale\"",
                "\"currentChatHudAlpha\"", "\"soundResponse\"", "\"tabComplete\"", "\"gfsSack\"", "\"viewRecipe\"", "\"showItem\"", "\"stashCommand\"", "\"vipVisits\"",
                "\"onlyBestFriends\"", "\"knownFriends\"", "\"knownBestFriends\"", "\"knownGuildMembers\"", "\"catPurreow\"", "\"goat\""}) {
            assertTrue(json.contains(key), key);
        }
        // Die Infotexte und die Akkordeons werden nicht gespeichert
        assertFalse(json.contains("\"soundsFolder\""));
    }

    @Test
    void aFileFromBeforeKeepsTheDefaultsOfTheNewParts() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"dungeonAmbience\":true}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        assertTrue(loaded.chatTab.dungeonAmbience);
        assertNotNull(loaded.chatTab.soundResponse);
        assertNotNull(loaded.chatTab.tabComplete);
        assertEquals(-1, loaded.chatTab.peekChat);
        assertFalse(loaded.chatTab.copyChat);
        assertFalse(loaded.chatTab.soundResponse.enabled);
        assertTrue(loaded.chatTab.soundResponse.cat);
        assertFalse(loaded.chatTab.tabComplete.any());
    }

    @Test
    void aBrokenFileGetsTheMissingPartsBack() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"soundResponse\":null,\"tabComplete\":null}}", ModConfig.class);
        assertEquals(null, loaded.chatTab.soundResponse);
        ModConfig.repairChatTab(loaded);
        assertNotNull(loaded.chatTab.soundResponse);
        assertNotNull(loaded.chatTab.tabComplete);
        assertFalse(loaded.chatTab.soundResponse.enabled);
        assertTrue(loaded.chatTab.tabComplete.knownFriends.isEmpty());

        ModConfig empty = ModConfig.GSON.fromJson("{\"chatTab\":null}", ModConfig.class);
        ModConfig.repairChatTab(empty);
        assertNotNull(empty.chatTab.soundResponse);
        assertNotNull(empty.chatTab.tabComplete);
    }

    @Test
    void theRememberedNamesAreCleanedWhenTheFileIsRead() {
        ModConfig loaded = ModConfig.GSON.fromJson(
                "{\"chatTab\":{\"tabComplete\":{\"knownFriends\":[\"Fred\",\"bad name\",null,\"Fred\",\"Frida\"],"
                        + "\"knownBestFriends\":null,\"knownGuildMembers\":[\"waytoolongtobeaname123\",\"Gus\"]}}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        TabCompleteCategory t = loaded.chatTab.tabComplete;
        assertEquals(List.of("Fred", "Frida"), t.knownFriends, "kein Doppeltes, keine falschen Namen, kein null");
        assertEquals(List.of(), t.knownBestFriends, "eine fehlende Liste ist eine leere");
        assertEquals(List.of("Gus"), t.knownGuildMembers);
    }

    @Test
    void theRememberedNamesStopAtFiveThousand() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 6000; i++) many.add("n" + i);
        assertEquals(5000, ModConfig.cleanNames(many).size());
        assertEquals(List.of(), ModConfig.cleanNames(null));
    }

    @Test
    void thePeekKeyAndCopyChatTouchNoGateButTheirOwn() {
        boolean baseline = baselineLocation();
        withConfig(c -> c.chatTab.peekChat = 75, () -> {
            assertTrue(FeatureGate.chatPeek());
            assertFalse(FeatureGate.chatFilters());
            assertFalse(FeatureGate.chatRosters());
            assertEquals(baseline, FeatureGate.location(), "Peek Chat braucht den Ort nicht");
        });
        withConfig(c -> c.chatTab.peekChat = 20, () -> assertFalse(FeatureGate.chatPeek(), "ein Wert unter 32 ist keine Taste"));
        withConfig(c -> c.chatTab.copyChat = true, () -> {
            assertTrue(FeatureGate.copyChat());
            assertFalse(FeatureGate.chatFilters());
            assertEquals(baseline, FeatureGate.location(), "Copy Chat braucht den Ort nicht");
        });
    }

    @Test
    void currentChatDisplayNeedsThePlaceAndTheRosters() {
        withConfig(c -> c.chatTab.currentChatDisplay = true, () -> {
            assertTrue(FeatureGate.currentChatDisplay());
            assertTrue(FeatureGate.chatRosters(), "die Zeile 'Party (3 members)' braucht die Party");
            assertTrue(FeatureGate.location(), "der Kasten steht nur in SkyBlock");
            assertFalse(FeatureGate.chatFilters());
            assertFalse(FeatureGate.chatTabComplete());
        });
    }

    @Test
    void soundResponsesAndTabCompleteNeedThePlace() {
        withConfig(c -> c.chatTab.soundResponse.enabled = true, () -> {
            assertTrue(FeatureGate.location(), "Sound Responses gelten nur in SkyBlock");
            assertFalse(FeatureGate.chatRosters());
        });
        List<Consumer<ModConfig>> any = List.of(c -> c.chatTab.tabComplete.warps = true, c -> c.chatTab.tabComplete.islandPlayers = true,
                c -> c.chatTab.tabComplete.friends = true, c -> c.chatTab.tabComplete.party = true, c -> c.chatTab.tabComplete.guild = true,
                c -> c.chatTab.tabComplete.gfsSack = true, c -> c.chatTab.tabComplete.viewRecipe = true, c -> c.chatTab.tabComplete.showItem = true,
                c -> c.chatTab.tabComplete.call = true, c -> c.chatTab.tabComplete.stashCommand = true, c -> c.chatTab.tabComplete.vipVisits = true);
        for (int i = 0; i < any.size(); i++) {
            int index = i;
            withConfig(any.get(i), () -> {
                assertTrue(FeatureGate.chatTabComplete(), "Schalter " + index);
                assertTrue(FeatureGate.chatRosters(), "Schalter " + index + " braucht die Listen");
                assertTrue(FeatureGate.location(), "Schalter " + index + " gilt nur in SkyBlock");
                assertFalse(FeatureGate.chatFilters(), "Schalter " + index);
            });
        }
        withConfig(c -> c.chatTab.tabComplete.onlyBestFriends = true, () -> assertFalse(FeatureGate.chatTabComplete(), "Only Best Friends allein tut nichts"));
    }

    @Test
    void theCurrentChatPanelIsABoxOfTheHudEditor() {
        withConfig(c -> {
        }, () -> {
            SafariHud.Panel p = SafariHud.Panel.CURRENT_CHAT;
            assertEquals("Current Chat Display", p.settingsSearch());
            assertEquals(0.01f, p.x());
            assertEquals(0.93f, p.y());
            assertEquals(1.0f, p.scale());
            assertEquals(1.0f, p.alpha());
            assertFalse(p.visible(), "ohne den Schalter steht er nie");

            p.setPosition(0.5f, 0.25f);
            p.setScale(2.0f);
            p.setAlpha(0.5f);
            assertEquals(0.5f, ModConfig.INSTANCE.chatTab.currentChatHudX);
            assertEquals(0.25f, ModConfig.INSTANCE.chatTab.currentChatHudY);
            assertEquals(2.0f, ModConfig.INSTANCE.chatTab.currentChatHudScale);
            assertEquals(0.5f, ModConfig.INSTANCE.chatTab.currentChatHudAlpha);
            p.setScale(9.0f);
            assertEquals(3.0f, p.scale(), "die Grenze des Editors");
            p.setAlpha(7.0f);
            assertEquals(1.0f, p.alpha());
        });
    }
}
