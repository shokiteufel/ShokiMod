package com.shokiteufel.shokimod.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aus dem Namen einer Ware im Basar die Kennungen, unter denen der Basar sie fuehrt.
 *
 * Die Liste der Items bei Hypixel ({@link ItemNames}) kennt die meisten Waren beim Namen - "Enchanted Brown Mushroom" ist
 * ENCHANTED_BROWN_MUSHROOM. Drei Sorten stehen dort nicht, der Basar fuehrt sie aber: die Shards ("Sprawl Shard" ist SHARD_SPRAWL), die Essenzen
 * ("Gold Essence" ist ESSENCE_GOLD) und die verzauberten Buecher ("Sharpness VI" ist ENCHANTMENT_SHARPNESS_6, "Wisdom V" ist
 * ENCHANTMENT_ULTIMATE_WISDOM_5 - bei den Ultimates fehlt das ULTIMATE im Namen). In den Auftragslisten des Basars tragen die Stuecke keine Kennung in sich;
 * ohne diese Ableitung fanden Order helper und Preiszeilen dort keinen Preis, gerade bei diesen dreien.
 *
 * <p>Die Ableitung raet: Sie nennt alle Kennungen, die der Name ergeben kann. Welche es im Basar wirklich gibt, entscheidet der, der sie benutzt -
 * er nimmt die erste mit einem Preis.
 */
public final class BazaarIds {

    /** Ein Name, der auf eine Stufe endet: "Sharpness VI", "Turbo-Wheat V" (grosse roemische Zahl oder bis zu zwei Ziffern) */
    private static final Pattern LEVEL = Pattern.compile("^(.+?)\\s+([IVXLC]+|\\d{1,2})$");

    private BazaarIds() {
    }

    /**
     * Wie der Basar einen Namen schreibt: gross, ohne Hochkomma, jede Folge von Zeichen, die keine Buchstaben und Ziffern sind, als ein Unterstrich
     * ("Turbo-Wheat" wird TURBO_WHEAT, "Jerry's Stick" wird JERRYS_STICK).
     */
    static String key(String name) {
        if (name == null) return "";
        String s = name.replace("'", "").replace("’", "").toUpperCase(Locale.ROOT);
        s = s.replaceAll("[^A-Z0-9]+", "_");
        int from = 0;
        int to = s.length();
        while (from < to && s.charAt(from) == '_') from++;
        while (to > from && s.charAt(to - 1) == '_') to--;
        return s.substring(from, to);
    }

    /** Die Kennungen, die sich allein aus dem Schreiben des Namens ergeben (ohne die Liste der Items). Ohne Farbcodes, ohne Doppelte, die wahrscheinlichste zuerst */
    static List<String> derived(String name) {
        if (name == null) return List.of();
        String clean = name.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return List.of();
        Set<String> out = new LinkedHashSet<>();

        if (clean.endsWith(" Shard")) {
            String base = key(clean.substring(0, clean.length() - " Shard".length()));
            if (!base.isEmpty()) {
                out.add("SHARD_" + base);
                out.add(base + "_SHARD");
            }
        }
        if (clean.endsWith(" Essence")) {
            String base = key(clean.substring(0, clean.length() - " Essence".length()));
            if (!base.isEmpty()) out.add("ESSENCE_" + base);
        }
        Matcher m = LEVEL.matcher(clean);
        if (m.matches()) {
            int level = SkillLevels.romanToDecimal(m.group(2));
            String base = key(m.group(1));
            if (level > 0 && !base.isEmpty()) {
                out.add("ENCHANTMENT_" + base + "_" + level);
                out.add("ENCHANTMENT_ULTIMATE_" + base + "_" + level);
            }
        }
        String plain = key(clean);
        if (!plain.isEmpty()) out.add(plain);
        return List.copyOf(out);
    }

    /** Alle Kennungen zu einem Namen: erst die der Liste der Items, dann die abgeleiteten. Leer nur bei einem leeren Namen */
    public static List<String> candidates(String name) {
        Set<String> out = new LinkedHashSet<>(ItemNames.idsFor(name));
        out.addAll(derived(name));
        return List.copyOf(out);
    }
}
