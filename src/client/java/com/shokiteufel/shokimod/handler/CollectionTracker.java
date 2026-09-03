package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.CollectionTrackerCategory;
import com.shokiteufel.shokimod.util.CollectionData;
import com.shokiteufel.shokimod.util.CollectionData.Yield;
import com.shokiteufel.shokimod.util.ItemNames;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ItemValue.PriceMode;
import com.shokiteufel.shokimod.util.ItemValue.Value;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Collection-Tracker: was seit dem Reset dazugekommen ist, und wie schnell.
 *
 * Anders als Shards meldet Hypixel Collections nicht im Klartext. Was ankommt, steht
 * im Sack-Hinweis: die Zeile "[Sacks] +38 items." traegt am Mauszeiger die Aufstellung
 * "+38 Enchanted Helix Log (Enchanted Foraging Sack)". Genau die wird gelesen.
 *
 * Verzaubertes zaehlt mehrfach: ein Enchanted Helix Log ist 160 Helix Log. Die Zahl
 * kommt aus dem Bauplan ({@link CollectionData}), nicht aus einer gepflegten Liste -
 * so stimmt sie auch fuer Items, die es heute noch nicht gibt.
 *
 * Der Gesamtstand einer Collection steht nur im Collections-Menue. Wer es aufmacht,
 * fuellt ihn nebenbei; bis dahin zeigt der Kasten nur den Zuwachs.
 *
 * Die Zeit laeuft wie beim Hunting Tracker: Pause nach Untaetigkeit, und die Wartezeit
 * seit dem letzten Fund wird dann wieder abgezogen.
 */
public final class CollectionTracker {

    /** "+38 Enchanted Helix Log (...)" oder "-80,900 Ender Pearl (...)" - Vorzeichen, Anzahl, Name */
    private static final Pattern ITEM_LINE = Pattern.compile("^([+-])\\s*([\\d,.]+)\\s+(.+?)\\s*\\(");
    private static final Pattern TOTAL_COLLECTED = Pattern.compile("Total Collected:\\s*([\\d,.]+)");
    private static final Pattern COOP_HEADER = Pattern.compile("(?i)co-?op contributions");
    /** "[MVP+] SchiggyMobil: 1.1M" - Name und Anteil, gekuerzt wie im Spiel */
    private static final Pattern COOP_LINE = Pattern.compile("([A-Za-z0-9_]{3,16}):\\s*([\\d,.]+)\\s*([kKmMbB])?\\s*$");
    private static final String SACK_MARKER = "[Sacks]";
    private static final String ADDED_HEADER = "added items";
    private static final String REMOVED_HEADER = "removed items";
    private static final long SAVE_INTERVAL_MILLIS = 15_000L;
    private static final int MENU_SCAN_TICKS = 20;

    /** Eine Zeile im Kasten */
    public record Row(String collectionId, String name, long gained, long total, double value) {
    }

    /** Ein Posten, dessen Bauplan noch geholt wird */
    private record Waiting(String itemId, int amount, long at) {
    }

    private static final int MAX_WAITING = 100;
    private static final long WAITING_TIMEOUT_MILLIS = 120_000L;
    private static final List<Waiting> waiting = new ArrayList<>();

    private static long lastTickMillis = 0L;
    private static long lastActivityMillis = 0L;
    private static long unconfirmedMillis = 0L;
    private static boolean paused = true;
    private static boolean dirty = false;
    private static long lastSaveMillis = 0L;
    private static int menuTicks = 0;

    private CollectionTracker() {
    }

