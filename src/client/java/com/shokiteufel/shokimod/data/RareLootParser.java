package com.shokiteufel.shokimod.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest einen Fund aus Hypixels Chatzeile.
 *
 * "RARE DROP! Enchanted Book (Flash I) (+289 Magic Find)" wird zu: Anzeigename
 * "Flash I", Stueckzahl 1, Kontext "+289 MF" und einer Liste moeglicher Kennungen,
 * unter denen der Basar oder das Auktionshaus den Fund fuehren koennte. Die
 * Kennung steht nicht in der Zeile; sie wird aus dem Namen gebildet und spaeter
 * gegen die Preislisten geprueft - die erste mit Preis gewinnt.
 *
 * Portiert aus Skysofts RareLootChatParser, RareLootItemIds und
 * RareLootDisplayNames (LGPL-3.0, Akinsoft), ohne die Pet-Aufloesung: die braucht
 * ein Item-Repo, das diese Mod nicht mitfuehrt.
 */
public final class RareLootParser {

    /** Ein erkannter Fund */
    public record Drop(String displayName, int amount, String context, List<String> itemIdCandidates) {
    }

    private static final Pattern DUG_OUT = Pattern.compile(
            "^(?:(?:(?:VERY|CRAZY)\\s+)?RARE DROP!\\s+|Wow!\\s+)?You dug out(?: an? )?(?<drop>.+?)!(?: .*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT = Pattern.compile(
            "^(?:(?:VERY|CRAZY)\\s+)?RARE DROP!\\s+(?<drop>.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTEXT = Pattern.compile(
            "^(?<drop>.+?)\\s+(?<context>\\([^)]*(?:Magic Find|MF)[^)]*\\))(?:\\s+.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
            "^(?<amount>\\d+)\\s*x\\s+(?<drop>.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCHANTED_BOOK = Pattern.compile(
            "^Enchanted Book \\((?<enchant>.+?) (?<level>\\d+|[IVXLCDM]+)\\)$", Pattern.CASE_INSENSITIVE);
    /**
     * Ein gefangener Shard: "CHARM! You charmed the Ghost and received 3 Ghost Shards!"
     *
     * Hypixels Item-Liste fuehrt Shards nicht als Items, der Basar aber als Ware
     * SHARD_<NAME>. "Ghost Shard" wird also zu SHARD_GHOST, "Wiki Tiki Shard" zu
     * SHARD_WIKI_TIKI - gebildet aus dem Namen ohne das Wort Shard.
     */
    private static final Pattern CHARM = Pattern.compile(
            "^CHARM!\\s+You charmed the .+? and received (?<amount>\\d+|an?) (?<shard>.+?) Shards?!?$",
            Pattern.CASE_INSENSITIVE);
    /** Derselbe Shard, anders gemeldet: "You caught x3 Timil Shards! (2)" */
    private static final Pattern CAUGHT = Pattern.compile(
            "^You caught (?:x(?<amount>\\d+) |an? )?(?<shard>.+?) Shards?!(?:\\s*\\(\\d+\\))?$",
            Pattern.CASE_INSENSITIVE);
    /**
     * Geteilte Beute: "LOOT SHARE You received 3 Silkbreeze Shards for assisting Outyc!"
     *
     * Bei Shards ist diese Zeile die einzige Meldung - es folgt kein eigener Fund. Wer sie
     * uebergeht, verliert die Shards fuer Anzeige und Jagd-Zaehler.
     */
    private static final Pattern LOOT_SHARE = Pattern.compile(
            "^LOOT SHARE +You received (?<amount>[0-9]+|an?) (?<shard>.+?) Shards? "
                    + "for assisting [A-Za-z0-9_]{1,16}!?(?: *[(][0-9]+[)])?$",
            Pattern.CASE_INSENSITIVE);
    private static final String SHARD_PREFIX = "SHARD_";

    /**
     * Prismarin ist kein Jagd-Shard, sondern gewoehnliches Material.
     *
     * Ohne diese Ausnahme wuerde "Enchanted Prismarine Shard" zu SHARD_ENCHANTED_PRISMARINE
     * und damit im Jagd-Zaehler landen. Mit ihr bekommt es seine normale Kennung: der Fund
     * wird weiterhin gemeldet und bewertet, nur eben nicht als Jagdbeute gezaehlt.
     */
    private static final java.util.Set<String> NOT_HUNTING = java.util.Set.of(
            "PRISMARINE", "ENCHANTED_PRISMARINE");

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");
    private static final Pattern NOT_ID_CHARS = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern NOT_ASCII = Pattern.compile("[^\\x20-\\x7E]");

