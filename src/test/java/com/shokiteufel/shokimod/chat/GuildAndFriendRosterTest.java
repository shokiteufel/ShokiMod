package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3): die Gilde aus {@code /guild list}, die Freunde aus den Meldungen und der Liste, und der Name aus einem Stueck Chat. */
class GuildAndFriendRosterTest {

    private static final String LINE = "-----------------------------------------------------";

    // ---- PlayerNames ----

    @Test
    void theNameLosesRankBracketsAndMarks() {
        assertEquals("Throwpo", PlayerNames.clean("[MVP+] Throwpo"));
        assertEquals("Throwpo", PlayerNames.clean("Throwpo"));
        assertEquals("Name", PlayerNames.clean("[VIP] Name ●"));
        assertEquals("Name", PlayerNames.clean("  Name  "));
        assertEquals("", PlayerNames.clean(" ● "));
        assertEquals("", PlayerNames.clean(null));
        assertEquals("a_b1", PlayerNames.clean("[MVP++] a_b1"));
        assertTrue(PlayerNames.isName("Throwpo"));
        assertFalse(PlayerNames.isName("has spaces"));
        assertFalse(PlayerNames.isName("waytoolongtobeaname1"));
        assertFalse(PlayerNames.isName(""));
        assertFalse(PlayerNames.isName(null));
    }

    // ---- Gilde ----

    @Test
    void theGuildListIsTakenWhenTheSeparatorClosesIt() {
        GuildRoster g = new GuildRoster();
        boolean[] changed = new boolean[1];
        for (String line : List.of(LINE, "Guild Name: Foo", "-- Guild Master --", "[MVP+] Alice ●", "-- Member --", "[VIP] Bob ● Carl ●",
                "Total Members: 3")) {
            changed[0] |= g.onLine(line);
        }
        assertFalse(changed[0], "solange die Linie fehlt, gilt die Liste nicht");
        assertTrue(g.members().isEmpty());
        assertTrue(g.onLine(LINE), "die Linie schliesst die Liste");
        assertEquals(List.of("Alice", "Bob", "Carl"), g.members());
    }

    @Test
    void anOnlineListEndingWithOfflineMembersIsDiscarded() {
        GuildRoster g = new GuildRoster();
        g.load(List.of("Kept1"));
        for (String line : List.of("Guild Name: Foo", "[MVP+] Alice ●", "Offline Members: 5", LINE)) g.onLine(line);
        assertEquals(List.of("Kept1"), g.members(), "unvollstaendig: die alte Liste bleibt");
    }

    @Test
    void theGuildLinesOutsideTheListAreIgnoredAndDoublesDropOut() {
        GuildRoster g = new GuildRoster();
        g.onLine("[MVP+] Stranger ●");
        for (String line : List.of("Guild Name: Foo", "[MVP+] Alice ●", "Alice ●", LINE)) g.onLine(line);
        assertEquals(List.of("Alice"), g.members());
        assertFalse(g.mightConcern("You sold Wheat"));
        assertTrue(g.mightConcern("Guild Name: Foo"));
    }

    @Test
    void severalGuildLinesInOneTextAreReadOneByOne() {
        GuildRoster g = new GuildRoster();
        assertTrue(g.onLine(LINE + "\nGuild Name: Foo\n[MVP+] Alice ● [VIP] Bob ●\n" + LINE));
        assertEquals(List.of("Alice", "Bob"), g.members());
    }

    // ---- Freunde ----

    @Test
    void addingAndRemovingFriendsFromTheMessages() {
        FriendRoster f = new FriendRoster();
        assertTrue(f.onLine("You are now friends with [MVP+] Throwpo"));
        assertEquals(List.of("Throwpo"), f.friends());
        assertTrue(f.onLine("[MVP+] Throwpo is now a best friend!"));
        assertEquals(List.of("Throwpo"), f.bestFriends());
        assertTrue(f.onLine("[MVP+] Throwpo is no longer a best friend!"));
        assertTrue(f.bestFriends().isEmpty());
        assertTrue(f.onLine("You removed [MVP+] Throwpo from your friends list!"));
        assertTrue(f.friends().isEmpty());
        assertFalse(f.onLine("You removed [MVP+] Throwpo from your friends list!"), "ein zweites Mal aendert nichts");
    }

    @Test
    void aBestFriendIsAlsoAFriend() {
        FriendRoster f = new FriendRoster();
        f.onLine("[MVP+] hannibal2 is now a best friend!");
        assertEquals(List.of("hannibal2"), f.friends());
        assertEquals(List.of("hannibal2"), f.bestFriends());
    }

    @Test
    void aPlayerCannotFakeAFriendMessageInTheirChatLine() {
        FriendRoster f = new FriendRoster();
        assertFalse(f.onLine("[MVP+] Foo: You are now friends with Bar"));
        assertFalse(f.onLine("[MVP+] Foo: Bar is now a best friend!"));
        assertFalse(f.onLine("[MVP+] Foo: You removed Bar from your friends list!"));
        assertTrue(f.friends().isEmpty());
    }

    @Test
    void theNameComesFromTheHoverOfAListEntry() {
        assertEquals("Throwpo", FriendRoster.nameFromHover("Click here to view [MVP+] Throwpo's profile"));
        assertEquals("Throwpo", FriendRoster.nameFromHover("[MVP+] Throwpo\nSome info\nClick here to view Throwpo's profile"));
        assertNull(FriendRoster.nameFromHover("Something else"));
        assertNull(FriendRoster.nameFromHover(null));
    }

    @Test
    void onlyAPageOfTheFriendListCountsAsAList() {
        assertTrue(FriendRoster.isFriendListBlock("-----------\nFriends (Page 1 of 4)\n \n[MVP+] Foo is in Skyblock"));
        assertTrue(FriendRoster.isFriendListBlock("Friends"));
        assertFalse(FriendRoster.isFriendListBlock("[MVP+] Foo: Friends (Page 1 of 4)"), "der Chat eines Spielers ist keine Liste");
        assertFalse(FriendRoster.isFriendListBlock("Foo: Friends"));
        assertFalse(FriendRoster.isFriendListBlock("My Friends are great"));
        assertFalse(FriendRoster.isFriendListBlock(null));
    }

    @Test
    void addFriendWithTheBestFlagSetsAndClearsIt() {
        FriendRoster f = new FriendRoster();
        assertTrue(f.addFriend("Fred", true));
        assertEquals(List.of("Fred"), f.bestFriends());
        assertFalse(f.addFriend("Fred", true), "keine Aenderung");
        assertTrue(f.addFriend("Fred", false));
        assertTrue(f.bestFriends().isEmpty());
        assertEquals(List.of("Fred"), f.friends());
        assertFalse(f.addFriend("not a name", false));
    }

    @Test
    void theSavedListsAreLoadedAndOnlyNamesSurvive() {
        FriendRoster f = new FriendRoster();
        f.load(List.of("Fred", "bad name", "Frida"), List.of("Frida", "Bert", "also bad"));
        assertEquals(List.of("Fred", "Frida", "Bert"), f.friends(), "ein bester Freund ist auch ein Freund");
        assertEquals(List.of("Frida", "Bert"), f.bestFriends());
        f.load(null, null);
        assertTrue(f.friends().isEmpty());
    }

    @Test
    void theCheapLook() {
        assertTrue(FriendRoster.mightConcern("You are now friends with Foo"));
        assertTrue(FriendRoster.mightConcern("Friends (Page 1 of 2)"));
        assertFalse(FriendRoster.mightConcern("You sold Wheat"));
    }
}
