package com.shokiteufel.shokimod.paket.kern;

/**
 * Wann ist ein Zugang ein Fund? Die einzige Regel, die aus den Ursachen des {@link Kern}s den Profit-Tracker speist.
 *
 * <p>Grundsatz: Ein Zugang ist ein Fund, wenn der Server ihn gegeben hat und kein eigener Schritt (Klick, Kauf, Befehl, Umzug, Wurf,
 * Rueckgabe) ihn erklaert. Dann zaehlt er, auch wenn die Quelle unbekannt bleibt. Bei offenem Menue braucht es einen positiven Beleg
 * (Mob-Tod, Aufsammeln): dort ist die Antwort auf einen eigenen Klick nicht auszuschliessen, ein Kauf oder eine Abholung darf kein Fund sein.
 */
public final class FundRegel {
    private FundRegel() {
    }

    /**
     * Was ein Dungeon-Lauf selbst verteilt: die Faehigkeiten der gewaehlten Klasse ("MAGE_DUNGEON_ABILITY_1", "_3") und die Dungeon-Traenke
     * ("POTION_DUNGEON;5"). Der Server legt sie zu Beginn und nach einer Wiederbelebung neu ins Inventar (ohne uuid, darum sieht es wie ein
     * neuer Zugang aus). Beute ist das nie. Gemessen am Dungeon-Lauf von Schiggy am 09.11. (Catacombs Floor V, ein Tod mit Revive Stone):
     * zwei Faehigkeiten und zwei Traenke standen als Fund im Profit-Tracker.
     */
    static boolean verteiltDerDungeonSelbst(String id) {
        return id != null && (id.contains("_DUNGEON_ABILITY_") || id.startsWith("POTION_DUNGEON"));
    }

    public static boolean istFund(Kern.Zugang z) {
        // Ruestung am Koerper ist nie Beute, egal was der Kern als Ursache annimmt (nach einem Mob-Tod haette ein Neusenden "Mob-Drop" geheissen).
        // Auch bei offenem Menue: Der Platz ist immer der des Spielerinventars, und die Container-Nummer nennt dann nur das Fenster, das offen ist
        // (Schiggy 09.11., 19:12: Magma Lord Chestplate/Leggings/Boots standen als "mob drop" im Tracker, weil der Wechsel des Sets bei offenem
        // Fenster 12 kam und gerade ein Mob starb)
        if (Kern.AUSRUESTUNGS_PLAETZE.contains(z.platz())) {
            return false;
        }
        if (verteiltDerDungeonSelbst(z.id())) {
            return false;
        }
        return switch (z.ursache()) {
            case MOB_DROP, MEHRERE_TODE, AUFGESAMMELT -> true;
            // Ein Pet Item ohne jeden Beleg ist fast immer das alte aus einem Tausch (kein Chat, kein Klick); echte Pet-Item-Beute meldet
            // Hypixel als Drop-Zeile, die der Profit-Tracker aus dem Chat nachtraegt (siehe FundBuch), oder sie kommt von einem Mob-Tod
            case UNBEKANNT -> z.container() == 0 && !PetItems.ist(z.id());
            case KAUF, KLICK_ANTWORT, UMZUG, RUECKKEHR, WURF, RESYNC, MEHRDEUTIG -> false;
        };
    }

    /** Ein kurzer Name fuer das Log und die Quelle im Profit-Tracker. */
    public static String quelle(Kern.Ursache u) {
        return switch (u) {
            case MOB_DROP -> "mob drop";
            case MEHRERE_TODE -> "mob drop (several kills)";
            case AUFGESAMMELT -> "picked up";
            case UNBEKANNT -> "server (source unknown)";
            default -> u.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
}
