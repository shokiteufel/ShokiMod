package com.shokiteufel.shokimod.garden;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Karte der Plots: das Raster, der Plot unter einer Stelle, die Zeilen der Karte mit Farben, die befallenen Plots aus der Tab-Liste und die Spalte unter einem Klick.
 */
class PlotGridTest {

    @AfterEach
    void reset() {
        PlotsState.reset();
    }

    @Test
    void theGridHoldsTheNumbersZeroToTwentyFourOnce() {
        Set<Integer> seen = new TreeSet<>();
        for (int[] row : PlotGrid.MAP) {
            assertEquals(5, row.length);
            for (int plot : row) assertTrue(seen.add(plot), "doppelt: " + plot);
        }
        assertEquals(25, seen.size());
        assertEquals(0, (int) ((TreeSet<Integer>) seen).first());
        assertEquals(24, (int) ((TreeSet<Integer>) seen).last());
        assertEquals(0, PlotGrid.MAP[2][2], "die Scheune liegt in der Mitte");
    }

    @Test
    void theNumbersSitWhereSkyblockerAndSkyHanniPutThem() {
        // Skyblockers Tabelle: Plot 1 liegt im Platz 7 des Rasters (Zeile 1, Spalte 2), Plot 12 im Platz 22 (Zeile 4, Spalte 2), Plot 21 im Platz 0
        assertEquals(1, PlotGrid.plotInCell(1, 2));
        assertEquals(12, PlotGrid.plotInCell(4, 2));
        assertEquals(21, PlotGrid.plotInCell(0, 0));
        assertEquals(24, PlotGrid.plotInCell(4, 4));
        assertEquals(4, PlotGrid.plotInCell(3, 2));
        assertEquals(2, PlotGrid.plotInCell(2, 1));
        assertEquals(-1, PlotGrid.plotInCell(5, 0));
        assertEquals(-1, PlotGrid.plotInCell(0, -1));
    }

    @Test
    void thePlotUnderAPlace() {
        assertEquals(0, PlotGrid.plotAt(0, 0));
        assertEquals(0, PlotGrid.plotAt(-47.9, 47.9));
        assertEquals(5, PlotGrid.plotAt(-100, -100), "eine Spalte und eine Zeile links und oberhalb der Mitte");
        assertEquals(3, PlotGrid.plotAt(60, 0), "ostwaerts der Scheune");
        assertEquals(1, PlotGrid.plotAt(0, -60), "nordwaerts (z waechst nach Sueden)");
        assertEquals(12, PlotGrid.plotAt(0, 200));
        assertEquals(21, PlotGrid.plotAt(-240, -240));
        assertEquals(24, PlotGrid.plotAt(240, 240), "der Rand gehoert noch zum letzten Plot");
        assertEquals(-1, PlotGrid.plotAt(241, 0));
        assertEquals(-1, PlotGrid.plotAt(0, -300));
    }

    @Test
    void onlyTheMiddlePlotIsTheBarn() {
        assertTrue(PlotGrid.onBarnPlot(0, 0));
        assertTrue(PlotGrid.onBarnPlot(47.9, -47.9));
        assertFalse(PlotGrid.onBarnPlot(48, 0));
        assertFalse(PlotGrid.onBarnPlot(0, 400));
    }

    @Test
    void theTeleportCommandNamesThePlot() {
        assertEquals("plottp barn", PlotGrid.teleportCommand(0));
        assertEquals("plottp 12", PlotGrid.teleportCommand(12));
        assertEquals("plottp 7", PlotGrid.teleportCommand(7));
    }

    @Test
    void aCellIsTwoCharactersWide() {
        assertEquals("Ba", PlotGrid.cellText(0));
        assertEquals("07", PlotGrid.cellText(7));
        assertEquals("24", PlotGrid.cellText(24));
        for (int plot = 0; plot <= 24; plot++) assertEquals(2, PlotGrid.cellText(plot).length(), "Plot " + plot);
    }

    @Test
    void theRowsShowTheGridWithoutPestsAndWithoutPosition() {
        var rows = PlotGrid.rows(Set.of(), -1);
        assertEquals(5, rows.size());
        assertEquals("§721§r §713§r §709§r §714§r §722§r", rows.get(0));
        assertEquals("§710§r §702§r §eBa§r §703§r §711§r", rows.get(2), "die Scheune in Gelb");
    }

