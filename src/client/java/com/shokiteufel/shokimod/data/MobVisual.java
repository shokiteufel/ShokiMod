package com.shokiteufel.shokimod.data;

/**
 * Eine Mob-Art, die hervorgehoben werden kann - als Umriss (Highlight), als Linie
 * vom Fadenkreuz (Tracer) und als Namensschild.
 *
 * Umgesetzt wird das von {@link CustomMob} für selbst hinzugefügte Mobs und von
 * {@link SparklingTarget} für seltene Critter. Die drei Gesamtschalter liegen in
 * den Einstellungen; jede Art darf zusätzlich eigene Bedingungen anlegen.
 *
 * Die Beschriftung trägt Farbcodes direkt im Text, weil die Listen im
 * Einstellungsfenster toString() zur Anzeige benutzen. Die Farbe wird zweimal
 * gebraucht: als RGB für den Glow-Umriss und als ARGB für die Tracer-Linie.
 */
public interface MobVisual {

    String label();

    int glowColorRGB();

    /** Anzeigename ohne Farbcodes, für Stellen die die Farbe selbst setzen */
    default String plainLabel() {
        return label().replaceAll("§.", "");
    }

    default int tracerColorARGB() {
        return 0xFF000000 | glowColorRGB();
    }

    default boolean highlight() {
        return master() && ModConfig.INSTANCE.mobVisuals.enableHighlight;
    }

    default boolean tracer() {
        return master() && ModConfig.INSTANCE.mobVisuals.enableTracer;
    }

    default boolean nameplate() {
        return master() && ModConfig.INSTANCE.mobVisuals.enableNameplate;
    }

    /** Ein Kasten um die Trefferbox - die zweite Hervorhebung neben dem Glow */
    default boolean box() {
        return master() && ModConfig.INSTANCE.mobVisuals.enableBox;
    }

    /** Der Hauptschalter des Reiters. Aus heisst: nichts davon, ohne etwas zu verlieren */
    static boolean master() {
        return ModConfig.INSTANCE.mobVisuals.masterEnabled;
    }

    /** Nur wenn wenigstens eine Anzeige an ist, lohnt das Suchen überhaupt */
    default boolean anyEnabled() {
        return highlight() || tracer() || nameplate() || box();
    }
}
