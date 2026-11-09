package com.shokiteufel.shokimod.paket.zustand;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabListeZustandTest {
    private TeamZustand teams;
    private TabListeZustand tab;
    private List<String> geaendert;

    @BeforeEach
    void aufbauen() {
        teams = new TeamZustand();
        tab = new TabListeZustand(teams);
        geaendert = new ArrayList<>();
        tab.hoeren((id, name, alt, neu) -> geaendert.add(name + ": " + alt + " -> " + neu));
    }

    private UUID eintrag(String name, boolean gelistet, int ordnung) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes());
        tab.hinzufuegen(id, name);
        tab.gelistet(id, gelistet);
        tab.ordnung(id, ordnung);
        return id;
    }

    @Test
    void sortsLikeVanillaByOrderThenSpectatorThenTeamThenName() {
        eintrag("zeta", true, 0);
        eintrag("Alpha", true, 0);
        eintrag("spaeter", true, -5);                // niedrigere Ordnung: weiter unten
        eintrag("oben", true, 10);                   // hoehere Ordnung: zuerst
        UUID zuschauer = eintrag("zuschauer", true, 0);
        tab.spielmodus(zuschauer, true);             // Zuschauer nach den anderen derselben Ordnung
        teams.anlegen("b-team", "", "", List.of("Alpha"));   // Alpha liegt im Team "b-team", zeta in "a-team":
        teams.anlegen("a-team", "", "", List.of("zeta"));    // das Team entscheidet vor dem Namen
        tab.teamsGeaendert();

        assertEquals(List.of("oben", "zeta", "Alpha", "zuschauer", "spaeter"), tab.zeilen());
    }

    @Test
    void sortsByNameIgnoringCaseWhenOrderAndTeamAreEqual() {
        eintrag("beta", true, 0);
        eintrag("Alpha", true, 0);
        eintrag("Gamma", true, 0);

        assertEquals(List.of("Alpha", "beta", "Gamma"), tab.zeilen());
    }

    @Test
    void showsOnlyListedEntries() {
        eintrag("sichtbar", true, 0);
        eintrag("versteckt", false, 0);

        assertEquals(List.of("sichtbar"), tab.zeilen());
    }

    @Test
    void usesTheDisplayNameOtherwisePrefixNameSuffixOfTheTeam() {
        UUID widget = eintrag("!A-a", true, 5);
        eintrag("Steve", true, 0);
        teams.anlegen("t", "§b[MVP] ", " [X]", List.of("Steve"));
        tab.anzeigename(widget, "Area: Hub");        // der Spiegel bekommt den Klartext (die Anmeldung am Bus entfernt Farbcodes)
        tab.teamsGeaendert();

        assertEquals(List.of("Area: Hub", "[MVP] Steve [X]"), tab.zeilen());
    }

    @Test
    void aPartialUpdateNeverTouchesFieldsOfOtherActions() {
        UUID id = eintrag("w", true, 7);
        tab.anzeigename(id, "Pet: Foxy");

        tab.gelistet(id, true);      // nur "gelistet" wurde gemeldet
        tab.ordnung(id, 8);          // nur die Ordnung
        tab.spielmodus(id, false);

        assertEquals("Pet: Foxy", tab.eintrag(id).anzeigename());
        assertEquals(List.of("Pet: Foxy"), tab.zeilen());
    }

    @Test
    void reportsOnlyRealChangesOfADisplayName() {
        UUID id = eintrag("w", true, 0);

        tab.anzeigename(id, "Area: Hub");
        tab.anzeigename(id, "Area: Hub");            // gleich: keine Meldung
        tab.anzeigename(id, "Area: Your Island");
        tab.anzeigename(id, null);                    // geloescht

        assertEquals(List.of("w: null -> Area: Hub", "w: Area: Hub -> Area: Your Island", "w: Area: Your Island -> null"), geaendert);
    }

    @Test
    void removingAnEntryDropsItFromTheList() {
        UUID id = eintrag("weg", true, 0);
        eintrag("bleibt", true, 0);

        tab.entfernen(id);

        assertEquals(List.of("bleibt"), tab.zeilen());
        assertEquals(1, tab.anzahl());
    }

    @Test
    void updatesForUnknownEntriesAreIgnored() {
        UUID fremd = UUID.randomUUID();

        tab.gelistet(fremd, true);
        tab.anzeigename(fremd, "x");
        tab.ordnung(fremd, 3);

        assertEquals(0, tab.anzahl());
        assertTrue(geaendert.isEmpty());
    }

    @Test
    void showsAtMostEightyLines() {
        for (int i = 0; i < 100; i++) {
            eintrag(String.format("p%03d", i), true, 0);
        }

        assertEquals(TabListeZustand.MAX_ZEILEN, tab.zeilen().size());
    }

    @Test
    void keepsHeaderAndFooterAndClearsEverything() {
        tab.kopfUndFuss("Kopf", "Fuss");
        eintrag("a", true, 0);
        assertEquals("Kopf", tab.kopf());
        assertEquals("Fuss", tab.fuss());

        tab.leeren();

        assertEquals("", tab.kopf());
        assertEquals(0, tab.anzahl());
        assertEquals(List.of(), tab.zeilen());
    }
}
