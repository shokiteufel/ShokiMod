package com.shokiteufel.shokimod.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat > Show Actual Powder Gained (Crystal Hollows): In der Truhe steht "Gemstone Powder x1,944" - hier steht dahinter in Klammern, was wirklich gutgeschrieben
 * wird ("x3,888"), und beim Zeigen die Rechnung: welche Faktoren (Bohrer, Sky Mall, Scatha ...) sie vervielfachen.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code ActualGemstonePowderDisplay}). Von dort stammt die Rechnung: Jede Quelle ist ein Zuschlag in Prozent; die Zuschlaege
 * innerhalb einer Gruppe (Bohrer: Grundwert und Goblin-Egg-Aufwertung) werden addiert, die Gruppen miteinander mal genommen; steht am Ende mehr als 1, ersetzt "Gemstone
 * Powder x1,944 (x3,888)" die Zeile. Der Bohrer zaehlt nur, wenn er in der Hand liegt und Zeilen wie "Grants +50% Gemstone Powder, and" und "Earn +25% Powder from all
 * sources." tragen; Sky Mall mit 15 Prozent; das legendaere Scatha-Pet mit 0,2 Prozent je Stufe. Die Umsetzung ist eigene Arbeit: Java, die Faktoren kommen aus einem
 * reinen Wert ({@link ChatPlace.PowderContext}), die Zeile wird ueber den Klartext erkannt.
 *
 * <p>Abweichung: SkyHanni liest auch den HOTM-Zuschlag "Powder Buff", die Attribute (Atomized Crystals und seine Echos) und das Ereignis "2x Powder". ShokiMod liest
 * keines davon. Die Zahl ist deshalb zu klein, wenn man eines davon hat; der Text beim Zeigen sagt, was nicht mitgerechnet ist.
 */
final class ActualPowder {

    private static final Pattern REWARD = Pattern.compile(" {4}Gemstone Powder(?: x(?<amount>[\\d,]+))?");
    private static final Pattern DRILL_BASE = Pattern.compile("Grants \\+(\\d+)% Gemstone Powder, and");
    private static final Pattern DRILL_UPGRADE = Pattern.compile("Earn \\+(\\d+)% Powder from all sources\\.");

    /** Was nicht mitgerechnet wird; steht im Text beim Zeigen */
    static final String NOT_COUNTED = "§8Not counted (not read by ShokiMod): HOTM Powder Buff, attribute shards, 2x Powder event";

    private ActualPowder() {
    }

    /** Ein Zuschlag: Name, Anteil (0,15 fuer +15 Prozent) und - bei einer Gruppe - die Zuschlaege darin (der Anteil der Gruppe ist ihre Summe) */
    record Factor(String name, double bonus, List<Factor> children) {

        static Factor simple(String name, double bonus) {
            return new Factor(name, bonus, List.of());
        }

        static Factor group(String name, List<Factor> children) {
            double sum = 0.0;
            for (Factor f : children) sum += f.bonus();
            return new Factor(name, sum, children);
        }

        double multiplier() {
            return 1.0 + bonus;
        }

        /** Die Zeilen des Texts beim Zeigen; leer, wenn der Faktor nichts bringt. {@code level}: 1 fuer die Gruppen oben, darunter 2 und 3 */
        List<String> tooltip(int level) {
            List<String> out = new ArrayList<>();
            boolean group = !children.isEmpty();
            if (group ? multiplier() <= 1.0 : bonus <= 0) return out;
            out.add(line(level));
            for (Factor child : children) out.addAll(child.tooltip(level + 1));
            return out;
        }

        private String line(int level) {
            String indent;
            String label;
            String value;
            switch (level) {
                case 1:
                    indent = "§7└ ";
                    label = "§7";
                    value = "§a";
                    break;
                case 2:
                    indent = "§8  └ ";
                    label = "§8";
                    value = "§2";
                    break;
                default:
                    indent = "§5    └ ";
                    label = "§5";
                    value = "§b";
                    break;
            }
            String shown = level == 1 ? multiplierText(multiplier()) : percentText(bonus);
            return indent + label + name + ": " + value + shown;
        }
    }

