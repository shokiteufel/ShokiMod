package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.EstimatedItemValueCategory;
import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.util.EivCalculator;
import com.shokiteufel.shokimod.util.EivData;
import com.shokiteufel.shokimod.util.EivFormat;
import com.shokiteufel.shokimod.util.EivLive;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimated Item Value: was der Gegenstand unter der Maus wert ist, mit allem, was daran verbaut ist - in einem Kasten auf dem Bild, auf Wunsch zusaetzlich
 * als Zeile im Tooltip, und die Summe eines Satzes im Kleiderschrank (Wardrobe).
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, {@code EstimatedItemValue}, {@code EstimatedItemValueTooltip}, {@code EstimatedWardrobePrice}). Von dort stammen die
 * Regeln: Gerechnet wird, was unter der Maus liegt; der Kasten erscheint immer oder nur mit einer Taste, bei "Hover Delay" erst nach einer Wartezeit auf dem
 * ersten Gegenstand eines Fensters; es steht nichts da, wenn der Gegenstand nur seinen Grundpreis hat; Buecher, Runen, Dungeon-Paesse, Karten und ein
 * paar andere haben keine Schaetzung. Im Kleiderschrank (Fenster "(1/3) Armor Sets" und "(1/2) Equipment Sets") steht unter dem Knopf eines Satzes jedes
 * der vier Stuecke mit Wert und die Summe. Die Umsetzung ist eigene Arbeit: Java, der Gegenstand unter der Maus wird im Tick gelesen und alle drei Sekunden
 * neu gerechnet (die Preise bewegen sich), der Kasten ist ein Kasten des HUD-Editors, eigener Wortlaut.
 */
public final class EstimatedItemValue {

    private static final long RECOMPUTE_MILLIS = 3_000L;
    private static final Pattern ARMOR_TITLE = Pattern.compile("\\(\\d+/\\d+\\) Armor Sets");
    private static final Pattern EQUIPMENT_TITLE = Pattern.compile("\\(\\d+/\\d+\\) Equipment Sets");
    /** Im Kleiderschrank liegen die vier Stuecke eines Satzes in einer Spalte (0, 9, 18, 27 plus Spalte), der Knopf darunter (36 plus Spalte) */
    private static final int WARDROBE_BUTTONS = 36;

    private static List<String> display = List.of();
    private static ItemStack hovered = ItemStack.EMPTY;
    private static long hoverStart = 0L;
    private static long computedAt = 0L;
    private static boolean hoverDelayOver = false;
    private static boolean hotkeyHeld = false;
    private static boolean failedOnce = false;
    private static long tickClock = 0L;

    // Das Ergebnis fuer die Tooltip-Zeile: der Tooltip wird bei jedem Bild gebaut, gerechnet wird aber nur alle drei Sekunden
    private static ItemStack tooltipStack = ItemStack.EMPTY;
    private static long tooltipAt = 0L;
    private static Double tooltipTotal = null;

