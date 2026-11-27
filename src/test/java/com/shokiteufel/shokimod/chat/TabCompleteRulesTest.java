package com.shokiteufel.shokimod.chat;

import com.shokiteufel.shokimod.chat.TabCompleteRules.Result;
import com.shokiteufel.shokimod.chat.TabCompleteRules.Sources;
import com.shokiteufel.shokimod.chat.TabCompleteRules.Switches;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3) > Tab Complete: was beim Tippen eines Befehls vorgeschlagen wird. */
class TabCompleteRulesTest {

    /** Namen, die der Test festlegt */
    private static final class Fake implements Sources {
        List<String> island = List.of();
        String self = "Me";
        List<String> party = List.of();
        List<String> guild = List.of();
        List<String> friends = List.of();
        List<String> best = List.of();
        List<String> warps = List.of();
        List<String> recipes = List.of();
        List<String> items = List.of();

        @Override public List<String> islandPlayers() { return island; }
        @Override public String self() { return self; }
        @Override public List<String> partyMembers() { return party; }
        @Override public List<String> guildMembers() { return guild; }
        @Override public List<String> friends() { return friends; }
        @Override public List<String> bestFriends() { return best; }
        @Override public List<String> warps() { return warps; }
        @Override public List<String> abiphoneContacts() { return AbiphoneContacts.NAMES; }
        @Override public List<String> craftableIds() { return recipes; }
        @Override public List<String> itemIds() { return items; }
    }

    private static Switches none() {
        return new Switches(false, false, false, false, false, false, false, false, false, false, false);
    }

    private static Switches only(boolean warps, boolean island, boolean friends, boolean onlyBest, boolean party, boolean guild, boolean gfs, boolean recipe,
                                 boolean show, boolean call, boolean stash) {
        return new Switches(warps, island, friends, onlyBest, party, guild, gfs, recipe, show, call, stash);
    }

    private static Switches players(boolean island, boolean friends, boolean party, boolean guild) {
        return only(false, island, friends, false, party, guild, false, false, false, false, false);
    }

    private static List<String> candidates(Result r) {
        assertNotNull(r);
        return r.candidates();
    }

    // ---- die Befehle mit einem Wort ----

    @Test
    void callCompletesTheContactNamesTheExampleOfTheUser() {
        Switches s = only(false, false, false, false, false, false, false, false, false, true, false);
        Result r = TabCompleteRules.suggest("call", "el", s, new Fake());
        assertEquals(0, r.wordStart());
        // Seit 2.5.10 kennt die Liste alle Kontakte: "el" trifft Elizabeth und Elle, "eli" nur Elizabeth
        assertEquals(List.of("elizabeth", "elle"), r.candidates());
        assertEquals(List.of("elizabeth"), candidates(TabCompleteRules.suggest("call", "eli", s, new Fake())));
        assertEquals(AbiphoneContacts.NAMES.size(), candidates(TabCompleteRules.suggest("call", "", s, new Fake())).size());
        assertEquals(List.of("elizabeth", "elle"), candidates(TabCompleteRules.suggest("CALL", "EL", s, new Fake())), "Gross und Klein egal");
    }

    @Test
    void aSecondWordGetsNothingForTheSingleWordCommands() {
        Switches s = only(true, false, false, false, false, false, true, true, true, true, true);
        Result r = TabCompleteRules.suggest("call", "elizabeth x", s, new Fake());
        assertEquals(10, r.wordStart(), "das zweite Wort beginnt hinter 'elizabeth '");
        assertTrue(r.candidates().isEmpty());
        assertTrue(candidates(TabCompleteRules.suggest("warp", "hub x", s, new Fake())).isEmpty());
    }

    @Test
    void warpCompletesTheWarpNames() {
        Fake f = new Fake();
        f.warps = List.of("hub", "wizard", "wizard_tower", "crypts");
        Switches s = only(true, false, false, false, false, false, false, false, false, false, false);
        assertEquals(List.of("wizard", "wizard_tower"), candidates(TabCompleteRules.suggest("warp", "wiz", s, f)));
        assertEquals(List.of("hub", "wizard", "wizard_tower", "crypts"), candidates(TabCompleteRules.suggest("warp", "", s, f)));
    }

