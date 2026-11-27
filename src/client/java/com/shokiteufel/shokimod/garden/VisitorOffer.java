package com.shokiteufel.shokimod.garden;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das Angebot eines Besuchers im Garden, gelesen aus dem Fenster, das beim Anklicken aufgeht.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorApi}, {@code VisitorTooltipParser}, {@code GardenVisitorTooltip}, {@code UtilsPatterns}). Von dort stammen die
 * Tatsachen: Das Fenster hat auf Platz 13 das Schild des Besuchers (vier Zeilen, die letzte "Offers Accepted: N"), auf Platz 29 den Knopf "Accept Offer" und auf Platz
 * 33 "Refuse Offer"; in der Beschreibung des ersten steht "Items Required:" mit den verlangten Gegenstaenden ("Enchanted Carrot x3"), nach einer Leerzeile "Rewards:" mit
 * "+20 Copper", "+15 Garden Experience" und eventuellen Gegenstaenden, und "Click to give!", sobald alles da ist. Eine Zeile nennt einen Gegenstand mit Menge entweder
 * hinten ("Name x3") oder vorn ("8x Name"); ein Buch heisst "Turbo-Cacti I Book", sein Gegenstand "Turbo-Cacti I". Die Umsetzung ist eigene Arbeit: Java, der Abgleich
 * laeuft gegen den Text ohne Farbcodes, alles hier ist rein (ohne Spielzustand).
 */
public final class VisitorOffer {

    /** Platz mit dem Schild des Besuchers */
    public static final int INFO_SLOT = 13;
    /** Platz mit dem Knopf "Accept Offer" */
    public static final int ACCEPT_SLOT = 29;
    /** Platz mit dem Knopf "Refuse Offer" */
    public static final int REFUSE_SLOT = 33;

    public static final String ACCEPT_NAME = "Accept Offer";
    public static final String REFUSE_NAME = "Refuse Offer";
    static final String ITEMS_HEADER = "Items Required:";
    static final String READY_LINE = "Click to give!";

    /** "Enchanted Carrot x3": der Name, dann " x" und die Menge am Ende */
    private static final Pattern AMOUNT_BEHIND = Pattern.compile("^(?<name>.+?) x(?<amount>[\\d,]+)$");
    /** "8x Enchanted Pork" oder "+8 Name": die Menge vorn, mit k oder m moeglich */
    private static final Pattern AMOUNT_BEFORE = Pattern.compile("^\\+?(?<amount>\\d[\\d.,]*[km]?)x? (?<name>.+)$");
    private static final Pattern BOOK = Pattern.compile("^(?<name>.* [IVX]+) Book$");
    private static final Pattern COPPER = Pattern.compile("^\\+?(?<amount>[\\d,]+) Copper(?: .*)?$");
    private static final Pattern GARDEN_EXP = Pattern.compile("^\\+?(?<amount>[\\d,]+) Garden Experience$");
    private static final Pattern OFFERS_ACCEPTED = Pattern.compile("^Offers Accepted: (?<amount>\\d+)$");

    /** Ein Gegenstand mit Menge: der Name ohne Farbcodes und die Zahl (ohne Angabe eins) */
    public record ItemAmount(String name, int amount) {
    }

    /**
     * Was in der Beschreibung von "Accept Offer" steht.
     *
     * @param required     die verlangten Gegenstaende in der Reihenfolge der Beschreibung
     * @param rewards      die Gegenstaende unter "Rewards:" (ohne Copper und Garden Experience)
     * @param copper       die Copper-Belohnung, oder -1, wenn die Beschreibung keine nennt
     * @param gardenExp    die Garden Experience, oder -1
     * @param readyToGive  steht "Click to give!" dort - hat man alles dabei?
     */
    public record Parsed(Map<String, Integer> required, List<ItemAmount> rewards, int copper, int gardenExp, boolean readyToGive) {

        public boolean hasCopper() {
            return copper >= 0;
        }
    }

    private VisitorOffer() {
    }

    /** Eine Zeile ohne Farbcodes und Rand, ohne das Herz hinter den Belohnungen eines "charmed" Besuchers */
    static String clean(String raw) {
        if (raw == null) return "";
        return stripMarker(raw.replaceAll("§.", "").trim());
    }

