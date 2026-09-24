package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ProfitCategory;
import com.shokiteufel.shokimod.scanner.ItemChanges;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.SellMode;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Der Profit-Tracker: was seit dem Reset dazugekommen ist, und was es bringt.
 *
 * Gezaehlt wird, was {@link ItemChanges} meldet - also alles, was tatsaechlich ins
 * Inventar oder in einen Sack gewandert ist. Der Chat spielt keine Rolle mehr,
 * und damit auch nicht die Frage, ob Hypixel einen Fund fuer meldenswert haelt.
 *
 * Zwei Dinge lassen sich fuer jedes Item einzeln einstellen:
 *
 * <ul>
 *   <li><b>Ob es im Kasten steht.</b> Entweder steht alles drin ausser dem, was man
 *       ausblendet, oder nur das, was man angeklickt hat. Gezaehlt wird in beiden
 *       Faellen alles - wer die Auswahl spaeter aendert, findet die Funde von vorhin
 *       noch vor. Ausblenden loescht nichts.</li>
 *   <li><b>Wie es zu Geld wird.</b> Sofortverkauf, Verkaufsorder oder Haendler, je
 *       Item. Shards verkauft man sofort, einen Deep Sea Orb legt man in eine Order,
 *       und Enchanted Pumpkin nimmt ohnehin nur der NPC. Ohne eigene Wahl gilt die
 *       Voreinstellung des Kastens.</li>
 * </ul>
 *
 * Die Zeit laeuft wie beim Hunting Tracker: Pause nach Untaetigkeit, und die
 * Wartezeit seit dem letzten Fund wird dann wieder abgezogen.
 */
public final class ProfitTracker {

    private static final long SAVE_INTERVAL_MILLIS = 15_000L;

    /** Eine Zeile im Kasten: Item, Stueckzahl, Wert des ganzen Stapels */
    public record Row(String itemId, String name, int count, double value, boolean priced, SellMode mode) {
    }

    private static long lastTickMillis = 0L;
    private static long lastActivityMillis = 0L;
    /** Zeit seit dem letzten Fund, die noch nicht bestaetigt ist - faellt bei Pause weg */
    private static long unconfirmedMillis = 0L;
    private static boolean paused = true;
    private static boolean dirty = false;
    private static long lastSaveMillis = 0L;

    private ProfitTracker() {
    }

    public static ProfitCategory cfg() {
        return ModConfig.INSTANCE.profit;
    }

    public static boolean enabled() {
        return cfg().enabled;
    }

    public static void register() {
        ItemChanges.listen(ProfitTracker::onGains);
        ClientTickEvents.END_CLIENT_TICK.register(ProfitTracker::tick);
    }

    /** Ein Schwung Zugaenge aus dem Inventar oder aus einem Sack */
    private static void onGains(Map<String, Integer> gains) {
        if (!enabled()) return;

        boolean counted = false;
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            String itemId = entry.getKey();
            int amount = entry.getValue();
            if (itemId == null || amount <= 0) continue;
            // Nur der erste Fund einer Ware kommt ins Log. Beim Minen faellt jede
            // Sekunde etwas an - Zeile fuer Zeile waere das Log nach einer Stunde
            // unlesbar, und mehr als "das hier wurde erkannt" sagt es nicht aus
            boolean first = !cfg().counts.containsKey(itemId);
            cfg().counts.merge(itemId, amount, Integer::sum);
            counted = true;
            // Die Namensfarbe wird nur im Augenblick des Fundes gesehen - festhalten,
            // solange sie da ist
            if (!cfg().colours.containsKey(itemId)) {
                int colour = ItemChanges.colourOf(itemId);
                if (colour != 0) cfg().colours.put(itemId, colour);
            }
            if (first) ShokiMod.LOGGER.info("[Profit] first {} x{}", itemId, amount);
        }

