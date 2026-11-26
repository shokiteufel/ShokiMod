package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.ModConfig;

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

    // ------------------------------------------------------------------ Fettdruck (HUD > Bold numbers)

    /**
     * Ist der Schalter "Bold numbers" an? Ohne Konfiguration (ganz frueher Start, Test) gilt die Vorgabe: an.
     *
     * Fett gibt es nur beim Anzeigen (Kasten, Tooltip): Der Wert selbst - ItemValue.format, ShortenCoins, alles, was auch als Klartext in Chat oder
     * Befehle geht - bekommt nie ein "§l".
     */
    public static boolean bold() {
        ModConfig c = ModConfig.INSTANCE;
        return c == null || c.hud == null || c.hud.boldNumbers;
    }

    /** Das Steuerzeichen fuer Fettdruck */
    private static final String BOLD = "§l";

    /** Ein Farbcode oder "Zurueck" (§0-9, a-f, r): Er hebt den Fettdruck auf, nach ihm muss er neu gesetzt werden */
    private static boolean colourCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || (code >= 'A' && code <= 'F') || code == 'r' || code == 'R';
    }

    private static boolean boldCodeAt(String text, int at) {
        return at + 1 < text.length() && text.charAt(at) == '§' && (text.charAt(at + 1) == 'l' || text.charAt(at + 1) == 'L');
    }

    /**
     * Den ganzen Text fett: "§l" davor und nach jedem Farbcode noch einmal, denn ein Farbcode nimmt den Fettdruck wieder weg ("§6§l5k" ist fett,
     * "§l§65k" nicht). Was schon fett ist, bekommt kein zweites "§l"; leer bleibt leer. Gilt immer, ohne den Schalter zu fragen - wer ihn
     * beachten will, fragt {@link #bold()} selbst.
     */
    public static String bolden(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder out = new StringBuilder(text.length() + 8);
        if (!boldCodeAt(text, 0) && !(text.length() > 1 && text.charAt(0) == '§' && colourCode(text.charAt(1)))) out.append(BOLD);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c);
            if (c == '§' && i + 1 < text.length() && colourCode(text.charAt(i + 1))) {
                out.append(text.charAt(i + 1));
                i++;
                if (!boldCodeAt(text, i + 1)) out.append(BOLD);
            }
        }
        return out.toString();
    }

    /**
     * Eine Betragszahl, die gleich hinter einem Farbcode von Gold, Rot oder Grau steht und vor einem weiteren Farbcode, dem Zeilenende oder
     * " coins" endet: "§6150k", "§c-5.2M", "§61,234". Namen und Beschriftungen beginnen nicht mit einer Ziffer und bleiben damit schlicht.
     */
    private static final java.util.regex.Pattern COINS = java.util.regex.Pattern.compile(
            "(§[67cC])(-?\\d[\\d,]*(?:\\.\\d+)?[kKmMbBtTpPeE]?)(?=§|$| coins)");

    /**
     * Die Betraege in einer fertigen Zeile fett, der Rest bleibt: "§7(§6150k§7)" wird "§7(§6§l150k§7)". Fuer Zeilen, die ein anderer Teil schon
     * mit Farbcodes gebaut hat (Estimated Item Value, Basar) und die dort - weil auch Tests ihren Text pruefen - unveraendert bleiben. Gilt
     * immer, ohne den Schalter zu fragen.
     */
    public static String boldCoins(String line) {
        if (line == null || line.indexOf('§') < 0) return line;
        return COINS.matcher(line).replaceAll("$1§l$2");
    }

    /** {@link #boldCoins}, aber nur wenn "Bold numbers" an ist */
    public static String styleCoins(String line) {
        return bold() ? boldCoins(line) : line;
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
