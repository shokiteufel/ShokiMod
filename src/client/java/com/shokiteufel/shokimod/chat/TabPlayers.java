package com.shokiteufel.shokimod.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat (Teil 4) > Player Messages > Part Order: was die Tab-Liste ueber die Spieler weiss, die die Chatzeile nicht sagt - ob einer Ironman spielt (das Zeichen
 * {@code ♲} hinter dem Namen) und, auf der Crimson Isle, zu welcher Fraktion er gehoert (Barbar {@code ⚒}, Magier {@code ቾ}).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code AdvancedPlayerList}: {@code levelPattern}, {@code readPlayerData}, {@code CrimsonIsleFaction}). Von dort stammen die Regel
 * (eine Zeile mit {@code [Stufe] Name ...}, ohne "SB Level"), die Stelle des Namens (das Wort hinter der Stufe, bei einem Rang in Klammern das uebernaechste) und die
 * Zeichen. Die Umsetzung ist eigene Arbeit: Java, gelesen wird der Klartext der Zeilen, die {@code TabListScanner} schon vorhaelt.
 *
 * <p>Nicht gebaut: das Bingo-Rang-Zeichen (Teil "Bingo Level"). SkyHanni liest die Zeichen und ihre Raenge aus einer Datei seines Repos ({@code BingoRanks}), die nicht
 * im Quelltext liegt.
 */
public final class TabPlayers {

    /** Die Fraktion auf der Crimson Isle */
    public enum Faction { NONE, BARBARIAN, MAGE }

    /** Was ueber einen Spieler bekannt ist */
    public record Extra(boolean ironman, Faction faction) {
    }

    /** "[290] Skirtwearer ꀾ♲" - die Stufe in Klammern, dann der Rest; die Zeile "SB Level: [419] ..." ist keine Spielerzeile */
    private static final Pattern LEVEL_LINE = Pattern.compile("^(?!SB Level).*\\[([\\d,]+)] (.*)$");

    private TabPlayers() {
    }

    /**
     * Liest die Zeilen der Tab-Liste (ohne Farbcodes).
     *
     * @param lines       die Zeilen
     * @param crimsonIsle ob man auf der Crimson Isle steht (nur dort gibt es Fraktionen)
     * @return je Spielername, was die Liste ueber ihn sagt; leer, wenn nichts da ist
     */
    public static Map<String, Extra> read(List<String> lines, boolean crimsonIsle) {
        if (lines == null || lines.isEmpty()) return Map.of();
        Map<String, Extra> out = new HashMap<>();
        for (String line : lines) {
            if (line == null) continue;
            Matcher m = LEVEL_LINE.matcher(line);
            if (!m.matches()) continue;
            String[] words = m.group(2).split(" ");
            // Ein Rang in Klammern steht vor dem Namen: der Name ist dann das naechste Wort
            int index = m.group(2).contains("[") ? 1 : 0;
            if (index >= words.length) continue;
            String name = words[index];
            if (name.isEmpty()) continue;
            StringBuilder suffix = new StringBuilder();
            for (int i = index + 1; i < words.length; i++) suffix.append(words[i]).append(' ');
            boolean ironman = suffix.indexOf("♲") >= 0;
            Faction faction = Faction.NONE;
            if (crimsonIsle) {
                if (line.contains("⚒")) faction = Faction.BARBARIAN;
                else if (line.contains("ቾ")) faction = Faction.MAGE;
            }
            out.put(name, new Extra(ironman, faction));
        }
        return out;
    }

    /**
     * Der Spielername aus dem Namen einer Chatzeile ("[MVP+] lrg89" oder "lrg89"): Bei mehr als einem Wort das zweite, sonst das erste; ein {@code 's} faellt weg.
     */
    public static String nameOf(String author) {
        String[] split = author.trim().split(" ");
        String word = split.length > 1 ? split[1] : split[0];
        return word.endsWith("'s") ? word.substring(0, word.length() - 2) : word;
    }
}
