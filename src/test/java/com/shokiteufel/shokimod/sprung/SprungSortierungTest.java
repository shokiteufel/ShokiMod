package com.shokiteufel.shokimod.sprung;

import com.shokiteufel.shokimod.data.ModConfig.LeapCustomType;
import com.shokiteufel.shokimod.data.ModConfig.LeapOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Die Reihenfolge im Leap Menu, wie bei NoammAddons: A-Z Class, A-Z Name, Odin, eigene Liste, keine */
class SprungSortierungTest {

    private static SprungSpieler s(String name, SprungKlasse klasse) {
        return new SprungSpieler(name, klasse, false);
    }

    private static List<String> namen(List<SprungSpieler> l) {
        List<String> aus = new ArrayList<>();
        for (SprungSpieler s : l) aus.add(s.name);
        return aus;
    }

    private static final List<SprungSpieler> TEAM = List.of(
            s("Zed", SprungKlasse.TANK), s("Anna", SprungKlasse.MAGE), s("Bert", SprungKlasse.ARCHER), s("Cleo", SprungKlasse.MAGE));

    @Test
    void byClassTheOrderIsArcherBerserkHealerMageTankAndThenByName() {
        List<SprungSpieler> aus = SprungSortierung.sortieren(TEAM, LeapOrder.CLASS, null, LeapCustomType.NAME, List.of());
        assertEquals(List.of("Bert", "Anna", "Cleo", "Zed"), namen(aus));
    }

    @Test
    void byNameIsAToZ() {
        List<SprungSpieler> aus = SprungSortierung.sortieren(TEAM, LeapOrder.NAME, null, LeapCustomType.NAME, List.of());
        assertEquals(List.of("Anna", "Bert", "Cleo", "Zed"), namen(aus));
    }

    @Test
    void noSortingKeepsTheOrderOfTheTabList() {
        List<SprungSpieler> aus = SprungSortierung.sortieren(TEAM, LeapOrder.NONE, null, LeapCustomType.NAME, List.of());
        assertEquals(List.of("Zed", "Anna", "Bert", "Cleo"), namen(aus));
    }

    @Test
    void theInputIsNeverChanged() {
        SprungSortierung.sortieren(TEAM, LeapOrder.NAME, null, LeapCustomType.NAME, List.of());
        assertEquals(List.of("Zed", "Anna", "Bert", "Cleo"), namen(TEAM));
    }

    @Test
    void customByNameBringsTheListedFirstInTheirOrder() {
        List<SprungSpieler> aus = SprungSortierung.sortieren(TEAM, LeapOrder.CUSTOM, null, LeapCustomType.NAME, List.of("zed", "cleo"));
        // Zed und Cleo stehen vorn in der Reihenfolge der Liste, die anderen danach A-Z
        assertEquals(List.of("Zed", "Cleo", "Anna", "Bert"), namen(aus));
    }

    @Test
    void customByClassTiesAreBrokenByTheClassName() {
        List<SprungSpieler> aus = SprungSortierung.sortieren(TEAM, LeapOrder.CUSTOM, null, LeapCustomType.CLASS, List.of("tank", "mage"));
        // Tank vor Mage; die beiden Mages gleich weit vorn und gleich nach der Klasse: ihre Reihenfolge bleibt (stabile Sortierung)
        assertEquals(List.of("Zed", "Anna", "Cleo", "Bert"), namen(aus));
    }

    @Test
    void odinPutsTheClassesOnTheirPlacesByYourOwnClass() {
        // Als Archer: Platz 1 Mage, 2 Berserk, 3 Healer, 4 Tank
        List<SprungSpieler> team = List.of(s("T", SprungKlasse.TANK), s("H", SprungKlasse.HEALER), s("B", SprungKlasse.BERSERK), s("M", SprungKlasse.MAGE));
        List<SprungSpieler> aus = SprungSortierung.sortieren(team, LeapOrder.ODIN, SprungKlasse.ARCHER, LeapCustomType.NAME, List.of());
        assertEquals(List.of("M", "B", "H", "T"), namen(aus));
    }

    @Test
    void odinFillsAFreePlaceWithWhoDoesNotFitAnywhere() {
        // Als Archer, zwei Mages und kein Berserk: der zweite Mage rueckt in den freien Platz 2
        List<SprungSpieler> team = List.of(s("M1", SprungKlasse.MAGE), s("M2", SprungKlasse.MAGE), s("H", SprungKlasse.HEALER), s("T", SprungKlasse.TANK));
        List<SprungSpieler> aus = SprungSortierung.sortieren(team, LeapOrder.ODIN, SprungKlasse.ARCHER, LeapCustomType.NAME, List.of());
        assertEquals(List.of("M1", "M2", "H", "T"), namen(aus));
    }

    @Test
    void odinWithoutYourClassLeavesTheOrderAsItIs() {
        List<SprungSpieler> aus = SprungSortierung.sortieren(TEAM, LeapOrder.ODIN, null, LeapCustomType.NAME, List.of());
        assertEquals(List.of("Zed", "Anna", "Bert", "Cleo"), namen(aus));
        aus = SprungSortierung.sortieren(TEAM, LeapOrder.ODIN, SprungKlasse.LEER, LeapCustomType.NAME, List.of());
        assertEquals(List.of("Zed", "Anna", "Bert", "Cleo"), namen(aus));
    }

    @Test
    void theCustomListIsReadAsLowerCaseWithoutEmptyEntries() {
        assertEquals(List.of("anna", "bob", "mage"), SprungSortierung.liste("Anna, bob ,, Mage"));
        assertEquals(List.of(), SprungSortierung.liste(""));
        assertEquals(List.of(), SprungSortierung.liste(null));
    }
}
