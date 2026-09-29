package com.shokiteufel.shokimod.render.hud;

import java.util.Locale;

/**
 * Stueckzahlen, wie sie in einen Kasten passen.
 *
 * Ein Kasten ist schmal, und eine Zahl wie 1.284.393 schiebt die Spalte daneben aus dem
 * Bild. Ab zehntausend wird deshalb gekuerzt - darunter nicht, weil "9.999" noch passt
 * und genauer ist als "10,0k".
 *
 * Abgeschnitten wird nichts, was etwas aussagt: "10k" statt "10,0k", "1,25M" statt
 * "1,3M". Was hinter dem Komma nur eine Null ist, faellt weg.
 */
public final class HudNumbers {

    private HudNumbers() {
    }

    private static final double[] TEILER = {1_000.0, 1_000_000.0, 1_000_000_000.0};
    private static final String[] ZEICHEN = {"k", "M", "B"};
    /** Wie viele Nachkommastellen je Einheit - bei Millionen lohnt eine mehr */
    private static final int[] STELLEN = {1, 2, 1};

    /** "9.999", "12.3k", "1.25M", "2.5B" */
    public static String amount(long value) {
        long abs = Math.abs(value);
        if (abs < 10_000L) return String.format(Locale.ROOT, "%,d", value);

        int einheit = abs >= 1_000_000_000L ? 2 : abs >= 1_000_000L ? 1 : 0;
        String text = trim(value / TEILER[einheit], STELLEN[einheit]);
        // Gerundet wird aus 999.999 sonst ein "1000k" - das gehoert eine Einheit hoeher
        if (einheit + 1 < TEILER.length && vierstellig(text)) {
            einheit++;
            text = trim(value / TEILER[einheit], STELLEN[einheit]);
        }
        return text + ZEICHEN[einheit];
    }

    /** Steht vor dem Komma eine Tausend oder mehr? */
    private static boolean vierstellig(String text) {
        String zahl = text.startsWith("-") ? text.substring(1) : text;
        int punkt = zahl.indexOf('.');
        return (punkt < 0 ? zahl.length() : punkt) >= 4;
    }

    /** Die Zahl mit hoechstens so vielen Stellen, ohne Nullen am Ende */
    private static String trim(double value, int stellen) {
        String text = String.format(Locale.ROOT, "%." + stellen + "f", value);
        if (text.indexOf('.') < 0) return text;

        int ende = text.length();
        while (ende > 0 && text.charAt(ende - 1) == '0') ende--;
        if (ende > 0 && text.charAt(ende - 1) == '.') ende--;
        return text.substring(0, ende);
    }
}
