package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.BazaarIds;
import com.shokiteufel.shokimod.util.ItemValue;
import com.shokiteufel.shokimod.util.ShopPrices;
import com.shokiteufel.shokimod.util.ShopValue;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NPC Shop Values: liest das Fenster eines Haendlers (SkyMart, Pesthunter's Wares, Fear Mongerer, Agatha's Shop, Miria's Shop) und rechnet je Waehrung die Liste aus
 * ({@link ShopValue}); den Kasten daneben zeichnet {@code ShopValueHud}.
 *
 * Eigene Funktion, keine der 26.3-Umstellung: gefragt wird nur der eigene Schalter ({@link FeatureGate#shopValue()}). Gelesen wird beim Oeffnen jeden Tick, bis die Plaetze
 * gefuellt sind, danach alle zehn Ticks - die Preise bewegen sich langsam, die Beschreibungen gar nicht. Eine Seite des Fensters ist ein eigenes Fenster: "(1/2)" und
 * "(2/2)" werden je einzeln gerechnet, die Liste gilt fuer das, was man gerade sieht.
 */
public final class ShopValues {

    private static final int TICKS_BETWEEN_READS = 10;
    private static final int TICKS_BETWEEN_PRICE_LOADS = 200;
    /** "Enchanted Seeds x16": die Menge steht manchmal im Namen */
    private static final Pattern NAME_AMOUNT = Pattern.compile("^(.+?)\\s+x(\\d[\\d,]*)$");

    private static Object openScreen = null;
    private static String openTitle = null;
    private static ShopValue.Shop shop = null;
    private static volatile List<ShopValue.Section> sections = List.of();
    private static int ticks = TICKS_BETWEEN_READS;
    private static int priceTicks = 0;
    private static boolean sawItems = false;
    private static boolean logged = false;
    private static boolean failedOnce = false;

    private ShopValues() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ShopValues::tick);
    }

    // ------------------------------------------------------------------ Fragen an den Zustand

    /** Ist gerade das Fenster eines Haendlers mit einer Liste offen? */
    public static boolean active() {
        return shop != null;
    }

    /** Die Listen zum offenen Fenster, eine je Waehrung; leer, solange noch nicht gelesen wurde */
    public static List<ShopValue.Section> sections() {
        return sections;
    }

    // ------------------------------------------------------------------ Tick

    private static void tick(Minecraft client) {
        if (!FeatureGate.shopValue() || client.player == null || !GameState.Server.isSkyblock()
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            leave();
            return;
        }
        String title = screen.getTitle().getString();
        if (screen != openScreen || !title.equals(openTitle)) {
            openScreen = screen;
            openTitle = title;
            shop = ShopValue.shopOf(title);
            sections = List.of();
            sawItems = false;
            logged = false;
            ticks = TICKS_BETWEEN_READS;
            if (shop != null) {
                ItemValue.prefetch();
                priceTicks = 0;
            }
        }
        if (shop == null) return;
        // Die Preislisten holen sich selbst nach, wenn sie alt sind; hier wird nur dafuer gesorgt, dass sie angefragt werden
        if (++priceTicks >= TICKS_BETWEEN_PRICE_LOADS) {
            priceTicks = 0;
            ItemValue.prefetch();
        }
        if (sawItems && ++ticks < TICKS_BETWEEN_READS) return;
        ticks = 0;
        try {
            read(screen);
        } catch (RuntimeException e) {
            if (!failedOnce) {
                failedOnce = true;
                ShokiMod.LOGGER.warn("[ShopValue] reading the shop window failed: {}", e.toString());
            }
        }
    }

    private static void leave() {
        if (openScreen == null && shop == null) return;
        openScreen = null;
        openTitle = null;
        shop = null;
        sections = List.of();
        sawItems = false;
    }

    // ------------------------------------------------------------------ Lesen

    private static void read(AbstractContainerScreen<?> screen) {
        List<ShopValue.Offer> offers = new ArrayList<>();
        int items = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            items++;
            ShopValue.Offer offer = offerOf(stack);
            if (offer != null) offers.add(offer);
        }
        // Die Plaetze fuellen sich erst nach dem Oeffnen: noch nichts da, nichts zu rechnen
        if (items == 0) return;
        sawItems = true;

        ModConfig.ShopValueCategory c = ModConfig.INSTANCE.inventory.shopValue;
        ShopPrices prices = new ShopPrices(c.source == ModConfig.ShopValueSource.SELL_OFFER);
        List<ShopValue.Section> out = new ArrayList<>(shop.currencies().size());
        int ranked = 0;
        for (ShopValue.Currency currency : shop.currencies()) {
            List<ShopValue.Row> rows = ShopValue.rank(currency, offers, prices, c.subtractMaterials, c.entries);
            ranked += rows.size();
            out.add(new ShopValue.Section(currency, rows));
        }
        sections = out;
        // Einmal je Fenster ins Log: wie viel gelesen und wie viel davon bepreist wurde. Steht dort "0 with a cost", passt der Wortlaut der Kosten nicht; "0 listed" bei
        // vielen Kosten heisst: keine Preise
        if (!logged) {
            logged = true;
            ShokiMod.LOGGER.info("[ShopValue] {}: {} items, {} with a cost, {} listed", openTitle, items, offers.size(), ranked);
        }
    }

    // ------------------------------------------------------------------ Stueck -> Angebot

    /** Das Angebot hinter einem Stueck im Fenster, oder null, wenn es keine Kostenzeile hat (Glasscheiben, Knoepfe, Seitenpfeile) */
    static ShopValue.Offer offerOf(ItemStack stack) {
        List<ShopValue.Cost> costs = ShopValue.readCosts(SkyBlockItems.loreOf(stack));
        if (costs.isEmpty()) return null;

        String raw = BazaarHelpers.clean(stack.getHoverName().getString());
        String name = raw;
        int amount = Math.max(1, stack.getCount());
        Matcher m = NAME_AMOUNT.matcher(raw);
        if (m.matches()) {
            name = m.group(1);
            try {
                amount = Math.max(1, Integer.parseInt(m.group(2).replace(",", "")));
            } catch (NumberFormatException e) {
                // bleibt bei der Anzahl des Stapels
            }
        }

        // Erst die Kennung des Stuecks, dann was der Name hergibt: Hat die Kennung keinen Preis, kann es der Name noch haben
        Set<String> ids = new LinkedHashSet<>();
        String id = SkyBlockItems.idOf(stack);
        if (id != null) ids.addAll(SkyBlockItems.priceCandidates(id));
        ids.addAll(BazaarIds.candidates(name));
        return new ShopValue.Offer(name, legacyColour(SkyBlockItems.nameColour(stack)), amount, List.copyOf(ids), costs);
    }

    /** Die sechzehn Farben der Farbcodes 0 bis f, in dieser Reihenfolge */
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** Der Farbcode ("§9") zur Farbe eines Namens - die nach Farbabstand naechste der sechzehn; ohne Farbe "§f" */
    static String legacyColour(int argb) {
        if (argb == 0) return "§f";
        int rgb = argb & 0xFFFFFF;
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            long dr = ((rgb >> 16) & 0xFF) - ((LEGACY_RGB[i] >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((LEGACY_RGB[i] >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (LEGACY_RGB[i] & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return "§" + Character.forDigit(best, 16);
    }
}
