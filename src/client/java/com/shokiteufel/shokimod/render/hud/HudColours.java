package com.shokiteufel.shokimod.render.hud;

/**
 * Die Schriftfarben der Anzeigen - Minecrafts eigene Palette.
 *
 * Hypixel schreibt seine Seitenleiste mit den Standard-Farbcodes, und die wirken
 * kraeftiger als selbst gemischte Toene. Der Grund ist die Saettigung: 55FF55 hat
 * einen tiefen Boden und volle Spitze, waehrend ein aufgehelltes 7CFF7C Richtung
 * Weiss zieht und dabei matt wird. Wer daneben steht, sieht den Unterschied sofort.
 *
 * Deshalb dieselben Werte wie das Spiel, damit die eigenen Kaesten neben Hypixels
 * Anzeigen nicht abfallen.
 */
public final class HudColours {

    public static final int WHITE = 0xFFFFFFFF;         // §f
    public static final int GRAY = 0xFFAAAAAA;          // §7
    public static final int DARK_GRAY = 0xFF555555;     // §8
    public static final int GREEN = 0xFF55FF55;         // §a
    public static final int AQUA = 0xFF55FFFF;          // §b
    public static final int YELLOW = 0xFFFFFF55;        // §e
    public static final int GOLD = 0xFFFFAA00;          // §6
    public static final int LIGHT_PURPLE = 0xFFFF55FF;  // §d
    public static final int RED = 0xFFFF5555;           // §c

    private HudColours() {
    }
}
