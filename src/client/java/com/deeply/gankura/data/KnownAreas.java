package com.deeply.gankura.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Auswahlliste der Gebiete für den Ortsfilter von Chatregeln.
 *
 * Der Grundstock stammt aus Skyblockers Location-Enum (LGPL-3.0) und deckt die
 * bekannten Inseln ab. Weil die Tab-Liste feiner unterteilt als dieses Enum -
 * "Critter Safari Entrance" statt nur "Safari" - und Hypixel laufend Gebiete
 * ergänzt, wächst die Liste im Betrieb mit: jedes betretene Gebiet, das noch
 * nicht bekannt ist, wird aufgenommen und in der Config gespeichert.
 */
public final class KnownAreas {

    /** Bekannte Inseln als Startpunkt, damit die Auswahl nicht leer beginnt */
    private static final List<String> SEED = List.of(
            "Private Island", "Garden", "Hub", "The Farming Islands", "The Park",
            "Spider's Den", "The End", "Crimson Isle", "Gold Mine", "Deep Caverns",
            "Dwarven Mines", "Backwater Bayou", "Dungeon Hub", "Jerry's Workshop",
            "The Rift", "Dark Auction", "Crystal Hollows", "Dungeons", "Kuudra's Hollow",
            "Glacite Mineshafts", "Moonglade Marsh", "Torrhus Canyon", "Safari", "Lotus Atoll"
    );

    private KnownAreas() {
    }

    /**
     * Merkt sich ein betretenes Gebiet, falls es neu ist.
     * Wird bei jedem Tab-Listen-Durchlauf aufgerufen, muss also billig bleiben.
     */
    public static void observe(String area) {
        if (!isUsable(area)) return;

        List<String> discovered = ModConfig.INSTANCE.customize.discoveredAreas;
        if (discovered == null) return;
        if (SEED.contains(area) || discovered.contains(area)) return;

        discovered.add(area);
        ModConfig.INSTANCE.saveNow();
    }

    /** Grundstock und selbst entdeckte Gebiete, ohne Dubletten, in stabiler Reihenfolge */
    public static List<String> all() {
        Set<String> merged = new LinkedHashSet<>(SEED);
        List<String> discovered = ModConfig.INSTANCE.customize.discoveredAreas;
        if (discovered != null) {
            for (String area : discovered) {
                if (isUsable(area)) merged.add(area);
            }
        }
        return new ArrayList<>(merged);
    }

    /** Nur die selbst entdeckten - für die Anzeige, wie viele dazugekommen sind */
    public static int discoveredCount() {
        List<String> discovered = ModConfig.INSTANCE.customize.discoveredAreas;
        return discovered == null ? 0 : discovered.size();
    }

    public static boolean isSeeded(String area) {
        return SEED.contains(area);
    }

    private static boolean isUsable(String area) {
        return area != null && !area.isBlank() && !"Unknown".equals(area);
    }
}
