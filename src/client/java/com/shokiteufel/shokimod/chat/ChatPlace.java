package com.shokiteufel.shokimod.chat;

import java.util.Locale;
import java.util.Set;

/**
 * Wo man steht, soweit die Chat-Filter es brauchen. Austauschbar: {@link ChatFilters} liest den echten Ort (Tab-Liste, Dungeon-Zustand, Seitenleiste),
 * die Tests setzen einen festen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code IslandType}, {@code IslandTypeTag}). Von dort stammen die Gruppen: Bergbau-Inseln, Foraging-Inseln und
 * die Inseln mit eigenen Baeumen. Die Namen sind die der Gebietszeile der Tab-Liste ("Area: Dwarven Mines"). Die Umsetzung ist eigene Arbeit: Java,
 * ein Vergleich ohne Beachtung der Gross- und Kleinschreibung statt eines Aufzaehlungstyps.
 */
public interface ChatPlace {

    /** Ist man in SkyBlock? */
    boolean skyblock();

    /** Das Gebiet aus der Tab-Liste; {@code "Unknown"}, solange es nicht gelesen ist */
    String area();

    /** Steht man in einem Dungeon (Catacombs)? */
    boolean dungeon();

    /** Steht man in Arachne's Sanctuary? Die Zone steht in der Seitenleiste; die echte Fassung liest sie erst, wenn danach gefragt wird */
    boolean sanctuary();

    /** Ist man im Alpha-Netzwerk von Hypixel? */
    boolean alphaNetwork();

    /** Ein fester Ort, fuer Tests */
    static ChatPlace of(boolean skyblock, String area, boolean dungeon) {
        return new Fixed(skyblock, area, dungeon, false, false);
    }

    /** Derselbe Ort, aber in Arachne's Sanctuary */
    default ChatPlace inSanctuary() {
        return new Fixed(skyblock(), area(), dungeon(), true, alphaNetwork());
    }

    /** Derselbe Ort, aber im Alpha-Netzwerk */
    default ChatPlace onAlpha() {
        return new Fixed(skyblock(), area(), dungeon(), sanctuary(), true);
    }

    /** Ein fester Ort */
    record Fixed(boolean skyblock, String area, boolean dungeon, boolean sanctuary, boolean alphaNetwork) implements ChatPlace {
    }

    // ---- Gebiete (kleingeschrieben verglichen) ----

    Set<String> MINING = Set.of("gold mine", "deep caverns", "dwarven mines", "crystal hollows", "glacite mineshafts", "glacite mineshaft", "mineshaft");
    Set<String> FORAGING = Set.of("the park", "moonglade marsh", "torrhus canyon");
    Set<String> FORAGING_CUSTOM_TREES = Set.of("moonglade marsh", "torrhus canyon");
    Set<String> CATACOMBS = Set.of("catacombs", "dungeons", "dungeon");
    Set<String> KUUDRA = Set.of("kuudra's hollow", "kuudra");

    private static String key(String area) {
        return area == null ? "" : area.trim().toLowerCase(Locale.ROOT);
    }

    /** Ist das Gebiet gelesen? Ein noch unbekanntes zaehlt bei "ausserhalb von ..." nicht als ausserhalb */
    static boolean known(String area) {
        String k = key(area);
        return !k.isEmpty() && !k.equals("unknown");
    }

    static boolean isGarden(String area) {
        return key(area).equals("garden");
    }

    static boolean isMining(String area) {
        return MINING.contains(key(area));
    }

    static boolean isForaging(String area) {
        return FORAGING.contains(key(area));
    }

    static boolean isForagingCustomTrees(String area) {
        return FORAGING_CUSTOM_TREES.contains(key(area));
    }

    static boolean isMoonglade(String area) {
        return key(area).equals("moonglade marsh");
    }

    static boolean isSpiderDen(String area) {
        return key(area).equals("spider's den");
    }

    static boolean isCrystalHollows(String area) {
        return key(area).equals("crystal hollows");
    }

    static boolean isCatacombs(String area) {
        return CATACOMBS.contains(key(area));
    }

    static boolean isKuudra(String area) {
        return KUUDRA.contains(key(area));
    }
}
