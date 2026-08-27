package com.shokiteufel.shokimod.data;

/** Feste Zeichenketten von Hypixel und ein paar Vergleichshilfen. */
public class ModConstants {

    // Ein Namensschild-Text, der schon fertig gefärbt ist und nicht als Zahl gelesen werden soll
    public static final String RAW_HEALTH_PREFIX = "RAW:";

    // ---- Ortsbestimmung ----
    public static final String GAME_TYPE_SKYBLOCK = "SKYBLOCK";
    public static final String MAP_SAFARI = "Safari";
    public static final String MAP_MOONGLADE_MARSH = "Moonglade Marsh";

    // Zeilen der Tab-Liste, aus denen Gebiet und Server kommen
    public static final String TAB_AREA_PREFIX = "Area:";
    public static final String TAB_SERVER_PREFIX = "Server:";

    // Titel der Seitenleiste, an dem SkyBlock zu erkennen ist
    public static final String SIDEBAR_SKYBLOCK_TITLE = "SKYBLOCK";

    public static boolean containsIgnoreCase(String source, String target) {
        return source != null && target != null
                && source.toLowerCase(java.util.Locale.ROOT).contains(target.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean startsWithIgnoreCase(String source, String prefix) {
        return source != null && prefix != null
                && source.toLowerCase(java.util.Locale.ROOT).startsWith(prefix.toLowerCase(java.util.Locale.ROOT));
    }
}
