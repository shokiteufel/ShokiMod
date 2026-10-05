package com.shokiteufel.shokimod.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Was sich am Bazaar durch Auftrag und Angebot verdienen laesst.
 *
 * Der Handel besteht aus zwei eigenen Auftraegen: Ein Kaufauftrag wartet, bis jemand
 * ihm verkauft, ein Verkaufsangebot wartet, bis jemand ihm abkauft. Dazwischen liegt
 * die Spanne, und sie ist der ganze Verdienst.
 *
 * <p>Gerechnet wird gegen die Spitze des Auftragsbuchs, nicht gegen die Mittel aus
 * quick_status: Fuer einen eigenen Auftrag zaehlt allein, wen man ueberbieten oder
 * unterbieten muss. Wer sich einreihen will, legt 0,1 Muenzen drauf beziehungsweise ab
 * - genau das steht hier in der Rechnung, denn bei billigen Waren ist dieser Zehntel
 * die halbe Spanne.
 *
 * <p>Vom Verkauf zieht Hypixel eine Steuer ab, bevor die Muenzen ankommen. Sie haengt
 * an den Gemeinschafts-Ausbauten und am Bazaar-Flipper-Vorteil und steht deshalb in den
 * Einstellungen.
 *
 * <p>Die Umschlagzahlen sagen, ob sich eine Spanne ueberhaupt holen laesst. Eine Ware,
 * die zweimal am Tag den Besitzer wechselt, kann die schoenste Spanne haben - der
 * Auftrag wird nie bedient. Hypixel fuehrt nur die Summe der letzten sieben Tage, der
 * Tageswert ist ein Siebtel davon.
 */
public final class BazaarFlips {

    /** Um so viel muss ein eigener Auftrag ueber- beziehungsweise unterbieten */
    public static final double STEP = 0.1;

    private BazaarFlips() {
    }

    /**
     * Eine Ware mit allem, was die Entscheidung braucht.
     *
     * @param buyOrder   was der eigene Kaufauftrag kostet (hoechster Auftrag plus 0,1)
     * @param sellOffer  was das eigene Angebot einbringt (guenstigstes Angebot minus 0,1)
     * @param profit     was nach Steuer je Stueck uebrig bleibt
     * @param bought     Stueck, die am Tag gekauft werden
     * @param sold       Stueck, die am Tag verkauft werden
     */
    public record Row(String id, String name, double buyOrder, double sellOffer,
                      double profit, long bought, long sold) {

        /** Der Gewinn im Verhaeltnis zum Einsatz, in Prozent */
        public double margin() {
            return buyOrder <= 0 ? 0 : profit / buyOrder * 100.0;
        }

        /**
         * Was ein Tag hergaebe, wenn man die ganze Ware umschlagen koennte.
         *
         * Eine Obergrenze, keine Vorhersage: Gehandelt wird die kleinere der beiden
         * Mengen, denn gekauft werden muss ebenso wie verkauft. In Wirklichkeit teilt
         * man sie mit allen anderen, die denselben Auftrag stellen.
         */
        public double perDay() {
            return profit * Math.min(bought, sold);
        }
    }

    /**
     * Alle Waren, bei denen zwischen Auftrag und Angebot etwas uebrig bleibt.
     *
     * @param taxPercent  die Steuer auf den Verkauf, in Prozent
     * @param minPerDay   wie oft die Ware taeglich mindestens den Besitzer wechseln muss
     */
    public static List<Row> rows(double taxPercent, long minPerDay) {
        List<Row> out = new ArrayList<>();
        for (String id : BazaarLive.ids()) {
            Row row = rowFor(id, taxPercent);
            if (row == null) continue;
            if (row.bought() < minPerDay || row.sold() < minPerDay) continue;
            out.add(row);
        }
        out.sort((a, b) -> Double.compare(b.profit(), a.profit()));
        return out;
    }

    /** Eine einzelne Ware, oder null wenn sich damit nichts verdienen laesst */
    public static Row rowFor(String id, double taxPercent) {
        double angebot = BazaarLive.cheapestOfferExact(id);
        double auftrag = BazaarLive.highestBidExact(id);
        if (angebot <= 0 || auftrag <= 0) return null;

        double kauf = buyAt(auftrag);
        double verkauf = sellAt(angebot);
        double profit = profitOf(angebot, auftrag, taxPercent);
        if (profit <= 0) return null;

        long gekauft = Math.max(0L, BazaarLive.boughtPerDay(id));
        long verkauft = Math.max(0L, BazaarLive.soldPerDay(id));
        return new Row(id, nameOf(id), kauf, verkauf, profit, gekauft, verkauft);
    }

