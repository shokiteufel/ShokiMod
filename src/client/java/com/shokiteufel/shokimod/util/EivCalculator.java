package com.shokiteufel.shokimod.util;

import com.shokiteufel.shokimod.data.ModConfig.EivPriceSource;
import com.shokiteufel.shokimod.util.EivData.Endcap;
import com.shokiteufel.shokimod.util.EivData.Reforge;
import com.shokiteufel.shokimod.util.EivData.Upgrade;
import com.shokiteufel.shokimod.util.EivData.ValueData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Rechner hinter Estimated Item Value: was ein Gegenstand wert ist - sein Grundpreis und alles, was daran verbaut ist.
 *
 * Herkunft der Idee und der Regeln: SkyHanni (LGPL-2.1, {@code EstimatedItemValueCalculator}). Von dort stammt, was zusammengerechnet wird, und in
 * welcher Reihenfolge: der Umschmiedestein samt Kosten des Anbringens, Recombobulator, Art of War und Art of Peace, Etherwarp, Kraftrolle, Holz-Singularitaet,
 * Jalapeno-Buch, Statistik-Buch, Anreicherung, Divan-Puderbeschichtung, Mithril-Infusion, Free Will, das Crimson-Prestige, die Sterne (Essenz, Coins und
 * Gegenstaende) und Meistersterne, die Hot-Potato-Buecher und Fuming-Buecher, Wet Book, Farming for Dummies, Overclocker, Silex, Transmission Tuner, Mana
 * Disintegrator, Polarvoid, Bookworm, Pocket Sack-in-a-Sack, Helmskin, Ruestungsfarbe, Rune, Faehigkeits-Schriftrollen, Booster, Bohrerteile, Angelteile,
 * Edelstein-Plaetze und eingesetzte Edelsteine und zuletzt die Verzauberungen (mit den Sonderregeln: von sich aus aktive, Abschluss-Gegenstaende bei
 * den hoechsten Stufen, nur Stufe 1 oder 5 zaehlt, Buchpakete, stapelnde Verzauberungen).
 *
 * <p>Die Umsetzung ist eigene Arbeit: Java, alles ueber eine Schnittstelle {@link Env} (Preise, Namen, Tabellen), damit sich der Rechner ohne Spiel pruefen
 * laesst. Nicht uebernommen: der Herstellungspreis (Zutaten eines Rezepts) als Rueckfall, wenn ein Stueck weder im Basar noch im Auktionshaus noch beim NPC
 * zu haben ist - dort steht der Grundpreis dann bei null.
 */
public final class EivCalculator {

    /** Woher der Rechner Preise, Namen und Tabellen nimmt */
    public interface Env {
        /** Der Preis einer Kennung nach der gewaehlten Quelle, oder null, wenn es keinen gibt */
        Double price(String id, EivPriceSource source);

        /** Der Name einer Kennung mit Farbcodes ("§9Sharpness VI") */
        String name(String id);

        Reforge reforge(String modifier);

        Map<Integer, Upgrade> essence(String id);

        Set<String> essenceIds();

        boolean hasGemSlotData();

        Map<String, List<String>> gemSlots(String id);

        /** Die Regeln fuer Verzauberungen und andere Zahlen, oder null, solange sie nicht geholt sind */
        ValueData values();

        boolean stacking(String enchant);

        /** Fuehrt der Basar diese Kennung? */
        boolean bazaar(String id);
    }

    /** Der Gegenstand, der gerechnet wird. {@code id} ist die Kennung wie SkyHanni sie nennt (Pets "ENDERMITE;5", Buecher mit Verzauberung), {@code rawId} die im Gegenstand */
    public record Subject(String id, String rawId, String priceId, CompoundTag tag, List<String> lore, String hoverName) {
    }

    /** Was einstellbar ist */
    public record Options(EivPriceSource source, int enchantmentsCap, int starMaterialCap, boolean ignoreHelmetSkins, boolean ignoreArmorDyes,
                          boolean ignoreRunes) {
    }

    /** Die Summe, der Grundpreis allein und die Zeilen dazu (mit Farbcodes) */
    public record Result(double total, double base, List<String> lines) {
    }

