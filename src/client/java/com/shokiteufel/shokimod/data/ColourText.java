package com.shokiteufel.shokimod.data;

import io.github.notenoughupdates.moulconfig.ChromaColour;

/**
 * Farben als Text - in den Einstellungen und in den Banner-Designs.
 *
 * Der Farbwaehler der Einstellungen (MoulConfig) schreibt "Tempo:Deckkraft:R:G:B", etwa "0:255:255:85:85". Aeltere
 * Dateien und die Designs der Banner kennen nur sechs Hexstellen ("FF5555"). Beides wird gelesen. Geschrieben wird dort,
 * wo der Waehler die Farbe zeigt, sein Format - sonst koennte er den Wert nicht anzeigen.
 *
 * Eine Deckkraft, die der Waehler mitschreibt, bleibt unbeachtet: Ein Umriss um einen Mob oder eine Linie hat sie nicht.
 */
public final class ColourText {

    private ColourText() {
    }

    /** Die Farbe (RGB, ohne Deckkraft) aus Hexstellen oder dem Text des Waehlers; leer oder unlesbar heisst: der Ersatz */
    @SuppressWarnings("deprecation")
    public static int rgb(String text, int fallback) {
        if (text == null) return fallback;
        String t = text.trim();
        if (t.isEmpty()) return fallback;
        try {
            if (t.indexOf(':') >= 0) return pickerRgb(t, fallback);
            if (t.startsWith("#")) t = t.substring(1);
            if (t.length() != 6) return fallback;
            return Integer.parseInt(t, 16) & 0xFFFFFF;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Der Text des Waehlers: fuenf Zahlen mit Doppelpunkt, "Tempo:Deckkraft:R:G:B". Die Bibliothek liest Unsinn
     * ohne Fehler zu einer Farbe, deshalb wird hier erst geprueft, was dasteht.
     */
    @SuppressWarnings("deprecation")
    private static int pickerRgb(String text, int fallback) {
        String[] parts = text.split(":");
        if (parts.length != 5) return fallback;
        int[] n = new int[5];
        for (int i = 0; i < 5; i++) {
            n[i] = Integer.parseInt(parts[i].trim());
            if (n[i] < 0) return fallback;
        }
        if (n[1] > 255 || n[2] > 255 || n[3] > 255 || n[4] > 255) return fallback;
        // Ohne Tempo steht die Farbe fest; mit Tempo laeuft sie durch den Regenbogen und wird gelesen, wie sie gerade ist
        if (n[0] == 0) return (n[2] << 16) | (n[3] << 8) | n[4];
        return ChromaColour.specialToChromaRGB(text) & 0xFFFFFF;
    }

    /** Der Text fuer den Waehler, ohne Tempo und voll deckend */
    @SuppressWarnings("deprecation")
    public static String ofRgb(int rgb) {
        return ChromaColour.special(0, 255, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    /**
     * Was beim Laden in ein Feld mit Farbwaehler gehoert: Der Text des Waehlers bleibt, ein alter Hexwert wird
     * umgeschrieben, und was nicht zu lesen ist, wird durch den Ersatz ersetzt.
     */
    public static String forPicker(String text, int fallbackRgb) {
        if (text != null && text.indexOf(':') >= 0) return text;
        return ofRgb(rgb(text, fallbackRgb));
    }
}
