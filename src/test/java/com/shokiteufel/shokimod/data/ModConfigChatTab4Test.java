package com.shokiteufel.shokimod.data;

import com.shokiteufel.shokimod.chat.TranslatableLanguage;
import com.shokiteufel.shokimod.data.ModConfig.MessagePart;
import com.shokiteufel.shokimod.data.ModConfig.PlayerMessagesCategory;
import com.shokiteufel.shokimod.data.ModConfig.TranslatorCategory;
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
 * Chat (Teil 4): Player Messages und Translator - von Haus aus aus, speichern und laden, eine Datei von vorher, kaputte Werte (unbekannte Teile, doppelte, ein Sprachcode,
 * der keiner ist), und was die Schalter in den Chat-Weg und den Ort einschaltet.
 */
class ModConfigChatTab4Test {

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

    // ---- von Haus aus ----

    @Test
    void everythingIsOffFromTheStartEvenWhereSkyHanniHasItOn() throws Exception {
        ModConfig c = new ModConfig();
        List<Field> player = booleans(PlayerMessagesCategory.class);
        assertEquals(7, player.size(), "Enable Chat Formatting, Hide Level Brackets, Level Color As Name, Player Rank Hider, Ignore YouTube Rank, Chat Filter, Same Chat Color");
        for (Field f : player) assertFalse(f.getBoolean(c.chatTab.playerMessages), "Player Messages: " + f.getName());
        assertFalse(c.chatTab.playerMessages.sameChatColor, "Same Chat Color steht bei SkyHanni auf an - hier aus");
        assertFalse(c.chatTab.translator.translateOnClick, "der Uebersetzer schickt Text ins Netz: von Haus aus aus");
        assertEquals(1, booleans(TranslatorCategory.class).size());
        assertFalse(c.chatTab.playerMessages.any());
        withConfig(x -> {
        }, () -> {
            assertFalse(FeatureGate.chatPlayerMessages());
            assertFalse(FeatureGate.chatTranslator());
            assertFalse(FeatureGate.chatFilters(), "keiner der neuen Schalter greift in den Chat-Weg ein");
        });
    }

    @Test
    void theDefaultsOfTheValuesAreThoseOfSkyHanni() {
        ModConfig c = new ModConfig();
        assertEquals(List.of(MessagePart.SKYBLOCK_LEVEL, MessagePart.PRIVATE_ISLAND_RANK, MessagePart.PRIVATE_ISLAND_GUEST, MessagePart.PLAYER_NAME, MessagePart.GUILD_RANK,
                MessagePart.EMBLEM), c.chatTab.playerMessages.partsOrder);
        assertEquals(TranslatableLanguage.ENGLISH, c.chatTab.translator.languageName);
        assertEquals("", c.chatTab.translator.languageCode, "bei SkyHanni 'en'; hier leer, damit die Auswahl zaehlt");
        assertEquals("en", c.chatTab.translator.targetCode());
    }

    @Test
    void thePartOrderListCanBeChangedInPlaceLikeTheEditorDoes() {
        PlayerMessagesCategory p = new ModConfig().chatTab.playerMessages;
        List<MessagePart> live = p.partsOrder;
        live.add(MessagePart.MODE_IRONMAN);
        live.remove(MessagePart.EMBLEM);
        live.add(0, live.remove(3));
        assertEquals(MessagePart.PLAYER_NAME, p.partsOrder.get(0));
        assertTrue(p.partsOrder.contains(MessagePart.MODE_IRONMAN));
    }

    // ---- speichern und laden ----