    private static CollectionTrackerCategory cfg() {
        return ModConfig.INSTANCE.collections.tracker;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CollectionTracker::tick);
    }

    /**
     * Die Chatzeile mit allem, was daranhaengt.
     *
     * @param message die Nachricht samt Mauszeiger-Text
     * @param plain   dieselbe Zeile ohne Farbcodes
     */
    public static void onChatMessage(Component message, String plain) {
        if (!FeatureGate.collectionTracker() || message == null || plain == null) return;
        if (!plain.contains(SACK_MARKER)) return;
        if (!GameState.Server.isSkyblock()) return;

        List<String> hover = new ArrayList<>();
        collectHoverText(message, hover);
        if (hover.isEmpty()) return;

        // Erst die ganze Nachricht zusammenrechnen, dann buchen. Wer hochcraftet, nimmt rohe
        // Perlen heraus und legt verzauberte hinein - erst zusammen ergibt das die Wahrheit,
        // und die Reihenfolge der Zeilen spielt keine Rolle mehr
        Map<String, Integer> deltas = new LinkedHashMap<>();
        boolean adding = false;
        for (String line : hover) {
            String clean = line.replaceAll("§.", "").trim();
            String lower = clean.toLowerCase(Locale.ROOT);
            if (lower.startsWith(ADDED_HEADER)) {
                adding = true;
                continue;
            }
            if (lower.startsWith(REMOVED_HEADER)) {
                adding = false;
                continue;
            }

            Matcher matcher = ITEM_LINE.matcher(clean);
            if (!matcher.find()) continue;
            int amount = number(matcher.group(2));
            if (amount <= 0) continue;
            boolean minus = "-".equals(matcher.group(1)) || !adding;
            String name = itemName(matcher.group(3));
            if (name.isEmpty()) continue;
            deltas.merge(name, minus ? -amount : amount, Integer::sum);
        }

        boolean counted = false;
        for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
            if (entry.getValue() == 0) continue;
            if (count(entry.getKey(), entry.getValue())) counted = true;
        }

        if (counted) {
            markActivity();
            dirty = true;
        }
    }

    /**
     * Ein Posten aus dem Sack-Hinweis, mit Vorzeichen. true, wenn er gezaehlt oder vorgemerkt wurde.
     *
     * Negative Posten ziehen ab, aber nie unter null: wer aus dem Sack nimmt, was er vor dem
     * Reset gesammelt hat, soll keinen Minusstand bekommen.
     */
    private static boolean count(String displayName, int amount) {
        List<String> ids = ItemNames.idsFor(displayName);
        if (ids.isEmpty()) {
            // Der Name aus dem Sack ist manchmal schon die Collection selbst ("Helix Log")
            String direct = CollectionData.idForName(displayName);
            if (direct == null) return false;
            ids = List.of(direct);
        }

        for (String id : ids) {
            if (apply(id, amount)) return true;
        }
        // Der Bauplan wird gerade geholt. Der Posten wartet, statt verloren zu gehen -
        // sonst zaehlte der erste Fund nach dem Einloggen nie mit
        if (waiting.size() < MAX_WAITING) {
            waiting.add(new Waiting(ids.get(0), amount, System.currentTimeMillis()));
        }
        return true;
    }

    /** Bucht einen Posten, sobald der Bauplan bekannt ist */
    private static boolean apply(String itemId, int amount) {
        Yield yield = CollectionData.yieldOf(itemId);
        if (yield == null) return false;

        long units = yield.amount() * amount;
        cfg().gains.merge(yield.collectionId(), units, (a, b) -> Math.max(0L, a + b));
        Value value = ItemValue.resolve(List.of(itemId), Math.abs(amount), PriceMode.INSTANT_SELL, cfg().priceMode);
        if (value != null) {
            double coins = amount < 0 ? -value.coins() : value.coins();
            cfg().values.merge(yield.collectionId(), coins, (a, b) -> Math.max(0.0, a + b));
        }
        ShokiMod.LOGGER.info("[Collections] {}{} {} -> {} x {}", amount < 0 ? "" : "+", amount, itemId,
                units, yield.collectionId());
        return true;
    }

    /** Wartende Posten erneut versuchen; was zu lange liegt, wird verworfen */
    private static void retryWaiting() {
        if (waiting.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Waiting> keep = new ArrayList<>();
        for (Waiting entry : waiting) {
            if (apply(entry.itemId(), entry.amount())) {
                dirty = true;
                continue;
            }
            if (now - entry.at() < WAITING_TIMEOUT_MILLIS) keep.add(entry);
        }
        waiting.clear();
        waiting.addAll(keep);
    }

    /**
     * Der reine Name eines Postens.
     *
     * Vor manchen Namen steht ein Zeichen aus dem Ressourcenpaket - Gemstones haben ihr
     * eigenes Symbol. Es gehoert nicht zum Namen und wuerde jede Suche verfehlen, also
     * faellt alles weg, was vorn und hinten kein Buchstabe und keine Ziffer ist.
     */
    private static String itemName(String raw) {
        return raw.replaceAll("^[^\\p{L}\\p{N}]+", "").replaceAll("[^\\p{L}\\p{N}]+$", "").trim();
    }

    /** Der Text am Mauszeiger, Zeile fuer Zeile - auch aus allen Anhaengseln der Nachricht */
    private static void collectHoverText(Component component, List<String> out) {
        HoverEvent hover = component.getStyle().getHoverEvent();
        if (hover instanceof HoverEvent.ShowText showText) {
            for (String line : showText.value().getString().split("\n")) out.add(line);
        }
        for (Component sibling : component.getSiblings()) collectHoverText(sibling, out);
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

        if (!FeatureGate.collectionTracker()) {
            paused = true;
            return;
        }

        if (client.player != null && GameState.Server.isSkyblock()) {
            CollectionData.prefetch();
            ItemValue.BAZAAR.prefetch();
            retryWaiting();
            if (++menuTicks >= MENU_SCAN_TICKS) {
                menuTicks = 0;
                readOpenMenu(client);
            }
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

    /**
     * Steht das Collections-Menue offen, wird der Gesamtstand mitgenommen.
     *
     * Gelesen wird die Beschreibung jedes Feldes: "Total Collected: 1,234,567". Findet
     * sich die Zeile nicht, bleibt der Gesamtstand einfach leer - der Zuwachs zaehlt
     * ohnehin fuer sich.
     */
    private static void readOpenMenu(Minecraft client) {
        if (!(client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) return;
        String title = screen.getTitle().getString();
        if (!title.toLowerCase(Locale.ROOT).contains("collection")) return;

        boolean changed = false;
        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().replaceAll("§.", "").trim();
            // "Helix Log" oder "Helix Log I" - die Stufe steht als roemische Zahl dahinter
            String collectionId = CollectionData.idForName(name);
            if (collectionId == null) {
                int space = name.lastIndexOf(' ');
                if (space > 0) collectionId = CollectionData.idForName(name.substring(0, space));
            }
            if (collectionId == null) continue;

            net.minecraft.world.item.component.ItemLore lore =
                    stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore == null) continue;
            long total = readOwnTotal(client, lore.lines());
            if (total <= 0) continue;
            Long known = cfg().totals.get(collectionId);
            if (known == null || known != total) {
                cfg().totals.put(collectionId, total);
                changed = true;
            }
        }
        if (changed) dirty = true;
    }

    /**
     * Was man selbst gesammelt hat.
     *
     * "Total Collected" ist auf einem Co-op-Profil die Summe aller Mitspieler. Steht darunter
     * die Aufstellung "Co-op Contributions", zaehlt nur die eigene Zeile. Die ist gekuerzt
     * ("1.1M"), also auf hunderttausend genau - besser eine grobe eigene Zahl als eine genaue,
     * die drei Leuten gehoert.
     */
    private static long readOwnTotal(Minecraft client, List<Component> lines) {
        String me = client.getUser() == null ? "" : client.getUser().getName();
        long total = 0;
        boolean coop = false;
        for (Component component : lines) {
            String line = component.getString().replaceAll("§.", "").trim();
            if (COOP_HEADER.matcher(line).find()) {
                coop = true;
                continue;
            }
            if (!coop) {
                Matcher matcher = TOTAL_COLLECTED.matcher(line);
                if (matcher.find()) total = number(matcher.group(1));
                continue;
            }
            Matcher matcher = COOP_LINE.matcher(line);
            if (matcher.find() && matcher.group(1).equalsIgnoreCase(me)) return shortNumber(matcher.group(2), matcher.group(3));
        }
        return total;
    }

    /** "1.1M" oder "133.4k" in eine ganze Zahl */
    private static long shortNumber(String digits, String suffix) {
        try {
            double value = Double.parseDouble(digits.replace(",", ""));
            if (suffix != null) {
                value *= switch (Character.toLowerCase(suffix.charAt(0))) {
                    case 'k' -> 1_000d;
                    case 'm' -> 1_000_000d;
                    case 'b' -> 1_000_000_000d;
                    default -> 1d;
                };
            }
            return Math.round(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int number(String text) {
        try {
            return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(text.replaceAll("[.,]", "")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- Was der Kasten fragt ----

    /** Die gewaehlten Collections, die wertvollsten zuerst; ohne Auswahl alles Gezaehlte */
    public static List<Row> rows() {
        CollectionTrackerCategory cfg = cfg();
        List<String> wanted = cfg.selected == null || cfg.selected.isEmpty()
                ? new ArrayList<>(cfg.gains.keySet())
                : cfg.selected;

        List<Row> out = new ArrayList<>();
        for (String id : wanted) {
            long gained = cfg.gains.getOrDefault(id, 0L);
            long total = cfg.totals.getOrDefault(id, 0L);
            double value = cfg.values.getOrDefault(id, 0.0);
            out.add(new Row(id, CollectionData.nameOf(id), gained, total, value));
        }
        out.sort(Comparator.comparingLong(Row::gained).reversed());
        return out;
    }

    public static long totalGained() {
        long sum = 0;
        for (Row row : rows()) sum += row.gained();
        return sum;
    }

    public static double totalValue() {
        double sum = 0;
        for (Row row : rows()) sum += row.value();
        return sum;
    }

    /** Zuwachs je Stunde einer Zeile, oder 0 solange keine Zeit gelaufen ist */
    public static double perHour(double amount) {
        long millis = cfg().uptimeMillis;
        if (millis < 1000L) return 0;
        return amount * 3_600_000.0 / millis;
    }

    public static long uptimeMillis() {
        return cfg().uptimeMillis;
    }

    public static boolean isPaused() {
        return paused;
    }

    /** Der Reset-Knopf: Zuwachs, Wert und Zeit auf null. Der Gesamtstand bleibt */
    public static void reset() {
        cfg().gains.clear();
        cfg().values.clear();
        cfg().uptimeMillis = 0L;
        cfg().startedAt = 0L;
        lastActivityMillis = 0L;
        unconfirmedMillis = 0L;
        paused = true;
        dirty = false;
        ModConfig.INSTANCE.saveNow();
        ShokiMod.LOGGER.info("[Collections] tracker reset");
    }

    /** Fuer den Diagnosebericht */
    public static String status() {
        if (!FeatureGate.collectionTracker()) return "collection tracker: off";
        StringBuilder out = new StringBuilder("collection tracker: ");
        out.append(cfg().selected == null ? 0 : cfg().selected.size()).append(" selected, ")
                .append(cfg().gains.size()).append(" counted, uptime ")
                .append(cfg().uptimeMillis / 1000).append("s");
        for (Map.Entry<String, Long> entry : cfg().gains.entrySet()) {
            out.append("\n  ").append(entry.getKey()).append(" +").append(entry.getValue());
        }
        return out.toString();
    }
}
