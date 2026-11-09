package com.shokiteufel.shokimod.dungeonkarte;

/** Die Art einer Tuer zwischen zwei Raeumen. Erkannt wird sie am Block auf Hoehe 69 (oder an der Farbe der Hypixel-Karte) */
public enum TuerTyp {
    BLUT, WITHER, NORMAL, EINGANG;

    /** Der Typ zu dem Block in der Tuer; Luft und alles Unbekannte ist eine normale Tuer */
    public static TuerTyp vonBlock(String registername) {
        if (registername == null) return NORMAL;
        return switch (registername) {
            case "minecraft:red_terracotta" -> BLUT;
            case "minecraft:coal_block" -> WITHER;
            case "minecraft:infested_chiseled_stone_bricks" -> EINGANG;
            default -> NORMAL;
        };
    }

    /** Der Typ zu einer Farbe der Hypixel-Karte; null, wenn die Farbe keine Tuer meint */
    public static TuerTyp vonKartenfarbe(int farbe) {
        return switch (farbe) {
            case 18 -> BLUT;
            case 30 -> EINGANG;
            case 119 -> WITHER;
            case 74, 82, 66, 62, 85, 63 -> NORMAL;
            default -> null;
        };
    }
}
