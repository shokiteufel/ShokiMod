package com.shokiteufel.shokimod.garden;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

/**
 * Der Text der Einkaufsliste der Besucher ("Visitor Shopping List") und die Regel, wo sie steht.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code GardenVisitorShoppingList}, {@code ShoppingListConfig}). Von dort stammen der Aufbau - eine Kopfzeile mit der Summe
 * der Preise, darunter jeder Gegenstand mit Menge und Preis, dann die neuen Besucher (ohne bekanntes Angebot), dann die uebrigen mit Namen - und die Regeln: Besucher, die
 * schon angenommen oder abgelehnt sind, zaehlen nicht; wer aus der Liste genommen wurde (Klick auf den Namen, durchgestrichen), zaehlt in der Summe nicht, steht aber weiter
 * da. Die Liste steht im Garden (nur auf dem Plot der Scheune, wenn "Only when Close" an ist), in der Bazaar Alley im Hub und, mit "Farming Areas", auf den Farming Islands
 * und auf dem Hof im Hub. Die Umsetzung ist eigene Arbeit: Java, alles hier ist rein (Preise und Zahlenformat kommen als Funktionen herein); es gibt weder Mengen im Sack
 * noch Super Craft noch die Vorschau, die SkyHanni hat.
 */
public final class ShoppingListText {

    /** Das Gebiet der Insel "Garden" in der Tab-Liste */
    public static final String MAP_GARDEN = "Garden";
    public static final String MAP_HUB = "Hub";
    public static final String MAP_FARMING_ISLANDS = "The Farming Islands";
    /** Die Zone (aus der Seitenleiste) in der Bazaar Alley und auf dem Hof im Hub */
    public static final String ZONE_BAZAAR_ALLEY = "Bazaar Alley";
    public static final String ZONE_FARM = "Farm";

    static final String TITLE = "§7Visitor Shopping List:";

    /**
     * Die fertigen Zeilen (mit Farbcodes; eine leere ist eine Leerzeile) und zu jeder, wohin ein Klick geht.
     *
     * @param visitorAt zu jeder Zeile der Name des Besuchers, dessen Name dort steht, sonst null
     * @param itemAt    zu jeder Zeile der Name des Gegenstands, dessen Zeile es ist, sonst null
     */
    public record Built(List<String> lines, List<String> visitorAt, List<String> itemAt) {

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    private ShoppingListText() {
    }