    /** Die Faktoren, die die Mod kennt */
    static List<Factor> factors(ChatPlace.PowderContext c) {
        List<Factor> out = new ArrayList<>();
        List<Factor> hotm = new ArrayList<>();
        if (c.heldLore() != null && isDrill(c.heldLore())) {
            double base = 0.0;
            double upgrade = 0.0;
            for (String line : c.heldLore()) {
                Matcher b = DRILL_BASE.matcher(line);
                if (b.matches()) base += Integer.parseInt(b.group(1));
                Matcher u = DRILL_UPGRADE.matcher(line);
                if (u.matches()) upgrade += Integer.parseInt(u.group(1));
            }
            List<Factor> drill = new ArrayList<>();
            if (base > 0) drill.add(Factor.simple("Drill Base", base / 100.0));
            if (upgrade > 0) drill.add(Factor.simple("Goblin Egg", upgrade / 100.0));
            if (!drill.isEmpty()) hotm.add(Factor.group(c.heldNameLegacy() == null || c.heldNameLegacy().isEmpty() ? "Drill" : c.heldNameLegacy(), drill));
        }
        if (!hotm.isEmpty()) out.add(Factor.group("HOTM Powder Buffs", hotm));
        if (c.skyMallExtraPowder()) out.add(Factor.simple("Sky Mall", 0.15));
        if (c.scathaLevel() >= 0) out.add(Factor.simple("§7[Lvl " + c.scathaLevel() + "] §6Scatha", 0.2 * c.scathaLevel() / 100.0));
        return out;
    }

    /** Die Beschreibung eines Bohrers endet mit der Zeile "<SELTENHEIT> DRILL" */
    private static boolean isDrill(List<String> lore) {
        if (lore.isEmpty()) return false;
        String last = lore.get(lore.size() - 1);
        return last.endsWith("DRILL");
    }

    /** Die Zeile mit der wirklichen Menge, oder null (keine Powder-Zeile, keine Menge, oder nichts vervielfacht sie) */
    static StyledText rewrite(StyledText line, ChatPlace.PowderContext context) {
        String plain = line.plain();
        // Alle Belohnungen stehen mit vier Leerzeichen eingerueckt
        if (plain.length() < 20 || plain.indexOf("Gemstone Powder") < 0) return null;
        Matcher m = REWARD.matcher(plain);
        if (!m.matches() || m.group("amount") == null) return null;
        long original;
        try {
            original = Long.parseLong(m.group("amount").replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        List<Factor> factors = factors(context);
        double total = 1.0;
        for (Factor f : factors) total *= f.multiplier();
        if (total <= 1.0) return null;
        long actual = (long) (original * total);

        List<String> hover = new ArrayList<>();
        hover.add("§7Base Powder: §e" + separated(original));
        hover.add("");
        hover.add("§7Total Multiplier: §6" + multiplierText(total));
        for (Factor f : factors) hover.addAll(f.tooltip(1));
        hover.add("");
        hover.add("§7Actual Powder: §d" + separated(actual));
        hover.add(NOT_COUNTED);

        String text = "    §r§dGemstone Powder §r§8x" + separated(original) + " §7(x" + separated(actual) + ")";
        return StyledText.ofLegacy(text).withEvents(null, StyledText.hoverOf(String.join("\n", hover)));
    }

    private static String separated(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    /** 2.0 wird "x2", 1.15 wird "x1.15" */
    static String multiplierText(double value) {
        return "x" + decimals(value);
    }

    /** 0.2 wird "+20%", 0.155 wird "+15.5%" */
    static String percentText(double fraction) {
        return "+" + decimals(fraction * 100.0) + "%";
    }

    /** Zwei Nachkommastellen, ohne Nullen am Ende */
    private static String decimals(double value) {
        String s = String.format(Locale.US, "%.2f", value);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) s = s.substring(0, s.length() - 1);
        return s;
    }
}
