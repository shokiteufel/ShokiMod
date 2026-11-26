package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.data.ModConfig.ChatFilterTypesCategory;
import com.shokiteufel.shokimod.data.ModConfig.ChatTabCategory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 1, 2.4.18): von Haus aus aus, speichern und laden, eine Datei von vorher, kaputte Werte, und was den Ort und den Dungeon-Leser einschaltet. */
class ModConfigChatTabTest {

    /**
     * Unteroptionen eines Akkordeons, die SkyHannis Vorgabe behalten (true), weil sie erst wirken, wenn der Hauptschalter an ist. Jeder andere
     * Schalter ist von Haus aus aus - steht hier etwas nicht, das true ist, schlaegt der Test an.
     */
    private static final Set<String> SUB_OPTIONS_WITH_SKYHANNI_DEFAULT = Set.of(
            "powderAscensionRope", "powderWishingCompass", "powderOilBarrel", "powderJungleHeart", "powderSludgeJuice", "powderYoggie", "powderTreasurite",
            "powderStrongerTool",
            "nucleusRunCompleted", "nucleusCrystalCollected", "nucleusCrystalPlaced", "nucleusKeepers", "nucleusProfRobot", "nucleusKingYolkar",
            "nucleusGoblinGuards", "nucleusNonToolScavenge",
            "stashHideDupes", "stashOncePerWorld", "stashHideAdded");

