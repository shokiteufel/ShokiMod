package com.shokiteufel.shokimod.paket.zustand;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Prueft die Seitenleiste mit dem Aufbau, den Hypixel SkyBlock im Mitschnitt liefert: unsichtbare Namen, Text in Teams. */
class SeitenleisteZustandTest {
    private TeamZustand teams;
    private SeitenleisteZustand seite;
    private List<SeitenleisteZustand.Aenderung> aenderungen;

    @BeforeEach
    void aufbauen() {
        teams = new TeamZustand();
        seite = new SeitenleisteZustand(teams);
        aenderungen = new ArrayList<>();
        seite.hoeren(aenderungen::add);
    }

    /** Eine Zeile wie bei Hypixel: Team mit Text, der Name des Eintrags ist unsichtbar, der Punktestand ordnet sie. */
    private void zeile(String eintrag, int punkte, String praefix, String suffix) {
        teams.anlegen("zeile" + punkte, praefix, suffix, List.of(eintrag));
        seite.punkte("SBScoreboard", eintrag, punkte, null);
    }

    private void skyblock() {
        seite.ziel("SBScoreboard", "§e§lSKYBLOCK", 0);
        seite.anzeigeZiel("SBScoreboard");
        zeile("§a", 3, "§7Purse: §6", "37,824,435");
        zeile("§b", 2, "§7Bits: §b", "6,100");
        zeile("§c", 1, "§ewww.hypixel.net", "");
    }

    @Test
    void buildsTheVisibleLinesFromScoresAndTeamsAndOrdersThemByScoreDescending() {
        skyblock();

        assertEquals("SKYBLOCK", seite.titel());
        assertEquals(List.of("Purse: 37,824,435", "Bits: 6,100", "www.hypixel.net"), seite.zeilen());
    }

    @Test
    void aTeamChangeReportsExactlyTheChangedLine() {
        skyblock();
        aenderungen.clear();

        teams.aendern("zeile3", "§7Purse: §6", "38,000,000");
        seite.teamGeaendert(java.util.Set.of("§a"));

        assertEquals(1, aenderungen.size());
        assertEquals(List.of(0), aenderungen.get(0).geaenderteZeilen());
        assertEquals("Purse: 37,824,435", aenderungen.get(0).alt().get(0));
        assertEquals("Purse: 38,000,000", aenderungen.get(0).neu().get(0));
    }

    @Test
    void aTeamChangeOfSomeoneNotInTheSidebarCostsNothingAndReportsNothing() {
        skyblock();
        aenderungen.clear();

        seite.teamGeaendert(java.util.Set.of("irgendein Spieler"));

        assertTrue(aenderungen.isEmpty());
    }

    @Test
    void doesNotReportWhenNothingVisibleChanged() {
        skyblock();
        aenderungen.clear();

        seite.punkte("SBScoreboard", "§a", 3, null); // derselbe Stand noch einmal

        assertTrue(aenderungen.isEmpty());
    }

    @Test
    void ignoresScoresOfObjectivesThatAreNotShown() {
        skyblock();
        aenderungen.clear();

        seite.ziel("Anderes", "x", 0);
        seite.punkte("Anderes", "§z", 9, null);

        assertTrue(aenderungen.isEmpty());
        assertEquals(3, seite.zeilen().size());
    }

    @Test
    void hidesEntriesStartingWithHashAndBreaksTiesAlphabeticallyIgnoringCase() {
        seite.ziel("Z", "T", 0);
        seite.anzeigeZiel("Z");
        seite.punkte("Z", "#versteckt", 99, null);
        seite.punkte("Z", "beta", 5, null);
        seite.punkte("Z", "Alpha", 5, null);
        seite.punkte("Z", "gamma", 7, null);

        assertEquals(List.of("gamma", "Alpha", "beta"), seite.zeilen());
    }

    @Test
    void showsAtMostFifteenLines() {
        seite.ziel("Z", "T", 0);
        seite.anzeigeZiel("Z");
        for (int i = 1; i <= 20; i++) {
            seite.punkte("Z", "e" + i, i, null);
        }

        assertEquals(SeitenleisteZustand.MAX_ZEILEN, seite.zeilen().size());
        assertEquals("e20", seite.zeilen().get(0));
        assertEquals("e6", seite.zeilen().get(14));
    }

    @Test
    void anOwnDisplayTextOverridesTheTeamText() {
        teams.anlegen("t", "Team ", "", List.of("x"));
        seite.ziel("Z", "T", 0);
        seite.anzeigeZiel("Z");

        seite.punkte("Z", "x", 1, "§cEigener Text");

        assertEquals(List.of("Eigener Text"), seite.zeilen());
    }

    @Test
    void removingTheShownObjectiveEmptiesTheSidebar() {
        skyblock();
        aenderungen.clear();

        seite.ziel("SBScoreboard", "", 1);

        assertEquals(List.of(), seite.zeilen());
        assertEquals("", seite.titel());
        assertEquals(1, aenderungen.size());
    }

    @Test
    void settingTheDisplayToNothingEmptiesTheSidebarButKeepsTheScores() {
        skyblock();

        seite.anzeigeZiel("");
        assertEquals(List.of(), seite.zeilen());

        seite.anzeigeZiel("SBScoreboard");
        assertEquals(3, seite.zeilen().size());
    }

    @Test
    void deletingAScoreRemovesItsLine() {
        skyblock();

        seite.punkteLoeschen("§b", "SBScoreboard");

        assertEquals(List.of("Purse: 37,824,435", "www.hypixel.net"), seite.zeilen());
    }

    @Test
    void deletingWithoutAnObjectiveRemovesTheEntryEverywhere() {
        skyblock();

        seite.punkteLoeschen("§a", null);

        assertEquals(2, seite.zeilen().size());
    }

    @Test
    void aTitleChangeIsReportedEvenWhenTheLinesStayTheSame() {
        skyblock();
        aenderungen.clear();

        seite.ziel("SBScoreboard", "§e§lSKYBLOCK 2", 2);

        assertEquals(1, aenderungen.size());
        assertEquals("SKYBLOCK", aenderungen.get(0).titelAlt());
        assertEquals("SKYBLOCK 2", aenderungen.get(0).titelNeu());
        assertTrue(aenderungen.get(0).geaenderteZeilen().isEmpty());
    }

    @Test
    void clearingEmptiesEverythingAndReportsIt() {
        skyblock();
        aenderungen.clear();

        seite.leeren();

        assertEquals(List.of(), seite.zeilen());
        assertEquals(1, aenderungen.size());
    }
}
