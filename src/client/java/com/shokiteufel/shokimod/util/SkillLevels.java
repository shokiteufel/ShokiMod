package com.shokiteufel.shokimod.util;

import java.util.Locale;

/**
 * Wie viel Erfahrung eine Faehigkeit-Stufe in SkyBlock kostet - und was daraus folgt, auch ueber die letzte Stufe hinaus (Overflow).
 *
 * Herkunft der Regel: SkyHanni (LGPL-2.1, {@code SkillUtil}, {@code SkillApi}). Bis Stufe 60 steht der Preis jeder einzelnen Stufe in einer Tabelle
 * (50, 125, 200 ... 7.000.000; zusammen 111.672.425). Danach kostet die naechste Stufe 7.000.000 plus einen Zuschlag, der mit jeder Stufe
 * um 600.000 waechst - und sich bei jeder zehnten Stufe verdoppelt. Die Tabelle selbst ist die der Hypixel-Wiki; dass sie stimmt, belegt ihre
 * Summe (Test). Reine Rechnung ohne Spiel, damit sie sich nachrechnen laesst.
 */
public final class SkillLevels {

    /** Was jede der ersten sechzig Stufen kostet: Eintrag 0 ist der Preis von Stufe 1 */
    static final long[] LEVEL_COSTS = {
            50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
            5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
            300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000,
            1_300_000, 1_400_000, 1_500_000, 1_600_000, 1_700_000, 1_800_000, 1_900_000, 2_000_000, 2_100_000, 2_200_000,
            2_300_000, 2_400_000, 2_500_000, 2_600_000, 2_750_000, 2_900_000, 3_100_000, 3_400_000, 3_700_000, 4_000_000,
            4_300_000, 4_600_000, 4_900_000, 5_200_000, 5_500_000, 5_800_000, 6_100_000, 6_400_000, 6_700_000, 7_000_000};

    /** Die Erfahrung bis Stufe 60 - SkyHannis Konstante {@code XP_NEEDED_FOR_60} */
    public static final long XP_FOR_60 = 111_672_425L;
    static final int TABLE_LEVELS = 60;
    /** Der Preis der ersten Stufe ueber 60, und der Zuschlag, um den jede weitere mehr kostet */
    private static final long OVERFLOW_BASE = 7_000_000L;
    private static final long OVERFLOW_SLOPE = 600_000L;

    /** Eine Faehigkeit mit ihrer eingebauten Obergrenze (die Stufe, bis zu der sie ohne Overflow geht) */
    public enum Skill {
        COMBAT("Combat", 60),
        FARMING("Farming", 60),
        FISHING("Fishing", 50),
        MINING("Mining", 60),
        FORAGING("Foraging", 57),
        ENCHANTING("Enchanting", 60),
        ALCHEMY("Alchemy", 50),
        CARPENTRY("Carpentry", 50),
        TAMING("Taming", 60),
        HUNTING("Hunting", 50);

        private final String displayName;
        private final int maxLevel;

        Skill(String displayName, int maxLevel) {
            this.displayName = displayName;
            this.maxLevel = maxLevel;
        }

        public String displayName() {
            return displayName;
        }

        public int maxLevel() {
            return maxLevel;
        }

        /** Die Faehigkeit mit diesem Namen (Gross- und Kleinschreibung egal), oder null */
        public static Skill byName(String name) {
            if (name == null) return null;
            for (Skill skill : values()) {
                if (skill.displayName.equalsIgnoreCase(name.trim())) return skill;
            }
            return null;
        }
    }

    /**
     * Wo man bei einer Menge Erfahrung steht.
     *
     * @param level      die Stufe, nicht auf die Obergrenze gekuerzt
     * @param xpCurrent  wie viel auf dieser Stufe schon da ist
     * @param xpForNext  wie viel die naechste Stufe kostet (0, wenn die Tabelle endet)
     * @param overflowXp die Erfahrung ueber der Obergrenze der Faehigkeit
     */
    public record Level(int level, long xpCurrent, long xpForNext, long overflowXp) {
    }

    private SkillLevels() {
    }

    /** Was Stufe {@code level} kostet (1 bis 60), sonst 0 */
    public static long costOfLevel(int level) {
        return level >= 1 && level <= TABLE_LEVELS ? LEVEL_COSTS[level - 1] : 0L;
    }

    /**
     * Was Stufe {@code level} kostet, auch ueber 60: 7.000.000 plus ein Zuschlag, der mit jeder Stufe um 600.000 waechst und sich bei jeder zehnten
     * verdoppelt (Stufe 61: 7.600.000, 62: 8.200.000 ... 71: 13.600.000, 72: 14.800.000). Unter 1 ist es 0.
     */
    public static long costOfLevelUncapped(int level) {
        if (level < 1) return 0L;
        if (level <= TABLE_LEVELS) return costOfLevel(level);
        long xpForNext = OVERFLOW_BASE + OVERFLOW_SLOPE;
        long slope = OVERFLOW_SLOPE;
        for (int lvl = TABLE_LEVELS + 1; lvl < level; lvl++) {
            xpForNext += slope;
            if (lvl % 10 == 0) slope *= 2;
        }
        return xpForNext;
    }