    private static List<Field> booleans(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (f.getType() == boolean.class && !Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) out.add(f);
        }
        return out;
    }

    @Test
    void everySwitchIsOffFromTheStartExceptTheSubOptionsOfAFoldOut() throws Exception {
        ModConfig c = new ModConfig();
        for (Field f : booleans(ChatTabCategory.class)) {
            assertFalse(f.getBoolean(c.chatTab), "Chat: " + f.getName());
        }
        int subOptionsSeen = 0;
        for (Field f : booleans(ChatFilterTypesCategory.class)) {
            boolean value = f.getBoolean(c.chatTab.filterTypes);
            if (SUB_OPTIONS_WITH_SKYHANNI_DEFAULT.contains(f.getName())) {
                assertTrue(value, "Unteroption " + f.getName() + " behaelt SkyHannis Vorgabe");
                subOptionsSeen++;
            } else {
                assertFalse(value, "Chat Filter Types: " + f.getName());
            }
        }
        assertEquals(SUB_OPTIONS_WITH_SKYHANNI_DEFAULT.size(), subOptionsSeen, "alle genannten Unteroptionen gibt es");
        withConfig(x -> {
        }, () -> {
            assertFalse(FeatureGate.chatFilters());
            assertFalse(FeatureGate.chatFiltersLocation());
            assertFalse(FeatureGate.chatFiltersDungeon());
            assertFalse(FeatureGate.farDeaths());
        });
    }

    @Test
    void theNumbersStartWhereSkyHanniPutsThem() {
        ChatFilterTypesCategory f = new ModConfig().chatTab.filterTypes;
        assertEquals(1000, f.powderThreshold);
        assertEquals(5, f.essenceThreshold);
        assertEquals(0, f.stashHideLow);
        assertEquals(ModConfig.PowderGoblinEggs.YELLOW_UP, f.powderGoblinEggs);
        assertEquals(ModConfig.PowderGemstones.FINE_UP, f.powderRuby);
        assertEquals(ModConfig.PowderGemstones.FINE_UP, f.powderJasper);
    }

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        c.chatTab.dungeonAmbience = true;
        c.chatTab.hideSacksChange = true;
        c.chatTab.onlyHideSacksChangeOnGarden = true;
        c.chatTab.filterTypes.powderEnabled = true;
        c.chatTab.filterTypes.powderThreshold = 20_500;
        c.chatTab.filterTypes.powderGoblinEggs = ModConfig.PowderGoblinEggs.BLUE_ONLY;
        c.chatTab.filterTypes.powderJade = ModConfig.PowderGemstones.HIDE_ALL;
        c.chatTab.filterTypes.powderYoggie = false;
        c.chatTab.filterTypes.stashEnabled = true;
        c.chatTab.filterTypes.stashHideLow = 5_000;
        c.chatTab.filterTypes.others = true;
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        assertTrue(again.chatTab.dungeonAmbience);
        assertFalse(again.chatTab.dungeonPickup);
        assertTrue(again.chatTab.hideSacksChange);
        assertTrue(again.chatTab.onlyHideSacksChangeOnGarden);
        assertTrue(again.chatTab.filterTypes.powderEnabled);
        assertEquals(20_500, again.chatTab.filterTypes.powderThreshold);
        assertEquals(ModConfig.PowderGoblinEggs.BLUE_ONLY, again.chatTab.filterTypes.powderGoblinEggs);
        assertEquals(ModConfig.PowderGemstones.HIDE_ALL, again.chatTab.filterTypes.powderJade);
        assertFalse(again.chatTab.filterTypes.powderYoggie);
        assertTrue(again.chatTab.filterTypes.stashEnabled);
        assertEquals(5_000, again.chatTab.filterTypes.stashHideLow);
        assertTrue(again.chatTab.filterTypes.others);
        assertFalse(again.chatTab.filterTypes.empty);
    }

    @Test
    void theKeysInTheFileAreTheOnesTheModWrites() {
        String json = ModConfig.GSON.toJson(new ModConfig());
        for (String key : new String[] {"\"chatTab\"", "\"filterTypes\"", "\"dungeonPrepare\"", "\"hideFarDeaths\"", "\"powderGoblinEggs\"", "\"stashHideLow\""}) {
            assertTrue(json.contains(key), key);
        }
        // Die Infotexte werden nicht gespeichert
        assertFalse(json.contains("\"stashNotice\""));
    }

    @Test
    void aFileFromBeforeTheChatTabGetsIt() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":null}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        assertNotNull(loaded.chatTab);
        assertNotNull(loaded.chatTab.filterTypes);
        ModConfig second = ModConfig.GSON.fromJson("{\"chatTab\":{\"filterTypes\":null}}", ModConfig.class);
        ModConfig.repairChatTab(second);
        assertNotNull(second.chatTab.filterTypes);
        // Eine alte Datei ohne den Reiter: das Feld bleibt, wie es neu angelegt wurde
        ModConfig empty = ModConfig.GSON.fromJson("{}", ModConfig.class);
        ModConfig.repairChatTab(empty);
        assertFalse(empty.chatTab.anyFilter());
    }

    @Test
    void brokenValuesAreRepaired() {
        ModConfig loaded = ModConfig.GSON.fromJson(
                "{\"chatTab\":{\"filterTypes\":{\"powderGoblinEggs\":\"NOPE\",\"powderRuby\":\"NOPE\",\"powderTopaz\":null,"
                        + "\"powderThreshold\":-5,\"essenceThreshold\":99,\"stashHideLow\":99999999}}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        ChatFilterTypesCategory f = loaded.chatTab.filterTypes;
        assertEquals(ModConfig.PowderGoblinEggs.YELLOW_UP, f.powderGoblinEggs);
        assertEquals(ModConfig.PowderGemstones.FINE_UP, f.powderRuby);
        assertEquals(ModConfig.PowderGemstones.FINE_UP, f.powderTopaz);
        assertEquals(0, f.powderThreshold);
        assertEquals(20, f.essenceThreshold);
        assertEquals(1_000_000, f.stashHideLow);
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
    void everySwitchThatNeedsThePlaceTurnsOnTheLocationReader() {
        List<Consumer<ModConfig>> needPlace = List.of(
                c -> c.chatTab.dungeonPrepare = true, c -> c.chatTab.dungeonStart = true, c -> c.chatTab.dungeonAmbience = true,
                c -> c.chatTab.dungeonPickup = true, c -> c.chatTab.dungeonReminder = true, c -> c.chatTab.dungeonBuff = true,
                c -> c.chatTab.dungeonNotPossible = true, c -> c.chatTab.dungeonDamage = true, c -> c.chatTab.dungeonAbility = true,
                c -> c.chatTab.dungeonPuzzle = true, c -> c.chatTab.dungeonEnd = true, c -> c.chatTab.dungeonBossMessages = true,
                c -> c.chatTab.hideFarDeaths = true, c -> c.chatTab.hideArachne = true, c -> c.chatTab.hideAnita = true,
                c -> c.chatTab.hideSkyMall = true, c -> c.chatTab.hideForagingBuff = true,
                c -> {
                    c.chatTab.hideSacksChange = true;
                    c.chatTab.onlyHideSacksChangeOnGarden = true;
                },
                c -> c.chatTab.filterTypes.powderEnabled = true, c -> c.chatTab.filterTypes.nucleusEnabled = true,
                c -> c.chatTab.filterTypes.unmineableTrees = true, c -> c.chatTab.filterTypes.huntingRedundantComments = true,
                c -> c.chatTab.filterTypes.stashEnabled = true, c -> c.chatTab.filterTypes.gardenNoPest = true);
        for (int i = 0; i < needPlace.size(); i++) {
            int index = i;
            withConfig(needPlace.get(i), () -> {
                assertTrue(FeatureGate.chatFilters(), "Schalter " + index + " schaltet den Chat-Weg ein");
                assertTrue(FeatureGate.chatFiltersLocation(), "Schalter " + index + " braucht den Ort");
                assertTrue(FeatureGate.location(), "Schalter " + index + " liest die Tab-Liste");
            });
        }
    }

    @Test
    void theSwitchesThatNeedNoPlaceDoNotTurnOnTheLocationReader() {
        List<Consumer<ModConfig>> noPlace = List.of(
                c -> c.chatTab.hideSacksChange = true, c -> c.chatTab.filterTypes.empty = true, c -> c.chatTab.filterTypes.others = true,
                c -> c.chatTab.filterTypes.hypixelLobbies = true, c -> c.chatTab.filterTypes.watchdog = true, c -> c.chatTab.filterTypes.parkour = true,
                c -> c.chatTab.filterTypes.diana = true, c -> c.chatTab.filterTypes.eventLevelUp = true, c -> c.chatTab.filterTypes.huntingSwoopAxe = true);
        for (int i = 0; i < noPlace.size(); i++) {
            int index = i;
            withConfig(noPlace.get(i), () -> {
                assertTrue(FeatureGate.chatFilters(), "Schalter " + index + " schaltet den Chat-Weg ein");
                assertFalse(FeatureGate.chatFiltersLocation(), "Schalter " + index + " braucht den Ort nicht");
            });
        }
    }

    @Test
    void theDungeonReaderRunsOnlyForTheSwitchesThatAskForIt() {
        withConfig(c -> c.chatTab.dungeonBuff = true, () -> assertTrue(FeatureGate.chatFiltersDungeon()));
        withConfig(c -> c.chatTab.dungeonBossMessages = true, () -> assertTrue(FeatureGate.chatFiltersDungeon()));
        withConfig(c -> c.chatTab.hideFarDeaths = true, () -> {
            assertTrue(FeatureGate.chatFiltersDungeon());
            assertTrue(FeatureGate.farDeaths());
        });
        withConfig(c -> c.chatTab.filterTypes.powderEnabled = true, () -> assertFalse(FeatureGate.chatFiltersDungeon()));
        withConfig(c -> c.chatTab.hideArachne = true, () -> assertFalse(FeatureGate.chatFiltersDungeon()));
    }
}