    private static final String COIN = "SKYBLOCK_COIN";
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    private static final Pattern RARITY_WORD = Pattern.compile("(VERY SPECIAL|SPECIAL|UNCOMMON|COMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|ULTIMATE|SUPREME)");
    private static final Pattern KUUDRA = Pattern.compile(
            "^(?<tier>HOT|BURNING|FIERY|INFERNAL|)_?(?<type>AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)_(?:HELMET|CHESTPLATE|LEGGINGS|BOOTS)$");
    static final List<String> KUUDRA_TIERS = List.of("", "HOT", "BURNING", "FIERY", "INFERNAL");
    private static final List<String> GEM_TYPES = List.of("JADE", "AMBER", "TOPAZ", "SAPPHIRE", "AMETHYST", "JASPER", "RUBY", "OPAL", "ONYX",
            "AQUAMARINE", "CITRINE", "PERIDOT");
    private static final List<String> GEM_QUALITIES = List.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");
    /** Die Farbe, in der der Platz eines Edelsteins steht; der erste Treffer im Namen gilt */
    private static final String[][] SLOT_COLOURS = {
            {"JADE", "a"}, {"AMBER", "6"}, {"TOPAZ", "e"}, {"SAPPHIRE", "b"}, {"AMETHYST", "5"}, {"JASPER", "d"}, {"RUBY", "c"}, {"OPAL", "f"},
            {"ONYX", "8"}, {"AQUAMARINE", "3"}, {"CITRINE", "4"}, {"PERIDOT", "2"}, {"COMBAT", "4"}, {"DEFENSIVE", "a"}, {"MINING", "5"}, {"UNIVERSAL", "f"}};
    private static final String[] DRILL_PARTS = {"drill_part_upgrade_module", "drill_part_engine", "drill_part_fuel_tank"};
    private static final String[] ROD_PARTS = {"hook", "line", "sinker"};

    private final Env env;
    private final Options options;
    private final List<BiFunction<Subject, List<String>, Double>> additions;

    public EivCalculator(Env env, Options options) {
        this.env = env;
        this.options = options;
        this.additions = List.of(
                this::reforgeStone,
                // einmalig
                this::recombobulator, this::artOfWar, this::artOfPeace, this::etherwarp, this::powerScroll, this::woodSingularity, this::jalapenoBook,
                this::statsBook, this::enrichment, this::divanPowderCoating, this::mithrilInfusion, this::freeWill,
                // gezaehlt
                this::crimsonPrestige, this::stars, this::masterStars, this::hotPotatoBooks,
                (s, l) -> counted(s, l, "wet_book_count", "Wet Book", 5, "WET_BOOK"),
                (s, l) -> counted(s, l, "farming_for_dummies_count", "Farming for Dummies", 5, "FARMING_FOR_DUMMIES"),
                (s, l) -> counted(s, l, "levelable_overclocks", "Overclocker 3000", 10, "OVERCLOCKER_3000"),
                this::silex,
                (s, l) -> counted(s, l, "tuned_transmission", "Transmission Tuners", 4, "TRANSMISSION_TUNER"),
                (s, l) -> counted(s, l, "mana_disintegrator_count", "Mana Disintegrators", 10, "MANA_DISINTEGRATOR"),
                (s, l) -> counted(s, l, "polarvoid", "Polarvoid", 5, "POLARVOID_BOOK"),
                (s, l) -> counted(s, l, "bookworm_books", "Bookworm's Favorite Book", 5, "BOOKWORM_BOOK"),
                (s, l) -> counted(s, l, "sack_pss", "Pocket Sack-in-a-Sack", 3, "POCKET_SACK_IN_A_SACK"),
                // Schmuck
                (s, l) -> cosmetic(s.tag().getStringOr("skin", ""), l, "Skin", options.ignoreHelmetSkins()),
                (s, l) -> cosmetic(s.tag().getStringOr("dye_item", ""), l, "Dye", options.ignoreArmorDyes()),
                this::rune,
                // Veraenderliches
                this::abilityScrolls, this::boosters, this::drillUpgrades, this::rodUpgrades, this::gemSlotUnlock, this::gemstones, this::enchantments);
    }

    // ------------------------------------------------------------------ Einstieg

    public Result calculate(Subject s) {
        List<String> list = new ArrayList<>();
        double base = baseItem(s, list);
        // Der Wert der Verzauberungen kommt in enchantments() dazu; ein Buch wuerde sonst doppelt zaehlen
        double total = "ENCHANTED_BOOK".equals(s.rawId()) ? 0.0 : base;
        for (BiFunction<Subject, List<String>, Double> addition : additions) total += addition.apply(s, list);
        return new Result(total, base, list);
    }