    @Test
    void viewstashOffersItemAndMaterial() {
        Switches s = only(false, false, false, false, false, false, false, false, false, false, true);
        assertEquals(List.of("item", "material"), candidates(TabCompleteRules.suggest("viewstash", "", s, new Fake())));
        assertEquals(List.of("material"), candidates(TabCompleteRules.suggest("viewstash", "ma", s, new Fake())));
    }

    @Test
    void showOffersTheSlotsNotTheItems() {
        Switches s = only(false, false, false, false, false, false, false, false, true, false, false);
        assertEquals(List.of("helmet"), candidates(TabCompleteRules.suggest("show", "he", s, new Fake())));
        assertEquals(TabCompleteRules.SHOW_SLOTS, candidates(TabCompleteRules.suggest("showitem", "", s, new Fake())));
        assertEquals(List.of("pet"), candidates(TabCompleteRules.suggest("showoff", "pe", s, new Fake())));
    }

    @Test
    void gfsContainsTheTextAndPutsTheOnesThatStartWithItFirst() {
        Fake f = new Fake();
        f.items = List.of("COAL", "ENCHANTED_COAL", "COAL_BLOCK", "OIL_BARREL");
        Switches s = only(false, false, false, false, false, false, true, false, false, false, false);
        assertEquals(List.of("COAL", "COAL_BLOCK", "ENCHANTED_COAL"), candidates(TabCompleteRules.suggest("gfs", "coal", s, f)));
        assertEquals(List.of("OIL_BARREL"), candidates(TabCompleteRules.suggest("getfromsacks", "barrel", s, f)));
        assertTrue(candidates(TabCompleteRules.suggest("gfs", "OIL_BARREL 5", s, f)).isEmpty(), "die Menge bekommt keinen Vorschlag");
    }

