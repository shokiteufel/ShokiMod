package com.shokiteufel.shokimod.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NPC Shop Values: Was ist das, was ein Haendler anbietet, in Coins je ausgegebener Waehrung wert?
 *
 * Eigene Funktion (kein Teil der 26.3-Umstellung, der 26.3-Schalter beruehrt sie nicht). Die Idee, in SkyMart zu zeigen, wie viele Coins ein Copper bringt, kennt
 * SkyHanni ({@code SkyMartCopperPrice}, {@code PesthunterProfit}, LGPL-2.1); die Umsetzung ist eigene Arbeit: reines Java ohne Spielklassen, die Kosten werden aus der
 * Beschreibung der Stuecke gelesen (Zeile "Cost" und die Zeilen darunter), die Preise kommen von aussen ({@link Prices}).
 *
 * <p>Die Rechnung je Stueck: (Wert des Stuecks - Wert der uebrigen Kosten) / Menge der Waehrung. Wert = Basar oder niedrigste BIN, nie der NPC-Preis: Ein Stueck ohne Basar-
 * und Auktionspreis kommt nicht in die Liste. Gleiches gilt, wenn ein Rohstoff in den Kosten keinen Preis hat - dann liesse sich der Gewinn nicht rechnen.
 */
public final class ShopValue {

    /** Die Waehrungen der Haendler, fuer die es eine Liste gibt */
    public enum Currency {
        COPPER("Copper", "copper", "§c"),
        PESTS("Pest", "pest", "§2"),
        GREEN_CANDY("Green Candy", "green candy", "§a"),
        PURPLE_CANDY("Purple Candy", "purple candy", "§5"),
        AGATHA_COUPON("Agatha's Coupon", "agathas coupon", "§e"),
        MIRIA_COUPON("Miria's Coupon", "mirias coupon", "§e");

        private final String label;
        private final String key;
        private final String colour;

        Currency(String label, String key, String colour) {
            this.label = label;
            this.key = key;
            this.colour = colour;
        }

        /** Der Name in der Ueberschrift ("Coins per Copper") */
        public String label() {
            return label;
        }

        /** Der Farbcode, in dem die Menge dieser Waehrung steht */
        public String colour() {
            return colour;
        }

        /** Ist das der Name dieser Waehrung, wie er in einer Kostenzeile steht? Mit und ohne Mehrzahl-s, ohne Hochkomma, gleich wie gross geschrieben */
        public boolean matches(String costName) {
            return key.equals(normal(costName));
        }
    }

    /** Ein Haendler: wie sein Fenster heisst (ohne Seitenzahl) und welche Listen er bekommt */
    public record Shop(String name, List<Currency> currencies) {
    }

    /** Eine Zeile der Kosten: Name und Menge */
    public record Cost(String name, long amount) {
    }

    /** Ein Stueck im Fenster: Name, Menge, Kennungen zum Suchen des Preises und die Kosten aus der Beschreibung */
    public record Offer(String name, String nameColour, int amount, List<String> priceIds, List<Cost> costs) {
    }

    /** Eine Zeile der Liste */
    public record Row(String name, String nameColour, int amount, long cost, double ratio) {
    }

    /** Eine Liste je Waehrung */
    public record Section(Currency currency, List<Row> rows) {
    }

    /** Woher die Preise kommen. Eine Zahl bis null heisst: unbekannt */
    public interface Prices {

        /** Der Wert eines Stuecks, aus den Kennungen (Basar, sonst niedrigste BIN) */
        double valueOfItem(List<String> candidates);

        /** Der Wert eines Rohstoffs aus einer Kostenzeile, nach seinem Namen */
        double valueOfName(String name);
    }

    private static final Pattern PAGE = Pattern.compile("^\\(\\d+/\\d+\\)\\s*");
    private static final Pattern COST_HEADER = Pattern.compile("^Costs?(?: to unlock)?:?(?:\\s+(.+))?$");
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([\\d,]+)x?\\s+(.+)$");
    private static final Pattern AMOUNT_LAST = Pattern.compile("^(.+?)\\s+x([\\d,]+)$");
    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    private ShopValue() {
    }

    // ------------------------------------------------------------------ Fenster

    /** Der Titel ohne Farbcodes und ohne die Seitenzahl vorn: "(1/2) SkyMart Farming Essentials" wird "SkyMart Farming Essentials" */
    static String stripPage(String title) {
        if (title == null) return "";
        String clean = COLOUR_CODE.matcher(title).replaceAll("").trim();
        return PAGE.matcher(clean).replaceFirst("").trim();
    }

    /** Der Haendler zu einem Fenstertitel, oder null, wenn es keiner von denen mit einer Liste ist */
    public static Shop shopOf(String title) {
        String name = stripPage(title).replace('’', '\'');
        if (name.startsWith("SkyMart")) return new Shop("SkyMart", List.of(Currency.COPPER));
        return switch (name) {
            case "Pesthunter's Wares" -> new Shop(name, List.of(Currency.PESTS));
            case "Fear Mongerer" -> new Shop(name, List.of(Currency.GREEN_CANDY, Currency.PURPLE_CANDY));
            case "Agatha's Shop" -> new Shop(name, List.of(Currency.AGATHA_COUPON));
            case "Miria's Shop" -> new Shop(name, List.of(Currency.MIRIA_COUPON));
            default -> null;
        };
    }

    // ------------------------------------------------------------------ Kosten