    @Test
    void theChoicesSurviveSavingAndLoading() {
        ModConfig c = new ModConfig();
        PlayerMessagesCategory p = c.chatTab.playerMessages;
        p.enable = true;
        p.hideLevelBrackets = true;
        p.useLevelColorForName = true;
        p.playerRankHider = true;
        p.ignoreYouTube = true;
        p.chatFilter = true;
        p.sameChatColor = true;
        p.partsOrder = new ArrayList<>(List.of(MessagePart.EMBLEM, MessagePart.PLAYER_NAME, MessagePart.CRIMSON_FACTION, MessagePart.MODE_IRONMAN));
        TranslatorCategory t = c.chatTab.translator;
        t.translateOnClick = true;
        t.languageName = TranslatableLanguage.GERMAN;
        t.languageCode = "pt-PT";
        ModConfig again = ModConfig.GSON.fromJson(ModConfig.GSON.toJson(c), ModConfig.class);
        PlayerMessagesCategory q = again.chatTab.playerMessages;
        assertTrue(q.enable && q.hideLevelBrackets && q.useLevelColorForName && q.playerRankHider && q.ignoreYouTube && q.chatFilter && q.sameChatColor);
        assertEquals(List.of(MessagePart.EMBLEM, MessagePart.PLAYER_NAME, MessagePart.CRIMSON_FACTION, MessagePart.MODE_IRONMAN), q.partsOrder);
        assertTrue(again.chatTab.translator.translateOnClick);
        assertEquals(TranslatableLanguage.GERMAN, again.chatTab.translator.languageName);
        assertEquals("pt-PT", again.chatTab.translator.languageCode);
        assertEquals("pt-PT", again.chatTab.translator.targetCode());
    }

    @Test
    void theKeysInTheFileAreTheOnesTheModWrites() {
        String json = ModConfig.GSON.toJson(new ModConfig());
        for (String key : new String[] {"\"playerMessages\"", "\"enable\"", "\"partsOrder\"", "\"hideLevelBrackets\"", "\"useLevelColorForName\"", "\"playerRankHider\"",
                "\"ignoreYouTube\"", "\"chatFilter\"", "\"sameChatColor\"", "\"translator\"", "\"translateOnClick\"", "\"languageName\"", "\"languageCode\"",
                "\"SKYBLOCK_LEVEL\"", "\"EMBLEM\"", "\"ENGLISH\""}) {
            assertTrue(json.contains(key), key);
        }
        // Der Infotext und der Knopf werden nicht gespeichert
        assertFalse(json.contains("\"langCodesUrl\""));
        assertFalse(json.contains("\"about\""));
    }