    @Test
    void gfsStopsAtThreeHundredSuggestions() {
        Fake f = new Fake();
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 1000; i++) many.add("ITEM_" + i);
        f.items = many;
        Switches s = only(false, false, false, false, false, false, true, false, false, false, false);
        assertEquals(TabCompleteRules.MAX_SUGGESTIONS, candidates(TabCompleteRules.suggest("gfs", "item", s, f)).size());
    }

    @Test
    void viewrecipeNamesTheItemsWithARecipe() {
        Fake f = new Fake();
        f.recipes = List.of("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "HYPERION");
        Switches s = only(false, false, false, false, false, false, false, true, false, false, false);
        assertEquals(List.of("ASPECT_OF_THE_END"), candidates(TabCompleteRules.suggest("viewrecipe", "aspect_of_the_e", s, f)));
        assertEquals(List.of("HYPERION"), candidates(TabCompleteRules.suggest("viewrecipe", "hyp", s, f)));
    }

    // ---- /p, /f, /g und die anderen Spieler-Befehle ----

    @Test
    void partyWithoutAPartyOffersTheInvitesAndThePlayers() {
        Fake f = new Fake();
        f.island = List.of("Alice", "Bob");
        List<String> all = candidates(TabCompleteRules.suggest("p", "", players(true, false, false, false), f));
        assertTrue(all.containsAll(List.of("accept", "invite", "Alice", "Bob")), all.toString());
        assertFalse(all.contains("kick"), "ohne Party gibt es nichts zu kicken");
        assertFalse(all.contains("list"));
    }

    @Test
    void partyWithMembersOffersTheCommandsAgainstThem() {
        Fake f = new Fake();
        f.island = List.of("Alice", "Bob");
        f.party = List.of("Carl", "Plum");
        Switches s = players(true, false, true, false);
        List<String> first = candidates(TabCompleteRules.suggest("p", "", s, f));
        assertTrue(first.containsAll(List.of("accept", "invite", "kick", "demote", "promote", "transfer", "list", "leave", "disband", "Alice", "Bob")), first.toString());
        // Wer schon in der Party ist, wird nicht noch einmal zum Einladen vorgeschlagen
        assertFalse(first.contains("Carl"));
        assertEquals(List.of("kick", "kickoffline"), candidates(TabCompleteRules.suggest("party", "ki", s, f)));

        // /p kick Pl: die Mitglieder - bei SkyHanni findet das nie etwas, weil die Gruppe "nur in einer Party" den Weg zu "kick" versperrt
        Result kick = TabCompleteRules.suggest("p", "kick Pl", s, f);
        assertEquals(5, kick.wordStart());
        assertEquals(List.of("Plum"), kick.candidates());
        assertEquals(List.of("Carl", "Plum"), candidates(TabCompleteRules.suggest("p", "promote ", s, f)));
    }

    @Test
    void partyInviteOffersEveryoneWhoIsNotInThePartyAlready() {
        Fake f = new Fake();
        f.island = List.of("Alice", "Alex");
        f.party = List.of("Alex");
        f.friends = List.of("Alvin");
        Switches s = players(true, true, true, false);
        assertEquals(List.of("Alvin", "Alice"), candidates(TabCompleteRules.suggest("p", "invite Al", s, f)));
    }

    @Test
    void friendOffersItsCommandsAndTheNamesBehindThem() {
        Fake f = new Fake();
        f.friends = List.of("Fred", "Frida");
        f.best = List.of("Frida");
        f.island = List.of("Alice");
        Switches s = players(true, false, false, false);
        List<String> first = candidates(TabCompleteRules.suggest("friend", "", s, f));
        assertTrue(first.containsAll(List.of("accept", "add", "deny", "best", "remove", "nickname", "list", "help", "notifications", "removeall", "requests")), first.toString());
        assertEquals(List.of("best"), candidates(TabCompleteRules.suggest("f", "list b", s, f)));
        // Wen man entfernt, sind immer die Freunde: auch wenn "Friends" aus ist
        assertEquals(List.of("Fred", "Frida"), candidates(TabCompleteRules.suggest("f", "remove F", s, f)));
        assertEquals(List.of("Fred", "Frida"), candidates(TabCompleteRules.suggest("f", "best ", s, f)));
        // Wen man hinzufuegt, sind die anderen: nicht die, die schon Freunde sind
        assertEquals(List.of("Alice"), candidates(TabCompleteRules.suggest("f", "accept Al", s, f)));
    }

    @Test
    void guildOffersItsCommandsAndTheMembers() {
        Fake f = new Fake();
        f.guild = List.of("Gus", "Gwen");
        f.island = List.of("Alice");
        Switches s = players(true, false, false, true);
        List<String> first = candidates(TabCompleteRules.suggest("g", "", s, f));
        assertTrue(first.containsAll(List.of("invite", "kick", "mute", "member", "online", "discord", "disband", "accept")), first.toString());
        assertEquals(List.of("Gus", "Gwen"), candidates(TabCompleteRules.suggest("g", "kick G", s, f)));
        assertEquals(List.of("Gus", "Gwen", "everyone"), candidates(TabCompleteRules.suggest("guild", "mute ", s, f)));
        assertEquals(List.of("Alice"), candidates(TabCompleteRules.suggest("g", "invite A", s, f)), "wer eingeladen wird, ist noch nicht in der Gilde");
    }

    @Test
    void whisperVisitInviteAhAndPvTakeAllThePlayers() {
        Fake f = new Fake();
        f.island = List.of("Alice");
        f.party = List.of("Alba");
        Switches s = players(true, false, true, false);
        for (String command : List.of("w", "msg", "tell", "boop", "boo", "visit", "invite", "ah", "pv")) {
            assertEquals(List.of("Alice", "Alba"), candidates(TabCompleteRules.suggest(command, "Al", s, f)), command);
        }
    }

    @Test
    void pvOffersYourOwnNameToo() {
        Fake f = new Fake();
        f.island = List.of("Mia");
        Switches s = players(true, false, false, false);
        assertEquals(List.of("Mia", "Me"), candidates(TabCompleteRules.suggest("pv", "M", s, f)));
    }

    @Test
    void tradeNamesTheIslandPlayersWhateverTheirSwitch() {
        Fake f = new Fake();
        f.island = List.of("Zed");
        assertEquals(List.of("Zed"), candidates(TabCompleteRules.suggest("trade", "Z", players(false, true, false, false), f)));
    }

    @Test
    void onlyBestFriendsLimitsTheFriendsInThePlayerList() {
        Fake f = new Fake();
        f.friends = List.of("Fred", "Frida");
        f.best = List.of("Frida");
        Switches s = only(false, false, true, true, false, false, false, false, false, false, false);
        assertEquals(List.of("Frida"), candidates(TabCompleteRules.suggest("w", "F", s, f)));
        Switches all = only(false, false, true, false, false, false, false, false, false, false, false);
        assertEquals(List.of("Fred", "Frida"), candidates(TabCompleteRules.suggest("w", "F", all, f)));
    }

    @Test
    void nameMatchingIgnoresTheCaseAndKeepsTheCaseOfTheName() {
        Fake f = new Fake();
        f.island = List.of("AliceWonder");
        assertEquals(List.of("AliceWonder"), candidates(TabCompleteRules.suggest("msg", "aLI", players(true, false, false, false), f)));
    }

    // ---- wann nichts passiert ----

    @Test
    void withoutASwitchTheCommandGetsNothing() {
        Fake f = new Fake();
        f.island = List.of("Alice");
        assertNull(TabCompleteRules.suggest("call", "el", none(), f));
        assertNull(TabCompleteRules.suggest("warp", "", none(), f));
        assertNull(TabCompleteRules.suggest("p", "", none(), f));
        // Nur "Only Best Friends" ist keine Quelle
        assertNull(TabCompleteRules.suggest("w", "", only(false, false, false, true, false, false, false, false, false, false, false), f));
        // Ein Schalter fuer etwas anderes schaltet diesen Befehl nicht ein
        assertNull(TabCompleteRules.suggest("p", "", only(true, false, false, false, false, false, false, false, false, true, true), f));
        assertNull(TabCompleteRules.suggest("call", "el", only(true, true, true, false, true, true, true, true, true, false, true), f));
    }

    @Test
    void anUnknownCommandOrMissingTextGetsNothing() {
        Switches all = only(true, true, true, false, true, true, true, true, true, true, true);
        assertNull(TabCompleteRules.suggest("shoki", "", all, new Fake()));
        assertNull(TabCompleteRules.suggest(null, "", all, new Fake()));
        assertNull(TabCompleteRules.suggest("call", null, all, new Fake()));
        assertFalse(TabCompleteRules.handles(null, all));
        assertFalse(TabCompleteRules.handles("call", null));
    }

    @Test
    void handlesFollowsTheSwitchOfTheCommand() {
        Switches s = only(true, false, false, false, false, false, false, false, false, false, false);
        assertTrue(TabCompleteRules.handles("warp", s));
        assertFalse(TabCompleteRules.handles("call", s));
        assertFalse(TabCompleteRules.handles("p", s));
        assertTrue(TabCompleteRules.handles("gfs", only(false, false, false, false, false, false, true, false, false, false, false)));
        assertTrue(TabCompleteRules.handles("getfromsacks", only(false, false, false, false, false, false, true, false, false, false, false)));
        assertTrue(TabCompleteRules.handles("showoff", only(false, false, false, false, false, false, false, false, true, false, false)));
        assertTrue(TabCompleteRules.handles("viewstash", only(false, false, false, false, false, false, false, false, false, false, true)));
        assertTrue(TabCompleteRules.handles("trade", players(false, false, false, true)));
    }

    @Test
    void everyKnownCommandIsDecidedByHandlesNotLeftOut() {
        Switches all = only(true, true, true, false, true, true, true, true, true, true, true);
        for (String command : TabCompleteRules.COMMANDS) {
            assertTrue(TabCompleteRules.handles(command, all), command);
            assertNotNull(TabCompleteRules.suggest(command, "", all, new Fake()), command);
        }
        assertTrue(TabCompleteRules.COMMANDS.containsAll(List.of("call", "warp", "viewstash", "gfs", "getfromsacks", "viewrecipe", "show", "p", "party", "f", "friend", "g",
                "guild", "w", "msg", "visit", "invite", "ah", "pv", "trade")));
    }

    @Test
    void theSwitchesKnowWhetherAnyIsOn() {
        assertFalse(none().any());
        assertFalse(only(false, false, false, true, false, false, false, false, false, false, false).any(), "Only Best Friends allein tut nichts");
        assertTrue(only(false, false, false, false, false, false, false, false, false, false, true).any());
        assertTrue(players(false, false, true, false).anyPlayerSource());
        assertFalse(only(true, false, false, false, false, false, false, false, false, false, false).anyPlayerSource());
    }
}
