package com.shokiteufel.shokimod.data;

/**
 * Die vier Biome der Critter Safari.
 *
 * Reihenfolge und Farben wie in crittermod, damit die Anzeigen dieselbe Sprache sprechen.
 */
public enum SafariBiome {

    FOREST("Forest", 0x55FF55, 0x7CFF7C),
    CAVERN("Cavern", 0xFFAA00, 0xFFC93C),
    ICY("Icy", 0x55FFFF, 0x7CF5FF),
    // Das Dunkelviolett von crittermod verschwindet auf dem dunklen Kasten fast -
    // fuer die Schrift daher eine deutlich hellere Fassung
    HAUNTED("Haunted", 0xAA00AA, 0xE45FFF);

    private final String displayName;
    private final int colour;
    private final int textColour;

    SafariBiome(String displayName, int colour, int textColour) {
        this.displayName = displayName;
        this.colour = colour;
        this.textColour = textColour;
    }

    /** Hellere Fassung fuer Schrift auf dunklem Grund */
    public int textColour() {
        return textColour;
    }

    public String displayName() {
        return displayName;
    }

    public int colour() {
        return colour;
    }
}