    /** Klein, ohne Hochkomma, ohne Mehrzahl-s ("Agatha's Coupons" wird "agathas coupon") */
    static String normal(String name) {
        if (name == null) return "";
        String s = COLOUR_CODE.matcher(name).replaceAll("").replace("'", "").replace("’", "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return s.length() > 4 && s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
    }

    private static long number(String text) {
        try {
            return Long.parseLong(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Eine Kostenzeile: "250 Copper", "10x Green Candy", "Enchanted Fig Log x24" oder nur ein Name (Menge 1); null, wenn nichts zu lesen ist */
    static Cost readCostLine(String line) {
        if (line == null) return null;
        String text = COLOUR_CODE.matcher(line).replaceAll("").trim();
        if (text.isEmpty()) return null;
        Matcher first = AMOUNT_FIRST.matcher(text);
        if (first.matches()) {
            long amount = number(first.group(1));
            return amount > 0 ? new Cost(first.group(2).trim(), amount) : null;
        }
        Matcher last = AMOUNT_LAST.matcher(text);
        if (last.matches()) {
            long amount = number(last.group(2));
            return amount > 0 ? new Cost(last.group(1).trim(), amount) : null;
        }
        return new Cost(text, 1);
    }

    /**
     * Die Kosten aus der Beschreibung (Zeilen ohne Farbcodes): hinter der letzten Zeile "Cost" stehen sie, eine je Zeile, bis zur Leerzeile oder bis ein "Click ..." kommt;
     * auch "Cost: 250 Copper" in einer Zeile. Leer, wenn es keine Kostenzeile gibt.
     */
    public static List<Cost> readCosts(List<String> lore) {
        if (lore == null || lore.isEmpty()) return List.of();
        int header = -1;
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line = lore.get(i);
            if (line != null && COST_HEADER.matcher(line.trim()).matches()) {
                header = i;
                break;
            }
        }
        if (header < 0) return List.of();

        List<Cost> out = new ArrayList<>(3);
        Matcher inline = COST_HEADER.matcher(lore.get(header).trim());
        if (inline.matches() && inline.group(1) != null) {
            Cost cost = readCostLine(inline.group(1));
            return cost == null ? List.of() : List.of(cost);
        }
        for (int i = header + 1; i < lore.size(); i++) {
            String line = lore.get(i) == null ? "" : lore.get(i).trim();
            if (line.isEmpty() || line.startsWith("Click") || line.startsWith("Right Click") || line.startsWith("Left Click")) break;
            Cost cost = readCostLine(line);
            if (cost != null) out.add(cost);
        }
        return out;
    }

    // ------------------------------------------------------------------ Rechnung

    private static boolean isCoins(String name) {
        String n = normal(name);
        return n.equals("coin") || n.equals("skyblock coin");
    }

    private static boolean isAnyCurrency(String name) {
        for (Currency c : Currency.values()) {
            if (c.matches(name)) return true;
        }
        return false;
    }

    /**
     * Die Liste zu einer Waehrung: alle Stuecke, die diese Waehrung kosten und einen Preis haben, nach Coins je Waehrung, das beste zuerst, hoechstens {@code limit}.
     *
     * @param subtractMaterials ob die uebrigen Kosten (Rohstoffe, Coins) vom Wert abgehen; ein Rohstoff ohne Preis oder eine andere Waehrung lassen das Stueck dann weg
     */
    public static List<Row> rank(Currency currency, List<Offer> offers, Prices prices, boolean subtractMaterials, int limit) {
        List<Row> rows = new ArrayList<>();
        if (currency == null || offers == null || prices == null || limit <= 0) return rows;
        for (Offer offer : offers) {
            Row row = rowOf(currency, offer, prices, subtractMaterials);
            if (row != null) rows.add(row);
        }
        rows.sort(Comparator.comparingDouble(Row::ratio).reversed().thenComparing(Row::name));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    private static Row rowOf(Currency currency, Offer offer, Prices prices, boolean subtractMaterials) {
        long spent = 0;
        for (Cost cost : offer.costs()) {
            if (currency.matches(cost.name())) spent += cost.amount();
        }
        if (spent <= 0) return null;

        double unit = prices.valueOfItem(offer.priceIds());
        if (unit <= 0) return null;
        double value = unit * Math.max(1, offer.amount());

        if (subtractMaterials) {
            for (Cost cost : offer.costs()) {
                if (currency.matches(cost.name())) continue;
                if (isCoins(cost.name())) {
                    value -= cost.amount();
                    continue;
                }
                // Eine andere Waehrung hat keinen Preis in Coins: Das Stueck liesse sich nicht rechnen
                if (isAnyCurrency(cost.name())) return null;
                double material = prices.valueOfName(cost.name());
                if (material <= 0) return null;
                value -= material * cost.amount();
            }
        }
        double ratio = value / spent;
        if (ratio <= 0) return null;
        return new Row(offer.name(), offer.nameColour(), Math.max(1, offer.amount()), spent, ratio);
    }

    // ------------------------------------------------------------------ Anzeige

    /** Die Ueberschrift einer Liste: "Coins per Copper:" */
    public static String heading(Currency currency) {
        return "§eCoins per " + currency.label() + "§f:";
    }

    /** Eine Zeile der Liste: Rang, Name (mit Menge ab 2), die Kosten in Klammern, dann die Coins je Waehrung - mit {@code bold} die Zahl fett */
    public static String line(Currency currency, Row row, int rank, boolean bold) {
        String amount = row.amount() > 1 ? " §8x" + row.amount() : "";
        String number = ItemValue.format(row.ratio());
        return "§7" + rank + ". " + row.nameColour() + row.name() + amount
                + " §7(" + currency.colour() + String.format(Locale.US, "%,d", row.cost()) + "§7)§f: §6" + (bold ? "§l" : "") + number;
    }

    /** Der Hinweis, wenn zu einer Waehrung nichts zu rechnen war */
    public static final String NOTHING = "§7Nothing with a price on this page";
}