    /** Eine Zahl mit Tausenderpunkten, wie die Menge in der Liste ("1,280") */
    static String amount(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    /**
     * Die Liste bauen.
     *
     * @param book      die Besucher
     * @param showPrice mit Preisen (Kopfzeile und je Gegenstand)
     * @param unitPrice Kaufpreis eines Gegenstands nach Namen, unter null: unbekannt (dann steht dort kein Preis)
     * @param format    schreibt eine Summe kurz ("12.3k")
     */
    public static Built build(VisitorBook book, boolean showPrice, ToDoubleFunction<String> unitPrice, DoubleFunction<String> format) {
        return build(book, showPrice, unitPrice, format, UnaryOperator.identity());
    }

    /**
     * Wie {@link #build(VisitorBook, boolean, ToDoubleFunction, DoubleFunction)}, mit einer Schrift fuer die Mengen.
     *
     * @param amountStyle formt die Zahl einer Menge ("160") fuer die Anzeige, zum Beispiel fett; steht hinter dem "x" und vor dem naechsten Farbcode, den
     *                    ein "§l" darin also beendet. Die Preise formt {@code format}. Der Aufrufer macht aus beidem nur bei der Anzeige etwas
     *                    Besonderes - der Text dieser Klasse selbst bleibt schlicht
     */
    public static Built build(VisitorBook book, boolean showPrice, ToDoubleFunction<String> unitPrice, DoubleFunction<String> format,
                              UnaryOperator<String> amountStyle) {
        List<String> lines = new ArrayList<>();
        List<String> visitorAt = new ArrayList<>();
        List<String> itemAt = new ArrayList<>();

        List<VisitorBook.Entry> fresh = new ArrayList<>();
        List<VisitorBook.Entry> known = new ArrayList<>();
        for (VisitorBook.Entry e : book.entries()) {
            if (!e.active()) continue;
            (e.known() ? known : fresh).add(e);
        }

        Map<String, Integer> totals = book.shoppingTotals();
        if (!totals.isEmpty()) {
            double sum = 0.0;
            List<String> itemLines = new ArrayList<>();
            List<String> itemNames = new ArrayList<>();
            for (Map.Entry<String, Integer> item : totals.entrySet()) {
                StringBuilder line = new StringBuilder(" §7- §f").append(item.getKey()).append(" §ex").append(amountStyle.apply(amount(item.getValue())));
                if (showPrice) {
                    double unit = unitPrice == null ? -1.0 : unitPrice.applyAsDouble(item.getKey());
                    if (unit > 0) {
                        double price = unit * item.getValue();
                        sum += price;
                        line.append(" §7(§6").append(format.apply(price)).append("§7)");
                    }
                }
                itemLines.add(line.toString());
                itemNames.add(item.getKey());
            }
            lines.add(showPrice && sum > 0 ? TITLE + " §7(§6" + format.apply(sum) + "§7)" : TITLE);
            visitorAt.add(null);
            itemAt.add(null);
            for (int i = 0; i < itemLines.size(); i++) {
                lines.add(itemLines.get(i));
                visitorAt.add(null);
                itemAt.add(itemNames.get(i));
            }
        }

        section(lines, visitorAt, itemAt, fresh, "New Visitor");
        section(lines, visitorAt, itemAt, known, "Visitor");
        return new Built(List.copyOf(lines), java.util.Collections.unmodifiableList(visitorAt), java.util.Collections.unmodifiableList(itemAt));
    }

    private static void section(List<String> lines, List<String> visitorAt, List<String> itemAt, List<VisitorBook.Entry> visitors, String header) {
        if (visitors.isEmpty()) return;
        if (!lines.isEmpty()) {
            lines.add("");
            visitorAt.add(null);
            itemAt.add(null);
        }
        lines.add("§e" + visitors.size() + " §7" + header + (visitors.size() == 1 ? "" : "s") + ":");
        visitorAt.add(null);
        itemAt.add(null);
        for (VisitorBook.Entry e : visitors) {
            lines.add(e.ignored() ? " §7- §7§m" + e.name() : " §7- §f" + e.name());
            visitorAt.add(e.name());
            itemAt.add(null);
        }
    }

    /**
     * Steht die Liste an diesem Ort?
     *
     * @param map            das Gebiet aus der Tab-Liste ("Garden", "Hub", "The Farming Islands")
     * @param zone           die Zone aus der Seitenleiste ("Bazaar Alley", "Farm"), oder null
     * @param onBarnPlot     steht man auf dem Plot der Scheune (dem mittleren)?
     */
    public static boolean locationAllows(boolean inBazaarAlley, boolean inFarmingAreas, boolean onlyWhenClose, String map, String zone, boolean onBarnPlot) {
        if (MAP_HUB.equals(map)) {
            if (inBazaarAlley && ZONE_BAZAAR_ALLEY.equals(zone)) return true;
            if (inFarmingAreas && ZONE_FARM.equals(zone)) return true;
        }
        if (inFarmingAreas && MAP_FARMING_ISLANDS.equals(map)) return true;
        return isGarden(map) && (onBarnPlot || !onlyWhenClose);
    }

    /** Ist das Gebiet der Garden? ("Garden" oder "The Garden") */
    public static boolean isGarden(String map) {
        if (map == null) return false;
        String lower = map.toLowerCase(Locale.ROOT);
        return lower.equals("garden") || lower.equals("the garden");
    }

    /** Muss die Zone aus der Seitenleiste gelesen werden? Nur im Hub und nur, wenn eine der beiden Hub-Zonen gewaehlt ist */
    public static boolean needsZone(boolean inBazaarAlley, boolean inFarmingAreas, String map) {
        return MAP_HUB.equals(map) && (inBazaarAlley || inFarmingAreas);
    }
}
