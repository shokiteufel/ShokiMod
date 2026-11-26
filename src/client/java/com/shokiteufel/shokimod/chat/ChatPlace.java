package com.shokiteufel.shokimod.chat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    // ---- Chat (Teil 2): was die umschreibenden Funktionen sonst noch vom Spiel wissen wollen (jedes erst, wenn gefragt wird) ----

    /** War der Experimentation Table (oder eines seiner Spiele) in den letzten drei Sekunden offen oder ist er es noch? */
    default boolean experimentTableRecent(long now) {
        return false;
    }

    /** Die verzauberten Buecher im Inventar: ihr Name mit Farbcodes ("§9Angler VI") und wie viele davon da sind */
    default Map<String, Integer> enchantedBooks() {
        return Map.of();
    }

    /** Ist ein Fenster (Truhe, Menue) offen? Was dann ins Inventar kommt, ist meist gekauft oder verschoben, kein Drop */
    default boolean menuOpen() {
        return false;
    }

    /** Was Gemstone Powder vervielfacht, soweit die Mod es kennt */
    default PowderContext powderContext() {
        return PowderContext.NONE;
    }

    /**
     * Die Quellen fuer "Show Actual Powder Gained": Sky Mall mit mehr Powder, die Stufe des Scatha-Pets (legendaer; -1, wenn es nicht das aktive ist), der Name des
     * Gegenstands in der Hand mit Farbcodes und seine Beschreibung ohne
     */
    record PowderContext(boolean skyMallExtraPowder, int scathaLevel, String heldNameLegacy, List<String> heldLore) {
        public static final PowderContext NONE = new PowderContext(false, -1, "", List.of());
    }

    // ---- Chat (Teil 4): was Player Messages sonst noch vom Spiel wissen will (jedes erst, wenn gefragt wird) ----

    /** Der eigene Spielername: Die eigene Zeile im Chat hat keinen Farbcode vor dem Doppelpunkt, daran wird sie erkannt. Leer, wenn er nicht bekannt ist */
    default String ownName() {
        return "";
    }

    /** Steht man auf einer Privatinsel? Nur dort tragen die Namen den Gast-Hinweis und die Raenge der Insel */
    default boolean privateIsland() {
        return isPrivateIsland(area());
    }

    /** Was die Tab-Liste ueber die Spieler weiss (Ironman, Fraktion); die echte Fassung liest sie erst, wenn danach gefragt wird */
    default Map<String, TabPlayers.Extra> tabPlayers() {
        return Map.of();
    }

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

    static boolean isDarkAuction(String area) {
        return key(area).equals("dark auction");
    }

    /** Die Privatinsel (auch als Gast), nicht der Garden */
    static boolean isPrivateIsland(String area) {
        String k = key(area);
        return k.equals("private island") || k.equals("private island guest");
    }

    static boolean isCrimsonIsle(String area) {
        return key(area).equals("crimson isle");
    }
}
