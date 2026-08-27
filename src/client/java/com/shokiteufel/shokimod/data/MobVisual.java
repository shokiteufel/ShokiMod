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
        return ModConfig.INSTANCE.mobVisuals.enableHighlight;
    }

    default boolean tracer() {
        return ModConfig.INSTANCE.mobVisuals.enableTracer;
    }

    default boolean nameplate() {
        return ModConfig.INSTANCE.mobVisuals.enableNameplate;
    }

    /** Nur wenn wenigstens eine Anzeige an ist, lohnt das Suchen überhaupt */
    default boolean anyEnabled() {
        return highlight() || tracer() || nameplate();
    }
}