    // ------------------------------------------------------------------ Hilfen

    private double price(String id) {
        Double p = env.price(id, options.source());
        return p == null ? 0.0 : p;
    }

    private static int count(CompoundTag tag, String key) {
        return tag.getInt(key).orElse(0);
    }

    private static boolean flag(CompoundTag tag, String key) {
        return tag.getByte(key).orElse((byte) 0) != 0;
    }

    private static String firstUpper(String text) {
        StringBuilder out = new StringBuilder();
        for (String part : text.toLowerCase(Locale.ROOT).split(" ")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return out.toString();
    }

    static String stripColours(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    private double having(List<String> list, String label, String id) {
        double p = price(id);
        list.add("§7" + label + ": §a§l✔ " + EivFormat.coinsInBrackets(p, false));
        return p;
    }

    private static String progress(String label, int have, int max, double price) {
        return "§7" + label + ": §e" + have + "§7/§e" + max + " " + EivFormat.coinsInBrackets(price, false);
    }

    /** "§83x §rName" - oder nur "§rName" bei einem Stueck */
    private String numbered(String id, double amount) {
        String prefix = amount == 1.0 ? "" : "§8" + EivFormat.withSeparators(amount) + "x ";
        return prefix + "§r" + env.name(id);
    }

    /** Eine Zeile mit Menge und Preis: "§83x §rName §7(§6150k§7)", bei Coins nur "§6150k coins" */
    private String priceName(String id, double amount, double pricePer) {
        double total = pricePer * amount;
        if (id.equals(COIN)) return EivFormat.coin(total, false) + " coins";
        return " " + numbered(id, amount) + " " + EivFormat.coinsInBrackets(total, false);
    }

    private record Named(double total, List<String> names) {
    }

    /**
     * Der Preis aller Posten und je Posten eine Zeile, die teuersten zuerst. Ein Posten ohne Preis (der Basar hat auf der gewaehlten Seite keinen Auftrag, das
     * Auktionshaus und der NPC kennen ihn nicht) steht mit 0 da und damit ganz unten; bis 2.5.19 stand dort "Unknown price!" mit dem Sortierwert
     * {@link Double#MAX_VALUE}, was die Zeile an die Spitze setzte. Die Reihenfolge unter mehreren Posten ohne Preis bleibt die der Eingabe (die Sortierung ist stabil).
     */
    private Named totalAndNames(Map<String, ? extends Number> items) {
        double total = 0.0;
        List<Map.Entry<String, Double>> rows = new ArrayList<>();
        for (Map.Entry<String, ? extends Number> item : items.entrySet()) {
            Double p = env.price(item.getKey(), options.source());
            double amount = item.getValue().doubleValue();
            double each = p == null ? 0.0 : p;
            total += each * amount;
            rows.add(Map.entry(priceName(item.getKey(), amount, each), each));
        }
        rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> names = new ArrayList<>(rows.size());
        for (Map.Entry<String, Double> row : rows) names.add(row.getKey());
        return new Named(total, names);
    }

    private Named totalAndNames(List<String> ids) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (String id : ids) items.merge(id, 1, Integer::sum);
        return totalAndNames(items);
    }

    // ------------------------------------------------------------------ Kuudra

    static boolean isKuudraArmor(String id) {
        return id != null && KUUDRA.matcher(id).matches();
    }

    /** 1 ohne Vorsatz, 2 fuer HOT, 3 BURNING, 4 FIERY, 5 INFERNAL - oder 0, wenn es keine Kuudra-Ruestung ist */
    static int kuudraTier(String id) {
        if (id == null) return 0;
        Matcher m = KUUDRA.matcher(id);
        if (!m.matches()) return 0;
        int at = KUUDRA_TIERS.indexOf(m.group("tier"));
        return at < 0 ? 0 : at + 1;
    }

    static String removeKuudraTier(String id) {
        if (id == null) return "";
        Matcher m = KUUDRA.matcher(id);
        if (!m.matches() || m.group("tier").isEmpty()) return id;
        return id.substring(m.group("tier").length() + 1);
    }

    // ------------------------------------------------------------------ Grundpreis

    private double baseItem(Subject s, List<String> list) {
        String id = removeKuudraTier(s.id());
        String priceId = removeKuudraTier(s.priceId());
        if (id.isEmpty()) return 0.0;

        double price = price(priceId);
        if (price < 0) price = 0.0;
        String level = !s.priceId().equals(s.id()) ? levelOf(s.priceId()) : "";
        String name = env.name(id) + level;
        if (id.startsWith("ENCHANTED_BOOK_BUNDLE_")) {
            list.add("§7Base item: " + name);
            return 0.0;
        }
        list.add("§7Base item: " + name + " " + EivFormat.coinsInBrackets(price, false));
        return price;
    }

    /** " LVL 100" aus "ENDERMITE;5+100" (ohne Stufe: leer) */
    private static String levelOf(String priceId) {
        int plus = priceId.indexOf('+');
        return plus < 0 ? "" : " LVL " + priceId.substring(plus + 1);
    }

    // ------------------------------------------------------------------ Umschmiedung

    /** Die Seltenheit aus der letzten Zeile der Beschreibung ("LEGENDARY BOW", auch mit dem Zeichen eines Recombobulators davor), sonst null */
    static String rarityOf(List<String> lore) {
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line = lore.get(i);
            if (line == null || line.isBlank()) continue;
            Matcher m = RARITY_WORD.matcher(line);
            return m.find() ? m.group(1).replace(' ', '_') : null;
        }
        return null;
    }

