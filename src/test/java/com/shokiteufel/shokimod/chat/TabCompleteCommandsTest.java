package com.shokiteufel.shokimod.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 3) > Tab Complete: was die Mod fuer die Chat-Eingabe vorschlaegt, so wie das Mixin es fragt - ohne Spiel. Was hier nicht geprueft werden kann (dass das
 * Mixin im laufenden Minecraft greift, Hypixels echte Befehlsliste), steht im Bericht; das Mixin selbst prueft {@code CommandDispatcherMixinTest} auf seine Form
 * und der Entwicklungs-Client auf seine Wirkung.
 */
class TabCompleteCommandsTest {

    private ModConfig saved;

    @BeforeEach
    void setUp() {
        saved = ModConfig.INSTANCE;
        ModConfig.INSTANCE = new ModConfig();
        GameState.Server.gametype = "SKYBLOCK";
        PartyRoster.INSTANCE.clear();
    }

    @AfterEach
    void tearDown() {
        ModConfig.INSTANCE = saved;
        GameState.resetAll();
        PartyRoster.INSTANCE.clear();
    }

    private static Suggestions complete(String input) {
        return TabCompleteCommands.complete(input, input.length());
    }

    private static List<String> texts(Suggestions s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (Suggestion suggestion : s.getList()) out.add(suggestion.getText());
        return out;
    }

    // ---- die Vorschlaege