    /** Hypixel nennt manche Items anders, als ihr Anzeigename vermuten laesst */
    private static final Map<String, String> ID_OVERRIDES = Map.of(
            "CARROT", "CARROT_ITEM",
            "CARROTS", "CARROT_ITEM",
            "POTATO", "POTATO_ITEM",
            "POTATOES", "POTATO_ITEM");

    private RareLootParser() {
    }

    /** Der Fund aus der Zeile, oder null wenn sie keiner ist */
    /**
     * Die Ueberschrift des Beutebuendels aus dem Crystal Nucleus.
     *
     * Der Fund faellt dort nicht als einzelne "RARE DROP!"-Zeile, sondern als Liste:
     * eine Ueberschrift, dann je eine Zeile pro Gegenstand. Ohne diese Erkennung sieht
     * die Mod dort nie einen Fund, wie wertvoll er auch sei.
     */
    private static final Pattern BUNDLE_START = Pattern.compile(
            "^(?:CRYSTAL NUCLEUS LOOT BUNDLE"
            // Die Leichen in den Gletscherschaechten melden nach demselben Muster,
            // nur mit eigener Ueberschrift: "LAPIS CORPSE LOOT!". Vor dem Wort steht
            // die Sorte - Lapis, Tungsten, Umber, Vanguard -, und welche es kuenftig
            // noch gibt, weiss heute niemand. Deshalb wird die Sorte nicht
            // aufgezaehlt, sondern offen gelassen
            + "|.{0,32} CORPSE LOOT!?"
            + ")$", Pattern.CASE_INSENSITIVE);
    /** Die Zwischenzeile ueber der Liste - kein Gegenstand */
    private static final Pattern BUNDLE_HEADING = Pattern.compile(
            // "+1 bonus drop!" steht bei den Leichen zwischen Ueberschrift und Liste
            // und ist kein Gegenstand. Ohne diese Zeile hier landete sie als Fund in
            // der Auswertung und suchte vergeblich nach einem Preis
            "^(?:REWARDS|\\+\\d+ bonus drops?!?)$", Pattern.CASE_INSENSITIVE);
    /**
     * Das Symbol der Edelsteinsorte vor dem Namen. Statt die zwoelf Sorten aufzuzaehlen,
     * faellt jedes Zeichen aus Hypixels eigenem Zeichenvorrat weg - dann traegt die
     * Erkennung auch eine Sorte, die es heute noch nicht gibt.
     */
    private static final Pattern BUNDLE_LEAD = Pattern.compile("^[\\uE000-\\uF8FF\\s]+");
    /**
     * Die Stueckzahl am Ende: "Flawed Ruby Gemstone x58", auch "Mithril Powder x6,387".
     *
     * Das Leerzeichen vor dem x ist entscheidend - ohne es wuerde die Zeile
     * "Fine Onyx Gemstone" mitten im Namen zerschnitten.
     */
    private static final Pattern BUNDLE_AMOUNT = Pattern.compile(
            "^(?<drop>.*?)\\s+x(?<amount>[0-9][0-9,.]*)$");
    /** Woran das Buendel endet: die Trennlinie oder der Hinweis darunter */
    private static final Pattern BUNDLE_END = Pattern.compile(
            "^(?:[\\u25AC\\u2500-\\u257F=-]{4,}"
            + "|Pick it up near the Nucleus Vault!.*"
            // Bei den Leichen folgt die Gewinnmeldung anderer Mods; sie gehoert nicht
            // mehr zum Buendel und beendet es sicher
            + "|\\[SkyHanni\\].*"
            + ")$",
            Pattern.CASE_INSENSITIVE);
    /** Ein Gegenstandsname faengt mit einem Buchstaben oder einer Ziffer an */
    private static final Pattern BUNDLE_NAME = Pattern.compile("^[A-Za-z0-9].*");