    /** Was das Anbringen kostet: Wer ueber Mythic steht, zaehlt als Legendary, und ein Recombobulator zaehlt eine Stufe ab; null, wenn die Tabelle die Stufe nicht kennt */
    private Long applyCost(Subject s, Map<String, Long> costs) {
        String rarity = rarityOf(s.lore());
        if (rarity == null) return null;
        int at = RARITIES.indexOf(rarity);
        if (at < 0) {
            rarity = "LEGENDARY";
        } else if (count(s.tag(), "rarity_upgrades") > 0) {
            if (at == 0) return null;
            rarity = RARITIES.get(at - 1);
        }
        return costs.get(rarity);
    }

    private double reforgeStone(Subject s, List<String> list) {
        String modifier = s.tag().getStringOr("modifier", "");
        if (modifier.isEmpty()) return 0.0;
        Reforge reforge = env.reforge(modifier);
        if (reforge == null || reforge.stone() == null || reforge.costs().isEmpty()) return 0.0;
        double stonePrice = price(reforge.stone());
        Long apply = applyCost(s, reforge.costs());
        if (apply == null) return 0.0;
        list.add("§7Reforge: §9" + reforge.name());
        list.add(" §7Stone: " + env.name(reforge.stone()) + " " + EivFormat.coinsInBrackets(stonePrice, false));
        list.add(" §7Apply cost: " + EivFormat.coinsInBrackets(apply, false));
        return stonePrice + apply;
    }

    // ------------------------------------------------------------------ einmalige Zutaten

    private double recombobulator(Subject s, List<String> list) {
        return count(s.tag(), "rarity_upgrades") > 0 ? having(list, "Recombobulated", "RECOMBOBULATOR_3000") : 0.0;
    }

    private double artOfWar(Subject s, List<String> list) {
        return count(s.tag(), "art_of_war_count") > 0 ? having(list, "The Art of War", "THE_ART_OF_WAR") : 0.0;
    }

    private double artOfPeace(Subject s, List<String> list) {
        return flag(s.tag(), "artOfPeaceApplied") ? having(list, "The Art Of Peace", "THE_ART_OF_PEACE") : 0.0;
    }

    private double etherwarp(Subject s, List<String> list) {
        if (!flag(s.tag(), "ethermerge")) return 0.0;
        double p = price("ETHERWARP_CONDUIT") + price("ETHERWARP_MERGER");
        list.add("§7Etherwarp: §a§l✔ " + EivFormat.coinsInBrackets(p, false));
        return p;
    }

    private double powerScroll(Subject s, List<String> list) {
        String id = s.tag().getStringOr("power_ability_scroll", "");
        if (id.isBlank()) return 0.0;
        return having(list, stripColours(env.name(id)), id);
    }

    private double woodSingularity(Subject s, List<String> list) {
        return count(s.tag(), "wood_singularity_count") > 0 ? having(list, "Wood Singularity", "WOOD_SINGULARITY") : 0.0;
    }

    private double jalapenoBook(Subject s, List<String> list) {
        return count(s.tag(), "jalapeno_count") > 0 ? having(list, "Jalapeno Book", "JALAPENO_BOOK") : 0.0;
    }

