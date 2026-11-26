package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.PestTarget;
import com.shokiteufel.shokimod.scanner.TabText;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Die Karte der Plots im Garden fuer das "Garden Plots Widget".
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code GardenPlotsWidget}, {@code GardenPlots}); die Anordnung der Nummern stammt aus SkyHannis {@code GardenPlotApi}
 * (LGPL-2.1) und stimmt mit der Tabelle in Skyblockers Widget ueberein. Von dort stammt die Regel: Der Garten ist ein Raster aus fuenf mal fuenf Plots zu je 96 Bloecken;
 * in der Mitte liegt die Scheune (Plot 0, "The Barn"), die anderen tragen die Nummern 1 bis 24, und die Reihenfolge im Raster ist fest (siehe {@link #MAP}). Welche Plots
 * befallen sind, nennt das Widget "Pests" der Tab-Liste mit der Zeile "Plots: 4, 12, 13". Ein Klick auf einen Plot schickt {@code /plottp <Nummer>}, auf die Scheune
 * {@code /plottp barn}. Die Umsetzung ist eigene Arbeit: Java, die Karte ist kein eigenes Fenster neben dem Inventar, sondern ein Kasten des HUD-Editors mit fuenf Textzeilen
 * (zweistellige Nummern, damit die Spalten buendig stehen); alles hier ist rein.
 */
public final class PlotGrid {

    /** Die Nummern der Plots, Zeile fuer Zeile von Norden nach Sueden und Spalte fuer Spalte von Westen nach Osten (x waechst nach Osten, z nach Sueden) */
    public static final int[][] MAP = {
            {21, 13, 9, 14, 22},
            {15, 5, 1, 6, 16},
            {10, 2, 0, 3, 11},
            {17, 7, 4, 8, 18},
            {23, 19, 12, 20, 24},
    };
    /** Die Nummer der Scheune */
    public static final int BARN = 0;
    public static final int SIZE = 5;

    /** Der Gruen, Rot und Grau der Zellen */
    static final String CURRENT = "§a";
    static final String INFESTED = "§c";
    static final String NORMAL = "§7";
    static final String BARN_COLOUR = "§e";

    /**
     * Was die Tab-Liste ueber die befallenen Plots sagt.
     *
     * @param known ob das Widget "Pests" in der Liste steht (sonst weiss man nichts und behaelt den letzten Stand)
     * @param plots die Nummern der befallenen Plots
     */
    public record Infested(boolean known, Set<Integer> plots) {
    }

    private PlotGrid() {
    }

    /** Die Nummer des Plots, auf dem diese Stelle liegt, oder -1 ausserhalb des Rasters */
    public static int plotAt(double x, double z) {
        int column = PestTarget.plotCell(x);
        int row = PestTarget.plotCell(z);
        if (column < 0 || row < 0) return -1;
        return MAP[row][column];
    }

    /** Steht man auf dem Plot der Scheune - der Mitte? */
    public static boolean onBarnPlot(double x, double z) {
        return plotAt(x, z) == BARN;
    }

    /** Der Befehl, der zu einem Plot fuehrt (ohne Schraegstrich) */
    public static String teleportCommand(int plot) {
        return plot == BARN ? "plottp barn" : "plottp " + plot;
    }

    /** Der Text einer Zelle: die Nummer zweistellig ("07"), die Scheune als "Ba" */
    public static String cellText(int plot) {
        if (plot == BARN) return "Ba";
        return plot < 10 ? "0" + plot : Integer.toString(plot);
    }

    /** Die Farbcodes einer Zelle: befallen rot, hier gruen, beides rot und unterstrichen, sonst grau (die Scheune gelb) */
    static String colourOf(int plot, Set<Integer> infested, int current) {
        boolean bad = infested != null && infested.contains(plot);
        boolean here = plot == current;
        if (bad && here) return INFESTED + "§n";
        if (bad) return INFESTED;
        if (here) return CURRENT;
        return plot == BARN ? BARN_COLOUR : NORMAL;
    }

    /**
     * Die fuenf Zeilen der Karte, mit Farbcodes.
     *
     * @param infested die befallenen Plots
     * @param current  der Plot, auf dem man steht, oder -1
     */
    public static List<String> rows(Set<Integer> infested, int current) {
        List<String> rows = new ArrayList<>(SIZE);
        for (int row = 0; row < SIZE; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < SIZE; column++) {
                if (column > 0) line.append(' ');
                int plot = MAP[row][column];
                line.append(colourOf(plot, infested, current)).append(cellText(plot)).append("§r");
            }
            rows.add(line.toString());
        }
        return rows;
    }

    /**
     * Die Spalte unter einem waagerechten Abstand, oder -1 zwischen den Zellen und ausserhalb.
     *
     * @param localX    der Abstand vom linken Rand der Schrift (in den Massen des Kastens)
     * @param cellWidth die Breite einer Zelle (zwei Ziffern)
     * @param gap       die Breite eines Leerzeichens zwischen zwei Zellen
     */
    public static int columnAt(double localX, int cellWidth, int gap) {
        if (localX < 0 || cellWidth <= 0) return -1;
        int pitch = cellWidth + Math.max(0, gap);
        int column = (int) (localX / pitch);
        if (column < 0 || column >= SIZE) return -1;
        // Die Luecke zwischen zwei Zellen ist kein Plot
        return localX - (double) column * pitch < cellWidth ? column : -1;
    }

    /** Die Nummer des Plots in einer Zeile und Spalte des Rasters, oder -1 */
    public static int plotInCell(int row, int column) {
        if (row < 0 || row >= SIZE || column < 0 || column >= SIZE) return -1;
        return MAP[row][column];
    }

    /** Die Nummer eines Eintrags der Liste "Plots: 4, 12, 13" ("Plots: " vorn abgeschnitten); Unlesbares fehlt */
    static Set<Integer> numbers(String list) {
        Set<Integer> out = new LinkedHashSet<>();
        for (String part : list.split(",")) {
            String text = part.trim();
            if (text.isEmpty()) continue;
            try {
                int number = Integer.parseInt(text);
                if (number >= 0 && number < SIZE * SIZE) out.add(number);
            } catch (NumberFormatException e) {
                // keine Zahl: gehoert nicht zu den Plots
            }
        }
        return out;
    }

    /**
     * Die befallenen Plots aus den Zeilen der Tab-Liste (ohne Farbcodes).
     *
     * Steht die Zeile "Plots: ..." da, sind es diese; steht nur das Widget "Pests:" ohne sie, ist kein Plot befallen; fehlt beides, weiss man nichts.
     */
    public static Infested parseInfested(List<String> lines) {
        boolean sawPests = false;
        if (lines != null) {
            for (String raw : lines) {
                String line = TabText.tidy(raw);
                if (line.startsWith("Plots:")) return new Infested(true, numbers(line.substring("Plots:".length())));
                if (line.startsWith("Pests:")) sawPests = true;
            }
        }
        return new Infested(sawPests, new LinkedHashSet<>());
    }
}
