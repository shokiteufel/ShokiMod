package com.shokiteufel.shokimod.scanner;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der laufende Contest aus der Tab-Liste - Miria, Agatha oder Jacob.
 *
 * Gruppiert wird hier nichts: die Tab-Liste kommt ungeordnet an, im Mitschnitt lagen
 * Kopfzeile und Inhalt sechs Plaetze auseinander. Jede Zeile muss sich also an ihrer
 * eigenen Form erkennen lassen, nicht an ihrer Nachbarschaft.
 */
public final class TabContest {

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

    private static String host = "";
    private static String bracket = "";
    private static String amount = "";
    private static String next = "";
    private static String needed = "";
    private static String time = "";

    private TabContest() {
    }

    public static boolean isActive() {
        return !host.isEmpty();
    }

    /** Wer den Contest ausrichtet, etwa "Miria" */
    public static String host() {
        return host;
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

    /** Restzeit, wie Hypixel sie in der Seitenleiste schreibt ("0m35s"). Leer wenn unbekannt */
    public static String time() {
        return time;
    }

    public static void reset() {
        host = "";
        bracket = "";
        amount = "";
        next = "";
        needed = "";
        time = "";
    }

    /**
     * Die Seitenleiste fuehrt denselben Contest, aber mit Restzeit - und in
     * Anzeigereihenfolge, anders als die Tab-Liste.
     */
    public static void processSidebar(List<String> lines) {
        for (String raw : lines) {
            String line = raw.replaceAll("§.", "").trim();
            Matcher header = SIDEBAR_HEADER.matcher(line);
            if (header.matches()) {
                time = header.group("time").trim();
                if (host.isEmpty()) host = header.group("who");
                return;
            }
        }
        time = "";
    }

    public static void processTabList(List<String> lines) {
        String foundHost = "";
        String foundBracket = "";
        String foundAmount = "";
        String foundNext = "";
        String foundNeeded = "";

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher header = HEADER.matcher(line);
            if (foundHost.isEmpty() && header.matches()) {
                foundHost = header.group("who");
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

        host = foundHost;
        // Ohne Kopfzeile laeuft kein Contest; dann gehoeren auch die Zahlen nicht hierher
        bracket = foundHost.isEmpty() ? "" : foundBracket;
        amount = foundHost.isEmpty() ? "" : foundAmount;
        next = foundHost.isEmpty() ? "" : foundNext;
        needed = foundHost.isEmpty() ? "" : foundNeeded;
    }
}
