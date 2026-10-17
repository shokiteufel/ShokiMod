package com.shokiteufel.shokimod.scanner;

import java.util.regex.Pattern;

/**
 * Handgriffe fuer Tab-Zeilen, die mehrere Leser brauchen.
 *
 * String.trim() laesst geschuetzte Leerzeichen und Zeichen ohne Breite stehen - und Hypixel
 * schreibt gern damit. Wer eine Zeile mit "Pest Traps:" oder "Visitors:" sucht, soll daran nicht
 * scheitern.
 */
public final class TabText {

    /** Geschuetzte und schmale Leerzeichen, die String.trim() stehen laesst */
    private static final Pattern ODD_SPACE = Pattern.compile("[\\u00A0\\u2007\\u202F\\u2009\\u200A]");
    /** Unsichtbare Zeichen ohne Breite */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");

    private TabText() {
    }

    /** Die Zeile ohne unsichtbare Zeichen und mit gewoehnlichen Leerzeichen */
    public static String tidy(String line) {
        if (line == null) return "";
        String text = INVISIBLE.matcher(line).replaceAll("");
        return ODD_SPACE.matcher(text).replaceAll(" ").trim();
    }

    /**
     * Die Zeile mit Zeichencodes fuers Log: gewoehnliche Zeichen bleiben stehen, alle anderen
     * kommen als \\uXXXX. So sieht man, was Hypixel wirklich geschickt hat.
     */
    public static String codes(String raw) {
        StringBuilder codes = new StringBuilder();
        raw.codePoints().limit(40).forEach(c -> codes.append(c < 128 ? String.valueOf((char) c)
                : "\\u" + String.format("%04X", c)));
        return codes.toString();
    }
}