    /**
     * Der Name einer Bazaar-Ware - immer einer, nie null.
     *
     * Die Item-Liste von Hypixel fuehrt laengst nicht alles, was der Bazaar handelt:
     * Verzauberungen heissen dort ENCHANTMENT_FEAST_1 und stehen in der Liste gar
     * nicht, ebenso Essence. Ein fehlender Name hat das Fenster beim Zeichnen
     * abgeschossen - bei 2.197 Waren ist das keine Randerscheinung, sondern der
     * Normalfall. readableName baut ihn aus der Kennung und kennt die Sonderfaelle.
     */
    public static String nameOf(String id) {
        // shortName fragt erst die Liste und baut den Namen nur sonst aus der Kennung.
        // Andersherum ging es schief: Der Bazaar handelt zehn Waren mit den alten
        // Minecraft-Kennungen, in denen eine Zahl hinter einem Doppelpunkt die Sorte
        // angibt - LOG:3 ist ein Jungle Log, INK_SACK:4 Lapis. Diese Zahl laesst sich
        // ohne die Liste nicht aufloesen, und es stand "Log:3" da
        String name = ItemNames.shortName(id);
        if (name != null && !name.isBlank()) return name;
        return String.valueOf(id);
    }

    /**
     * Der Name, unter dem Hypixel die Ware im Bazaar findet.
     *
     * Nicht derselbe wie in der Anzeige: Dort steht "Sunset 1", weil das in eine Zeile
     * passt - /bz sucht aber nach dem Namen aus der Item-Liste. Gibt es den nicht,
     * bleibt der angezeigte, und die Suche fuehrt wenigstens in die Naehe.
     */
    public static String searchName(String id) {
        String roemisch = bookSearchName(id);
        if (roemisch != null) return roemisch;
        String name = ItemNames.displayName(id);
        return name == null || name.isBlank() ? nameOf(id) : name;
    }

    /** "I" bis "X" - weiter gehen die Stufen der Verzauberungen nicht */
    private static final String[] ROEMISCH =
            {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /**
     * Der Suchname eines Buches, oder null wenn die Kennung keines ist.
     *
     * Angezeigt wird "Chimera 5", weil das in eine Zeile passt und sich lesen laesst.
     * Der Basar sucht aber nach dem Namen, wie er im Spiel steht, und dort traegt die
     * Stufe eine roemische Zahl: "Chimera V". Mit der arabischen findet /bz nichts, und
     * das Fenster ging ins Leere auf.
     */
    static String bookSearchName(String id) {
        if (id == null || !id.startsWith("ENCHANTMENT_")) return null;
        int strich = id.lastIndexOf('_');
        if (strich <= 0 || strich + 1 >= id.length()) return null;
        int zahl;
        try {
            zahl = Integer.parseInt(id.substring(strich + 1));
        } catch (NumberFormatException e) {
            return null;   // eine Kennung ohne Stufe am Ende ist kein Buch
        }
        if (zahl < 1 || zahl >= ROEMISCH.length) return null;

        String name = SkyBlockItems.readableName(id);
        // readableName haengt die Stufe als Zahl an - die wird ersetzt
        int letzte = name.lastIndexOf(' ');
        String stamm = letzte > 0 ? name.substring(0, letzte) : name;
        return stamm + " " + ROEMISCH[zahl];
    }

    /** Was der eigene Kaufauftrag kosten muss, um vor dem hoechsten zu stehen */
    public static double buyAt(double hoechsterAuftrag) {
        return hoechsterAuftrag + STEP;
    }

    /** Was das eigene Angebot kosten muss, um vor dem guenstigsten zu stehen */
    public static double sellAt(double guenstigstesAngebot) {
        return guenstigstesAngebot - STEP;
    }

    /**
     * Was nach Steuer je Stueck uebrig bleibt, oder 0 wenn nichts uebrig bleibt.
     *
     * Steht das eigene Angebot unter dem eigenen Auftrag, gibt es keine Spanne: beide
     * Seiten des Buchs liegen dann direkt aneinander, und wer sich dazwischenstellen
     * will, hat keinen Platz.
     */
    public static double profitOf(double guenstigstesAngebot, double hoechsterAuftrag, double taxPercent) {
        double kauf = buyAt(hoechsterAuftrag);
        double verkauf = sellAt(guenstigstesAngebot);
        if (verkauf <= kauf) return 0;
        double profit = verkauf * (1.0 - taxPercent / 100.0) - kauf;
        return Math.max(0, profit);
    }

    /** Preise am Bazaar haben zwei Nachkommastellen - abgeschnitten wird nichts davon */
    public static String coins(double wert) {
        if (Math.abs(wert) >= 100_000) return ItemValue.format((long) Math.round(wert));
        String text = String.format(java.util.Locale.ROOT, "%.1f", wert);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
