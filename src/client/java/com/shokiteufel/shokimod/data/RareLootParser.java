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
    private static final String SHARD_PREFIX = "SHARD_";

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
    public static Drop parse(String plain) {
        if (plain == null) return null;
        String clean = plain.trim();
        if (clean.isEmpty()) return null;

        Matcher charm = CHARM.matcher(clean);
        if (charm.matches()) return shardDrop(charm.group("amount"), charm.group("shard"));

        Matcher caught = CAUGHT.matcher(clean);
        if (caught.matches()) return shardDrop(caught.group("amount"), caught.group("shard"));

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
        if (!key.isEmpty()) candidates.add(SHARD_PREFIX + key);
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
