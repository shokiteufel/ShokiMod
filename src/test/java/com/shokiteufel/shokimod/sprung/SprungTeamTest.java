package com.shokiteufel.shokimod.sprung;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Die Mitspieler aus der Tab-Liste: Reihenfolge des Erscheinens, Klasse, Tote behalten ihre Klasse */
class SprungTeamTest {

    private static List<SprungSpieler> lesen(List<SprungSpieler> bestand, String... zeilen) {
        SprungTeam.lesen(bestand, java.util.Arrays.asList(zeilen));
        return bestand;
    }

    @Test
    void theTabLineGivesNameAndClassInTheOrderTheyAppear() {
        List<SprungSpieler> t = lesen(new ArrayList<>(),
                "[420] [MVP+] Anna (Mage XXV)",
                "[300] Bert (Healer XX)",
                "[15] [VIP] Cleo_9 (Archer XIV)");
        assertEquals(3, t.size());
        assertEquals("Anna", t.get(0).name);
        assertEquals(SprungKlasse.MAGE, t.get(0).klasse);
        assertEquals("Bert", t.get(1).name);
        assertEquals(SprungKlasse.HEALER, t.get(1).klasse);
        assertEquals("Cleo_9", t.get(2).name);
        assertEquals(SprungKlasse.ARCHER, t.get(2).klasse);
        assertFalse(t.get(0).tot);
    }

    @Test
    void aDeadPlayerKeepsTheClassHeHad() {
        List<SprungSpieler> t = lesen(new ArrayList<>(), "[420] Anna (Mage XXV)");
        lesen(t, "[420] Anna (DEAD)");
        assertEquals(1, t.size());
        assertEquals(SprungKlasse.MAGE, t.get(0).klasse, "die Tab-Liste schreibt (DEAD) statt der Klasse");
        assertTrue(t.get(0).tot);
        lesen(t, "[420] Anna (Mage XXV)");
        assertFalse(t.get(0).tot, "wiederbelebt");
    }

    @Test
    void aPlayerWhoIsDeadFromTheFirstLineHasNoClassYet() {
        List<SprungSpieler> t = lesen(new ArrayList<>(), "[420] Anna (DEAD)");
        assertEquals(SprungKlasse.LEER, t.get(0).klasse);
        assertTrue(t.get(0).tot);
    }

    @Test
    void theClassChangesWhenTheTabListNamesAnother() {
        List<SprungSpieler> t = lesen(new ArrayList<>(), "[420] Anna (Mage XXV)");
        lesen(t, "[420] Anna (Tank XXV)");
        assertEquals(1, t.size());
        assertEquals(SprungKlasse.TANK, t.get(0).klasse);
    }

    @Test
    void linesThatAreNoPlayersAreIgnored() {
        List<SprungSpieler> t = lesen(new ArrayList<>(), "Dungeon: Catacombs", "Puzzles: (3)", "Party (5)", null, "");
        assertTrue(t.isEmpty());
    }

    @Test
    void theClassNameIsReadWithoutRegardToCase() {
        assertEquals(SprungKlasse.BERSERK, SprungKlasse.aus("berserk"));
        assertEquals(SprungKlasse.LEER, SprungKlasse.aus("Necromancer"));
        assertEquals(SprungKlasse.LEER, SprungKlasse.aus(null));
        assertEquals("tank", SprungKlasse.TANK.klein());
    }
}
