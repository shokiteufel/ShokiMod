package com.shokiteufel.shokimod.equipment;

import com.shokiteufel.shokimod.equipment.EquipmentRules.Cell;
import com.shokiteufel.shokimod.equipment.EquipmentRules.Kind;
import com.shokiteufel.shokimod.equipment.EquipmentRules.Pick;
import com.shokiteufel.shokimod.equipment.EquipmentRules.Window;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Regeln, nach denen die vier Equipment-Plaetze aus den Fenstern gelesen werden: welche Fenster, welche Spalte, welche Plaetze, was leer ist.
 * Die Fenster sind aus dem nachgebaut, was Skyblockers Leser voraussetzt (Hypixels echtes Fenster ist im Spiel ungeprueft).
 */
class EquipmentRulesTest {

    /** Ein Fenster mit sechs Reihen, in dem alles schwarze Glasscheibe ist */
    private static List<Cell> panes() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < 54; i++) cells.add(EquipmentRules.cell(i, Kind.BLACK_PANE, " "));
        return cells;
    }

    private static void put(List<Cell> cells, int index, Kind kind, String name) {
        cells.set(index, EquipmentRules.cell(index, kind, name));
    }

    /** "Stats & Equipment": die vier Gegenstaende in der zweiten Spalte (Platz 10, 19, 28, 37), sonst Fuellung */
    private static List<Cell> statsWindow(String necklace, String cloak, String belt, String gloves) {
        List<Cell> cells = panes();
        put(cells, 10, Kind.OTHER, necklace);
        put(cells, 19, Kind.OTHER, cloak);
        put(cells, 28, Kind.OTHER, belt);
        put(cells, 37, Kind.OTHER, gloves);
        // Daneben stehen andere Dinge (Werte, Knoepfe): die zaehlen nicht, sie liegen in anderen Spalten
        put(cells, 13, Kind.OTHER, "Your Stats");
        put(cells, 49, Kind.OTHER, "Close");
        return cells;
    }

    // ---- Fenstertitel ----

    @Test
    void theThreeWindowsAreRecognisedByTheirTitle() {
        assertEquals(Window.STATS, EquipmentRules.windowOf("Stats & Equipment"));
        assertEquals(Window.SETS, EquipmentRules.windowOf("Equipment Sets"));
        assertEquals(Window.LOADOUTS, EquipmentRules.windowOf("Loadouts"));
    }

    @Test
    void pageNumbersInFrontAreAllowedForSetsAndLoadouts() {
        assertEquals(Window.SETS, EquipmentRules.windowOf("(1/2) Equipment Sets"));
        assertEquals(Window.LOADOUTS, EquipmentRules.windowOf("(2/2) Loadouts"));
        assertEquals(Window.LOADOUTS, EquipmentRules.windowOf("(1/10) Loadouts"));
    }

    @Test
    void colourCodesAndOuterSpacesDoNotMatter() {
        assertEquals(Window.STATS, EquipmentRules.windowOf("§aStats & Equipment"));
        assertEquals(Window.STATS, EquipmentRules.windowOf("  Stats & Equipment "));
        assertEquals(Window.SETS, EquipmentRules.windowOf("§e§lEquipment Sets"));
    }

    @Test
    void everythingElseIsNoWindowOfInterest() {
        assertEquals(Window.NONE, EquipmentRules.windowOf(null));
        assertEquals(Window.NONE, EquipmentRules.windowOf(""));
        assertEquals(Window.NONE, EquipmentRules.windowOf("Chest"));
        assertEquals(Window.NONE, EquipmentRules.windowOf("SkyBlock Menu"));
        // Das ganze Wort, nicht ein Anfang oder ein Ende: wie bei Skyblocker (matches, nicht find)
        assertEquals(Window.NONE, EquipmentRules.windowOf("Stats & Equipment (2)"));
        assertEquals(Window.NONE, EquipmentRules.windowOf("Equipment Setsx"));
        assertEquals(Window.NONE, EquipmentRules.windowOf("My Loadouts"));
        assertEquals(Window.NONE, EquipmentRules.windowOf("Wardrobe (1/2)"));
        // Der alte Titel ist keiner, den Skyblocker kennt: Die Mod liest nichts, was sie nicht kennt
        assertEquals(Window.NONE, EquipmentRules.windowOf("Your Equipment and Stats"));
    }

    // ---- Zellen und Platzhalter ----

    @Test
    void aCellNameIsLowerCasedAndStrippedOfColourCodes() {
        assertEquals("hollow cloak", EquipmentRules.cell(19, Kind.OTHER, "§6§lHollow Cloak ").name());
        assertEquals("", EquipmentRules.cell(19, Kind.AIR, null).name());
        assertEquals(19, EquipmentRules.cell(19, Kind.OTHER, "x").index());
        assertEquals(Kind.OTHER, EquipmentRules.cell(19, Kind.OTHER, "x").kind());
    }

    @Test
    void emptyAndSlotNamesAreTheEmptyPlaceholders() {
        assertTrue(EquipmentRules.isPlaceholderName("empty necklace slot"));
        assertTrue(EquipmentRules.isPlaceholderName("empty cloak slot"));
        assertTrue(EquipmentRules.isPlaceholderName("slot 3"));
        assertTrue(EquipmentRules.isPlaceholderName("  empty belt slot"));
        // Ein Platz ohne Namen ist kein Platzhalter, sondern (ohne Gegenstand) Luft: Das entscheidet die Art des Platzes, nicht der Name
        assertFalse(EquipmentRules.isPlaceholderName(""));
        assertFalse(EquipmentRules.isPlaceholderName(null));
        assertFalse(EquipmentRules.isPlaceholderName("hollow cloak"));
        assertFalse(EquipmentRules.isPlaceholderName("slot"), "ohne Leerzeichen dahinter ist es kein Platzhalter");
        assertFalse(EquipmentRules.isPlaceholderName("potato slotted spoon"));
    }

    @Test
    void aWindowIsFilledAsSoonAsAnyPlaceHoldsSomething() {
        assertFalse(EquipmentRules.isFilled(null));
        assertFalse(EquipmentRules.isFilled(List.of()));
        List<Cell> air = new ArrayList<>();
        for (int i = 0; i < 54; i++) air.add(EquipmentRules.cell(i, Kind.AIR, ""));
        assertFalse(EquipmentRules.isFilled(air), "der Server hat noch nichts geschickt");
        air.set(10, EquipmentRules.cell(10, Kind.BLACK_PANE, " "));
        assertTrue(EquipmentRules.isFilled(air));
    }

    // ---- Spalte ----

    @Test
    void statsAndLoadoutsReadTheSecondColumn() {
        assertEquals(1, EquipmentRules.columnOf(Window.STATS, panes()));
        assertEquals(1, EquipmentRules.columnOf(Window.LOADOUTS, panes()));
        assertEquals(-1, EquipmentRules.columnOf(Window.NONE, panes()));
        assertEquals(-1, EquipmentRules.columnOf(null, panes()));
    }

    @Test
    void theWardrobeReadsTheColumnOfTheSetWithTheGreenDye() {
        List<Cell> cells = panes();
        put(cells, 38, Kind.LIME_DYE, "set 3");   // Reihe 4, Spalte 2
        assertEquals(2, EquipmentRules.columnOf(Window.SETS, cells));
        put(cells, 38, Kind.OTHER, "set 3");
        put(cells, 36, Kind.LIME_DYE, "set 1");   // Spalte 0
        assertEquals(0, EquipmentRules.columnOf(Window.SETS, cells));
        put(cells, 36, Kind.OTHER, "set 1");
        put(cells, 44, Kind.LIME_DYE, "set 9");   // Spalte 8
        assertEquals(8, EquipmentRules.columnOf(Window.SETS, cells));
    }

    @Test
    void aGreenDyeOutsideTheBottomRowIsNoSign() {
        List<Cell> cells = panes();
        put(cells, 20, Kind.LIME_DYE, "something green");
        put(cells, 45, Kind.LIME_DYE, "next row");
        assertEquals(-1, EquipmentRules.columnOf(Window.SETS, cells));
        assertEquals(-1, EquipmentRules.columnOf(Window.SETS, panes()), "kein Satz gewaehlt");
        assertEquals(-1, EquipmentRules.columnOf(Window.SETS, null));
        assertTrue(EquipmentRules.isSetRow(36));
        assertTrue(EquipmentRules.isSetRow(44));
        assertFalse(EquipmentRules.isSetRow(35));
        assertFalse(EquipmentRules.isSetRow(45));
    }

    // ---- die vier Plaetze ----

    @Test
    void theFourPiecesAreTheFirstFourPlacesWithoutFillerInTheColumn() {
        List<Pick> picks = EquipmentRules.equipmentSlots(
                statsWindow("Hollow Cloak", "Zombie Belt", "Lotus Bracelet", "Ancient Gauntlet"), 1);
        assertNotNull(picks);
        assertEquals(List.of(new Pick(10, false), new Pick(19, false), new Pick(28, false), new Pick(37, false)), picks);
    }

    @Test
    void anEmptySlotIsOnlyFlaggedNotLeftOut() {
        List<Pick> picks = EquipmentRules.equipmentSlots(
                statsWindow("Empty Necklace Slot", "Hollow Cloak", "Empty Belt Slot", "Slot 4"), 1);
        assertNotNull(picks);
        assertEquals(List.of(new Pick(10, true), new Pick(19, false), new Pick(28, true), new Pick(37, true)), picks);
    }

    @Test
    void airInAPlaceCountsAsEmptyInAFilledWindow() {
        List<Cell> cells = statsWindow("Hollow Cloak", "x", "Zombie Belt", "Ancient Gauntlet");
        put(cells, 19, Kind.AIR, "");
        List<Pick> picks = EquipmentRules.equipmentSlots(cells, 1);
        assertNotNull(picks);
        assertEquals(List.of(new Pick(10, false), new Pick(19, true), new Pick(28, false), new Pick(37, false)), picks);
    }

    @Test
    void aWindowTheServerHasNotFilledYetIsNeverRead() {
        List<Cell> air = new ArrayList<>();
        for (int i = 0; i < 54; i++) air.add(EquipmentRules.cell(i, Kind.AIR, ""));
        assertNull(EquipmentRules.equipmentSlots(air, 1), "sonst wuerde das Gemerkte mit vier leeren Plaetzen ueberschrieben");
        assertNull(EquipmentRules.equipmentSlots(List.of(), 1));
        assertNull(EquipmentRules.equipmentSlots(null, 1));
    }

    @Test
    void lessThanFourPlacesInTheColumnIsNothingToRead() {
        List<Cell> cells = panes();
        put(cells, 10, Kind.OTHER, "Hollow Cloak");
        put(cells, 19, Kind.OTHER, "Zombie Belt");
        put(cells, 28, Kind.OTHER, "Lotus Bracelet");
        assertNull(EquipmentRules.equipmentSlots(cells, 1));
    }

    @Test
    void anUnknownColumnIsNothingToRead() {
        List<Cell> cells = statsWindow("a", "b", "c", "d");
        assertNull(EquipmentRules.equipmentSlots(cells, -1));
        assertNull(EquipmentRules.equipmentSlots(cells, 9));
    }

    @Test
    void theWardrobeGivesTheFourPiecesOfTheChosenColumnAndNotTheDye() {
        List<Cell> cells = panes();
        // Satz 3 (Spalte 2): vier Teile, darunter der gruene Farbstoff; in den anderen Spalten stehen andere Saetze
        put(cells, 2, Kind.OTHER, "Hollow Cloak");
        put(cells, 11, Kind.OTHER, "Empty Cloak Slot");
        put(cells, 20, Kind.OTHER, "Zombie Belt");
        put(cells, 29, Kind.OTHER, "Slot 4");
        put(cells, 38, Kind.LIME_DYE, "Set 3");
        put(cells, 0, Kind.OTHER, "Other Set Piece");
        put(cells, 36, Kind.OTHER, "Set 1");
        int column = EquipmentRules.columnOf(Window.SETS, cells);
        assertEquals(2, column);
        List<Pick> picks = EquipmentRules.equipmentSlots(cells, column);
        assertEquals(List.of(new Pick(2, false), new Pick(11, true), new Pick(20, false), new Pick(29, true)), picks);
    }

    // ---- Abnehmen im Kleiderschrank, Klick-Befehl ----

    @Test
    void clickingTheGreenDyeOfTheChosenSetTakesTheSetOff() {
        assertTrue(EquipmentRules.takesSetOff(Window.SETS, 38, Kind.LIME_DYE));
        assertFalse(EquipmentRules.takesSetOff(Window.SETS, 20, Kind.LIME_DYE), "nicht in der untersten Reihe");
        assertFalse(EquipmentRules.takesSetOff(Window.STATS, 38, Kind.LIME_DYE), "nur im Kleiderschrank");
        assertFalse(EquipmentRules.takesSetOff(Window.SETS, 38, Kind.OTHER), "kein gruener Farbstoff");
        assertFalse(EquipmentRules.takesSetOff(null, 38, Kind.LIME_DYE));
    }

    @Test
    void aClickOpensEquipmentExceptInTheRiftAndTheSafariWhereItOpensStats() {
        assertEquals("equipment", EquipmentRules.clickCommand(EquipmentSet.NORMAL));
        assertEquals("stats", EquipmentRules.clickCommand(EquipmentSet.RIFT));
        assertEquals("stats", EquipmentRules.clickCommand(EquipmentSet.SAFARI));
    }

    @Test
    void everySlotHasAName() {
        assertEquals(EquipmentRules.SLOTS, EquipmentRules.SLOT_NAMES.length);
        assertEquals(4, EquipmentRules.SLOTS);
        for (String name : EquipmentRules.SLOT_NAMES) assertFalse(name.isBlank());
    }
}