    @Test
    void aFileFromBeforeKeepsTheDefaultsOfTheNewParts() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"dungeonAmbience\":true}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        assertTrue(loaded.chatTab.dungeonAmbience);
        assertNotNull(loaded.chatTab.playerMessages);
        assertNotNull(loaded.chatTab.translator);
        assertFalse(loaded.chatTab.playerMessages.enable);
        assertEquals(new PlayerMessagesCategory().partsOrder, loaded.chatTab.playerMessages.partsOrder);
        assertFalse(loaded.chatTab.translator.translateOnClick);
        assertEquals(TranslatableLanguage.ENGLISH, loaded.chatTab.translator.languageName);
    }

    @Test
    void aBrokenFileGetsTheMissingPartsBack() {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"playerMessages\":null,\"translator\":null}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        assertNotNull(loaded.chatTab.playerMessages);
        assertNotNull(loaded.chatTab.translator);
        ModConfig empty = ModConfig.GSON.fromJson("{\"chatTab\":null}", ModConfig.class);
        ModConfig.repairChatTab(empty);
        assertNotNull(empty.chatTab.playerMessages);
        assertNotNull(empty.chatTab.translator);
    }

    // ---- kaputte Werte ----

    private static List<MessagePart> partsFromFile(String listJson) {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"playerMessages\":{\"partsOrder\":" + listJson + "}}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        return loaded.chatTab.playerMessages.partsOrder;
    }

    @Test
    void unknownPartsAndDoublesFallOutAndTheOrderOfTheRestStays() {
        assertEquals(List.of(MessagePart.EMBLEM, MessagePart.PLAYER_NAME), partsFromFile("[\"EMBLEM\",\"NOPE\",\"EMBLEM\",\"PLAYER_NAME\",null]"));
        // Das Bingo-Teil von SkyHanni gibt es hier nicht: ein Name aus einer fremden Datei faellt weg
        assertEquals(List.of(MessagePart.PLAYER_NAME, MessagePart.GUILD_RANK), partsFromFile("[\"PLAYER_NAME\",\"BINGO_LEVEL\",\"GUILD_RANK\"]"));
    }

    @Test
    void aPartThatIsMissingStaysMissingBecauseDeletingItInTheListHidesIt() {
        assertEquals(List.of(MessagePart.PLAYER_NAME), partsFromFile("[\"PLAYER_NAME\"]"));
    }

    @Test
    void anEmptyOrBrokenListGivesTheDefault() {
        List<MessagePart> def = new PlayerMessagesCategory().partsOrder;
        assertEquals(def, partsFromFile("[]"));
        assertEquals(def, partsFromFile("[\"NOPE\"]"));
        assertEquals(def, partsFromFile("[null,null]"));
        assertEquals(def, partsFromFile("null"));
    }

    @Test
    void theRepairedListCanBeChangedByTheEditor() {
        List<MessagePart> repaired = partsFromFile("[\"EMBLEM\"]");
        repaired.add(MessagePart.PLAYER_NAME);
        assertEquals(List.of(MessagePart.EMBLEM, MessagePart.PLAYER_NAME), repaired);
    }

    private static TranslatorCategory translatorFromFile(String json) {
        ModConfig loaded = ModConfig.GSON.fromJson("{\"chatTab\":{\"translator\":" + json + "}}", ModConfig.class);
        ModConfig.repairChatTab(loaded);
        return loaded.chatTab.translator;
    }

    @Test
    void aLanguageThatIsNotInTheListBecomesEnglishAndABadCodeIsDropped() {
        TranslatorCategory t = translatorFromFile("{\"languageName\":\"KLINGON\",\"languageCode\":\"de&q=x\"}");
        assertEquals(TranslatableLanguage.ENGLISH, t.languageName);
        assertEquals("", t.languageCode);
        assertEquals("en", t.targetCode());

        t = translatorFromFile("{\"languageName\":null,\"languageCode\":null}");
        assertEquals(TranslatableLanguage.ENGLISH, t.languageName);
        assertEquals("", t.languageCode);

        t = translatorFromFile("{\"languageName\":\"FRENCH\",\"languageCode\":\"  zh-CN \"}");
        assertEquals(TranslatableLanguage.FRENCH, t.languageName);
        assertEquals("zh-CN", t.languageCode);
        assertEquals("zh-CN", t.targetCode(), "das Feld gewinnt");
    }

    // ---- die Schalter melden sich an ----

    @Test
    void theSwitchesThatChangeALineTurnOnTheChatPathAndTheLocationReader() {
        List<Consumer<ModConfig>> on = List.of(c -> c.chatTab.playerMessages.enable = true, c -> c.chatTab.playerMessages.playerRankHider = true,
                c -> c.chatTab.translator.translateOnClick = true);
        for (int i = 0; i < on.size(); i++) {
            int index = i;
            withConfig(on.get(i), () -> {
                assertTrue(FeatureGate.chatFilters(), "Schalter " + index + " schaltet den Chat-Weg ein");
                assertTrue(FeatureGate.chatFiltersLocation(), "Schalter " + index + " braucht den Ort");
                assertTrue(FeatureGate.location(), "Schalter " + index + " liest die Tab-Liste");
            });
        }
        withConfig(c -> c.chatTab.playerMessages.enable = true, () -> {
            assertTrue(FeatureGate.chatPlayerMessages());
            assertFalse(FeatureGate.chatTranslator());
        });
        withConfig(c -> c.chatTab.translator.translateOnClick = true, () -> {
            assertTrue(FeatureGate.chatTranslator());
            assertFalse(FeatureGate.chatPlayerMessages());
        });
    }

    @Test
    void theSubSwitchesAloneDoNothing() {
        List<Consumer<ModConfig>> alone = List.of(c -> c.chatTab.playerMessages.hideLevelBrackets = true, c -> c.chatTab.playerMessages.useLevelColorForName = true,
                c -> c.chatTab.playerMessages.ignoreYouTube = true, c -> c.chatTab.playerMessages.chatFilter = true, c -> c.chatTab.playerMessages.sameChatColor = true);
        for (int i = 0; i < alone.size(); i++) {
            int index = i;
            withConfig(alone.get(i), () -> {
                assertFalse(FeatureGate.chatPlayerMessages(), "Unterschalter " + index + " wirkt erst mit Enable Chat Formatting");
                assertFalse(FeatureGate.chatFilters(), "Unterschalter " + index);
            });
        }
    }

    @Test
    void theButtonHasARunnableFromTheStart() {
        // Das Fenster ruft den Knopf, ohne zu fragen, ob er gesetzt ist
        assertNotNull(new ModConfig().chatTab.translator.langCodesUrl);
        new ModConfig().chatTab.translator.langCodesUrl.run();
    }
}
