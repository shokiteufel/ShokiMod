package com.shokiteufel.shokimod.dungeonkarte;

/**
 * Die Art eines Dungeon-Raums. Die Farbnummern sind die der Hypixel-Karte (Eingang 30, Blut 18, Fairy 82, selten 34,
 * Miniboss 74, Puzzle 66, Falle 62, normal 63), so liest der {@link KartenLeser} sie ab.
 */
public enum RaumTyp {
    BLUT, FAIRY, SELTEN, MINIBOSS, PUZZLE, FALLE, NORMAL, EINGANG;

    /** Der Typ zu einer Farbe der Hypixel-Karte; null, wenn die Farbe keinen Raum meint */
    public static RaumTyp vonKartenfarbe(int farbe) {
        return switch (farbe) {
            case 18 -> BLUT;
            case 82 -> FAIRY;
            case 34 -> SELTEN;
            case 74 -> MINIBOSS;
            case 66 -> PUZZLE;
            case 62 -> FALLE;
            case 63, 85 -> NORMAL;
            case 30 -> EINGANG;
            default -> null;
        };
    }

    /** Der Typ aus der Raumtabelle (BLOOD, FAIRY, RARE, CHAMPION, PUZZLE, TRAP, NORMAL, ENTRANCE); unbekannte Namen gelten als normal */
    static RaumTyp vonTabelle(String name) {
        if (name == null) return NORMAL;
        return switch (name) {
            case "BLOOD" -> BLUT;
            case "FAIRY" -> FAIRY;
            case "RARE" -> SELTEN;
            case "CHAMPION" -> MINIBOSS;
            case "PUZZLE" -> PUZZLE;
            case "TRAP" -> FALLE;
            case "ENTRANCE" -> EINGANG;
            default -> NORMAL;
        };
    }
}
