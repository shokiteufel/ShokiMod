package com.shokiteufel.shokimod.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chat (Teil 3) > Tab Complete: die Client-Befehle durch einen echten Brigadier-Dispatcher - ohne Spiel. Was hier nicht geprueft werden kann (ein laufendes
 * Minecraft, die Verbindung, Hypixels Befehlsliste), steht im Bericht.
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

    private static CommandDispatcher<FabricClientCommandSource> dispatcher() {
        CommandDispatcher<FabricClientCommandSource> d = new CommandDispatcher<>();
        for (String name : TabCompleteRules.COMMANDS) d.register(TabCompleteCommands.command(name));
        return d;
    }

    private static Suggestions suggestions(String input) throws Exception {
        CommandDispatcher<FabricClientCommandSource> d = dispatcher();
        ParseResults<FabricClientCommandSource> parse = d.parse(input, null);
        return d.getCompletionSuggestions(parse, input.length()).get();
    }

    private static List<String> texts(Suggestions s) {
        List<String> out = new ArrayList<>();
        for (Suggestion suggestion : s.getList()) out.add(suggestion.getText());
        return out;
    }

    @Test
    void callElCompletesToElizabethTheExampleOfTheUser() throws Exception {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        Suggestions s = suggestions("call el");
        // Seit 2.5.10 kennt die Liste alle Kontakte: "el" trifft Elizabeth und Elle
        assertEquals(List.of("elizabeth", "elle"), texts(s));
        assertEquals(5, s.getRange().getStart(), "der Vorschlag ersetzt 'el', nicht 'call el'");
        assertEquals(7, s.getRange().getEnd());
        assertEquals(List.of("elizabeth"), texts(suggestions("call eli")), "mit einem Buchstaben mehr nur noch Elizabeth");
    }

    @Test
    void callWithNothingTypedOffersEveryContact() throws Exception {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertEquals(AbiphoneContacts.NAMES.size(), suggestions("call ").getList().size());
    }

    @Test
    void anotherWordOfTheSameCommandSuggestsNothing() throws Exception {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        assertTrue(suggestions("call elizabeth ").isEmpty());
    }

    @Test
    void aLaterWordOfAPlayerCommandReplacesOnlyThatWord() throws Exception {
        ModConfig.INSTANCE.chatTab.tabComplete.party = true;
        PartyRoster.INSTANCE.onLine("You'll be partying with: Plum, Carl", "Me");
        Suggestions s = suggestions("p kick Pl");
        assertEquals(List.of("Plum"), texts(s));
        assertEquals(7, s.getRange().getStart(), "ab dem dritten Wort: 'p kick ' sind sieben Zeichen");
        assertEquals(9, s.getRange().getEnd());
    }

    @Test
    void withTheSwitchOffNothingIsSuggested() throws Exception {
        assertTrue(suggestions("call el").isEmpty());
        assertTrue(suggestions("warp ").isEmpty());
        assertTrue(suggestions("p ").isEmpty());
        // Ein anderer Schalter schaltet diesen Befehl nicht ein
        ModConfig.INSTANCE.chatTab.tabComplete.warps = true;
        assertTrue(suggestions("call el").isEmpty());
    }

    @Test
    void outsideSkyBlockNothingIsSuggested() throws Exception {
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        GameState.Server.gametype = "Unknown";
        assertTrue(suggestions("call el").isEmpty());
        assertFalse(TabCompleteCommands.active("call"));
        GameState.Server.gametype = "SKYBLOCK";
        assertTrue(TabCompleteCommands.active("call"));
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
    void aCommandTheModDoesNotRunDeclinesAsUnknownSoFabricLetsItGoToTheServer() throws Exception {
        CommandDispatcher<FabricClientCommandSource> d = dispatcher();
        // Aus: der Befehl lehnt ab - mit genau dem Fehler, den Fabric als "kein Client-Befehl" behandelt
        CommandSyntaxException typed = assertThrows(CommandSyntaxException.class, () -> d.execute("call elizabeth", null));
        assertSame(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), typed.getType());
        CommandSyntaxException bare = assertThrows(CommandSyntaxException.class, () -> d.execute("call", null));
        assertSame(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), bare.getType());

        // Ein anderer Schalter an, dieser aus: derselbe Weg
        ModConfig.INSTANCE.chatTab.tabComplete.warps = true;
        CommandSyntaxException otherSwitch = assertThrows(CommandSyntaxException.class, () -> d.execute("call elizabeth", null));
        assertSame(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), otherSwitch.getType());

        // Nicht in SkyBlock: derselbe Weg, auch mit dem Schalter an (die Verbindung wird erst danach gefragt, ein laufendes Spiel braucht es dafuer nicht)
        ModConfig.INSTANCE.chatTab.tabComplete.call = true;
        GameState.Server.gametype = "Unknown";
        CommandSyntaxException elsewhere = assertThrows(CommandSyntaxException.class, () -> d.execute("call elizabeth", null));
        assertSame(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(), elsewhere.getType());
    }

    @Test
    void withThe263SwitchOffNoSwitchCountsAndNothingIsSuggestedOrRun() throws Exception {
        ModConfig.TabCompleteCategory c = ModConfig.INSTANCE.chatTab.tabComplete;
        c.call = true;
        c.warps = true;
        c.party = true;
        assertTrue(TabCompleteCommands.active("call"));
        assertFalse(texts(suggestions("call el")).isEmpty());

        ModConfig.INSTANCE.update263.enabled = false;
        TabCompleteRules.Switches s = TabCompleteCommands.switches();
        assertFalse(s.call());
        assertFalse(s.warps());
        assertFalse(s.party());
        assertFalse(TabCompleteCommands.active("call"), "die Mod fuehrt den Befehl nicht aus: Fabric schickt ihn wie sonst an den Server");
        assertFalse(TabCompleteCommands.active("warp"));
        assertTrue(suggestions("call el").isEmpty(), "und schlaegt nichts vor");
        assertTrue(c.call && c.warps && c.party, "die Schalter selbst bleiben, wie sie waren");

        ModConfig.INSTANCE.update263.enabled = true;
        assertTrue(TabCompleteCommands.active("call"), "wieder an: gleich wieder da");
    }

    @Test
    void everyCommandIsRegisteredWithItsRestOfTheLine() {
        CommandDispatcher<FabricClientCommandSource> d = dispatcher();
        for (String name : TabCompleteRules.COMMANDS) {
            ParseResults<FabricClientCommandSource> parse = d.parse(name + " some words", null);
            assertFalse(parse.getReader().canRead(), name + ": der ganze Rest wird gelesen");
            assertTrue(parse.getExceptions().isEmpty(), name);
        }
    }
}
