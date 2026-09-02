package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.ValueAlertCategory.Tier;
import com.shokiteufel.shokimod.render.AlertBanner;
import com.shokiteufel.shokimod.render.ShokiModToast;
import com.shokiteufel.shokimod.util.BazaarPrices;
import com.shokiteufel.shokimod.util.CustomSoundPlayer;
import com.shokiteufel.shokimod.util.SkyblockItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Meldet, wenn etwas Wertvolles im Inventar landet.
 *
 * Erkannt wird am Item, nicht an der Chatzeile: verglichen wird der Inhalt des
 * eigenen Inventars von Tick zu Tick, und was dazukommt, wird ueber seine
 * SkyBlock-Kennung im Basar nachgeschlagen. Damit greift der Alarm auch dort, wo
 * Hypixel gar nichts schreibt - und er verwechselt keine Zahl im Text mehr mit
 * einem Betrag.
 *
 * Drei Stufen: ein Fund loest nur die hoechste aus, die er erreicht. Jede Stufe
 * hat ihre eigene Reaktion, damit ein 50M-Fund anders klingt als ein 1M-Fund.
 *
 * Umsortieren ist kein Fund: solange ein Fenster offen ist und kurz danach bleibt
 * der Alarm still. Sonst meldete jede Truhe, jeder Sack und jedes Aufraeumen einen
 * "Fund", nur weil dieselben Items den Platz gewechselt haben.
 */
public final class ValueAlertHandler {

    /** So lange nach dem Schliessen eines Fensters gilt noch alles als verschoben */
    private static final long QUIET_AFTER_SCREEN_MILLIS = 1000L;
    private static final long BANNER_MILLIS = 3000L;
    private static final long TOAST_MILLIS = 5000L;

    /** Je Stufe eine Farbe, damit man schon am Banner sieht, welche es war */
    private static final int[] TIER_COLOURS = {0x55FF55, 0xFFD700, 0xFF55FF};

    /** Der Stand des letzten Ticks: Kennung auf Stueckzahl */
    private static final Map<String, Integer> previous = new HashMap<>();
    private static final Map<String, Integer> current = new HashMap<>();

    /** Erst wenn einmal gezaehlt wurde, ist ein Zuwachs ueberhaupt erkennbar */
    private static boolean primed = false;
    private static long screenClosedAt = 0L;
    private static boolean screenWasOpen = false;