    private double statsBook(Subject s, List<String> list) {
        return count(s.tag(), "stats_book") != 0 ? having(list, "Book of Stats", "BOOK_OF_STATS") : 0.0;
    }

    private double enrichment(Subject s, List<String> list) {
        String type = s.tag().getStringOr("talisman_enrichment", "");
        if (type.isBlank()) return 0.0;
        String id = "TALISMAN_ENRICHMENT_" + type;
        double p = price(id);
        list.add("§7Enrichment: " + env.name(id) + " " + EivFormat.coinsInBrackets(p, false));
        return p;
    }

    private double divanPowderCoating(Subject s, List<String> list) {
        return flag(s.tag(), "divan_powder_coating") ? having(list, "Divan Powder Coating", "DIVAN_POWDER_COATING") : 0.0;
    }

    private double mithrilInfusion(Subject s, List<String> list) {
        return s.tag().getByte("mithril_infusion").orElse((byte) 0) == 1 ? having(list, "Mithril Infusion", "MITHRIL_INFUSION") : 0.0;
    }

    private double freeWill(Subject s, List<String> list) {
        return s.tag().getByte("free_will").orElse((byte) 0) == 1 ? having(list, "Free Will", "FREE_WILL") : 0.0;
    }

    // ------------------------------------------------------------------ gezaehlte Zutaten

    private double counted(Subject s, List<String> list, String key, String label, int max, String id) {
        int n = count(s.tag(), key);
        if (n == 0) return 0.0;
        double p = price(id) * n;
        list.add(progress(label, n, max, p));
        return p;
    }

    private double hotPotatoBooks(Subject s, List<String> list) {
        int n = count(s.tag(), "hot_potato_count");
        if (n == 0) return 0.0;
        int hpb = Math.min(n, 10);
        int fuming = Math.max(0, n - 10);
        double total = price("HOT_POTATO_BOOK") * hpb;
        list.add(progress("HPB's", hpb, 10, total));
        if (fuming > 0) {
            double f = price("FUMING_POTATO_BOOK") * fuming;
            list.add(progress("Fuming", fuming, 5, f));
            total += f;
        }
        return total;
    }

    private double silex(Subject s, List<String> list) {
        int efficiency = s.tag().getCompoundOrEmpty("enchantments").getInt("efficiency").orElse(0);
        if (efficiency == 0) return 0.0;
        int base = switch (s.id()) {
            case "STONK_PICKAXE" -> 1;
            case "PROMISING_SPADE" -> 5;
            default -> 0;
        };
        int tier = efficiency - 5 - base;
        if (tier <= 0) return 0.0;
        int max = s.id().equals("STONK_PICKAXE") ? 4 : 5;
        double p = price("SIL_EX") * tier;
        list.add(progress("Silex", tier, max, p));
        return p;
    }

    private double masterStars(Subject s, List<String> list) {
        Integer total = dungeonStars(s);
        if (total == null) return 0.0;
        int master = Math.min(total - 5, 5);
        if (master < 1) return 0.0;
        String[] names = {"FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH"};
        double p = 0.0;
        for (int i = 0; i < names.length; i++) {
            if (master >= i + 1) p += price(names[i] + "_MASTER_STAR");
        }
        list.add(progress("Master Stars", master, 5, p));
        return p;
    }

    // ------------------------------------------------------------------ Sterne

    private static boolean isDungeonItem(Subject s) {
        for (String line : s.lore()) {
            if (line != null && line.contains("DUNGEON ")) return true;
        }
        return false;
    }

    private static int starCount(CompoundTag tag) {
        return count(tag, "upgrade_level");
    }

    /** Die Sterne eines Dungeon-Gegenstands, sonst null */
    private static Integer dungeonStars(Subject s) {
        if (!isDungeonItem(s)) return null;
        int stars = starCount(s.tag());
        if (stars == 0) stars = count(s.tag(), "dungeon_item_level");
        return stars == 0 ? null : stars;
    }

    /** Alles, was die Sterne bis {@code totalStars} zusammen kosten; null, wenn die Tabelle keinen einzigen kennt */
    static Upgrade priceFor(Map<Integer, Upgrade> prices, int totalStars) {
        Upgrade sum = null;
        for (Map.Entry<Integer, Upgrade> tier : new TreeMap<>(prices).entrySet()) {
            if (tier.getKey() > totalStars) break;
            sum = sum == null ? tier.getValue() : sum.plus(tier.getValue());
        }
        return sum;
    }