    /**
     * Welche Stufe diesen Preis hat (1 bis 60), sonst -1. Die Leiste zeigt als Ziel den Preis der naechsten Stufe; so laesst sich daraus die Stufe lesen.
     */
    public static int levelWithCost(long cost) {
        for (int i = 0; i < TABLE_LEVELS; i++) {
            if (LEVEL_COSTS[i] == cost) return i + 1;
        }
        return -1;
    }

    /** Die gesamte Erfahrung, die man fuer die gewuenschte Stufe braucht - auch ueber 60 */
    public static long xpRequiredForLevel(int desiredLevel) {
        long total = 0L;
        if (desiredLevel <= TABLE_LEVELS) {
            for (int level = 1; level <= desiredLevel; level++) total += costOfLevel(level);
            return total;
        }
        total = XP_FOR_60;
        int level = TABLE_LEVELS;
        long xpForNext = OVERFLOW_BASE + OVERFLOW_SLOPE;
        long slope = OVERFLOW_SLOPE;
        while (level < desiredLevel) {
            total += xpForNext;
            level++;
            xpForNext += slope;
            if (level % 10 == 0) slope *= 2;
        }
        return total;
    }

    /**
     * Stufe, Stand auf der Stufe, Preis der naechsten und Erfahrung ueber der Obergrenze fuer eine Menge Gesamterfahrung.
     *
     * Die Stufe zaehlt ueber die Obergrenze hinaus weiter; nur {@code overflowXp} ist auf die Obergrenze bezogen. Wer die gekuerzte Stufe will,
     * kuerzt sie selbst ({@code Math.min(level, cap)}).
     */
    public static Level calculate(long currentXp, int maxSkillCap) {
        long xpCurrent = currentXp;
        int level = 0;
        while (level < TABLE_LEVELS) {
            long xpForNextLevel = costOfLevel(level + 1);
            if (xpCurrent < xpForNextLevel) break;
            xpCurrent -= xpForNextLevel;
            level++;
        }
        long xpForNext = costOfLevel(level + 1);
        if (level >= TABLE_LEVELS) {
            long slope = OVERFLOW_SLOPE;
            xpForNext = OVERFLOW_BASE + slope;
            while (xpCurrent >= xpForNext) {
                level++;
                xpCurrent -= xpForNext;
                xpForNext += slope;
                if (level % 10 == 0) slope *= 2;
            }
        }
        long xpForCap = xpRequiredForLevel(Math.min(maxSkillCap, TABLE_LEVELS));
        long overflowXp = Math.max(0L, currentXp - xpForCap);
        return new Level(level, xpCurrent, xpForNext, overflowXp);
    }

    /**
     * Eine Zahl mit Kuerzel, wie Hypixel sie schreibt: "75k", "4M", "1.5B", "12,345". Null, wenn es keine ist.
     */
    public static Long parseAmount(String text) {
        if (text == null) return null;
        String t = text.trim().replace(",", "");
        if (t.isEmpty()) return null;
        double factor = 1.0;
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        if (last == 'k') factor = 1_000.0;
        else if (last == 'm') factor = 1_000_000.0;
        else if (last == 'b') factor = 1_000_000_000.0;
        if (factor != 1.0) t = t.substring(0, t.length() - 1);
        try {
            return Math.round(Double.parseDouble(t) * factor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Eine roemische Zahl ("XIV") als Zahl; eine gewoehnliche Zahl bleibt, alles andere ist 0 */
    public static int romanToDecimal(String text) {
        if (text == null || text.isBlank()) return 0;
        String t = text.trim().toUpperCase(Locale.ROOT);
        if (t.chars().allMatch(Character::isDigit)) {
            try {
                return Integer.parseInt(t);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        int total = 0;
        int previous = 0;
        for (int i = t.length() - 1; i >= 0; i--) {
            int value = switch (t.charAt(i)) {
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
            if (value < previous) total -= value;
            else {
                total += value;
                previous = value;
            }
        }
        return total;
    }

    /** Eine Zahl als roemische Zahl (1 bis 3999), sonst die Zahl selbst - fuer die Titel im Menue der Faehigkeiten */
    public static String toRoman(int number) {
        if (number <= 0 || number >= 4000) return Integer.toString(number);
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        int rest = number;
        for (int i = 0; i < values.length; i++) {
            while (rest >= values[i]) {
                sb.append(symbols[i]);
                rest -= values[i];
            }
        }
        return sb.toString();
    }
}
