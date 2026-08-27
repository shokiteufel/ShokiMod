package com.shokiteufel.shokimod.data;

/**
 * Die vier Biome der Critter Safari.
 *
 * Reihenfolge und Farben wie in crittermod, damit die Anzeigen dieselbe Sprache sprechen.
 */
public enum SafariBiome {

    FOREST("Forest", 0x55FF55),
    CAVERN("Cavern", 0xFFAA00),
    ICY("Icy", 0x55FFFF),
    HAUNTED("Haunted", 0xAA00AA);

    private final String displayName;
    private final int colour;

    SafariBiome(String displayName, int colour) {
        this.displayName = displayName;
        this.colour = colour;
    }

    public String displayName() {
        return displayName;
    }

    public int colour() {
        return colour;
    }
}
