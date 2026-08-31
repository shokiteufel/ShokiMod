package com.shokiteufel.shokimod.scanner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Miries Contest aus der Tab-Liste.
 *
 * Gruppiert wird hier nichts: die Tab-Liste kommt ungeordnet an, im Mitschnitt lagen
 * Kopfzeile und Inhalt sechs Plaetze auseinander. Jede Zeile muss sich also an ihrer
 * eigenen Form erkennen lassen, nicht an ihrer Nachbarschaft.
 *
 * Hypixel schreibt in dieselben Zeilen auch fremde Contests - im Garden steht dort
 * Jacobs. Der traegt eigene Zahlen und eine eigene Restzeit; uebernommen wird deshalb
 * nur, was den hier erwarteten Ausrichter nennt.
 */
public final class TabContest {

    /** Wessen Contest dieses Panel fuehrt. Alles andere wird ueberlesen */
    public static final String HOST = "Miria";

    private static final Pattern HEADER = Pattern.compile("(?<who>[A-Za-z]+)'s Contest:");
    /**
     * Dieselbe Ueberschrift in der Seitenleiste - dort mit der Restzeit dahinter.
     * Das ist Hypixels eigene Angabe und damit die einzige verlaessliche Quelle dafuer.
     */
    private static final Pattern SIDEBAR_HEADER =
            Pattern.compile("(?<who>[A-Za-z]+)'s Contest\\s+(?<time>[\\dhms ]+)");
    /** Vor dem ersten Bracket zeigt Hypixel nur die gesammelte Menge */
    private static final Pattern COLLECTED = Pattern.compile("Collected (?<amount>[\\d,.]+)");
    /** Sobald man in einem Bracket ist: "UNCOMMON with 362" */
    private static final Pattern BRACKET = Pattern.compile("(?<bracket>[A-Z]{4,}) with (?<amount>[\\d,.]+)");
    /** Und was das naechste kostet: "Rare requires +638" */
    private static final Pattern NEXT = Pattern.compile("(?<next>[A-Za-z]+) requires \\+(?<needed>[\\d,.]+)");

    /** So lange gilt die zuletzt gelesene Restzeit weiter, auch ohne neuen Treffer */
    private static final long TIME_STICKY_MILLIS = 5000L;

    private static boolean active = false;
    private static String bracket = "";
    private static String amount = "";
    private static String next = "";
    private static String needed = "";
    private static String time = "";
    private static long timeSeenAt = 0L;

    private TabContest() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Das erreichte Bracket, leer solange man in keinem ist */
    public static String bracket() {
        return bracket;
    }

    /** Die eigene Menge - aus der Bracket-Zeile, sonst aus "Collected" */
    public static String amount() {
        return amount;
    }

    public static String next() {
        return next;
    }

    public static String needed() {
        return needed;
    }

    /**
     * Restzeit, wie Hypixel sie in der Seitenleiste schreibt ("0m35s"). Leer wenn unbekannt.
     *
     * Der Wert haelt einige Sekunden nach: Hypixel schreibt die Seitenleiste laufend neu,
     * dabei fehlt die Zeile immer wieder fuer einen Moment. Wuerde sie dann sofort als
     * unbekannt gelten, schaltete die Uhr staendig zwischen dieser Angabe und der
     * eigenen Rechnung hin und her - und weil beide leicht auseinanderliegen, zappelt
     * die Anzeige.
     */
    public static String time() {
        return System.currentTimeMillis() - timeSeenAt < TIME_STICKY_MILLIS ? time : "";
    }

    public static void reset() {
        active = false;
        bracket = "";
        amount = "";
        next = "";
        needed = "";
        time = "";
        timeSeenAt = 0L;
    }

    /**
     * Die Seitenleiste fuehrt denselben Contest, aber mit Restzeit - und in
     * Anzeigereihenfolge, anders als die Tab-Liste.
     */
    public static void processSidebar(List<String> lines) {
        for (String raw : lines) {
            String line = raw.replaceAll("§.", "").trim();
            Matcher header = SIDEBAR_HEADER.matcher(line);
            if (header.matches() && HOST.equalsIgnoreCase(header.group("who"))) {
                time = header.group("time").trim();
                timeSeenAt = System.currentTimeMillis();
                return;
            }
        }
        // Kein Treffer heisst nicht "weg" - die Zeile kann einen Moment fehlen.
        // Der Wert altert von selbst aus, siehe time()
    }

    public static void processTabList(List<String> lines) {
        boolean foundHeader = false;
        String foundBracket = "";
        String foundAmount = "";
        String foundNext = "";
        String foundNeeded = "";

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                // Ein fremder Ausrichter macht die ganze Liste unbrauchbar: die Zahlen
                // darunter gehoeren dann zu seinem Contest, nicht zu unserem
                if (!HOST.equalsIgnoreCase(header.group("who"))) {
                    foundHeader = false;
                    break;
                }
                foundHeader = true;
                continue;
            }

            Matcher inBracket = BRACKET.matcher(line);
            if (foundBracket.isEmpty() && inBracket.matches()) {
                foundBracket = inBracket.group("bracket");
                foundAmount = inBracket.group("amount");
                continue;
            }

            Matcher collected = COLLECTED.matcher(line);
            if (foundAmount.isEmpty() && collected.matches()) {
                foundAmount = collected.group("amount");
                continue;
            }

            Matcher upcoming = NEXT.matcher(line);
            if (foundNext.isEmpty() && upcoming.matches()) {
                foundNext = upcoming.group("next");
                foundNeeded = upcoming.group("needed");
            }
        }

        active = foundHeader;
        // Ohne Kopfzeile laeuft kein Contest; dann gehoeren auch die Zahlen nicht hierher
        bracket = foundHeader ? foundBracket : "";
        amount = foundHeader ? foundAmount : "";
        next = foundHeader ? foundNext : "";
        needed = foundHeader ? foundNeeded : "";
    }
}
