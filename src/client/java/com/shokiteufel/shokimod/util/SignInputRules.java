package com.shokiteufel.shokimod.util;

/**
 * Welche Schilder von SkyBlock sind Zahlenschilder (Gebot, Preis, Menge) und welche nicht? Rein: keine Minecraft-Typen, die vier Zeilen kommen als Texte.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code AbstractSignEditScreenMixin}). Von dort stammen die Kennzeichen: Hypixel schreibt in die zweite Zeile fuenfzehn
 * Pfeile ({@code ^^^^^^^^^^^^^^^}) unter die Eingabe und in die dritte und vierte Zeile, was man eingeben soll ("Enter price" / "per unit", "Your auction" /
 * "starting bid"); Suchschilder (/recipes, /shards, /bestiary: "... your" / "... query"), das Schild fuer einen Namen ("Enter name") sind keine Zahlenschilder; ein
 * Preisschild hat in der dritten Zeile das Wort "price". Die Umsetzung ist eigene Arbeit.
 */
public final class SignInputRules {

    /** Die zweite Zeile eines Eingabeschildes */
    static final String INPUT_MARKER = "^^^^^^^^^^^^^^^";
    /** Kurze Form, zum Beispiel bei der Menge fuer Super Craft */
    static final String ALT_INPUT_MARKER = "^^^^^^";
    static final String NAME_PROMPT = "Enter name";

    private SignInputRules() {
    }

    /** Ist das ein Schild, auf dem man eine Zahl eingibt? {@code lines} sind die vier Zeilen des Schildes (die erste ist die Eingabe) */
    public static boolean isNumberSign(String[] lines) {
        if (lines == null || lines.length < 4) return false;
        String marker = lines[1];
        String prompt = lines[2];
        if (marker == null || prompt == null) return false;
        if (!INPUT_MARKER.equals(marker) && !ALT_INPUT_MARKER.equals(marker)) return false;
        if (prompt.endsWith("your") || prompt.endsWith("query")) return false;
        return !NAME_PROMPT.equals(prompt);
    }

    /** Ein Preisschild: Hypixel nimmt dort Nachkommastellen; bei allen anderen (Gebot, Menge) eine ganze Zahl */
    public static boolean isPriceSign(String[] lines) {
        return lines != null && lines.length >= 3 && lines[2] != null && lines[2].contains("price");
    }
}
