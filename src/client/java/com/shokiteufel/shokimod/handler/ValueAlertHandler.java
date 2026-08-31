package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
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
 * Umsortieren ist kein Fund: solange ein Fenster offen ist und kurz danach bleibt
 * der Alarm still. Sonst meldete jede Truhe, jeder Sack und jedes Aufraeumen einen
 * "Fund", nur weil dieselben Items den Platz gewechselt haben.
 */
public final class ValueAlertHandler {

    /** So lange nach dem Schliessen eines Fensters gilt noch alles als verschoben */
    private static final long QUIET_AFTER_SCREEN_MILLIS = 1000L;
    private static final long BANNER_MILLIS = 3000L;
    private static final long TOAST_MILLIS = 5000L;
    private static final int BANNER_COLOUR = 0xFFD700;

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
        ModConfig.ChatRulesCategory cfg = ModConfig.INSTANCE.chat;
        // Ist der Alarm aus, wird nichts gezaehlt, nichts nachgeschlagen und nichts
        // aus dem Netz geholt. Der gemerkte Stand faellt weg, damit auch kein
        // Speicher liegen bleibt
        if (!cfg.valueAlert) {
            if (primed) reset();
            return;
        }

        if (client.player == null || !GameState.Server.isSkyblock()) {
            if (primed) reset();
            return;
        }

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
        double threshold = parseAmount(ModConfig.INSTANCE.chat.valueAlertThreshold);
        if (threshold <= 0 || total < threshold) return;

        announce(client, itemId, amount, total);
    }

    private static void announce(Minecraft client, String itemId, int amount, double total) {
        ModConfig.ChatRulesCategory cfg = ModConfig.INSTANCE.chat;
        String name = readableName(itemId);
        String worth = format(total);
        String headline = amount > 1 ? name + " x" + amount : name;

        if (cfg.valueAlertBanner) {
            AlertBanner.show(headline, worth + " coins", "", BANNER_COLOUR, BANNER_MILLIS);
        }

        if (cfg.valueAlertToast && client.getToastManager() != null) {
            client.getToastManager().addToast(new ShokiModToast(
                    Component.literal(headline + " - " + worth), TOAST_MILLIS, null));
        }

        if (cfg.valueAlertChat && client.player != null) {
            client.player.sendSystemMessage(
                    Component.literal("§6" + headline + "§7 - §e" + worth + " coins"));
        }

        String sound = cfg.valueAlertSound;
        if (sound != null && !sound.isBlank()) {
            CustomSoundPlayer.play(sound, 1.0f, ValueAlertHandler.class);
        }
    }

    /** ENCHANTED_DIAMOND_BLOCK wird zu "Enchanted Diamond Block" */
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