    private record StarPrice(Upgrade price, int have, int max) {
    }

    private StarPrice starPrice(String id, int inputStars) {
        int totalStars = inputStars;
        Upgrade price;
        int maxStars;
        if (isKuudraArmor(id)) {
            totalStars += (kuudraTier(id) - 1) * 10;
            int remaining = totalStars;
            String removed = removeKuudraTier(id);
            maxStars = 0;
            Upgrade finalPrice = null;
            Map<String, Integer> tiers = new LinkedHashMap<>();
            for (String other : env.essenceIds()) {
                if (other.contains(removed)) tiers.put(other, kuudraTier(other) - 1);
            }
            List<Map.Entry<String, Integer>> ordered = new ArrayList<>(tiers.entrySet());
            ordered.sort(Map.Entry.comparingByValue());
            for (Map.Entry<String, Integer> tier : ordered) {
                Map<Integer, Upgrade> prices = env.essence(tier.getKey());
                if (prices == null) prices = Map.of();
                maxStars += prices.size();
                if (remaining <= 0) continue;
                Upgrade part = priceFor(prices, remaining);
                if (part == null) return null;
                finalPrice = finalPrice == null ? part : finalPrice.plus(part);
                remaining -= prices.size();
            }
            if (finalPrice == null) return null;
            price = finalPrice;
        } else {
            Map<Integer, Upgrade> prices = env.essence(id);
            if (totalStars == 0 || prices == null) return null;
            price = priceFor(prices, totalStars);
            if (price == null) return null;
            maxStars = prices.size();
        }
        return new StarPrice(price, Math.min(totalStars, maxStars), maxStars);
    }

    private double stars(Subject s, List<String> list) {
        Integer dungeon = dungeonStars(s);
        int total = dungeon != null ? dungeon : starCount(s.tag());
        StarPrice star = starPrice(s.id(), total);
        if (star == null) return 0.0;

        Map<String, Number> items = new LinkedHashMap<>();
        items.put("ESSENCE_" + star.price().essenceType().toUpperCase(Locale.ROOT), star.price().essence());
        if (star.price().coins() != 0L) items.put(COIN, star.price().coins());
        items.putAll(star.price().items());
        Named named = totalAndNames(items);

        list.add(progress("Stars", star.have(), star.max(), named.total()));
        list.addAll(named.names().subList(0, Math.min(options.starMaterialCap(), named.names().size())));
        return named.total();
    }

    private double crimsonPrestige(Subject s, List<String> list) {
        String id = s.id();
        if (!isKuudraArmor(id)) return 0.0;
        int tierIndex = kuudraTier(id);
        if (tierIndex <= 1) return 0.0;
        ValueData data = env.values();
        if (data == null) return 0.0;
        String armorTier = KUUDRA_TIERS.get(tierIndex - 1);
        Map<String, Integer> all = new LinkedHashMap<>();
        for (int index = 1; index < tierIndex; index++) {
            Map<String, Integer> costs = data.crimsonPrestige().get(KUUDRA_TIERS.get(index));
            if (costs == null) return 0.0;
            costs.forEach((item, amount) -> all.merge(item, amount, Integer::sum));
        }
        Named named = totalAndNames(all);
        list.add("§7Prestige Tier: " + firstUpper(armorTier) + " " + EivFormat.coinsInBrackets(named.total(), false));
        list.addAll(named.names());
        return named.total();
    }

    // ------------------------------------------------------------------ Schmuck

    private double cosmetic(String id, List<String> list, String label, boolean ignore) {
        if (id.isBlank()) return 0.0;
        double p = price(id);
        String name = env.name(id);
        list.add("§7" + label + ": " + name + " " + EivFormat.coinsInBrackets(p, ignore));
        return ignore ? 0.0 : p;
    }

    private double rune(Subject s, List<String> list) {
        String own = s.id();
        if (own != null && (own.endsWith("_RUNE") || own.contains("_RUNE;") || own.equals("RUNE"))) return 0.0;
        CompoundTag runes = s.tag().getCompoundOrEmpty("runes");
        if (runes.isEmpty()) return 0.0;
        String name = runes.keySet().iterator().next();
        int tier = runes.getInt(name).orElse(0);
        return cosmetic(name.toUpperCase(Locale.ROOT) + "_RUNE;" + tier, list, "Rune", options.ignoreRunes());
    }

