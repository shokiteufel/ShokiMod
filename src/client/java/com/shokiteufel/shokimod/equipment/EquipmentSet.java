package com.shokiteufel.shokimod.equipment;

import com.shokiteufel.shokimod.data.ModConstants;

import java.util.Locale;

/**
 * Welcher Satz Equipment gilt: der gewoehnliche, der in der Rift oder der in der Safari.
 *
 * Herkunft der Idee: Skyblocker (LGPL-3.0, {@code SkyblockInventoryScreen}). Von dort stammt die Regel: Die Rift und die Safari haben jede ihre eigenen vier
 * Plaetze (in der Rift und in der Safari steht dort anderes als auf den uebrigen Inseln), und das Equipment-Menue liest und schreibt immer den Satz, der zum
 * Gebiet gehoert. Die Umsetzung ist eigene Arbeit: Java; das Gebiet kommt hier aus der Gebietszeile der Tab-Liste ({@code GameState.Server.map}), nicht aus der Mod-API.
 */
public enum EquipmentSet {
    NORMAL("normal"),
    RIFT("rift"),
    SAFARI("safari");

    private final String key;

    EquipmentSet(String key) {
        this.key = key;
    }

    /** Der Name in der Datei */
    public String key() {
        return key;
    }

    /** Der Satz zu einem Namen aus der Datei, oder null, wenn die Datei einen kennt, den es nicht gibt */
    public static EquipmentSet byKey(String key) {
        if (key == null) return null;
        for (EquipmentSet set : values()) {
            if (set.key.equals(key)) return set;
        }
        return null;
    }

    /**
     * Der Satz, der in diesem Gebiet gilt. Gebiet ist die Insel aus der Gebietszeile ("The Rift", "Safari"); ein noch nicht gelesenes oder unbekanntes
     * Gebiet ist der gewoehnliche Satz. Die Rift kommt in zwei Schreibweisen vor ("The Rift", "Rift Dimension" - wie bei {@code HideOtherHooks}).
     */
    public static EquipmentSet forMap(String map) {
        if (map == null) return NORMAL;
        // Wird in jedem Bild gefragt, das Gebiet wechselt aber selten: derselbe Text (dasselbe Objekt) ergibt dieselbe Antwort, ohne neu zu rechnen
        if (map == lastMap) return lastSet;
        String name = map.trim().toLowerCase(Locale.ROOT);
        EquipmentSet set = NORMAL;
        if (name.equals("the rift") || name.equals("rift dimension")) set = RIFT;
        else if (name.equals(ModConstants.MAP_SAFARI.toLowerCase(Locale.ROOT))) set = SAFARI;
        lastMap = map;
        lastSet = set;
        return set;
    }

    /** Das zuletzt gefragte Gebiet und seine Antwort (nur der Spiel-Faden fragt) */
    private static String lastMap = null;
    private static EquipmentSet lastSet = NORMAL;
}
