package com.shokiteufel.shokimod.garden;

import java.util.List;
import java.util.Set;

/**
 * Welche Plots im Garden gerade befallen sind - der Stand fuer das "Garden Plots Widget".
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code GardenPlotsWidget.updateInfestedFromTab}). Von dort stammt die Quelle: das Widget "Pests" der Tab-Liste mit der Zeile
 * "Plots: 4, 12, 13". Die Umsetzung ist eigene Arbeit: Java, der Stand wird bei jeder Aenderung der Tab-Liste nachgefuehrt (nicht alle drei Sekunden gelesen); steht das
 * Widget nicht in der Liste, bleibt der letzte Stand. Das Lesen selbst steht in {@link PlotGrid#parseInfested}.
 */
public final class PlotsState {

    private static Set<Integer> infested = Set.of();
    private static boolean known = false;

    private PlotsState() {
    }

    /** Die Tab-Liste (ohne Farbcodes) lesen und den Stand nachfuehren */
    public static void onTabList(List<String> lines) {
        PlotGrid.Infested read = PlotGrid.parseInfested(lines);
        if (!read.known()) return;
        infested = Set.copyOf(read.plots());
        known = true;
    }

    /** Die befallenen Plots - nur lesen */
    public static Set<Integer> infested() {
        return infested;
    }

    /** Hat die Tab-Liste das Widget "Pests" schon einmal gezeigt? */
    public static boolean known() {
        return known;
    }

    public static void reset() {
        infested = Set.of();
        known = false;
    }
}
