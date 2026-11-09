package com.shokiteufel.shokimod.paket.zustand;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamZustandTest {

    @Test
    void showsPrefixNameAndSuffixWithoutColourCodes() {
        TeamZustand teams = new TeamZustand();

        teams.anlegen("t1", "§b[MVP§f+§b] ", " §3[DYES]", List.of("Spieler"));

        assertEquals("[MVP+] Spieler [DYES]", teams.anzeige("Spieler"));
        assertEquals("t1", teams.teamName("Spieler"));
    }

    @Test
    void anInvisibleOwnerNameLeavesOnlyPrefixAndSuffix() {
        TeamZustand teams = new TeamZustand();

        teams.anlegen("zeile", "Purse: ", "37,824,435", List.of("§x"));

        assertEquals("Purse: 37,824,435", teams.anzeige("§x"));
    }

    @Test
    void aMemberWithoutATeamShowsJustItsName() {
        assertEquals("Einzeln", new TeamZustand().anzeige("Einzeln"));
        assertEquals("", new TeamZustand().teamName("Einzeln"));
    }

    @Test
    void changingATeamReportsItsMembersAndChangesTheirText() {
        TeamZustand teams = new TeamZustand();
        teams.anlegen("t1", "alt ", "", List.of("a", "b"));

        Set<String> betroffen = teams.aendern("t1", "neu ", "!");

        assertEquals(Set.of("a", "b"), betroffen);
        assertEquals("neu a!", teams.anzeige("a"));
    }

    @Test
    void aMemberBelongsToOneTeamOnlyAndMovesWhenAddedToAnother() {
        TeamZustand teams = new TeamZustand();
        teams.anlegen("t1", "1 ", "", List.of("a"));
        teams.anlegen("t2", "2 ", "", List.of());

        teams.hinzu("t2", List.of("a"));

        assertEquals("t2", teams.teamName("a"));
        assertEquals("2 a", teams.anzeige("a"));
        // Aus dem alten Team ist es weg: Entfernen des alten Teams trifft es nicht mehr
        assertEquals(Set.of(), teams.entfernen("t1"));
        assertEquals("t2", teams.teamName("a"));
    }

    @Test
    void leavingATeamOnlyAffectsActualMembers() {
        TeamZustand teams = new TeamZustand();
        teams.anlegen("t1", "", "", List.of("a"));

        assertEquals(Set.of("a"), teams.weg("t1", List.of("a", "fremd")));
        assertEquals("", teams.teamName("a"));
    }

    @Test
    void removingATeamFreesItsMembers() {
        TeamZustand teams = new TeamZustand();
        teams.anlegen("t1", "x ", "", List.of("a", "b"));

        Set<String> betroffen = teams.entfernen("t1");

        assertEquals(Set.of("a", "b"), betroffen);
        assertEquals("a", teams.anzeige("a"));
        assertEquals(0, teams.anzahl());
    }

    @Test
    void recreatingATeamReplacesTheOldOne() {
        TeamZustand teams = new TeamZustand();
        teams.anlegen("t1", "alt ", "", List.of("a"));

        Set<String> betroffen = teams.anlegen("t1", "neu ", "", List.of("b"));

        assertTrue(betroffen.containsAll(Set.of("a", "b")));
        assertEquals("", teams.teamName("a"));
        assertEquals("neu b", teams.anzeige("b"));
    }

    @Test
    void unknownTeamsAreIgnored() {
        TeamZustand teams = new TeamZustand();

        assertEquals(Set.of(), teams.aendern("nein", "x", "y"));
        assertEquals(Set.of(), teams.hinzu("nein", List.of("a")));
        assertEquals(Set.of(), teams.weg("nein", List.of("a")));
        assertEquals(Set.of(), teams.entfernen("nein"));
    }
}
