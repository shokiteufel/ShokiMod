package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.HuntingTrackerCategory;
import com.shokiteufel.shokimod.data.RareLootParser;
import com.shokiteufel.shokimod.data.RareLootParser.Drop;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.BazaarPrice;
import com.shokiteufel.shokimod.util.ItemValue.PriceMode;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Der Hunting Tracker: was die Jagd an Shards gebracht hat, und was das wert ist.
 *
 * Gezaehlt werden nur Shards - aus denselben Chatzeilen, die auch der Fund-Alarm
 * liest ("You caught x3 Timil Shards!", "CHARM! ..."). Bewertet wird live mit dem
 * Basarpreis, wahlweise Sofortverkauf oder Verkaufsorder: der Gesamtwert folgt
 * also dem Markt, nicht dem Kurs zum Zeitpunkt des Fangs.
 *
 * Die Zeit laeuft nur, solange gejagt wird. Bleibt ein Fang laenger als die
 * eingestellte Pause aus, wird die Zeit seit dem letzten Fang wieder abgezogen -
 * so wie Skysofts Profit Tracker es macht. Ein Klo-Gang druekt Profit/h nicht.
 *
 * Zaehlstand und Zeit liegen in der Config und ueberleben einen Neustart, bis man
 * auf Reset drueckt.
 */
public final class HuntingTracker {

    /** So oft wird ein geaenderter Stand auf die Platte geschrieben */
    private static final long SAVE_INTERVAL_MILLIS = 15_000L;
    private static final String SHARD_PREFIX = "SHARD_";
    private static final String[] SKIPPED_PREFIXES = {"Party >", "Guild >", "Co-op >", "From ", "To "};

    /** Eine Zeile im Kasten: Shard, Stueckzahl, Wert des Stapels */
    public record Row(String itemId, String name, int count, double value, boolean priced) {
    }

    private static long lastTickMillis = 0L;
    private static long lastActivityMillis = 0L;
    /** Zeit seit dem letzten Fang, die noch nicht "bestaetigt" ist - faellt bei Pause weg */
    private static long unconfirmedMillis = 0L;
    private static boolean paused = true;
    private static boolean dirty = false;
    private static long lastSaveMillis = 0L;

    private HuntingTracker() {
    }

    private static HuntingTrackerCategory cfg() {
        return ModConfig.INSTANCE.hunting.tracker;
    }

    public static boolean enabled() {
        return cfg().enabled;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HuntingTracker::tick);
    }

    /**
     * @param plain die Chatzeile ohne Farbcodes
     */
    public static void onChatMessage(String plain) {
        if (plain == null || !enabled()) return;
        if (!GameState.Server.isSkyblock()) return;

        String clean = plain.trim();
        for (String prefix : SKIPPED_PREFIXES) {
            if (clean.startsWith(prefix)) return;
        }

        Drop drop = RareLootParser.parse(clean);
        if (drop == null) return;

        String shard = null;
        for (String candidate : drop.itemIdCandidates()) {
            if (candidate != null && candidate.startsWith(SHARD_PREFIX)) {
                shard = candidate;
                break;
            }
        }
        if (shard == null) return;

        cfg().counts.merge(shard, Math.max(1, drop.amount()), Integer::sum);
        markActivity();
        dirty = true;
        ShokiMod.LOGGER.info("[Hunting] +{} {} -> {}", drop.amount(), shard, cfg().counts.get(shard));
    }

    private static void markActivity() {
        long now = System.currentTimeMillis();
        if (cfg().startedAt <= 0L) cfg().startedAt = now;
        lastActivityMillis = now;
        // Ein neuer Fang bestaetigt die Zeit seit dem vorigen: sie zaehlt
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
        if (client.player != null && GameState.Server.isSkyblock()) ItemValue.BAZAAR.prefetch();

        long pauseAfter = Math.max(5, cfg().pauseAfterSeconds) * 1000L;
        boolean active = lastActivityMillis > 0L && now - lastActivityMillis <= pauseAfter
                && client.isWindowActive();

        if (active) {
            if (delta > 0 && delta < 5_000L) {
                cfg().uptimeMillis += delta;
                unconfirmedMillis += delta;
            }
            paused = false;
        } else if (!paused) {
            // Die Jagd ist eingeschlafen: die Zeit seit dem letzten Fang war keine Jagd
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

    /** Der Reset-Knopf: Zaehlstand und Zeit auf null */
    public static void reset() {
        cfg().counts.clear();
        cfg().uptimeMillis = 0L;
        cfg().startedAt = 0L;
        lastActivityMillis = 0L;
        unconfirmedMillis = 0L;
        paused = true;
        dirty = false;
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Hunting] tracker reset");
    }

    public static boolean isPaused() {
        return paused;
    }

    public static long uptimeMillis() {
        return cfg().uptimeMillis;
    }

    /** Der Wert eines Shards nach der gewaehlten Preisart, oder -1 wenn unbekannt */
    private static double unitPrice(String itemId) {
        BazaarPrice price = ItemValue.BAZAAR.get(itemId);
        if (price == null) return -1;
        PriceMode mode = cfg().priceMode == null ? PriceMode.INSTANT_SELL : cfg().priceMode;
        double chosen = mode == PriceMode.SELL_ORDER ? price.sellOrder() : price.instantSell();
        if (chosen <= 0) chosen = mode == PriceMode.SELL_ORDER ? price.instantSell() : price.sellOrder();
        return chosen > 0 ? chosen : -1;
    }

    /** Alle Zeilen, wertvollste zuerst */
    public static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : cfg().counts.entrySet()) {
            String id = entry.getKey();
            int count = entry.getValue();
            if (id == null || count <= 0) continue;

            double unit = unitPrice(id);
            out.add(new Row(id, readableName(id), count, unit > 0 ? unit * count : 0, unit > 0));
        }
        out.sort(Comparator.comparingDouble(Row::value).reversed().thenComparing(Row::name));
        return out;
    }

    public static double total() {
        double sum = 0;
        for (Row row : rows()) sum += row.value();
        return sum;
    }

    /** Profit je Stunde Jagdzeit. Ohne Zeit gibt es keinen Stundenwert */
    public static double perHour() {
        long uptime = cfg().uptimeMillis;
        if (uptime < 1_000L) return 0;
        return total() / (uptime / 3_600_000d);
    }

    /** SHARD_QUEEN_BEE wird zu "Queen Bee" */
    static String readableName(String itemId) {
        String rest = itemId.startsWith(SHARD_PREFIX) ? itemId.substring(SHARD_PREFIX.length()) : itemId;
        StringBuilder out = new StringBuilder(rest.length());
        for (String part : rest.toLowerCase().split("_")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return out.isEmpty() ? itemId : out.toString();
    }
}
