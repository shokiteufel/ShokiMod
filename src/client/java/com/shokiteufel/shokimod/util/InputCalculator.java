package com.shokiteufel.shokimod.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Der Rechner hinter UI & Visuals > Input Calculator: rechnet, was man in ein Zahlenschild von SkyBlock tippt (Gebot, Basar-Preis, Menge). Rein: keine Minecraft-Typen.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SignCalculator}, {@code Calculator}). Von dort stammen das Verhalten (unter der Eingabe steht in Gruen
 * "Eingabe = Ergebnis" mit Tausenderpunkten, ein Fehler steht in Rot; beim Fertigmachen des Schildes ersetzt das Ergebnis die Rechnung; Mengen werden auf ganze Zahlen
 * gerundet, Preise auf zwei Nachkommastellen) und die Schreibweise k, m, b fuer Tausend, Million, Milliarde. Die Umsetzung ist eigene Arbeit: ein kleiner Parser mit
 * Rangfolge (Potenz vor Punkt vor Strich, Klammern, ein Vorzeichen vor einer Zahl), ohne Funktionen und ohne Konstanten; Skyblocker kennt dazu sqrt, log, sin und mehr.
 *
 * <p>Zeichen: Zahlen (mit Punkt als Komma, nie mit Komma oder Unterstrich), {@code + - * / % ^}, Klammern, Leerzeichen zwischen den Teilen; ein {@code k}, {@code m},
 * {@code b} oder {@code t} direkt hinter einer Zahl (Tausend, Million, Milliarde, Billion). Gross und klein ist gleich.
 */
public final class InputCalculator {

    /** Die Zeile eines Schildes hat Platz fuer so viele Zeichen */
    public static final int SIGN_LINE_LENGTH = 15;

    /** Das Ergebnis einer Rechnung: eine Zahl, oder ein Fehler mit Text, oder "nichts eingegeben" (weder noch) */
    public record Result(boolean ok, double value, String error) {

        static Result of(double value) {
            return new Result(true, value, null);
        }

        static Result failed(String error) {
            return new Result(false, 0d, error);
        }

        /** Es wurde nichts eingegeben: kein Ergebnis und kein Fehler */
        public boolean isEmpty() {
            return !ok && error == null;
        }
    }

    private static final Result EMPTY = new Result(false, 0d, null);

    private InputCalculator() {
    }

    private static final class CalcException extends Exception {
        CalcException(String message) {
            super(message, null, false, false);
        }
    }

    /** Rechnet die Eingabe aus. Leer (oder nur Leerzeichen): {@link Result#isEmpty()}. Ein Fehler hat einen kurzen englischen Text */
    public static Result evaluate(String input) {
        if (input == null || input.isBlank()) return EMPTY;
        Parser parser = new Parser(input);
        try {
            double value = parser.parseAll();
            if (Double.isNaN(value) || Double.isInfinite(value)) return Result.failed("Result is too large");
            return Result.of(value == 0d ? 0d : value);
        } catch (CalcException e) {
            return Result.failed(e.getMessage());
        }
    }

