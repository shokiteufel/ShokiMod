package com.shokiteufel.shokimod.chat;

import java.util.regex.Pattern;

/**
 * Der Spielername aus einem Stueck Chat: "[MVP+] Throwpo" und "[VIP] Name ●" werden "Throwpo" und "Name".
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code StringUtils.cleanPlayerName}). Von dort stammt die Regel, Rang in eckigen Klammern und Zeichen davor und danach
 * abzuziehen; die Umsetzung ist eigene Arbeit: Java, im Klartext.
 */
final class PlayerNames {

    private static final Pattern RANK = Pattern.compile("\\[[^\\]]*\\]");
    private static final Pattern NOT_A_NAME_CHARACTER = Pattern.compile("[^A-Za-z0-9_]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private PlayerNames() {
    }

    /** Der Name, oder ein leerer Text, wenn nichts davon uebrig bleibt */
    static String clean(String raw) {
        if (raw == null) return "";
        String text = RANK.matcher(raw.replace('●', ' ')).replaceAll(" ").trim();
        if (text.isEmpty()) return "";
        String[] parts = WHITESPACE.split(text);
        return NOT_A_NAME_CHARACTER.matcher(parts[parts.length - 1]).replaceAll("");
    }

    /** Ein Spielername im Sinn der Mod: bis zu 16 Buchstaben, Ziffern und Unterstriche */
    static boolean isName(String name) {
        return name != null && name.length() >= 1 && name.length() <= 16 && !NOT_A_NAME_CHARACTER.matcher(name).find();
    }
}
