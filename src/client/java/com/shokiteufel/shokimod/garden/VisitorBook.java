package com.shokiteufel.shokimod.garden;

import com.shokiteufel.shokimod.data.ModConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Das Buch der Besucher im Garden: wer gerade da ist, was er verlangt und was er bietet.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code VisitorApi}, {@code GardenVisitorTooltip}, {@code GardenVisitorShoppingList}). Von dort stammen die Regeln: Die Namen der
 * Besucher stehen im Widget "Visitors: (n)" der Tab-Liste; wer dort fehlt, ist weg. Was ein Besucher verlangt, steht erst fest, wenn man sein Fenster geoeffnet hat -
 * bis dahin ist er "neu". Wer angenommen oder abgelehnt wurde, zaehlt nicht mehr. Der Preis der Gegenstaende ist der Kaufpreis, der Wert des Copper der Preis von Green
 * Thumb I durch 1500; der Preis des Angebots ist der der verlangten Gegenstaende abzueglich der Belohnungen (nie unter null). Die Umsetzung ist eigene Arbeit: Java,
 * reiner Zustand ohne Minecraft (Preise kommen als Funktion herein), der Besucher ist ueber seinen Namen ohne Farbcodes gefuehrt.
 */
public final class VisitorBook {

    /** Die Stationen eines Besuchers */
    public enum Status {
        /** In der Tab-Liste, sein Fenster war noch nicht offen */
        NEW,
        /** Fenster gesehen, es fehlt etwas */
        WAITING,
        /** Fenster gesehen, "Click to give!" stand da */
        READY,
        ACCEPTED,
        REFUSED
    }

    /** Ein Besucher */
    public static final class Entry {
        private final String name;
        private Status status = Status.NEW;
        private final Map<String, Integer> required = new LinkedHashMap<>();
        private List<String> rewardNames = List.of();
        private List<VisitorReward> warned = List.of();
        private boolean rewardsKnown = false;
        private int offersAccepted = -1;
        private boolean hasCopper = false;
        private boolean pricesKnown = false;
        private int pricePerCopper = 0;
        private double totalPrice = 0.0;
        private double totalReward = 0.0;
        private boolean ignored = false;
        private VisitorDecision.BlockReason block = null;
        private List<String> lastLore = List.of();

        Entry(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public Status status() {
            return status;
        }

        public void status(Status status) {
            this.status = status;
        }

        /** Die verlangten Gegenstaende, nur lesen */
        public Map<String, Integer> required() {
            return java.util.Collections.unmodifiableMap(required);
        }

        /** Hat man sein Fenster schon gesehen - weiss man also, was er verlangt? */
        public boolean known() {
            return !required.isEmpty();
        }

        public List<String> rewardNames() {
            return rewardNames;
        }

        /** Die gewaehlten Belohnungen, die in seinem Angebot stehen */
        public List<VisitorReward> warned() {
            return warned;
        }

        public int offersAccepted() {
            return offersAccepted;
        }

        /** Wie viele Angebote man diesem Besucher schon erfuellt hat (-1: unbekannt) */
        public void offersAccepted(int count) {
            this.offersAccepted = count;
        }

        /** Sind alle Preise der verlangten Gegenstaende und der Wert des Copper bekannt? Nur dann gelten die Preisregeln ({@link VisitorDecision}) */
        public boolean pricesKnown() {
            return pricesKnown;
        }

        public boolean hasCopper() {
            return hasCopper;
        }

        public int pricePerCopper() {
            return pricePerCopper;
        }

        public double totalPrice() {
            return totalPrice;
        }

        public double totalReward() {
            return totalReward;
        }

        public boolean ignored() {
            return ignored;
        }

        public void ignored(boolean ignored) {
            this.ignored = ignored;
        }

        /** Warum der Knopf gesperrt ist, oder null */
        public VisitorDecision.BlockReason block() {
            return block;
        }

        /** Zaehlt der Besucher noch (weder angenommen noch abgelehnt)? */
        public boolean active() {
            return status != Status.ACCEPTED && status != Status.REFUSED;
        }

        /** Die Fakten fuer {@link VisitorDecision#decide} */
        public VisitorDecision.Facts facts() {
            return new VisitorDecision.Facts(!warned.isEmpty(), offersAccepted, hasCopper && pricesKnown, pricePerCopper, totalPrice, totalReward);
        }

        /** Die Sperre neu berechnen (die Regeln koennen sich zwischen zwei Lesungen aendern) */
        public void decide(VisitorDecision.Rules rules) {
            this.block = VisitorDecision.decide(rules, facts());
        }

        /**
         * Das Angebot aus dem Fenster uebernehmen.
         *
         * @param lore         die Beschreibung von "Accept Offer" (ohne Farbcodes) - unveraendert seit dem letzten Mal: nichts zu tun
         * @param offer        das Gelesene
         * @param unitPrice    Kaufpreis eines Gegenstands nach Namen, oder eine Zahl unter null, wenn er unbekannt ist
         * @param copperValue  Wert eines Copper in Coins
         * @param chosen       die Einstellung der Belohnungen
         * @return die Belohnungen, die dieses Mal zum ersten Mal auftauchten (fuer "Notify in Chat"); leer, wenn nichts Neues dazukam
         */
        public List<VisitorReward> applyOffer(List<String> lore, VisitorOffer.Parsed offer, ToDoubleFunction<String> unitPrice, double copperValue,
                                              ModConfig.GardenRewardWarningCategory chosen) {
            if (lore != null && lore.equals(lastLore)) {
                // Dasselbe Angebot - aber die Auswahl der Belohnungen kann sich seit der letzten Lesung geaendert haben: Wer eine Belohnung erst einschaltet, nachdem
                // der Besucher gelesen wurde, wartete bisher, bis sich der Text aenderte (oft bis der Besucher ging), und nichts wurde gesperrt. Der Abgleich der
                // gemerkten Namen mit der Auswahl kostet eine Handvoll Vergleiche; neu gemeldet wird dabei nichts
                warned = List.copyOf(VisitorDecision.warnedRewards(rewardNames, chosen));
                return List.of();
            }
            lastLore = lore == null ? List.of() : List.copyOf(lore);

            required.clear();
            required.putAll(offer.required());
            // Wer schon angenommen oder abgelehnt ist, wird durch ein spaeteres Lesen nicht wieder zum Wartenden
            if (active()) status = offer.readyToGive() ? Status.READY : Status.WAITING;

            double price = 0.0;
            boolean allPriced = copperValue > 0;
            for (Map.Entry<String, Integer> item : offer.required().entrySet()) {
                double part = pricePart(unitPrice, item.getKey(), item.getValue());
                if (part <= 0) allPriced = false;
                price += part;
            }
            pricesKnown = allPriced && !offer.required().isEmpty();
            List<String> names = new ArrayList<>();
            for (VisitorOffer.ItemAmount reward : offer.rewards()) {
                names.add(reward.name());
                price -= pricePart(unitPrice, reward.name(), reward.amount());
            }
            totalPrice = Math.max(0.0, price);
            hasCopper = offer.hasCopper();
            pricePerCopper = hasCopper && offer.copper() > 0 ? (int) (totalPrice / offer.copper()) : 0;
            totalReward = hasCopper ? offer.copper() * copperValue : 0.0;
            rewardNames = List.copyOf(names);

            List<VisitorReward> now = VisitorDecision.warnedRewards(names, chosen);
            List<VisitorReward> fresh = new ArrayList<>();
            // Gemeldet wird einmal: wenn die Belohnungen zum ersten Mal bekannt werden
            if (!rewardsKnown && !names.isEmpty()) {
                rewardsKnown = true;
                fresh.addAll(now);
            }
            warned = List.copyOf(now);
            return fresh;
        }

        private static double pricePart(ToDoubleFunction<String> unitPrice, String name, int amount) {
            double unit = unitPrice == null ? -1.0 : unitPrice.applyAsDouble(name);
            return unit > 0 ? unit * amount : 0.0;
        }
    }