    /** Die Zahl mit Tausenderkommas und bis zu zwei Nachkommastellen: {@code 45,656,445,465}, {@code 1,500.5} */
    public static String format(double value) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(value == 0d ? 0d : value);
    }

    /** Steht eine Rechnung da (ein Zeichen fuer eine Rechenart oder eine Klammer), und nicht nur eine Zahl, auch eine mit k, m, b? Ein Minus vorn gehoert zur Zahl */
    public static boolean isExpression(String input) {
        if (input == null) return false;
        String text = input.trim();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '+' || c == '*' || c == '/' || c == '%' || c == '^' || c == '(' || c == ')') return true;
            if (c == '-' && i > 0) return true;
        }
        return false;
    }

    /**
     * Was beim Fertigmachen des Schildes in die erste Zeile kommt: bei einer Rechnung ihr Ergebnis (Menge: ganze Zahl; Preis: bis zu zwei Nachkommastellen), sonst das, was
     * dastand - auch bei einem Fehler, einem negativen oder einem Ergebnis, das nicht in eine Zeile passt ({@value #SIGN_LINE_LENGTH} Zeichen). Eine Zahl mit k oder m
     * bleibt, wie sie ist: Das versteht Hypixel selbst.
     */
    public static String replacement(String input, boolean price) {
        if (input == null || !isExpression(input)) return input;
        Result result = evaluate(input);
        if (!result.ok() || result.value() < 0d) return input;
        String text = price
                ? new BigDecimal(result.value()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                : Long.toString(Math.round(result.value()));
        return text.length() > SIGN_LINE_LENGTH ? input : text;
    }

    // ------------------------------------------------------------------ Parser

    private static final class Parser {
        private final String text;
        private int pos = 0;
        /** Wie viele Klammern gerade offen sind: Eine schliessende Klammer mitten in einer Rechnung ("()") ist eine leere Rechnung, keine ueberzaehlige Klammer */
        private int depth = 0;

        Parser(String text) {
            this.text = text;
        }

        double parseAll() throws CalcException {
            double value = parseSum();
            skipSpaces();
            if (pos < text.length()) throw leftover(text.charAt(pos));
            return value;
        }

        /** Hinter einer vollstaendigen Rechnung steht noch etwas: eine Klammer zu viel, ein falsches Zeichen, oder eine Zahl ohne Rechenzeichen davor */
        private CalcException leftover(char c) {
            if (c == ')') return new CalcException("Unbalanced parenthesis");
            if (c == ',' || c == '_') return new CalcException("Invalid number (use a dot for decimals, no separators)");
            if ((c >= '0' && c <= '9') || c == '.' || c == '(' || c == ' ') return new CalcException("Invalid equation");
            return new CalcException("Invalid character '" + c + "'");
        }

        private void skipSpaces() {
            while (pos < text.length() && text.charAt(pos) == ' ') pos++;
        }

        private boolean take(char c) {
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        /** Strich: Summe und Unterschied, von links nach rechts */
        private double parseSum() throws CalcException {
            double value = parseProduct();
            while (true) {
                if (take('+')) value += parseProduct();
                else if (take('-')) value -= parseProduct();
                else return value;
            }
        }

        /** Punkt: Mal, Geteilt, Rest, von links nach rechts */
        private double parseProduct() throws CalcException {
            double value = parseSigned();
            while (true) {
                if (take('*')) {
                    value *= parseSigned();
                } else if (take('/')) {
                    double divisor = parseSigned();
                    if (divisor == 0d) throw new CalcException("Division by zero");
                    value /= divisor;
                } else if (take('%')) {
                    double divisor = parseSigned();
                    if (divisor == 0d) throw new CalcException("Division by zero");
                    value %= divisor;
                } else {
                    return value;
                }
            }
        }

        /** Ein Vorzeichen vor der Potenz: {@code -2^2} ist minus vier */
        private double parseSigned() throws CalcException {
            if (take('-')) return -parseSigned();
            if (take('+')) return parseSigned();
            return parsePower();
        }

        /** Potenz: von rechts nach links ({@code 2^3^2} ist 2 hoch 9); der Hochwert darf ein Vorzeichen haben */
        private double parsePower() throws CalcException {
            double base = parseAtom();
            if (take('^')) return Math.pow(base, parseSigned());
            return base;
        }

        private double parseAtom() throws CalcException {
            skipSpaces();
            if (pos >= text.length()) throw new CalcException("Invalid equation");
            char c = text.charAt(pos);
            if (c == '(') {
                pos++;
                depth++;
                double value = parseSum();
                if (!take(')')) throw new CalcException("Unbalanced parenthesis");
                depth--;
                return value;
            }
            if ((c >= '0' && c <= '9') || c == '.') return parseNumber();
            if (c == ')') throw new CalcException(depth > 0 ? "Invalid equation" : "Unbalanced parenthesis");
            if (c == '*' || c == '/' || c == '%' || c == '^') throw new CalcException("Invalid equation");
            throw leftover(c);
        }

        private double parseNumber() throws CalcException {
            int start = pos;
            int dots = 0;
            while (pos < text.length() && ((text.charAt(pos) >= '0' && text.charAt(pos) <= '9') || text.charAt(pos) == '.')) {
                if (text.charAt(pos) == '.') dots++;
                pos++;
            }
            String digits = text.substring(start, pos);
            if (dots > 1 || digits.equals(".")) throw new CalcException("Invalid number");
            double value;
            try {
                value = Double.parseDouble(digits);
            } catch (NumberFormatException e) {
                throw new CalcException("Invalid number");
            }
            if (pos < text.length()) {
                double factor = switch (Character.toLowerCase(text.charAt(pos))) {
                    case 'k' -> 1e3;
                    case 'm' -> 1e6;
                    case 'b' -> 1e9;
                    case 't' -> 1e12;
                    default -> 0d;
                };
                if (factor != 0d) {
                    pos++;
                    value *= factor;
                }
            }
            return value;
        }
    }
}