    @Test
    void callElCompletesToElizabethTheExampleOfTheUser() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        Suggestions s = complete("/call el");
        // Seit 2.5.10 kennt die Liste alle Kontakte: "el" trifft Elizabeth und Elle
        assertEquals(List.of("elizabeth", "elle"), texts(s));
        assertEquals(6, s.getRange().getStart(), "der Vorschlag ersetzt 'el', nicht '/call el'");
        assertEquals(8, s.getRange().getEnd());
        assertEquals(List.of("elizabeth"), texts(complete("/call eli")), "mit einem Buchstaben mehr nur noch Elizabeth");
    }

    @Test
    void callWithNothingTypedOffersEveryContact() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertEquals(AbiphoneContacts.NAMES.size(), complete("/call ").getList().size());
    }

    @Test
    void callFindsANameWithSpacesAsAWholeAndReplacesTheWholeText() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        Suggestions s = complete("/call clerk s");
        assertEquals(List.of("clerk seraphine"), texts(s));
        assertEquals(6, s.getRange().getStart(), "ab dem Ende von '/call ': der ganze Text 'clerk s' wird ersetzt");
        assertEquals(13, s.getRange().getEnd());
        assertEquals(List.of("alixer"), texts(complete("/call alix")), "ein Name, den bis 2.6.7 nur SkyHanni kannte");
    }

    @Test
    void visitWithOnlyTheVipSwitchOnOffersTheVipNames() {
        ModConfig.INSTANCE.chatTab.tabComplete.vipVisits = true;
        Suggestions s = complete("/visit p");
        // Brigadier sortiert die Vorschlaege (Gross und Klein egal); nachgeladene Namen koennen dazukommen, darum nur "enthaelt"
        assertTrue(texts(s).containsAll(List.of("prtlhub", "PortalHub")), texts(s).toString());
        assertFalse(texts(s).contains("Minikloon"), "beginnt nicht mit p");
        assertEquals(7, s.getRange().getStart());
        assertTrue(TabCompleteCommands.active("visit"));
        assertFalse(TabCompleteCommands.active("p"), "VIP Visits schaltet nur /visit ein");
    }

    @Test
    void anotherWordOfTheSameCommandSuggestsNothing() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertNull(complete("/call elizabeth "), "nichts zu sagen: die Liste des Servers gilt");
    }

    @Test
    void aLaterWordOfAPlayerCommandReplacesOnlyThatWord() {
        ModConfig.INSTANCE.chatTab.tabComplete.party = true;
        PartyRoster.INSTANCE.onLine("You'll be partying with: Plum, Carl", "Me");
        Suggestions s = complete("/p kick Pl");
        assertEquals(List.of("Plum"), texts(s));
        assertEquals(8, s.getRange().getStart(), "ab dem dritten Wort: '/p kick ' sind acht Zeichen");
        assertEquals(10, s.getRange().getEnd());
    }

    @Test
    void theCommandWordMayBeTypedInAnyCase() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertEquals(List.of("elizabeth"), texts(complete("/CALL eli")));
    }

    @Test
    void onlyWhatStandsBeforeTheCursorCounts() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        // Cursor hinter "/call eli" in "/call elizabeth": der Rest dahinter wird nicht gelesen
        Suggestions s = TabCompleteCommands.complete("/call elizabeth", 9);
        assertEquals(List.of("elizabeth"), texts(s));
        assertEquals(6, s.getRange().getStart());
        assertEquals(9, s.getRange().getEnd());
    }

    // ---- wann die Mod nichts sagt (dann gilt die Liste des Servers)

    @Test
    void withTheSwitchOffNothingIsSuggested() {
        assertNull(complete("/call el"));
        assertNull(complete("/warp "));
        assertNull(complete("/p "));
        // Ein anderer Schalter schaltet diesen Befehl nicht ein
        ModConfig.INSTANCE.chatTab.tabComplete.warps = true;
        assertNull(complete("/call el"));
    }

    @Test
    void outsideSkyBlockNothingIsSuggested() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        GameState.Server.gametype = "Unknown";
        assertNull(complete("/call el"));
        assertFalse(TabCompleteCommands.active("call"));
        GameState.Server.gametype = "SKYBLOCK";
        assertNotNullAndActive();
    }

    private static void assertNotNullAndActive() {
        assertTrue(TabCompleteCommands.active("call"));
        assertFalse(texts(complete("/call el")).isEmpty());
    }

    @Test
    void theCommandWordItselfAndPlainChatAreLeftToTheGame() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertNull(complete("/ca"), "noch kein Leerzeichen: das Befehlswort bleibt dem Spiel");
        assertNull(complete("/call"), "auch das nicht");
        assertNull(complete("call el"), "ohne Schraegstrich ist es kein Befehl");
        assertNull(complete("hello world"));
        assertNull(complete(""));
        assertNull(TabCompleteCommands.complete(null, 3));
        assertNull(TabCompleteCommands.complete("/call el", 0), "Cursor ganz vorn");
    }

    @Test
    void whenTheModHasNoCandidateTheServersListStays() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertNull(complete("/call zzzzzz"), "kein Kontakt beginnt so: nicht mit einer leeren Liste die des Servers wegnehmen");
    }

    @Test
    void anUnknownCommandIsNoneOfOurs() {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertNull(complete("/give el"));
    }

    @Test
    void theSwitchesCarryOverFromTheSettings() {
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        c.warps = true;
        c.onlyBestFriends = true;
        c.stashCommand = true;
        TabCompleteRules.Switches s = TabCompleteCommands.switches();
        assertTrue(s.warps());
        assertTrue(s.onlyBestFriends());
        assertTrue(s.stashCommand());
        assertFalse(s.call());
        assertFalse(s.party());
        assertFalse(TabCompleteCommands.active("call"));
        assertTrue(TabCompleteCommands.active("warp"));
        assertTrue(TabCompleteCommands.active("viewstash"));
    }

    @Test
    void withThe263SwitchOffNoSwitchCountsAndNothingIsSuggested() {
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        c.call = true;
        c.warps = true;
        c.party = true;
        assertTrue(TabCompleteCommands.active("call"));
        assertFalse(texts(complete("/call el")).isEmpty());

        ModConfig.INSTANCE.update263.enabled = false;
        TabCompleteRules.Switches s = TabCompleteCommands.switches();
        assertFalse(s.call());
        assertFalse(s.warps());
        assertFalse(s.party());
        assertFalse(TabCompleteCommands.active("call"));
        assertFalse(TabCompleteCommands.active("warp"));
        assertNull(complete("/call el"), "und schlaegt nichts vor");
        assertTrue(c.call && c.warps && c.party, "die Schalter selbst bleiben, wie sie waren");

        ModConfig.INSTANCE.update263.enabled = true;
        assertTrue(TabCompleteCommands.active("call"), "wieder an: gleich wieder da");
    }

    // ---- zusammen mit Brigadier, so wie das Mixin es einhaengt

    /** Wie die Chat-Eingabe: Schraegstrich ueberspringen, parsen. Der Text im Parse behaelt den Schraegstrich */
    private static ParseResults<Object> parseLikeTheChat(CommandDispatcher<Object> d, String typed) {
        StringReader reader = new StringReader(typed);
        if (reader.canRead() && reader.peek() == '/') reader.skip();
        return d.parse(reader, null);
    }

    /** Wie Hypixels Liste (angenommen): jeder Befehl ein Wort mit einem Rest-Argument, dessen Vorschlag der Server liefert */
    private static CommandDispatcher<Object> serverLike() {
        CommandDispatcher<Object> d = new CommandDispatcher<>();
        for (String name : TabCompleteRules.COMMANDS) {
            d.register(com.mojang.brigadier.builder.LiteralArgumentBuilder.<Object>literal(name)
                    .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<Object, String>argument("args", StringArgumentType.greedyString())
                            .suggests((c, b) -> b.suggest("from-the-server").buildFuture())));
        }
        return d;
    }

    /** Was das Mixin tut: erst die Mod fragen, sonst Brigadier */
    private static List<String> asTheMixinDoes(CommandDispatcher<Object> d, String typed) throws Exception {
        ParseResults<Object> parse = parseLikeTheChat(d, typed);
        Suggestions own = TabCompleteCommands.complete(parse.getReader().getString(), typed.length());
        return texts(own != null ? own : d.getCompletionSuggestions(parse, typed.length()).get());
    }

    @Test
    void theParsedTextKeepsTheSlashWhichIsWhatTheMixinReads() {
        ParseResults<Object> parse = parseLikeTheChat(serverLike(), "/call el");
        assertEquals("/call el", parse.getReader().getString());
    }

    @Test
    void aCommandTheServerAlreadyKnowsStillGetsTheModsList() throws Exception {
        // Das war der Fehler bis 2.6.5: Ein Befehl, den der Server schon kennt, verdraengte den der Mod
        CommandDispatcher<Object> d = serverLike();
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertEquals(List.of("elizabeth", "elle"), asTheMixinDoes(d, "/call el"));
    }

    @Test
    void withTheSwitchOffTheServersListShowsUnchanged() throws Exception {
        CommandDispatcher<Object> d = serverLike();
        assertEquals(List.of("from-the-server"), asTheMixinDoes(d, "/call el"));
    }

    @Test
    void whenTheModHasNothingToSayTheServersListShowsUnchanged() throws Exception {
        CommandDispatcher<Object> d = serverLike();
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertEquals(List.of("from-the-server"), asTheMixinDoes(d, "/call zzzzzz"));
    }

    @Test
    void everyCommandOfTheFunctionAnswersWhenItsSwitchIsOn() {
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        c.call = true;
        c.warps = true;
        c.gfsSack = true;
        c.viewRecipe = true;
        c.showItem = true;
        c.stashCommand = true;
        c.vipVisits = true;
        c.party = true;
        c.guild = true;
        c.friends = true;
        c.islandPlayers = true;
        PartyRoster.INSTANCE.onLine("You'll be partying with: Plum", "Me");
        for (String name : TabCompleteRules.COMMANDS) {
            // Kein Befehl darf an einem Fehler scheitern, auch wenn seine Quellen leer sind
            TabCompleteCommands.complete("/" + name + " ", name.length() + 2);
            assertTrue(TabCompleteCommands.active(name), name);
        }
    }
}