        if (counted) {
            markActivity();
            dirty = true;
        }
    }

    private static void markActivity() {
        long now = System.currentTimeMillis();
        if (cfg().startedAt <= 0L) cfg().startedAt = now;
        lastActivityMillis = now;
        unconfirmedMillis = 0L;
        paused = false;
    }

    private static void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        long delta = lastTickMillis == 0L ? 0L : now - lastTickMillis;
        lastTickMillis = now;

        if (!enabled()) {
            paused = true;
            return;
        }

        // Die Preise sollen dastehen, sobald der Kasten sichtbar ist
        if (client.player != null && com.shokiteufel.shokimod.data.GameState.Server.isSkyblock()) {
            ItemValue.BAZAAR.prefetch();
            ItemNames.prefetch();
        }

        long pauseAfter = Math.max(5, cfg().pauseAfterSeconds) * 1000L;
        boolean active = cfg().timerEnabled && lastActivityMillis > 0L && now - lastActivityMillis <= pauseAfter
                && client.isWindowActive();

        if (active) {
            if (delta > 0 && delta < 5_000L) {
                cfg().uptimeMillis += delta;
                unconfirmedMillis += delta;
            }
            paused = false;
        } else if (!paused) {
            cfg().uptimeMillis = Math.max(0L, cfg().uptimeMillis - unconfirmedMillis);
            unconfirmedMillis = 0L;
            paused = true;
            dirty = true;
        }

        if (dirty && now - lastSaveMillis >= SAVE_INTERVAL_MILLIS) {
            dirty = false;
            lastSaveMillis = now;
            ModConfig.INSTANCE.saveNow();
        }
    }

    /** Die Voreinstellung des Kastens */
    public static SellMode mode() {
        return cfg().priceMode == null ? SellMode.INSTANT_SELL : cfg().priceMode;
    }

    /** Die Verkaufsart dieses Items - eigene Wahl, sonst die Voreinstellung */
    public static SellMode modeOf(String itemId) {
        SellMode own = cfg().modes.get(itemId);
        return own == null ? mode() : own;
    }

    /** null setzt das Item auf die Voreinstellung zurueck */
    public static void setMode(String itemId, SellMode mode) {
        if (mode == null) cfg().modes.remove(itemId);
        else cfg().modes.put(itemId, mode);
        ModConfig.INSTANCE.saveNow();
    }

    /** Hat dieses Item eine eigene Wahl, oder folgt es dem Kasten? */
    public static boolean hasOwnMode(String itemId) {
        return cfg().modes.containsKey(itemId);
    }

    /**
     * Steht das Item im Kasten?
     *
     * Bei "Alles" zaehlt nur, was nicht ausgeblendet ist; bei "Nur Ausgewaehlte"
     * muss es angeklickt worden sein.
     */
    public static boolean shown(String itemId) {
        if (cfg().selection == ModConfig.ProfitSelection.PICKED) return cfg().picked.contains(itemId);
        return !cfg().hidden.contains(itemId);
    }

    /**
     * Von Hand nachbessern.
     *
     * Der Kasten zeigt, was gezaehlt wurde - nicht, was gefallen ist. Meistens ist das
     * dasselbe, aber wenn nicht, soll man es geradeziehen koennen, ohne alles
     * zurueckzusetzen. Unter null geht nichts; wer eine Ware auf null stellt, nimmt sie
     * aus der Liste.
     */
    public static void adjust(String itemId, int delta) {
        if (itemId == null || delta == 0) return;

        int updated = countOf(itemId) + delta;
        if (updated > 0) cfg().counts.put(itemId, updated);
        else cfg().counts.remove(itemId);
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] {} {} by hand -> {}", delta > 0 ? "+" + delta : delta,
                itemId, Math.max(0, updated));
    }

    /** Der Klick in der Liste: aus wird an, an wird aus - in beiden Betriebsarten */
    public static void toggle(String itemId) {
        if (itemId == null) return;
        if (cfg().selection == ModConfig.ProfitSelection.PICKED) {
            if (!cfg().picked.remove(itemId)) cfg().picked.add(itemId);
        } else {
            if (!cfg().hidden.remove(itemId)) cfg().hidden.add(itemId);
        }
        ModConfig.INSTANCE.saveNow();
    }

    /** Alle Kennungen, die seit dem Reset dazugekommen sind - auch die ausgeblendeten */
    public static List<String> seen() {
        return new ArrayList<>(cfg().counts.keySet());
    }

    public static int countOf(String itemId) {
        Integer count = cfg().counts.get(itemId);
        return count == null ? 0 : count;
    }

    /** Der Name, wie ihn das Spiel schreibt - sonst aus der Kennung gebildet */
    public static String nameOf(String itemId) {
        String name = ItemNames.displayName(itemId);
        return name == null || name.isBlank() ? SkyBlockItems.readableName(itemId) : name;
    }

    /**
     * Die Farbe, in der der Name steht - die Seltenheit des Items.
     *
     * Drei Quellen, in dieser Reihenfolge: was beim Fund am Gegenstand selbst zu
     * sehen war, was diese Sitzung gesehen hat, und sonst die Seltenheit aus
     * Hypixels Item-Liste. Wer in keiner steht, bleibt weiss.
     */
    public static int colourOf(String itemId) {
        Integer stored = cfg().colours.get(itemId);
        if (stored != null && stored != 0) return stored;

        int seen = ItemChanges.colourOf(itemId);
        if (seen != 0) return seen;

        int rarity = SkyBlockItems.rarityColour(ItemNames.tier(itemId));
        return rarity != 0 ? rarity : 0xFFFFFFFF;
    }

    /** Was ein Stueck bringt, oder -1 wenn es dazu keine Zahl gibt */
    public static double unitPrice(String itemId) {
        return ItemValue.unitPrice(SkyBlockItems.priceCandidates(itemId), modeOf(itemId));
    }

    /** Alle sichtbaren Zeilen, wertvollste zuerst */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cfg().counts.entrySet()) {
            String itemId = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (itemId == null || count <= 0 || !shown(itemId)) continue;

            double unit = unitPrice(itemId);
            out.add(new Row(itemId, nameOf(itemId), count, unit > 0 ? unit * count : 0,
                    unit > 0, modeOf(itemId)));
        }
        out.sort(Comparator.comparingDouble(Row::value).reversed().thenComparing(Row::name));
        return out;
    }

    /** Was alles Sichtbare zusammen wert ist */
    public static double total() {
        double sum = 0;
        for (Row row : rows()) sum += row.value();
        return sum;
    }

    public static double perHour() {
        long uptime = cfg().uptimeMillis;
        if (uptime < 1_000L) return 0;
        return total() / (uptime / 3_600_000d);
    }

    public static boolean isPaused() {
        return paused;
    }

    public static long uptimeMillis() {
        return cfg().uptimeMillis;
    }

    /**
     * Der Reset-Knopf: Zaehlstand und Zeit auf null.
     *
     * Die Auswahl bleibt stehen. Wer sich seine Liste eingerichtet hat, will sie
     * nach dem naechsten Lauf wiederhaben und nicht neu anklicken.
     */
    public static void reset() {
        cfg().counts.clear();
        cfg().uptimeMillis = 0L;
        cfg().startedAt = 0L;
        lastActivityMillis = 0L;
        unconfirmedMillis = 0L;
        paused = true;
        dirty = false;
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Profit] tracker reset");
    }
}
