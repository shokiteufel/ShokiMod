package com.shokiteufel.shokimod.chat;

import java.util.regex.Pattern;

/**
 * Eine Regel, die eine Chatzeile (ohne Farbcodes) erkennt: wortgleich, am Anfang, mit einem Wortstueck oder als Muster fuer die ganze Zeile.
 *
 * Der Filter laeuft bei jeder Zeile des Chats, deshalb steht vor jedem Muster ein billiger Vorfilter: ein Wortstueck, das in jeder Zeile stehen muss,
 * die das Muster trifft ({@code contains} kostet einen Bruchteil eines Musters). Die Muster sind einmal uebersetzt und liegen in {@code static final}.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ChatFilter}): Dort stehen die Meldungen als Listen "genau diese Zeile", "Zeile trifft dieses Muster",
 * "Zeile beginnt so" und "Zeile enthaelt das". Die Umsetzung ist eigene Arbeit: Java, eine Regelart statt vier Listen, der Vorfilter, und alle Muster
 * gegen den Klartext der Zeile statt gegen die Fassung mit Farbcodes.
 */
public record ChatRule(Kind kind, String text, Pattern pattern) {

    public enum Kind {
        EXACT, STARTS_WITH, CONTAINS, REGEX
    }

    /** Die Zeile ist genau dieser Text */
    public static ChatRule exact(String text) {
        return new ChatRule(Kind.EXACT, text, null);
    }

    /** Die Zeile beginnt mit diesem Text */
    public static ChatRule startsWith(String text) {
        return new ChatRule(Kind.STARTS_WITH, text, null);
    }

    /** Die Zeile enthaelt diesen Text */
    public static ChatRule contains(String text) {
        return new ChatRule(Kind.CONTAINS, text, null);
    }

    /** Die ganze Zeile trifft das Muster; {@code needle} muss in jeder Zeile stehen, die es trifft (der Vorfilter), leer heisst ohne */
    public static ChatRule regex(String needle, String regex) {
        return new ChatRule(Kind.REGEX, needle, Pattern.compile(regex));
    }

    /** Ein Muster ohne Vorfilter */
    public static ChatRule regex(String regex) {
        return regex("", regex);
    }

    public boolean hits(String line) {
        switch (kind) {
            case EXACT:
                return line.equals(text);
            case STARTS_WITH:
                return line.startsWith(text);
            case CONTAINS:
                return line.contains(text);
            default:
                return (text.isEmpty() || line.contains(text)) && pattern.matcher(line).matches();
        }
    }

    /** Trifft eine der Regeln? */
    public static boolean any(ChatRule[] rules, String line) {
        for (int i = 0; i < rules.length; i++) {
            if (rules[i].hits(line)) return true;
        }
        return false;
    }

    /** Die Regeln mehrerer Gruppen hintereinander */
    public static ChatRule[] join(ChatRule[]... groups) {
        int n = 0;
        for (ChatRule[] g : groups) n += g.length;
        ChatRule[] out = new ChatRule[n];
        int at = 0;
        for (ChatRule[] g : groups) {
            System.arraycopy(g, 0, out, at, g.length);
            at += g.length;
        }
        return out;
    }
}
