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
