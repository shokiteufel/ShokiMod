package com.shokiteufel.shokimod.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Was eine Leiche im Glacite Mineshaft im Schnitt abwirft - Lapis, Umber, Tungsten, Vanguard.
 *
 * Die Beutetabellen sind die Gewichte aus dem Wiki (Glacite Mineshafts, Umber/Tungsten): Jede
 * Runde zieht einen Eintrag mit der Chance Gewicht / Summe der Gewichte, und eine Leiche hat bei
 * vollen Stats 4,902 (Lapis), 5,902 (Umber/Tungsten) oder 6,902 (Vanguard) Runden. Der Erwartungswert
 * ist also Runden mal die Summe aus Chance mal Menge mal Preis.
 *
 * Bewertet wird so, wie der Spieler es sieht: Schluessel zum Preis des hoechsten Kaufauftrags (man
 * kauft sie per Buy Order), alles andere zum niedrigsten Verkaufsangebot nach 1,25 % Bazaar-Steuer
 * (man verkauft per Sell Order), Auktionswaren zum niedrigsten BIN ohne Gebuehren. Eine Ware ohne Preis
 * zaehlt null und steht in {@link Result#missing()} - sie wird nicht stillschweigend weggelassen.
 *
 * Der Frozen Corpse RNG Meter: jede Leiche fuellt ihn, bei 2,5 Mio XP ist das naechste Shattered
 * Locket in einer Vanguard-Leiche sicher (nur deren Tabelle fuehrt es). Ein Shard bringt 10 % mehr XP.
 * Gerechnet wird der Dauerbetrieb ab 0 XP, bei dem jedes Locket den Meter zuruecksetzt.
 *
 * Reine Rechnung ohne Minecraft-Bezug, damit sie ausserhalb des Spiels geprueft werden kann.
 */
public final class CorpseProfit {

    /** Woher die Preise kommen - die Rechnung weiss nichts vom Netz */
    public interface Prices {
        /** Niedrigstes Verkaufsangebot im Bazaar (genau), 0 wenn es keins gibt */
        double offer(String bazaarId);

        /** Hoechster Kaufauftrag im Bazaar (genau), 0 wenn es keinen gibt */
        double bid(String bazaarId);

        /** Niedrigster BIN im Auktionshaus, 0 wenn nichts gelistet ist */
        double bin(String auctionId);
    }

    /** Ein Eintrag der Beutetabelle: Menge, Kennung, Gewicht */
    private record Drop(int qty, String id, int weight) {
    }

    /** Eine Zeile der Auskunft */
    public record Row(String name, double loot, double key, double plain, double metered, boolean hasMeter) {
    }

    /**
     * @param rows    eine Zeile je Leichenart, in der Reihenfolge Lapis, Umber, Tungsten, Vanguard
     * @param locket  Preis des Shattered Locket, 0 wenn unbekannt (dann gibt es keine Meter-Spalte)
     * @param missing Waren ohne Preis, die als null gezaehlt wurden
     */
    public record Result(List<Row> rows, double locket, double naturalLocketChance, Set<String> missing) {
    }

    // ---- Annahmen (Angaben des Users): Max-Stats, Shard, geblockte Kristalle ----

    public static final double ROLLS_LAPIS = 4.902;
    public static final double ROLLS_UMBER_TUNGSTEN = 5.902;
    public static final double ROLLS_VANGUARD = 6.902;
    /** Bazaar-Steuer beim Verkaufsangebot, ohne Perks */
    public static final double BAZAAR_TAX = 0.0125;
    /** Fuer ein Shattered Locket noetige XP im Frozen Corpse RNG Meter */
    public static final double METER_NEED = 2_500_000;
    /** Ein Shard bringt zehn Prozent mehr Meter-XP */
    public static final double METER_BONUS = 1.10;
    private static final double XP_LAPIS = 500 * METER_BONUS;
    private static final double XP_UMBER_TUNGSTEN = 2_500 * METER_BONUS;
    private static final double XP_VANGUARD = 25_000 * METER_BONUS;

    public static final String LOCKET = "SHATTERED_PENDANT";
    private static final Set<String> KEYS = Set.of("UMBER_KEY", "TUNGSTEN_KEY", "SKELETON_KEY");
    /** Was es nur im Auktionshaus gibt */
    private static final Set<String> AUCTION = Set.of("CAGED_WISP", LOCKET);

    private static final String[] GEMS = {"ONYX", "PERIDOT", "CITRINE", "AQUAMARINE"};

    private static final List<Drop> LAPIS = new ArrayList<>();
    private static final List<Drop> UMBER_TUNGSTEN = new ArrayList<>();
    private static final List<Drop> VANGUARD = new ArrayList<>();

    static {
        // Lapis-Leiche, kein Schluessel
        for (String g : GEMS) {
            LAPIS.add(new Drop(20, "FLAWED_" + g + "_GEM", 1000));
            LAPIS.add(new Drop(40, "FLAWED_" + g + "_GEM", 500));
            LAPIS.add(new Drop(1, "FINE_" + g + "_GEM", 250));
            LAPIS.add(new Drop(2, "FINE_" + g + "_GEM", 125));
        }
        LAPIS.add(new Drop(1, "SUSPICIOUS_SCRAP", 500));
        LAPIS.add(new Drop(1, "GOBLIN_EGG", 800));
        LAPIS.add(new Drop(2, "GOBLIN_EGG", 400));
        LAPIS.add(new Drop(4, "GOBLIN_EGG", 200));
        for (String c : new String[]{"GREEN", "YELLOW", "RED"}) {
            LAPIS.add(new Drop(1, "GOBLIN_EGG_" + c, 700));
            LAPIS.add(new Drop(2, "GOBLIN_EGG_" + c, 350));
            LAPIS.add(new Drop(4, "GOBLIN_EGG_" + c, 175));
        }
        for (String m : new String[]{"UMBER", "TUNGSTEN", "GLACITE"}) {
            LAPIS.add(new Drop(1, "ENCHANTED_" + m, 500));
            LAPIS.add(new Drop(2, "ENCHANTED_" + m, 250));
            LAPIS.add(new Drop(4, "ENCHANTED_" + m, 125));
        }
        LAPIS.add(new Drop(1, "REFINED_UMBER", 25));
        LAPIS.add(new Drop(1, "REFINED_TUNGSTEN", 25));
        LAPIS.add(new Drop(1, "GLACITE_AMALGAMATION", 25));
        LAPIS.add(new Drop(1, "UMBER_KEY", 25));
        LAPIS.add(new Drop(1, "TUNGSTEN_KEY", 25));
        LAPIS.add(new Drop(1, "GLACITE_JEWEL", 500));
        LAPIS.add(new Drop(2, "GLACITE_JEWEL", 250));
        LAPIS.add(new Drop(1, "BEJEWELED_HANDLE", 100));
        LAPIS.add(new Drop(2, "BEJEWELED_HANDLE", 50));
        LAPIS.add(new Drop(4, "BEJEWELED_HANDLE", 25));

        // Umber- und Tungsten-Leiche teilen sich die Tabelle (ohne die fuenf Kristalle: geblockt)
        for (String g : GEMS) {
            UMBER_TUNGSTEN.add(new Drop(4, "FINE_" + g + "_GEM", 700));
            UMBER_TUNGSTEN.add(new Drop(8, "FINE_" + g + "_GEM", 350));
            UMBER_TUNGSTEN.add(new Drop(16, "FINE_" + g + "_GEM", 175));
            UMBER_TUNGSTEN.add(new Drop(1, "FLAWLESS_" + g + "_GEM", 25));
        }
        UMBER_TUNGSTEN.add(new Drop(2, "SUSPICIOUS_SCRAP", 700));
        UMBER_TUNGSTEN.add(new Drop(4, "SUSPICIOUS_SCRAP", 350));
        UMBER_TUNGSTEN.add(new Drop(1, "ENCHANTMENT_ICE_COLD_1", 80));
        UMBER_TUNGSTEN.add(new Drop(1, "GOBLIN_EGG_BLUE", 80));
        UMBER_TUNGSTEN.add(new Drop(1, "REFINED_UMBER", 20));
        UMBER_TUNGSTEN.add(new Drop(1, "REFINED_TUNGSTEN", 20));
        UMBER_TUNGSTEN.add(new Drop(1, "GLACITE_AMALGAMATION", 20));
        UMBER_TUNGSTEN.add(new Drop(1, "UMBER_PLATE", 5));
        UMBER_TUNGSTEN.add(new Drop(1, "TUNGSTEN_PLATE", 5));
        UMBER_TUNGSTEN.add(new Drop(1, "UMBER_KEY", 10));
        UMBER_TUNGSTEN.add(new Drop(1, "TUNGSTEN_KEY", 10));
        UMBER_TUNGSTEN.add(new Drop(1, "MITHRIL_PLATE", 10));
        UMBER_TUNGSTEN.add(new Drop(1, "FROZEN_SCUTE", 4));
        UMBER_TUNGSTEN.add(new Drop(1, "DWARVEN_OS_METALLIC_MINIS", 20));
        UMBER_TUNGSTEN.add(new Drop(1, "SKELETON_KEY", 4));
        UMBER_TUNGSTEN.add(new Drop(1, "CAGED_WISP", 2));

        // Vanguard-Leiche (Skeleton Key), ebenfalls ohne Kristalle
        for (String g : GEMS) {
            VANGUARD.add(new Drop(1, "FLAWLESS_" + g + "_GEM", 160));
            VANGUARD.add(new Drop(2, "FLAWLESS_" + g + "_GEM", 80));
        }
        VANGUARD.add(new Drop(1, "DWARVEN_OS_METALLIC_MINIS", 120));
        VANGUARD.add(new Drop(8, "SUSPICIOUS_SCRAP", 160));
        VANGUARD.add(new Drop(16, "SUSPICIOUS_SCRAP", 80));
        VANGUARD.add(new Drop(1, "ENCHANTMENT_ICE_COLD_1", 160));
        VANGUARD.add(new Drop(1, "GOBLIN_EGG_BLUE", 160));
        VANGUARD.add(new Drop(2, "GOBLIN_EGG_BLUE", 80));
        VANGUARD.add(new Drop(1, "REFINED_UMBER", 160));
        VANGUARD.add(new Drop(2, "REFINED_UMBER", 80));
        VANGUARD.add(new Drop(1, "REFINED_TUNGSTEN", 160));
        VANGUARD.add(new Drop(2, "REFINED_TUNGSTEN", 80));
        VANGUARD.add(new Drop(1, "GLACITE_AMALGAMATION", 160));
        VANGUARD.add(new Drop(2, "GLACITE_AMALGAMATION", 80));
        VANGUARD.add(new Drop(4, "GLACITE_AMALGAMATION", 40));
        VANGUARD.add(new Drop(1, "MITHRIL_PLATE", 60));
        VANGUARD.add(new Drop(1, "UMBER_PLATE", 30));
        VANGUARD.add(new Drop(1, "TUNGSTEN_PLATE", 30));
        VANGUARD.add(new Drop(1, "UMBER_KEY", 80));
        VANGUARD.add(new Drop(2, "UMBER_KEY", 40));
        VANGUARD.add(new Drop(4, "UMBER_KEY", 20));
        VANGUARD.add(new Drop(1, "TUNGSTEN_KEY", 80));
        VANGUARD.add(new Drop(2, "TUNGSTEN_KEY", 40));
        VANGUARD.add(new Drop(4, "TUNGSTEN_KEY", 20));
        VANGUARD.add(new Drop(1, "SKELETON_KEY", 10));
        VANGUARD.add(new Drop(1, "FROZEN_SCUTE", 10));
        VANGUARD.add(new Drop(1, "CAGED_WISP", 10));
        VANGUARD.add(new Drop(1, LOCKET, 5));
    }

    private CorpseProfit() {
    }

    /** Die Gewichtssumme einer Tabelle - Pruefwert fuer die Abschrift aus dem Wiki */
    static int totalWeight(List<Drop> table) {
        int sum = 0;
        for (Drop d : table) sum += d.weight();
        return sum;
    }

    static int weightSumLapis() {
        return totalWeight(LAPIS);
    }

    static int weightSumUmberTungsten() {
        return totalWeight(UMBER_TUNGSTEN);
    }

    static int weightSumVanguard() {
        return totalWeight(VANGUARD);
    }

    /** Der Wert eines Stuecks - und ob es einen Preis gab. Fehlendes steht in {@code missing} */
    private static double value(String id, Prices prices, Set<String> missing) {
        double v;
        if (AUCTION.contains(id)) {
            v = prices.bin(id);
        } else {
            double offer = prices.offer(id);
            double bid = prices.bid(id);
            // Beide Seiten muessen es geben, sonst ist der Preis nicht verlaesslich
            if (offer <= 0 || bid <= 0) {
                v = 0;
            } else {
                v = KEYS.contains(id) ? bid : offer * (1 - BAZAAR_TAX);
            }
        }
        if (v <= 0) {
            missing.add(id);
            return 0;
        }
        return v;
    }

    /** Erwartungswert einer Leiche: Runden mal Summe aus Chance mal Menge mal Preis, ohne {@code skip} */
    private static double expected(List<Drop> table, double rolls, Prices prices, Set<String> missing, String skip) {
        int total = totalWeight(table);
        double sum = 0;
        for (Drop d : table) {
            if (d.id().equals(skip)) continue;
            sum += (double) d.weight() / total * d.qty() * value(d.id(), prices, missing);
        }
        return rolls * sum;
    }

    public static Result compute(Prices prices) {
        Set<String> missing = new LinkedHashSet<>();

        double evLapis = expected(LAPIS, ROLLS_LAPIS, prices, missing, null);
        double evUt = expected(UMBER_TUNGSTEN, ROLLS_UMBER_TUNGSTEN, prices, missing, null);
        double evVgAll = expected(VANGUARD, ROLLS_VANGUARD, prices, missing, null);
        double evVgEx = expected(VANGUARD, ROLLS_VANGUARD, prices, missing, LOCKET);

        double keyUmber = value("UMBER_KEY", prices, missing);
        double keyTungsten = value("TUNGSTEN_KEY", prices, missing);
        double keySkeleton = value("SKELETON_KEY", prices, missing);
        double locket = value(LOCKET, prices, missing);

        // Chance auf ein Locket in einer Vanguard-Leiche ohne Meter
        int locketWeight = 0;
        for (Drop d : VANGUARD) if (d.id().equals(LOCKET)) locketWeight += d.weight();
        double perRoll = (double) locketWeight / totalWeight(VANGUARD);
        double natural = 1 - Math.pow(1 - perRoll, ROLLS_VANGUARD);

        boolean meter = locket > 0;

        // Eine Vanguard-Leiche, in der das Locket sicher faellt - sie loest den Meter ein
        double cashIn = evVgEx - keySkeleton + locket;

        List<Row> rows = new ArrayList<>();
        rows.add(row("Lapis", evLapis, 0, XP_LAPIS, meter, cashIn));
        rows.add(row("Umber", evUt, keyUmber, XP_UMBER_TUNGSTEN, meter, cashIn));
        rows.add(row("Tungsten", evUt, keyTungsten, XP_UMBER_TUNGSTEN, meter, cashIn));

        // Vanguard: ohne Meter faellt das Locket nur zufaellig, mit Meter nach durchschnittlich
        // (1 - (1 - q)^(k + 1)) / q Leichen, spaetestens in der k + 1.
        double plainVg = evVgAll - keySkeleton;
        double meteredVg = 0;
        if (meter && natural > 0) {
            double k = Math.ceil(METER_NEED / XP_VANGUARD);
            double expectedCorpses = (1 - Math.pow(1 - natural, k + 1)) / natural;
            double total = expectedCorpses * (evVgEx - keySkeleton) + locket;
            meteredVg = total / expectedCorpses;
        }
        rows.add(new Row("Vanguard", evVgAll, keySkeleton, plainVg, meteredVg, meter));

        return new Result(List.copyOf(rows), locket, natural, missing);
    }

    /**
     * Umber, Tungsten, Lapis: n Leichen fuellen den Meter, dann eine Vanguard-Leiche mit sicherem
     * Locket. Der Gewinn des ganzen Zyklus geteilt durch n + 1 Leichen.
     */
    private static Row row(String name, double ev, double key, double xp, boolean meter, double cashIn) {
        double plain = ev - key;
        double metered = 0;
        if (meter) {
            double n = Math.ceil(METER_NEED / xp);
            metered = (n * plain + cashIn) / (n + 1);
        }
        return new Row(name, ev, key, plain, metered, meter);
    }
}