    /** Beginnt hier das Beutebuendel aus dem Nucleus? */
    public static boolean isBundleStart(String clean) {
        return BUNDLE_START.matcher(clean).matches();
    }

    /** Ist das die Zeile, hinter der das Buendel sicher zu Ende ist? */
    public static boolean isBundleEnd(String clean) {
        return BUNDLE_END.matcher(clean).matches();
    }

    /**
     * Eine Zeile aus dem Buendel als Fund - oder null, wenn sie keiner ist.
     *
     * Die Ueberschrift und die Leerzeile in der Mitte liefern null, ohne dass das
     * Buendel deshalb zu Ende waere; darueber entscheidet allein der Aufrufer.
     */
    public static Drop parseBundleLine(String clean) {
        if (clean == null) return null;
        String rest = BUNDLE_LEAD.matcher(clean).replaceFirst("").trim();
        if (rest.isEmpty() || BUNDLE_HEADING.matcher(rest).matches()) return null;

        int amount = 1;
        Matcher counted = BUNDLE_AMOUNT.matcher(rest);
        if (counted.matches()) {
            try {
                amount = Integer.parseInt(counted.group("amount").replace(",", "").replace(".", ""));
            } catch (NumberFormatException e) {
                return null;
            }
            if (amount < 1) return null;
            rest = counted.group("drop").trim();
        }
        if (rest.length() < 3 || !BUNDLE_NAME.matcher(rest).matches()) return null;

        // Denselben Weg gehen wie ein einzelner Fund: so gelten hier dieselben Kennungen,
        // Buecher und Shard-Namen, ohne dass die Regeln ein zweites Mal dastehen
        Drop base = build(rest, null);
        if (base == null) return null;
        return amount == 1 ? base
                : new Drop(base.displayName(), amount, base.context(), base.itemIdCandidates());
    }

    public static Drop parse(String plain) {
        if (plain == null) return null;
        String clean = plain.trim();
        if (clean.isEmpty()) return null;

        Matcher charm = CHARM.matcher(clean);
        if (charm.matches()) return shardDrop(charm.group("amount"), charm.group("shard"));

        Matcher caught = CAUGHT.matcher(clean);
        if (caught.matches()) return shardDrop(caught.group("amount"), caught.group("shard"));

        Matcher shared = LOOT_SHARE.matcher(clean);
        if (shared.matches()) return shardDrop(shared.group("amount"), shared.group("shard"));

        Matcher dug = DUG_OUT.matcher(clean);
        if (dug.matches()) {
            String name = cleanDropName(dug.group("drop"));
            return name == null ? null : build(name, null);
        }

        Matcher direct = DIRECT.matcher(clean);
        if (!direct.matches()) return null;

        String body = direct.group("drop").trim();
        String context = null;
        Matcher withContext = CONTEXT.matcher(body);
        if (withContext.matches() && withContext.group("context") != null) {
            body = withContext.group("drop").trim();
            context = normalizeContext(withContext.group("context"));
        }

        String name = cleanDropName(body);
        return name == null ? null : build(name, context);
    }

    private static Drop build(String name, String context) {
        int amount = 1;
        String displayName = name;
        Matcher counted = AMOUNT.matcher(name);
        if (counted.matches()) {
            try {
                amount = Math.max(1, Integer.parseInt(counted.group("amount")));
                displayName = counted.group("drop").trim();
            } catch (NumberFormatException ignored) {
                // dann ist es eben ein Name, der mit einer Zahl beginnt
            }
        }

        Matcher book = ENCHANTED_BOOK.matcher(displayName);
        if (book.matches()) {
            String enchant = book.group("enchant").trim();
            int level = parseLevel(book.group("level"));
            if (!enchant.isEmpty() && level > 0) {
                String key = NOT_ID_CHARS.matcher(enchant.toUpperCase(Locale.US)).replaceAll("_");
                key = trimUnderscores(key);
                List<String> candidates = new ArrayList<>(2);
                candidates.add("ENCHANTMENT_" + key + "_" + level);
                candidates.add("ENCHANTMENT_ULTIMATE_" + key + "_" + level);
                return new Drop(titleCase(enchant) + " " + roman(level), amount, context, candidates);
            }
        }

        List<String> candidates = new ArrayList<>(1);
        String id = idFromDisplayName(displayName);
        if (id != null) candidates.add(id);
        return new Drop(displayName, amount, context, candidates);
    }

