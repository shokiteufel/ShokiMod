package com.shokiteufel.shokimod.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die 37 Arten des Critterdex, mit Biom, Seltenheit und Stueckzahl pro Lauf.
 *
 * Uebernommen aus crittermods Critters. Die Stueckzahl ist der Punkt, der leicht
 * uebersehen wird: bei den meisten Arten reicht ein Fang, einige tauchen aber in
 * fester Anzahl auf und gelten erst als erledigt, wenn alle gefangen sind.
 */
public final class Critters {

    /** Eine Art. Stueckzahl 0 heisst: der erste Fang genuegt */
    public record Critter(String name, SafariBiome biome, Rarity rarity, int spawnQuota) {

        public Critter(String name, SafariBiome biome, Rarity rarity) {
            this(name, biome, rarity, 0);
        }

        public boolean hasQuota() {
            return spawnQuota > 0;
        }

        /** Wie oft die Art gefangen sein muss, damit sie als erledigt gilt */
        public int required(boolean firstCatchIsEnough) {
            return firstCatchIsEnough || !hasQuota() ? 1 : spawnQuota;
        }
    }

    public enum Rarity {
        COMMON("§f"), UNCOMMON("§a"), RARE("§9"), EPIC("§5"), LEGENDARY("§6");

        private final String colourCode;

        Rarity(String colourCode) {
            this.colourCode = colourCode;
        }

        public String colourCode() {
            return colourCode;
        }
    }

    public static final List<Critter> ALL = List.of(
            new Critter("Foxtrot", SafariBiome.FOREST, Rarity.COMMON),
            new Critter("Bluebird", SafariBiome.FOREST, Rarity.UNCOMMON),
            new Critter("Honeybug", SafariBiome.FOREST, Rarity.UNCOMMON),
            new Critter("Treefrog", SafariBiome.FOREST, Rarity.UNCOMMON),
            new Critter("Woodchucker", SafariBiome.FOREST, Rarity.UNCOMMON),
            new Critter("Fluffling", SafariBiome.FOREST, Rarity.RARE),
            new Critter("Hideonfloor", SafariBiome.FOREST, Rarity.RARE),
            new Critter("Parakeet", SafariBiome.FOREST, Rarity.RARE),
            new Critter("Macaw", SafariBiome.FOREST, Rarity.LEGENDARY),

            new Critter("Cavernfish", SafariBiome.CAVERN, Rarity.COMMON),
            new Critter("Flitter", SafariBiome.CAVERN, Rarity.COMMON),
            new Critter("Shyworm", SafariBiome.CAVERN, Rarity.COMMON),
            new Critter("Driftling", SafariBiome.CAVERN, Rarity.UNCOMMON),
            new Critter("Chuckwalla", SafariBiome.CAVERN, Rarity.RARE),
            new Critter("Rockmite", SafariBiome.CAVERN, Rarity.RARE),
            new Critter("Scrappy", SafariBiome.CAVERN, Rarity.RARE),
            new Critter("Snoozle", SafariBiome.CAVERN, Rarity.RARE),
            new Critter("Gemzie", SafariBiome.CAVERN, Rarity.EPIC, 3),

            new Critter("Strongarm", SafariBiome.ICY, Rarity.COMMON),
            new Critter("Tepid", SafariBiome.ICY, Rarity.COMMON),
            new Critter("Polaris", SafariBiome.ICY, Rarity.UNCOMMON),
            new Critter("Shuddersquid", SafariBiome.ICY, Rarity.UNCOMMON),
            new Critter("Billygoat", SafariBiome.ICY, Rarity.RARE),
            new Critter("Mantis Shrimp", SafariBiome.ICY, Rarity.RARE),
            new Critter("Nozzlenose", SafariBiome.ICY, Rarity.RARE),
            new Critter("Troodon", SafariBiome.ICY, Rarity.RARE, 3),
            new Critter("Wumpa", SafariBiome.ICY, Rarity.LEGENDARY, 1),

            new Critter("Areita", SafariBiome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Bloodbat", SafariBiome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Duplico", SafariBiome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gazer", SafariBiome.HAUNTED, Rarity.UNCOMMON, 4),
            new Critter("Litterbug", SafariBiome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Solsnatcher", SafariBiome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gimmiegold", SafariBiome.HAUNTED, Rarity.RARE),
            new Critter("Hideonwall", SafariBiome.HAUNTED, Rarity.RARE),
            new Critter("Hideyho", SafariBiome.HAUNTED, Rarity.RARE, 1),
            new Critter("Doomspiral", SafariBiome.HAUNTED, Rarity.LEGENDARY, 1));

    private static final Map<String, Critter> BY_NAME = new LinkedHashMap<>();
    private static final Map<SafariBiome, List<Critter>> BY_BIOME = new EnumMap<>(SafariBiome.class);
    /**
     * Nach Namenslaenge absteigend. Beim Suchen im Fliesstext muss der laengste Name
     * zuerst geprueft werden, sonst schluckt "Hideonwall" den Treffer "Hideonfloor" nicht,
     * aber kuerzere Namen wuerden in laengeren stecken bleiben.
     */
    private static final List<Critter> BY_NAME_LENGTH_DESC;

    static {
        for (Critter critter : ALL) {
            BY_NAME.put(critter.name().toLowerCase(Locale.ROOT), critter);
            BY_BIOME.computeIfAbsent(critter.biome(), b -> new ArrayList<>()).add(critter);
        }
        for (SafariBiome biome : SafariBiome.values()) {
            BY_BIOME.putIfAbsent(biome, List.of());
        }
        List<Critter> sorted = new ArrayList<>(ALL);
        sorted.sort(Comparator.comparingInt((Critter c) -> c.name().length()).reversed());
        BY_NAME_LENGTH_DESC = List.copyOf(sorted);
    }

    private Critters() {
    }

    public static int total() {
        return ALL.size();
    }

    public static List<Critter> inBiome(SafariBiome biome) {
        return BY_BIOME.getOrDefault(biome, List.of());
    }

    public static int totalIn(SafariBiome biome) {
        return inBiome(biome).size();
    }

    public static Critter byName(String name) {
        return name == null ? null : BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** Die erste Art, deren Name im Text vorkommt - laengste zuerst */
    public static Critter findIn(String text) {
        if (text == null) return null;
        String haystack = text.toLowerCase(Locale.ROOT);
        for (Critter critter : BY_NAME_LENGTH_DESC) {
            if (haystack.contains(critter.name().toLowerCase(Locale.ROOT))) return critter;
        }
        return null;
    }
}
