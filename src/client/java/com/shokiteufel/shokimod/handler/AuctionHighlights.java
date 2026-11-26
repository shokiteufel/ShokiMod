package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.ColourText;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.EivCalculator;
import com.shokiteufel.shokimod.util.EivData;
import com.shokiteufel.shokimod.util.EivLive;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inventory > Auction house > Highlight Auctions und Highlight Underbid Auctions: Im Fenster "Manage Auctions" werden die eigenen Auktionen eingefaerbt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code AuctionsHighlighter}). Von dort stammt die Regel: Im Fenster mit dem Titel "Manage Auctions" bekommt jeder eigene
 * Eintrag eine Farbe nach seiner Beschreibung - "Status: Sold!" die Farbe fuer Verkauft, "Status: Expired!" die fuer Abgelaufen; sonst, bei eingeschaltetem
 * Underbid, die Zeile "Buy it now: N coins": ist N groesser als der geschaetzte Wert des Stuecks (die Summe von Estimated Item Value), ist die Auktion ueberboten
 * (SkyHanni: "underbid own lowest BIN auctions that are outbid"). Ein Stueck ohne bekannten Wert wird nicht eingefaerbt.
 *
 * Die Umsetzung ist eigene Arbeit: Java, die Entscheidung je Platz ist ein reiner Wert ({@link #markOf}) ueber die Beschreibung ohne Farbcodes, gezeichnet wird
 * ueber {@link WindowHighlights} (freie Farben, Einsprung vor dem Stueck), der Wert kommt aus dem Rechner von Estimated Item Value ({@link EivCalculator}) und wird
 * nur dort gerechnet, wo die Beschreibung einen Sofortkaufpreis nennt. Drei Abweichungen von SkyHanni: "Highlight Underbid Auctions" gilt auch ohne "Highlight
 * Auctions" (bei SkyHanni liegt es innerhalb davon), der geschaetzte Wert wird mit der Stapelgroesse malgenommen (der Sofortkaufpreis gilt fuer den ganzen Stapel),
 * und ein Wert von null oder weniger (kein Preis bekannt) zaehlt als nicht zu bestimmen.
 */
public final class AuctionHighlights {

    /** Der Titel des Fensters mit den eigenen Auktionen */
    static final String TITLE = "Manage Auctions";
    static final String STATUS_SOLD = "Status: Sold!";
    static final String STATUS_EXPIRED = "Status: Expired!";
    private static final Pattern BUY_IT_NOW = Pattern.compile("^Buy it now: ([\\d,]+) coins$");
    /** Alle zehn Ticks neu lesen: Die Plaetze kommen nach dem Oeffnen nach und nach, und die Preise bewegen sich langsam */
    private static final int TICKS_BETWEEN_READS = 10;

    /** Wie ein Platz markiert wird */
    enum Mark {
        NONE,
        SOLD,
        EXPIRED,
        UNDERBID
    }

    private static boolean open = false;
    private static int ticks = 0;
    private static boolean failedOnce = false;

    private AuctionHighlights() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AuctionHighlights::tick);
    }

    private static ModConfig.AuctionHouseHelpersCategory cfg() {
        return ModConfig.INSTANCE.inventory.auctions;
    }

    // ------------------------------------------------------------------ reine Teile

    /** Der Sofortkaufpreis aus der Beschreibung ("Buy it now: 1,000,000 coins"), oder -1. Die Zeilen sind ohne Farbcodes */
    static long buyItNowIn(List<String> lore) {
        if (lore == null) return -1L;
        for (String line : lore) {
            if (line == null) continue;
            Matcher m = BUY_IT_NOW.matcher(line.trim());
            if (!m.matches()) continue;
            try {
                return Long.parseLong(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return -1L;
    }

    /**
     * Wie die Auktion mit dieser Beschreibung markiert wird. Zuerst der Status (verkauft vor abgelaufen), und nur wenn die zugehoerige Farbe an ist; eine verkaufte
     * oder abgelaufene Auktion ist nie "ueberboten". Sonst, bei eingeschaltetem Underbid, der Sofortkaufpreis gegen den geschaetzten Wert.
     *
     * @param lore      die Beschreibung, Zeile fuer Zeile ohne Farbcodes
     * @param statuses  ob Verkauft und Abgelaufen eingefaerbt werden (Highlight Auctions)
     * @param underbid  ob Ueberbotene eingefaerbt werden (Highlight Underbid Auctions)
     * @param estimate  der geschaetzte Wert des Stuecks in Coins, oder null, wenn er nicht zu bestimmen ist; wird nur gefragt, wenn die Beschreibung einen
     *                  Sofortkaufpreis nennt und Underbid an ist
     */
    static Mark markOf(List<String> lore, boolean statuses, boolean underbid, Supplier<Double> estimate) {
        if (lore == null || lore.isEmpty() || (!statuses && !underbid)) return Mark.NONE;
        boolean sold = false;
        boolean expired = false;
        for (String line : lore) {
            if (line == null) continue;
            String t = line.trim();
            if (t.equals(STATUS_SOLD)) sold = true;
            else if (t.equals(STATUS_EXPIRED)) expired = true;
        }
        if (sold) return statuses ? Mark.SOLD : Mark.NONE;
        if (expired) return statuses ? Mark.EXPIRED : Mark.NONE;
        if (!underbid) return Mark.NONE;
        long price = buyItNowIn(lore);
        if (price < 0L) return Mark.NONE;
        Double value = estimate == null ? null : estimate.get();
        // Gleich ist nicht ueberboten; ohne Wert wird nicht eingefaerbt
        return value != null && price > value ? Mark.UNDERBID : Mark.NONE;
    }

    /** Die Farbe (RGB) zu einer Markierung aus den Einstellungen; bei {@link Mark#NONE} -1 */
    static int rgbOf(Mark mark, ModConfig.AuctionHouseHelpersCategory c) {
        return switch (mark) {
            case SOLD -> ColourText.rgb(c.soldColour, 0x55FF55);
            case EXPIRED -> ColourText.rgb(c.expiredColour, 0xFF5555);
            case UNDERBID -> ColourText.rgb(c.underbidColour, 0xFFAA00);
            case NONE -> -1;
        };
    }

    static boolean isManageTitle(String title) {
        return TITLE.equals(title);
    }

    // ------------------------------------------------------------------ Tick

    private static void tick(Minecraft client) {
        if (!FeatureGate.auctionHighlights() || client.player == null || !GameState.Server.isSkyblock()
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            leave();
            return;
        }
        String title = screen.getTitle().getString().replaceAll("§.", "").trim();
        if (!isManageTitle(title)) {
            leave();
            return;
        }
        if (open && ++ticks < TICKS_BETWEEN_READS) return;
        open = true;
        ticks = 0;
        read(screen);
    }

    /** Das Fenster ist zu (oder die Funktion aus): die Markierungen gehen weg */
    private static void leave() {
        if (open || WindowHighlights.colourCount() > 0) {
            open = false;
            ticks = 0;
            WindowHighlights.clearColours();
        }
    }

    /** Das Fenster lesen und jeden Platz neu markieren */
    private static void read(AbstractContainerScreen<?> screen) {
        ModConfig.AuctionHouseHelpersCategory c = cfg();
        WindowHighlights.clearColours();
        if (c.highlightUnderbid) {
            // Der Rechner und die Preislisten holen sich selbst nach, wenn sie alt sind
            EivData.prefetch();
            ItemValue.prefetch();
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Mark mark = markOf(SkyBlockItems.loreOf(stack), c.highlightAuctions, c.highlightUnderbid, () -> estimatedValue(stack));
            int rgb = rgbOf(mark, c);
            if (rgb >= 0) WindowHighlights.markColour(slot.index, 0xFF000000 | rgb);
        }
    }

    /** Der geschaetzte Wert des Stuecks (Summe von Estimated Item Value mal Stapelgroesse), oder null, wenn er nicht zu bestimmen ist */
    private static Double estimatedValue(ItemStack stack) {
        try {
            EivCalculator.Subject subject = EivLive.subjectOf(stack);
            if (subject == null) return null;
            EivCalculator calculator = new EivCalculator(EivLive.INSTANCE, EstimatedItemValue.options(ModConfig.INSTANCE.inventory.itemValue));
            double total = calculator.calculate(subject).total();
            return total > 0.0 ? total * Math.max(1, stack.getCount()) : null;
        } catch (RuntimeException e) {
            // Ein Gegenstand mit seltsamen Daten darf den Tick nicht brechen; einmal ins Log
            if (!failedOnce) {
                failedOnce = true;
                ShokiMod.LOGGER.warn("[ShokiMod] Highlight Underbid Auctions failed for {}: {}", stack.getHoverName().getString(), e.toString());
            }
            return null;
        }
    }
}