    // ------------------------------------------------------------------ Veraenderliches

    private double abilityScrolls(Subject s, List<String> list) {
        ListTag scrolls = s.tag().getListOrEmpty("ability_scroll");
        if (scrolls.isEmpty()) return 0.0;
        Set<String> ids = new LinkedHashSet<>();
        for (Tag tag : scrolls) {
            String id = tag.asString().orElse("");
            if (id.isEmpty()) continue;
            if (id.equals("ULTIMATE_WITHER_SCROLL")) {
                ids.add("IMPLOSION_SCROLL");
                ids.add("WITHER_SHIELD_SCROLL");
                ids.add("SHADOW_WARP_SCROLL");
                continue;
            }
            ids.add(id);
        }
        return listed(list, "Ability Scrolls", new ArrayList<>(ids));
    }

    private double boosters(Subject s, List<String> list) {
        ListTag boosters = s.tag().getListOrEmpty("boosters");
        if (boosters.isEmpty()) return 0.0;
        List<String> ids = new ArrayList<>();
        for (Tag tag : boosters) {
            String id = tag.asString().orElse("");
            if (!id.isBlank()) ids.add(id + "_BOOSTER");
        }
        return listed(list, "Boosters", ids);
    }

    private double drillUpgrades(Subject s, List<String> list) {
        List<String> ids = new ArrayList<>();
        for (String part : DRILL_PARTS) {
            String id = s.tag().getStringOr(part, "");
            if (!id.isBlank()) ids.add(id.toUpperCase(Locale.ROOT));
        }
        return listed(list, "Drill upgrades", ids);
    }

    private double rodUpgrades(Subject s, List<String> list) {
        List<String> ids = new ArrayList<>();
        for (String part : ROD_PARTS) {
            String id = s.tag().getCompoundOrEmpty(part).getStringOr("part", "");
            if (!id.isBlank()) ids.add(id.toUpperCase(Locale.ROOT));
        }
        return listed(list, "Rod upgrades", ids);
    }

    /** Eine Gruppe mit Summe in der Ueberschrift und je Posten einer Zeile; ohne Posten keine Zeile */
    private double listed(List<String> list, String label, List<String> ids) {
        if (ids.isEmpty()) return 0.0;
        Named named = totalAndNames(ids);
        if (named.names().isEmpty()) return 0.0;
        list.add("§7" + label + ": " + EivFormat.coin(named.total(), false));
        list.addAll(named.names());
        return named.total();
    }

    private static String slotColour(String slotName) {
        String upper = slotName.toUpperCase(Locale.ROOT);
        for (String[] entry : SLOT_COLOURS) {
            if (upper.contains(entry[0])) return entry[1];
        }
        return "f";
    }

    private double gemSlotUnlock(Subject s, List<String> list) {
        Tag unlockedTag = s.tag().getCompoundOrEmpty("gems").get("unlocked_slots");
        if (unlockedTag == null || !env.hasGemSlotData()) return 0.0;
        Map<String, List<String>> slots = env.gemSlots(s.id());
        if (slots == null) return 0.0;
        String unlocked = unlockedTag.toString();

        Map<String, Integer> items = new LinkedHashMap<>();
        List<String> slotNames = new ArrayList<>();
        for (Map.Entry<String, List<String>> slot : slots.entrySet()) {
            if (!unlocked.contains(slot.getKey())) continue;
            for (String ingredient : slot.getValue()) {
                String[] parts = ingredient.split(":");
                if (parts.length != 2) continue;
                try {
                    items.merge(parts[0], Integer.parseInt(parts[1]), Integer::sum);
                } catch (NumberFormatException e) {
                    // eine kaputte Zutat zaehlt nicht
                }
            }
            String[] split = slot.getKey().split("_");
            String display = firstUpper(split[0]) + " Slot";
            if (split.length > 1 && !split[1].equals("0")) {
                try {
                    display += " " + (Integer.parseInt(split[1]) + 1);
                } catch (NumberFormatException e) {
                    display += " " + split[1];
                }
            }
            slotNames.add("§" + slotColour(split[0]) + display);
        }
        if (slotNames.isEmpty()) return 0.0;

        Named named = totalAndNames(items);
        list.add("§7Gemstone Slot Unlock Cost: " + EivFormat.coin(named.total(), false));
        list.addAll(named.names());
        list.add(" §7Unlocked slots: " + String.join("§7, ", slotNames));
        return named.total();
    }