    /** Das Buch, das die Mod im Spiel fuehrt */
    public static final VisitorBook INSTANCE = new VisitorBook();

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    /** Namen, die der Spieler aus der Liste genommen hat (Klick auf den Namen); sie ueberleben das Gehen und Kommen des Besuchers */
    private final Set<String> ignoredNames = new LinkedHashSet<>();

    public VisitorBook() {
    }

    /** Wer gerade im Buch steht, in der Reihenfolge, in der sie kamen */
    public Collection<Entry> entries() {
        return entries.values();
    }

    public Entry get(String name) {
        return name == null ? null : entries.get(name);
    }

    public int size() {
        return entries.size();
    }

    /** Wie viele Besucher noch zaehlen */
    public int activeCount() {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.active()) n++;
        }
        return n;
    }

    public void clear() {
        entries.clear();
    }

    /** Den Besucher holen, bei Bedarf neu anlegen (sein Fenster kann vor dem Widget der Tab-Liste aufgehen) */
    public Entry getOrCreate(String name) {
        Entry entry = entries.get(name);
        if (entry == null) {
            entry = new Entry(name);
            entry.ignored = ignoredNames.contains(name);
            entries.put(name, entry);
        }
        return entry;
    }

    /**
     * Das Widget der Tab-Liste uebernehmen: neue Namen kommen dazu, fehlende gehen.
     *
     * @param complete ob die Liste vollstaendig ist (so viele Namen wie die Kopfzeile sagt): Nur dann gilt ein fehlender Name als gegangen. Eine Liste mitten im
     *                 Aufbau fuegt Namen hinzu, nimmt aber keinen weg - sonst ginge, was man ueber einen Besucher gelesen hat, mit einem halben Bild verloren
     * @return ob sich etwas geaendert hat
     */
    public boolean sync(List<String> namesInTab, boolean complete) {
        boolean changed = false;
        Set<String> inTab = new LinkedHashSet<>(namesInTab);
        for (String name : inTab) {
            if (!entries.containsKey(name)) {
                getOrCreate(name);
                changed = true;
            }
        }
        if (complete && entries.keySet().removeIf(name -> !inTab.contains(name))) changed = true;
        return changed;
    }

    /** Die Besucher aus der Liste nehmen oder wieder hineinnehmen (Klick auf den Namen) */
    public void toggleIgnored(String name) {
        Entry entry = entries.get(name);
        boolean now = !(entry != null ? entry.ignored : ignoredNames.contains(name));
        if (now) ignoredNames.add(name);
        else ignoredNames.remove(name);
        if (entry != null) entry.ignored = now;
    }

    /** Alle verlangten Gegenstaende der Besucher, die noch zaehlen und nicht aus der Liste genommen sind, summiert nach Namen */
    public Map<String, Integer> shoppingTotals() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            if (!e.active() || e.ignored) continue;
            for (Map.Entry<String, Integer> item : e.required.entrySet()) {
                totals.merge(item.getKey(), item.getValue(), Integer::sum);
            }
        }
        return totals;
    }
}