    @Test
    void infestedPlotsAreRedAndYourPlotIsGreen() {
        var rows = PlotGrid.rows(Set.of(4, 12), 5);
        assertEquals("§715§r §a05§r §701§r §706§r §716§r", rows.get(1), "Plot 5 ist gruen");
        assertEquals("§717§r §707§r §c04§r §708§r §718§r", rows.get(3), "Plot 4 ist befallen");
        assertEquals("§723§r §719§r §c12§r §720§r §724§r", rows.get(4));
    }

    @Test
    void anInfestedPlotYouStandOnIsRedAndUnderlined() {
        var rows = PlotGrid.rows(Set.of(4), 4);
        assertEquals("§717§r §707§r §c§n04§r §708§r §718§r", rows.get(3));
    }

    @Test
    void theInfestedPlotsAreReadFromThePestsWidget() {
        // REGEX-TEST von SkyHanni: " Plots: 4, 12, 13, 18, 20"
        PlotGrid.Infested read = PlotGrid.parseInfested(java.util.List.of("Pests: (5)", " Alive: 5", " Plots: 4, 12, 13, 18, 20", " Spray: None"));
        assertTrue(read.known());
        assertEquals(Set.of(4, 12, 13, 18, 20), read.plots());
    }

    @Test
    void aLineWithOddSpacesIsReadToo() {
        PlotGrid.Infested read = PlotGrid.parseInfested(java.util.List.of(" Plots: 3 "));
        assertTrue(read.known());
        assertEquals(Set.of(3), read.plots());
    }

    @Test
    void thePestsWidgetWithoutPlotsMeansNoneIsInfested() {
        PlotGrid.Infested read = PlotGrid.parseInfested(java.util.List.of("Pests: (0)", " Alive: 0", " Spray: None"));
        assertTrue(read.known());
        assertTrue(read.plots().isEmpty());
    }

    @Test
    void withoutThePestsWidgetNothingIsKnown() {
        assertFalse(PlotGrid.parseInfested(java.util.List.of("Area: Garden", "Visitors: (1)")).known());
        assertFalse(PlotGrid.parseInfested(null).known());
    }

    @Test
    void unreadableOrImpossibleNumbersAreLeftOut() {
        PlotGrid.Infested read = PlotGrid.parseInfested(java.util.List.of(" Plots: 4, x, 99, -1, , 7"));
        assertEquals(Set.of(4, 7), read.plots());
    }

    @Test
    void theColumnUnderAClick() {
        // Eine Zelle ist zwoelf breit, dazwischen vier: die Zellen beginnen bei 0, 16, 32, 48, 64
        assertEquals(0, PlotGrid.columnAt(0, 12, 4));
        assertEquals(0, PlotGrid.columnAt(11.9, 12, 4));
        assertEquals(-1, PlotGrid.columnAt(12.5, 12, 4), "zwischen zwei Zellen");
        assertEquals(1, PlotGrid.columnAt(16, 12, 4));
        assertEquals(3, PlotGrid.columnAt(50, 12, 4));
        assertEquals(4, PlotGrid.columnAt(75.9, 12, 4));
        assertEquals(-1, PlotGrid.columnAt(80, 12, 4), "hinter der letzten Zelle");
        assertEquals(-1, PlotGrid.columnAt(-1, 12, 4));
        assertEquals(-1, PlotGrid.columnAt(5, 0, 4));
    }

    @Test
    void theStateFollowsTheTabListAndKeepsTheLastWhenTheWidgetIsGone() {
        assertFalse(PlotsState.known());
        PlotsState.onTabList(java.util.List.of(" Plots: 4, 12"));
        assertTrue(PlotsState.known());
        assertEquals(Set.of(4, 12), PlotsState.infested());
        // Das Widget fehlt kurz: der Stand bleibt
        PlotsState.onTabList(java.util.List.of("Area: Garden"));
        assertEquals(Set.of(4, 12), PlotsState.infested());
        // Das Widget kommt ohne Plots: keiner ist mehr befallen
        PlotsState.onTabList(java.util.List.of("Pests: (0)"));
        assertTrue(PlotsState.infested().isEmpty());
        assertTrue(PlotsState.known());
        PlotsState.reset();
        assertFalse(PlotsState.known());
    }
}