    /** "3" und "Ghost" werden zu 3x "Ghost Shard" mit der Kennung SHARD_GHOST */
    private static Drop shardDrop(String amountText, String shardName) {
        int amount = 1;
        if (amountText != null && Character.isDigit(amountText.charAt(0))) {
            try {
                amount = Math.max(1, Integer.parseInt(amountText));
            } catch (NumberFormatException ignored) {
                // "a" oder "an" bleiben bei eins
            }
        }
        String name = shardName.trim();
        String key = trimUnderscores(NOT_ID_CHARS.matcher(name.toUpperCase(Locale.US)).replaceAll("_"));
        List<String> candidates = new ArrayList<>(1);
        if (!key.isEmpty()) {
            // Prismarin bekommt seine gewoehnliche Kennung, damit der Jagd-Zaehler es auslaesst
            candidates.add(NOT_HUNTING.contains(key) ? key + "_SHARD" : SHARD_PREFIX + key);
        }
        return new Drop(name + " Shard", amount, null, candidates);
    }

    /** Ohne Ausrufezeichen, Klammern und Artikel. Null fuer Zeilen, die kein Fund sind */
    private static String cleanDropName(String text) {
        String clean = text.trim();
        if (clean.endsWith("!")) clean = clean.substring(0, clean.length() - 1).trim();
        if (clean.startsWith("(") && clean.endsWith(")")) clean = clean.substring(1, clean.length() - 1).trim();
        if (clean.regionMatches(true, 0, "a ", 0, 2)) clean = clean.substring(2).trim();
        else if (clean.regionMatches(true, 0, "an ", 0, 3)) clean = clean.substring(3).trim();

        if (clean.isBlank()) return null;
        // Die Griffin-Zeile nennt einen Ort, keinen Fund; Coins haben keinen Preis
        if (clean.equalsIgnoreCase("Griffin Burrow")) return null;
        if (clean.toLowerCase(Locale.ROOT).contains("coin")) return null;
        return clean;
    }

    /** "Enchanted Diamond" wird zu ENCHANTED_DIAMOND - so heisst es im Basar */
    public static String idFromDisplayName(String displayName) {
        if (displayName == null) return null;
        String id = COLOUR_CODE.matcher(displayName).replaceAll("");
        id = NOT_ID_CHARS.matcher(id.toUpperCase(Locale.US)).replaceAll("_");
        id = trimUnderscores(id);
        if (id.isBlank()) return null;
        return ID_OVERRIDES.getOrDefault(id, id);
    }

    /** "(+289 Magic Find)" wird zu "+289 MF", ohne Klammern und ohne Sonderzeichen */
    private static String normalizeContext(String raw) {
        String text = raw.trim();
        if (text.startsWith("(")) text = text.substring(1);
        if (text.endsWith(")")) text = text.substring(0, text.length() - 1);
        text = text.replaceAll("(?i)Magic Find", "MF");
        // Hypixels Sternchen und andere Glyphen kommen im Chat nicht sauber an
        text = NOT_ASCII.matcher(text).replaceAll("");
        return text.replaceAll("\\s+", " ").trim();
    }

    private static int parseLevel(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return romanToInt(text.toUpperCase(Locale.US));
        }
    }

    private static String trimUnderscores(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '_') start++;
        while (end > start && text.charAt(end - 1) == '_') end--;
        return text.substring(start, end);
    }

    /** "ultimate wise" wird zu "Ultimate Wise" */
    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (String word : text.toLowerCase(Locale.US).split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS =
            {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    public static String roman(int value) {
        if (value <= 0) return String.valueOf(value);
        StringBuilder out = new StringBuilder();
        int rest = value;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (rest >= ROMAN_VALUES[i]) {
                out.append(ROMAN_SYMBOLS[i]);
                rest -= ROMAN_VALUES[i];
            }
        }
        return out.toString();
    }

    private static int romanToInt(String text) {
        int total = 0;
        int previous = 0;
        for (int i = text.length() - 1; i >= 0; i--) {
            int value = switch (text.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> -1;
            };
            if (value < 0) return 0;
            total += value < previous ? -value : value;
            previous = value;
        }
        return total;
    }
}
