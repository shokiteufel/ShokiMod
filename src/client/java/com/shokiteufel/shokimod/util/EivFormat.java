package com.shokiteufel.shokimod.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Wie Estimated Item Value Zahlen schreibt.
 *
 * Herkunft der Regel: SkyHanni ({@code NumberUtil.shortFormat}). Ab tausend steht ein Buchstabe (k, M, B, T, P, E); dazu eine Nachkommastelle, solange
 * die Zahl darunter klein genug ist (bei k unter 10k, bei M unter 100M, bei B immer, bei T und darueber unter 10 Einheiten) - abgeschnitten, nicht
 * gerundet. So steht 56.1M da, 9.9k, aber 12k statt 12.3k.
 */
public final class EivFormat {

    private static final long[] DIVISORS = {1_000L, 1_000_000L, 1_000_000_000L, 1_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000_000L};
    private static final String[] SUFFIXES = {"k", "M", "B", "T", "P", "E"};

    private EivFormat() {
    }

    /** 1234 wird "1.2k", 56123456 wird "56.1M", 150000 wird "150k", 999 bleibt "999" */
    public static String shortFormat(double input) {
        double abs = Math.abs(input);
        if (abs < 1) return input == 0 ? "0" : trimDecimal(input);
        long value = (long) input;
        if (value < 0) return "-" + shortFormat(-(double) value);
        if (value < 1000) return Long.toString(value);

        int at = 0;
        for (int i = 0; i < DIVISORS.length; i++) {
            if (value >= DIVISORS[i]) at = i;
        }
        long divideBy = DIVISORS[at];
        String suffix = SUFFIXES[at];
        long truncated = value / (divideBy / 10);
        long truncatedAt = suffix.equals("M") ? 1000 : suffix.equals("B") ? 1_000_000 : 100;
        boolean hasDecimal = truncated < truncatedAt && truncated % 10 != 0;
        return hasDecimal ? (truncated / 10) + "." + (truncated % 10) + suffix : (truncated / 10) + suffix;
    }

    private static String trimDecimal(double value) {
        String text = String.format(Locale.US, "%.2f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** 1234567.8 wird "1,234,568" */
    public static String withSeparators(double value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(Math.round(value));
    }

    /** 6 wird "VI", 10 wird "X"; ausser 1 bis 3999 gibt es keine Schreibweise, dann die Zahl */
    public static String roman(int number) {
        if (number < 1 || number > 3999) return Integer.toString(number);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int rest = number;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                out.append(symbols[i]);
                rest -= values[i];
            }
        }
        return out.toString();
    }

    /** "§7(§6150k§7)": eine Summe in Klammern, grau umrahmt. Mit {@code gray} ganz grau (Preis, der nicht zaehlt) */
    public static String coinsInBrackets(double value, boolean gray) {
        return "§7(" + coin(value, gray) + "§7)";
    }

    /** "§6150k" - rot, wenn die Summe unter null liegt, grau, wenn sie nicht zaehlt */
    public static String coin(double value, boolean gray) {
        String colour = gray ? "§7" : value < 0 ? "§c" : "§6";
        return colour + shortFormat(value);
    }
}