    /**
     * Nimmt das Zeichen weg, das Hypixel hinter eine Belohnung setzt: ein Herz ("Visitors' Gratitude ♥"). Bis 2.5.14 hiess das nur {@code ❤} (U+2764, SkyHannis
     * Zeichen); Hypixel kann ebenso {@code ♥} (U+2665) oder ein Herz mit Varianten-Zeichen (U+FE0F) schreiben, und dann passte der Name keiner Belohnung mehr - sie
     * blieb unerkannt und nichts wurde gesperrt. Darum faellt jetzt jedes Zeichen der Art "Symbol" am Ende weg, samt Leerraum und Varianten-Zeichen davor.
     * Buchstaben, Ziffern und Satzzeichen ("Click to give!", "Rewards:", "Dedication IV") bleiben.
     */
    static String stripMarker(String text) {
        if (text == null) return "";
        int end = text.length();
        while (end > 0) {
            int codePoint = text.codePointBefore(end);
            if (!isMarker(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return text.substring(0, end).trim();
    }

    private static boolean isMarker(int codePoint) {
        // Leerraum (auch das geschuetzte Leerzeichen), Varianten-Zeichen und der Verbinder, die ein Herz begleiten koennen
        if (Character.isWhitespace(codePoint) || codePoint == 0x00A0 || codePoint == 0x202F) return true;
        if (codePoint == 0xFE0E || codePoint == 0xFE0F || codePoint == 0x200D) return true;
        return Character.getType(codePoint) == Character.OTHER_SYMBOL;
    }

    private static long number(String text) {
        try {
            return Long.parseLong(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Eine Menge wie "8", "1,000", "1.5k" oder "2m" als ganze Zahl; -1, wenn sie nicht zu lesen ist */
    static int amountOf(String text) {
        if (text == null || text.isEmpty()) return -1;
        double factor = 1.0;
        String digits = text;
        char last = text.charAt(text.length() - 1);
        if (last == 'k') {
            factor = 1_000.0;
            digits = text.substring(0, text.length() - 1);
        } else if (last == 'm') {
            factor = 1_000_000.0;
            digits = text.substring(0, text.length() - 1);
        }
        try {
            double value = Double.parseDouble(digits.replace(",", "")) * factor;
            if (value < 0 || value > Integer.MAX_VALUE) return -1;
            return (int) Math.round(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Eine Zeile mit einem Gegenstand und seiner Menge, oder null bei einer leeren Zeile. Ohne Angabe ist die Menge eins.
     * "Enchanted Carrot x3", "8x Enchanted Pork" und "Dedication IV" sind alle drei Zeilen dieser Art.
     */
    public static ItemAmount parseItem(String line) {
        String text = clean(line);
        if (text.isEmpty()) return null;
        Matcher behind = AMOUNT_BEHIND.matcher(text);
        if (behind.matches()) {
            int amount = amountOf(behind.group("amount"));
            if (amount > 0) return new ItemAmount(bookName(behind.group("name").trim()), amount);
        }
        Matcher before = AMOUNT_BEFORE.matcher(text);
        if (before.matches()) {
            int amount = amountOf(before.group("amount"));
            if (amount > 0) return new ItemAmount(bookName(before.group("name").trim()), amount);
        }
        return new ItemAmount(bookName(text), 1);
    }

    /** "Turbo-Cacti I Book" ist der Gegenstand "Turbo-Cacti I" (so schreibt ihn auch der Basar) */
    private static String bookName(String name) {
        Matcher book = BOOK.matcher(name);
        return book.matches() ? book.group("name") : name;
    }

    /** Die Zeilen der Beschreibung von "Accept Offer" lesen. Die Zeilen sind ohne Farbcodes und getrimmt (leere bleiben leer) */
    public static Parsed parse(List<String> lore) {
        Map<String, Integer> required = new LinkedHashMap<>();
        List<ItemAmount> rewards = new ArrayList<>();
        int copper = -1;
        int gardenExp = -1;
        boolean ready = false;
        // 0: vor der Liste, 1: in der Liste der verlangten Gegenstaende, 2: unter "Rewards:", 3: dahinter
        int section = 0;
        if (lore != null) {
            for (String raw : lore) {
                String line = clean(raw);
                if (line.equals(READY_LINE)) {
                    ready = true;
                    continue;
                }
                if (line.contains("Rewards")) {
                    section = 2;
                    continue;
                }
                if (line.equals(ITEMS_HEADER)) {
                    section = 1;
                    continue;
                }
                if (line.isEmpty()) {
                    // Eine Leerzeile beendet die Liste; hinter den Belohnungen steht nichts mehr, was zaehlt
                    if (section == 1) section = 0;
                    else if (section == 2) section = 3;
                    continue;
                }
                if (section == 1) {
                    ItemAmount item = parseItem(line);
                    if (item != null) required.put(item.name(), item.amount());
                } else if (section == 2) {
                    Matcher c = COPPER.matcher(line);
                    if (c.matches()) {
                        copper = (int) Math.max(0L, number(c.group("amount")));
                        continue;
                    }
                    Matcher g = GARDEN_EXP.matcher(line);
                    if (g.matches()) {
                        gardenExp = (int) Math.max(0L, number(g.group("amount")));
                        continue;
                    }
                    ItemAmount item = parseItem(line);
                    if (item != null) rewards.add(item);
                }
            }
        }
        return new Parsed(required, List.copyOf(rewards), copper, gardenExp, ready);
    }

    /** Ist das das Schild eines Besuchers? Vier Zeilen, die letzte "Offers Accepted: N" (SkyHanni: {@code isVisitorInfo}) */
    public static boolean isInfoLore(List<String> lore) {
        if (lore == null || lore.size() != 4) return false;
        return OFFERS_ACCEPTED.matcher(clean(lore.get(3))).matches();
    }

    /** Wie viele Angebote man diesem Besucher schon erfuellt hat, oder -1 */
    public static int offersAccepted(List<String> lore) {
        if (!isInfoLore(lore)) return -1;
        Matcher m = OFFERS_ACCEPTED.matcher(clean(lore.get(3)));
        return m.matches() ? (int) number(m.group("amount")) : -1;
    }
}
