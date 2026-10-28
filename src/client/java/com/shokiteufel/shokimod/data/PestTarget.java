package com.shokiteufel.shokimod.data;

import java.util.Locale;
import java.util.Set;

/**
 * Die Pests im Garden: ein fester Eintrag, der nicht in der Mob-Liste steht.
 *
 * Wie der Hideyho-Finder, nur fuer alle Pests zugleich und ohne den Hauptschalter der Mobs: Ein Umriss und eine Linie zu jedem, der im
 * Garden auftaucht. Die Eintraege der eigenen Liste ("Mite", "Cricket" ...) zeichnen je Eintrag nur eine Linie zum naechsten Mob; hier
 * bekommt jeder Pest seine.
 *
 * Erkannt wird am Namen, genau und ohne Farbcodes, Lebensanzeige und Klammern: "Rat" ist ein Pest, "Rat Catcher" nicht. Die Namen sind
 * die der Pests im Spiel (Wiki und SkyHanni: PestType).
 */
public final class PestTarget implements MobVisual {

    public static final PestTarget INSTANCE = new PestTarget();

    private static final int COLOUR = 0xFF5555;

    /** Die Namen der Pests, kleingeschrieben */
    private static final Set<String> NAMES = Set.of(
            "beetle", "cricket", "earthworm", "field mouse", "fly", "locust", "lunar moth", "mite", "mosquito", "moth", "rat", "slug",
            "praying mantis", "firefly", "dragonfly");

    private PestTarget() {
    }

    private static ModConfig.GardenPestsCategory cfg() {
        return ModConfig.INSTANCE.garden.pests;
    }

    public static boolean enabled() {
        return cfg().enabled;
    }

    /** Steht man im Garden? Das Gebiet kommt aus der Tab-Liste ("Area: Garden") */
    public static boolean inGarden() {
        if (!GameState.Server.isSkyblock()) return false;
        String area = GameState.Server.map;
        if (area == null) return false;
        String lower = area.toLowerCase(Locale.ROOT);
        return lower.equals("garden") || lower.equals("the garden");
    }

    /** Lohnt sich der Durchgang durch die Mobs: an, im Garden, und wenigstens eine Anzeige gewaehlt */
    public static boolean scanning() {
        return enabled() && INSTANCE.anyEnabled() && inGarden();
    }

    /**
     * Die Plots des Gartens: ein Raster aus 5 x 5 Feldern zu je 96 Bloecken, von -240 bis 240 (so rechnet auch SkyHanni, GardenPlotApi).
     * Die Mitte des Rasters ist der Hof mit der Scheune; sie ist ein Feld wie die anderen.
     */
    private static final double PLOT_SIZE = 96.0;
    private static final double GRID_MIN = -240.0;
    private static final double GRID_MAX = 240.0;
    private static final int GRID_LAST = 4;

    /** Das Feld des Rasters, in dem diese Koordinate liegt (0 bis 4); -1, wenn sie ausserhalb des Gartens liegt */
    static int plotCell(double v) {
        if (v < GRID_MIN || v > GRID_MAX) return -1;
        return Math.min(GRID_LAST, (int) Math.floor((v - GRID_MIN) / PLOT_SIZE));
    }

    /**
     * Gehoert ein Pest an dieser Stelle zur Markierung? Bei "Whole garden" immer. Bei "Current plot" nur, wenn er auf dem Plot steht, auf dem
     * man selbst steht. Steht man ausserhalb des Rasters, gibt es keinen eigenen Plot - dann zaehlt jeder, statt alle zu verschlucken.
     * Die Hoehe zaehlt nicht: Ein Plot ist eine Saeule.
     */
    public static boolean inRange(double playerX, double playerZ, double x, double z) {
        if (cfg().range != ModConfig.PestRange.PLOT) return true;
        int mineX = plotCell(playerX);
        int mineZ = plotCell(playerZ);
        if (mineX < 0 || mineZ < 0) return true;
        return plotCell(x) == mineX && plotCell(z) == mineZ;
    }

    /** Der Name, den das Spiel dem Pest gibt, ohne Beiwerk ("Mite", nicht "§cMite 100/100") */
    public static String displayName(String rawName) {
        return CustomMob.cleanPattern(rawName);
    }

    /** Ist das der Name eines Pests? Genau: "Rat" ja, "Rat Catcher" nein */
    public static boolean isPest(String rawName) {
        return NAMES.contains(displayName(rawName).toLowerCase(Locale.ROOT));
    }

    @Override
    public String label() {
        return "Pest";
    }

    @Override
    public int glowColorRGB() {
        return COLOUR;
    }

    // Eigene Schalter, nicht die der Mob-Liste
    @Override
    public boolean highlight() {
        return enabled() && cfg().highlight;
    }

    @Override
    public boolean tracer() {
        return enabled() && cfg().line;
    }

    @Override
    public boolean nameplate() {
        return enabled() && cfg().nameplate;
    }

    @Override
    public boolean box() {
        return enabled() && cfg().box;
    }
}