    /** Die eingesetzten Edelsteine als Kennungen ("FINE_JASPER_GEM"), eine je Platz */
    static List<String> gemstoneIds(CompoundTag gems) {
        List<String> out = new ArrayList<>();
        for (String key : gems.keySet()) {
            if (key.endsWith("_gem") || key.equals("unlocked_slots")) continue;
            String value = gems.getStringOr(key, "");
            if (value.isEmpty()) value = gems.getCompoundOrEmpty(key).getStringOr("quality", "");
            if (value.isEmpty()) continue;
            String quality = value.toUpperCase(Locale.ROOT);
            if (!GEM_QUALITIES.contains(quality)) continue;
            String type = key.split("_")[0].toUpperCase(Locale.ROOT);
            if (!GEM_TYPES.contains(type)) {
                type = gems.getStringOr(key + "_gem", "").toUpperCase(Locale.ROOT);
                if (!GEM_TYPES.contains(type)) continue;
            }
            out.add(quality + "_" + type + "_GEM");
        }
        return out;
    }

    private double gemstones(Subject s, List<String> list) {
        CompoundTag gems = s.tag().getCompoundOrEmpty("gems");
        if (gems.isEmpty()) return 0.0;
        return listed(list, "Gemstones Applied", gemstoneIds(gems));
    }

    // ------------------------------------------------------------------ Verzauberungen

    /** Welche Gegenstaende (Buecher und Abschluss-Stuecke) in den Verzauberungen eines Gegenstands stecken, mit Anzahl */
    Map<String, Integer> enchantmentItems(Map<String, Integer> enchantments, String internalName, ValueData data) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> enchantment : enchantments.entrySet()) {
            String rawName = enchantment.getKey();
            int rawLevel = enchantment.getValue();
            // Wirksamkeit 1 bis 5 ist billig, 6 bis 10 sind Silex
            if (rawName.equals("efficiency")) continue;

            EivData.AlwaysActive always = data.alwaysActive().get(rawName);
            if (always != null && always.level() == rawLevel && always.items().contains(internalName)) continue;

            List<Endcap> endcaps = data.endcaps().get(rawName);
            int level = rawLevel;
            if (endcaps != null && !endcaps.isEmpty()) {
                int minRequired = Integer.MAX_VALUE;
                for (Endcap e : endcaps) minRequired = Math.min(minRequired, e.requiredLevel());
                if (rawLevel >= minRequired) level = minRequired;
            }

            int multiplier = 1;
            if (data.onlyTierOne().contains(rawName) && level >= 2 && level <= 5) {
                multiplier = 1 << (level - 1);
                level = 1;
            } else if (data.onlyTierFive().contains(rawName) && level >= 6 && level <= 10) {
                multiplier = 1 << (level - 5);
                if (multiplier > 1) level = 5;
            }
            if (internalName.startsWith("ENCHANTED_BOOK_BUNDLE_")) {
                multiplier = data.bookBundleAmount().getOrDefault(rawName, 5);
            }
            if (env.stacking(rawName)) level = 1;

            if (endcaps != null) {
                for (Endcap e : endcaps) {
                    if (rawLevel >= e.requiredLevel() + 1) items.put(e.item(), 1);
                }
            }
            String enchantId = "ENCHANTMENT_" + rawName.toUpperCase(Locale.ROOT) + "_" + level;
            if (env.bazaar(enchantId)) items.put(enchantId, multiplier);
        }
        return items;
    }

    private double enchantments(Subject s, List<String> list) {
        CompoundTag tag = s.tag().getCompoundOrEmpty("enchantments");
        if (tag.isEmpty()) return 0.0;
        ValueData data = env.values();
        if (data == null) return 0.0;
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (String name : tag.keySet()) enchantments.put(name, tag.getInt(name).orElse(0));

        Named named = totalAndNames(enchantmentItems(enchantments, s.id(), data));
        if (named.names().isEmpty()) return 0.0;
        list.add("§7Enchantments: " + EivFormat.coin(named.total(), false));
        int cap = options.enchantmentsCap();
        for (int i = 0; i < named.names().size(); i++) {
            if (i == cap) {
                list.add(" §7§o" + (named.names().size() - cap) + " more enchantments..");
                break;
            }
            list.add(named.names().get(i));
        }
        return named.total();
    }
}