    private EstimatedItemValue() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EstimatedItemValue::tick);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> onTooltip(stack, lines));
    }

    private static EstimatedItemValueCategory cfg() {
        return ModConfig.INSTANCE.inventory.itemValue;
    }

    // ------------------------------------------------------------------ reine Teile

    /** Die Einstellungen, wie der Rechner sie braucht */
    static EivCalculator.Options options(EstimatedItemValueCategory c) {
        return new EivCalculator.Options(c.priceSource, c.enchantmentsCap, c.starMaterialCap, c.ignoreHelmetSkins, c.ignoreArmorDyes, c.ignoreRunes);
    }

    /** Hat dieser Gegenstand keine Schaetzung? ({@code title}: das offene Fenster, {@code inPlayerInventory}: ob er im eigenen Inventar liegt) */
    static boolean ignored(EivCalculator.Subject s, String title, boolean inPlayerInventory) {
        if (s == null) return true;
        String id = s.id();
        String name = s.hoverName() == null ? "" : s.hoverName();
        if ("ENCHANTED_BOOK".equals(s.rawId())) return true;
        if (name.contains("Salesperson") || name.equals("☘ Category: Item Ability (Passive)")) return true;
        if (id.endsWith("_RUNE") || id.contains("_RUNE;") || "RUNE".equals(s.rawId()) || id.contains("UNIQUE_RUNE")) return true;
        if (id.startsWith("ULTIMATE_ULTIMATE_") || id.startsWith("CATACOMBS_PASS_") || id.startsWith("MASTER_CATACOMBS_PASS_") || id.startsWith("MAP-")) return true;
        if (id.contains("WISP_POTION")) return true;
        if (!inPlayerInventory && "Choose a wardrobe slot".equals(title)) return true;
        if (title != null && title.startsWith("Museum ")) {
            for (String line : s.lore()) {
                if (line != null && line.contains("Armor Set")) return true;
            }
        }
        if ("Island Deliveries".equals(title)) {
            for (String line : s.lore()) {
                if (line != null && line.equals("Click to collect!")) return true;
            }
        }
        return false;
    }

    /** Die Zeilen des Kastens fuer ein Ergebnis: Ueberschrift, die Posten, die Summe - oder leer, wenn der Gegenstand nur seinen Grundpreis hat */
    static List<String> displayOf(EivCalculator.Result r, boolean exactPrice) {
        if (r.base() == r.total()) return List.of();
        List<String> out = new ArrayList<>(r.lines().size() + 2);
        out.add("§aEstimated Item Value:");
        out.addAll(r.lines());
        String total = exactPrice ? EivFormat.withSeparators(r.total()) : EivFormat.shortFormat(r.total());
        out.add("§aTotal: §6§l" + total + " coins");
        return out;
    }

    /** Die Zeilen unter dem Knopf eines Satzes im Kleiderschrank: je Stueck "  - Name: Wert", dann die Summe; leer, wenn alle vier Plaetze leer sind */
    static List<String> wardrobeLines(String valueName, List<String> pieceNames, List<Double> pieceTotals) {
        List<String> out = new ArrayList<>();
        double total = 0.0;
        for (int i = 0; i < pieceNames.size(); i++) {
            Double value = pieceTotals.get(i);
            if (value == null) continue;
            out.add("  §7- " + pieceNames.get(i) + "§7: §6" + EivFormat.shortFormat(value));
            total += value;
        }
        if (out.isEmpty()) return List.of();
        List<String> all = new ArrayList<>(out.size() + 2);
        all.add("§aEstimated " + valueName + " Value:");
        all.addAll(out);
        if (total != 0.0) all.add(" §aTotal Value: §6§l" + EivFormat.shortFormat(total) + " coins");
        return all;
    }

    // ------------------------------------------------------------------ Zustand

    /** Die Zeilen fuer den Kasten, so wie sie jetzt gelten (leer, wenn nichts anzuzeigen ist) */
    public static List<String> display() {
        return display;
    }

    public static long tickTime() {
        return tickClock;
    }

    /** Soll der Kasten jetzt gezeichnet werden? Mit Taste oder immer, je nach Einstellung, und erst nach der Wartezeit */
    public static boolean visible() {
        EstimatedItemValueCategory c = cfg();
        if (!c.enabled || display.isEmpty()) return false;
        if (!hotkeyHeld && !c.alwaysEnabled) return false;
        if (hotkeyHeld) return true;
        if (hoverDelayOver || c.hoverDelay <= 0) return true;
        if (System.currentTimeMillis() - hoverStart >= c.hoverDelay) {
            hoverDelayOver = true;
            return true;
        }
        return false;
    }

    private static void reset() {
        display = List.of();
        hovered = ItemStack.EMPTY;
        hoverDelayOver = false;
        hotkeyHeld = false;
        tooltipStack = ItemStack.EMPTY;
        tooltipTotal = null;
    }

    // ------------------------------------------------------------------ Tick

    private static void tick(Minecraft client) {
        tickClock++;
        if (!FeatureGate.estimatedItemValue()) {
            if (!display.isEmpty() || hovered != ItemStack.EMPTY) reset();
            return;
        }
        // Die Tabellen holen (im Hintergrund, hoechstens einmal je Stand)
        if (tickClock % 100 == 1 && client.player != null && GameState.Server.isSkyblock()) EivData.prefetch();

        EstimatedItemValueCategory c = cfg();
        if (!c.enabled || client.player == null || !GameState.Server.isSkyblock()
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            if (!display.isEmpty() || hovered != ItemStack.EMPTY) reset();
            return;
        }
        hotkeyHeld = c.hasHotkey() && ShortcutHandler.isDown(c.hotkey);

        Slot slot = ((AbstractContainerScreenAccessor) screen).shokimod$hoveredSlot();
        ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
        if (stack.isEmpty()) {
            display = List.of();
            hovered = ItemStack.EMPTY;
            return;
        }
        long now = System.currentTimeMillis();
        boolean same = hovered != ItemStack.EMPTY && ItemStack.isSameItemSameComponents(stack, hovered);
        if (same && now - computedAt < RECOMPUTE_MILLIS) return;
        if (!same) hoverStart = now;
        hovered = stack;
        computedAt = now;
        String title = screen.getTitle().getString().replaceAll("§.", "").trim();
        display = compute(stack, title, slot.container instanceof Inventory);
    }

    private static List<String> compute(ItemStack stack, String title, boolean inPlayerInventory) {
        try {
            EivCalculator.Subject subject = EivLive.subjectOf(stack);
            if (ignored(subject, title, inPlayerInventory)) return List.of();
            EstimatedItemValueCategory c = cfg();
            EivCalculator.Result result = new EivCalculator(EivLive.INSTANCE, options(c)).calculate(subject);
            return displayOf(result, c.exactPrice);
        } catch (RuntimeException e) {
            // Ein Gegenstand mit seltsamen Daten darf den Tick nicht brechen; einmal ins Log
            if (!failedOnce) {
                failedOnce = true;
                com.shokiteufel.shokimod.ShokiMod.LOGGER.warn("[ShokiMod] Estimated Item Value failed for {}: {}", stack.getHoverName().getString(), e.toString());
            }
            return List.of();
        }
    }

    // ------------------------------------------------------------------ Tooltip

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (lines == null || !FeatureGate.estimatedItemValue() || !GameState.Server.isSkyblock()) return;
        EstimatedItemValueCategory c = cfg();
        if (c.showTooltip) {
            Double total = tooltipTotalOf(stack);
            if (total != null) lines.add(Component.literal("§e§lEstimated Value: §6§l" + EivFormat.withSeparators(total) + " coins"));
        }
        if (c.armor || c.equipment) addWardrobe(lines, c);
    }

    /** Der Wert fuer die Tooltip-Zeile, oder null, wenn der Gegenstand nur seinen Grundpreis hat; alle drei Sekunden neu */
    private static Double tooltipTotalOf(ItemStack stack) {
        long now = System.currentTimeMillis();
        if (tooltipStack != ItemStack.EMPTY && stack == tooltipStack && now - tooltipAt < RECOMPUTE_MILLIS) return tooltipTotal;
        tooltipStack = stack;
        tooltipAt = now;
        tooltipTotal = null;
        try {
            EivCalculator.Subject subject = EivLive.subjectOf(stack);
            if (subject == null) return null;
            EivCalculator.Result result = new EivCalculator(EivLive.INSTANCE, options(cfg())).calculate(subject);
            if (result.total() != result.base()) tooltipTotal = result.total();
        } catch (RuntimeException e) {
            tooltipTotal = null;
        }
        return tooltipTotal;
    }

    private static boolean isGlassPane(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("glass_pane");
    }

    private static void addWardrobe(List<Component> lines, EstimatedItemValueCategory c) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        String title = screen.getTitle().getString().replaceAll("§.", "").trim();
        String valueName;
        if (c.armor && ARMOR_TITLE.matcher(title).find()) valueName = "Armor";
        else if (c.equipment && EQUIPMENT_TITLE.matcher(title).find()) valueName = "Equipment";
        else return;
        Slot hoveredSlot = ((AbstractContainerScreenAccessor) screen).shokimod$hoveredSlot();
        if (hoveredSlot == null || hoveredSlot.container instanceof Inventory) return;
        int column = hoveredSlot.index - WARDROBE_BUTTONS;
        if (column < 0 || column > 8) return;

        List<Slot> slots = screen.getMenu().slots;
        List<String> names = new ArrayList<>();
        List<Double> totals = new ArrayList<>();
        EivCalculator calculator = new EivCalculator(EivLive.INSTANCE, options(c));
        for (int row = 0; row < 4; row++) {
            int at = column + 9 * row;
            if (at >= slots.size()) continue;
            ItemStack piece = slots.get(at).getItem();
            if (piece.isEmpty() || isGlassPane(piece)) continue;
            EivCalculator.Subject subject = EivLive.subjectOf(piece);
            if (subject == null) continue;
            try {
                names.add(ScoreboardUtils.toLegacyString(piece.getHoverName()));
                totals.add(calculator.calculate(subject).total());
            } catch (RuntimeException e) {
                names.remove(names.size() - 1);
            }
        }
        List<String> out = wardrobeLines(valueName, names, totals);
        if (out.isEmpty()) return;
        lines.add(Component.empty());
        for (String line : out) lines.add(Component.literal(line));
    }
}