    private ValueAlertHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ValueAlertHandler::tick);
    }

    /** Nach einem Weltwechsel gilt der alte Stand nicht mehr */
    public static void reset() {
        previous.clear();
        current.clear();
        primed = false;
    }

    private static void tick(Minecraft client) {
        ModConfig.ValueAlertCategory cfg = ModConfig.INSTANCE.chat.valueAlerts;
        // Ist der Alarm aus, wird nichts gezaehlt, nichts nachgeschlagen und nichts
        // aus dem Netz geholt. Der gemerkte Stand faellt weg, damit auch kein
        // Speicher liegen bleibt
        if (!cfg.enabled) {
            if (primed) reset();
            return;
        }

        if (client.player == null || !GameState.Server.isSkyblock()) {
            if (primed) reset();
            return;
        }

        // Die Preise sollen dastehen, bevor der erste Fund faellt - nicht erst danach
        BazaarPrices.prefetch();

        boolean screenOpen = client.screen instanceof AbstractContainerScreen<?>;
        if (screenWasOpen && !screenOpen) screenClosedAt = System.currentTimeMillis();
        screenWasOpen = screenOpen;

        current.clear();
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            String id = SkyblockItem.idOf(stack);
            if (id.isEmpty()) continue;
            current.merge(id, stack.getCount(), Integer::sum);
        }

        // Der erste Durchgang legt nur den Vergleichsstand an: alles, was schon da
        // ist, ist kein Fund
        if (!primed) {
            primed = true;
            carryOver();
            return;
        }

        boolean quiet = screenOpen
                || System.currentTimeMillis() - screenClosedAt < QUIET_AFTER_SCREEN_MILLIS;

        if (!quiet) {
            for (Map.Entry<String, Integer> entry : current.entrySet()) {
                int before = previous.getOrDefault(entry.getKey(), 0);
                int gained = entry.getValue() - before;
                if (gained > 0) check(client, entry.getKey(), gained);
            }
        }

        // Auch in der stillen Zeit fortschreiben, sonst kaeme der ganze Zuwachs
        // gebuendelt heraus, sobald das Fenster zugeht
        carryOver();
    }

    private static void carryOver() {
        previous.clear();
        previous.putAll(current);
    }

    private static void check(Minecraft client, String itemId, int amount) {
        double unit = BazaarPrices.sellPrice(itemId);
        if (unit <= 0) return;

        double total = unit * amount;
        Tier tier = tierFor(total);
        if (tier == null) return;

        String name = readableName(itemId);
        announce(client, tier, amount > 1 ? name + " x" + amount : name, total);
    }

    /**
     * Die hoechste Stufe, deren Schwelle der Betrag erreicht - oder null.
     *
     * Hoechste heisst hoechste Schwelle, nicht hoechste Nummer: die Reihenfolge
     * der Stufen im Menue ist nur eine Anzeige. Eine Stufe ohne gueltige Schwelle
     * oder mit Schalter aus zaehlt nicht mit.
     */
    static Tier tierFor(double total) {
        Tier best = null;
        double bestThreshold = 0;
        for (Tier tier : ModConfig.INSTANCE.chat.valueAlerts.tiers()) {
            if (!tier.enabled()) continue;

            double threshold = parseAmount(tier.threshold());
            if (threshold <= 0 || total < threshold) continue;
            if (best == null || threshold > bestThreshold) {
                best = tier;
                bestThreshold = threshold;
            }
        }
        return best;
    }

    /**
     * Der Testknopf: feuert eine Stufe einmal mit einem Beispiel.
     *
     * Ohne echten Fund, ohne Basar - genau die Reaktion, die eingestellt ist. Der
     * Betrag ist die Schwelle der Stufe selbst, damit man sieht, ab wann sie greift.
     */
    public static void test(int number) {
        Tier tier = ModConfig.INSTANCE.chat.valueAlerts.tier(number);
        double threshold = parseAmount(tier.threshold());
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> announce(client, tier, "Test: Enchanted Book", Math.max(threshold, 0)));
    }

    private static void announce(Minecraft client, Tier tier, String headline, double total) {
        String worth = format(total);
        int colour = TIER_COLOURS[Math.min(Math.max(tier.number() - 1, 0), TIER_COLOURS.length - 1)];

        if (tier.banner()) {
            AlertBanner.show(headline, worth + " coins", "Tier " + tier.number(), colour, BANNER_MILLIS);
        }

        if (tier.toast() && client.getToastManager() != null) {
            client.getToastManager().addToast(new ShokiModToast(
                    Component.literal(headline + " - " + worth), TOAST_MILLIS, null));
        }

        if (tier.chat() && client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "§6" + headline + "§7 - §e" + worth + " coins §8(Tier " + tier.number() + ")"));
        }

        String sound = tier.sound();
        if (sound != null && !sound.isBlank()) {
            CustomSoundPlayer.play(sound, 1.0f, ValueAlertHandler.class);
        }
    }

    /** ENCHANTMENT_FLASH_1 wird zu "Enchantment Flash 1" */
    private static String readableName(String itemId) {
        String[] parts = itemId.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(itemId.length());
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
        }
        return out.isEmpty() ? itemId : out.toString();
    }

    /** 1234567 wird zu "1.2M" - lesbar statt genau */
    private static String format(double coins) {
        if (coins >= 1_000_000_000d) return String.format(Locale.ROOT, "%.1fB", coins / 1_000_000_000d);
        if (coins >= 1_000_000d) return String.format(Locale.ROOT, "%.1fM", coins / 1_000_000d);
        if (coins >= 1_000d) return String.format(Locale.ROOT, "%.1fk", coins / 1_000d);
        return String.format(Locale.ROOT, "%.0f", coins);
    }

    /**
     * "5M", "500k", "1.2B" oder eine blanke Zahl.
     *
     * Dieselbe Schreibweise, die Hypixel selbst verwendet - wer eine Schwelle
     * eintippt, soll sie nicht in Nullen ausschreiben muessen.
     */
    public static double parseAmount(String text) {
        if (text == null) return 0;

        String cleaned = text.trim().replace(",", ".").replace(" ", "");
        if (cleaned.isEmpty()) return 0;

        double factor = 1;
        char last = cleaned.charAt(cleaned.length() - 1);
        switch (Character.toLowerCase(last)) {
            case 'k' -> factor = 1_000d;
            case 'm' -> factor = 1_000_000d;
            case 'b' -> factor = 1_000_000_000d;
            default -> {
            }
        }
        if (factor > 1) cleaned = cleaned.substring(0, cleaned.length() - 1);

        try {
            return Double.parseDouble(cleaned) * factor;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
