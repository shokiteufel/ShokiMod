package com.shokiteufel.shokimod.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chat (Teil 3): wer in der Party ist, aus den Zeilen von Hypixel. Die Beispiele sind SkyHannis REGEX-TEST-Zeilen ohne Farbcodes. */
class PartyRosterTest {

    private static final String SELF = "Me";

    private static PartyRoster with(String... lines) {
        PartyRoster r = new PartyRoster();
        for (String line : lines) r.onLine(line, SELF);
        return r;
    }

    @Test
    void youJoinedAPartyMakesItsLeaderAMember() {
        PartyRoster r = with("You have joined [MVP+] Throwpo's party!");
        assertEquals(List.of("Throwpo"), r.members());
        assertEquals("Throwpo", r.leader());
        assertTrue(r.inParty());
    }

    @Test
    void anOtherJoiningAnEmptyPartyMakesYouTheLeader() {
        PartyRoster r = with("[MVP+] Throwpo joined the party.");
        assertEquals(List.of("Throwpo"), r.members());
        assertEquals(SELF, r.leader());
        r.onLine("[VIP] Second joined the party.", SELF);
        assertEquals(List.of("Throwpo", "Second"), r.members());
        assertEquals(SELF, r.leader());
    }

    @Test
    void youllBePartyingWithNamesTheOthers() {
        PartyRoster r = with("You'll be partying with: [VIP] FungalBeatle550");
        assertEquals(List.of("FungalBeatle550"), r.members());
        r.onLine("You'll be partying with: [MVP+] Alpha, Beta, [VIP+] Me", SELF);
        assertEquals(List.of("FungalBeatle550", "Alpha", "Beta"), r.members(), "der eigene Name steht nie in der Liste");
    }

    @Test
    void leavingRemovingAndDisconnectingTakeAMemberOut() {
        PartyRoster r = with("You'll be partying with: 246sweets, riblets, Throwpo, Other");
        r.onLine("246sweets has left the party.", SELF);
        assertEquals(List.of("riblets", "Throwpo", "Other"), r.members());
        r.onLine("riblets has been removed from the party.", SELF);
        assertEquals(List.of("Throwpo", "Other"), r.members());
        r.onLine("Kicked [MVP+] Throwpo because they were offline.", SELF);
        assertEquals(List.of("Other"), r.members());
        r.onLine("[MVP+] Other was removed from your party because they disconnected.", SELF);
        assertTrue(r.members().isEmpty());
        assertFalse(r.inParty());
    }

    @Test
    void aTransferChangesTheLeader() {
        PartyRoster r = with("You'll be partying with: CalMWolfs, Throwpo");
        r.onLine("The party was transferred to [MVP+] CalMWolfs because [MVP+] Throwpo left", SELF);
        assertEquals("CalMWolfs", r.leader());
        assertEquals(List.of("CalMWolfs"), r.members());
        r.onLine("The party was transferred to [MVP+] Throwpo by [MVP+] CalMWolfs", SELF);
        assertEquals("Throwpo", r.leader());
        assertEquals(List.of("CalMWolfs"), r.members(), "eine Uebergabe aendert die Mitglieder nicht");
    }

    @Test
    void theEndOfThePartyClearsTheList() {
        for (String end : List.of("[MVP+] Throwpo has disbanded the party!", "You have been kicked from the party by [MVP+] Throwpo ", "You left the party.",
                "The party was disbanded because all invites expired and the party was empty.", "You are not currently in a party.", "You are not in a party.",
                "The party was disbanded because the party leader disconnected.")) {
            PartyRoster r = with("You'll be partying with: A1, B2");
            r.onLine(end, SELF);
            assertFalse(r.inParty(), end);
            assertNull(r.leader(), end);
        }
    }

    @Test
    void thePartyListReplacesTheList() {
        PartyRoster r = with("You'll be partying with: Old1");
        r.onLine("Party Members (3)", SELF);
        assertTrue(r.members().isEmpty());
        r.onLine("Party Leader: [MVP+] CalMWolfs ●", SELF);
        r.onLine("Party Moderators: [VIP] mod1 ● [MVP] mod2 ●", SELF);
        r.onLine("Party Members: [MVP+] Throwpo ● Me ●", SELF);
        assertEquals(List.of("CalMWolfs", "mod1", "mod2", "Throwpo"), r.members());
        assertEquals("CalMWolfs", r.leader());
    }

    @Test
    void severalLinesInOneTextAreReadOneByOne() {
        PartyRoster r = new PartyRoster();
        r.onLine("-----------\nParty Members (2)\nParty Leader: [MVP+] X1 ●\nParty Members: Y2 ●\n-----------", SELF);
        assertEquals(List.of("X1", "Y2"), r.members());
    }

    @Test
    void partyChatAddsTheWriter() {
        PartyRoster r = with("Party > [MVP+] Bob: hi there");
        assertEquals(List.of("Bob"), r.members());
        r.onLine("Party > Me: hello", SELF);
        assertEquals(List.of("Bob"), r.members());
    }

    @Test
    void thePartyFinderAddsTheOneWhoJoined() {
        PartyRoster r = with("Party Finder > GhostsTM joined the dungeon group! (Archer Level 9)");
        assertEquals(List.of("GhostsTM"), r.members());
        r.onLine("Party Finder > Other joined the group! (Combat Level 50)", SELF);
        assertEquals(List.of("GhostsTM", "Other"), r.members());
    }

    @Test
    void aPlayerCannotFakeAMessageInTheirChatLine() {
        PartyRoster r = with("You'll be partying with: A1, B2");
        r.onLine("[MVP+] Foo: B2 has left the party.", SELF);
        r.onLine("[MVP+] Foo: [MVP+] Foo has disbanded the party!", SELF);
        assertEquals(List.of("A1", "B2"), r.members());
    }

    @Test
    void aNameIsNeverAddedTwiceAndCapitalsDoNotCreateANewOne() {
        PartyRoster r = with("[MVP+] Throwpo joined the party.", "[MVP+] throwpo joined the party.", "Party > Throwpo: hi");
        assertEquals(1, r.members().size());
    }

    @Test
    void clearAndTheCheapLook() {
        PartyRoster r = with("You'll be partying with: A1");
        r.clear();
        assertFalse(r.inParty());
        assertTrue(PartyRoster.mightConcern("You left the party."));
        assertTrue(PartyRoster.mightConcern("Kicked Foo because they were offline."));
        assertTrue(PartyRoster.mightConcern("Party Finder > Foo joined the group! (Combat Level 50)"));
        assertFalse(PartyRoster.mightConcern("You sold Wheat for 5 Coins!"));
        assertFalse(PartyRoster.mightConcern(null));
    }
}
