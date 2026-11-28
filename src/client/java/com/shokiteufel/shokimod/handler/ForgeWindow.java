package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Liest das Forge-Fenster ("The Forge", {@link ForgeWindowParser}) und schreibt auf, was in Fenstern mit "Forge" im Titel steht.
 *
 * Wunsch (25.11.): Die Zeit soll aus dem Forge-Fenster kommen statt aus der Tab-Liste, denn dort steht sie auf die Sekunde. Seit 2.7.3 tut sie das: Das Fenster wird
 * zehn Ticks nach dem Oeffnen und dann alle zehn Ticks gelesen ({@link #readSlots}), und {@link ForgeTracker#processWindow} legt das Ende auf die Sekunde fest. Der
 * Wortlaut stammt aus SkyOcean/SkyblockAPI (siehe {@link ForgeWindowParser}); wie ein FERTIGER Platz aussieht, ist nicht belegt - dafuer bleibt das Log: Je
 * Fenstertitel mit "Forge" werden die ersten Oeffnungen festgehalten ("[Forge] window ..."), eine Sekunde nach dem Oeffnen, wenn der Server die Plaetze geliefert hat.
 *
 * Laeuft nur mit eingeschalteter Forge (Mining > Forge > Enabled, von Haus aus aus) und kostet ausserhalb solcher Fenster einen Vergleich je Tick.
 */
public final class ForgeWindow {

    /** So viele Oeffnungen je Fenstertitel kommen ins Log - genug, um laufende, fertige und leere Plaetze zu sehen */
    static final int MAX_OPENINGS_PER_TITLE = 4;
    /** Und so viele verschiedene Titel hoechstens, falls "Forge" in vielen Fenstern steht */
    static final int MAX_TITLES = 6;
    /** Ticks zwischen Oeffnen und Aufschreiben: der Server liefert die Plaetze kurz nach dem Fenster */
    static final int WAIT_TICKS = 20;

    private static final Pattern COLOUR_CODE = Pattern.compile("§.");

    private static final Map<String, Integer> openings = new HashMap<>();
    private static String openTitle = null;
    private static int ticksOpen = 0;

    private ForgeWindow() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ForgeWindow::tick);
    }

    /** Erstes Lesen der Plaetze nach dem Oeffnen, dann alle so viele Ticks: Der Server liefert die Plaetze kurz nach dem Fenster, und die Zeit dort laeuft weiter */
    static final int READ_FIRST_TICKS = 10;
    static final int READ_EVERY_TICKS = 10;

    private static boolean reading = false;
    private static int readTicks = 0;

    private static void tick(Minecraft client) {
        if (!FeatureGate.forge() || client == null || client.player == null
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            openTitle = null;
            closeReading();
            return;
        }
        String title = clean(screen.getTitle().getString());
        if (!title.toLowerCase(Locale.ROOT).contains("forge")) {
            openTitle = null;
            closeReading();
            return;
        }
        // Die Zeit der Plaetze kommt aus diesem Fenster (genau, auf die Sekunde) - wo auch immer man es oeffnet
        if (ForgeWindowParser.isForgeTitle(title)) readSlots(client, screen);
        else closeReading();
        if (!title.equals(openTitle)) {
            openTitle = title;
            ticksOpen = 0;
        }
        if (ticksOpen < 0 || ++ticksOpen < WAIT_TICKS) return;
        ticksOpen = -1;   // einmal je Oeffnen

        if (!mayLog(title)) return;
        List<String> lines = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            // Der eigene Rucksack unten im Fenster gehoert nicht zur Forge
            if (slot.container == client.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            String name = clean(stack.getHoverName().getString());
            if (name.isEmpty()) continue;   // Fuellscheiben haben einen leeren Namen
            List<String> lore = new ArrayList<>();
            ItemLore component = stack.get(DataComponents.LORE);
            if (component != null) {
                for (Component line : component.lines()) lore.add(clean(line.getString()));
            }
            lines.add(describe(slot.index, String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())), stack.getCount(), name, lore));
        }
        ShokiMod.LOGGER.info("[Forge] window '{}' with {} item(s):", title, lines.size());
        for (String line : lines) ShokiMod.LOGGER.info("[Forge] window slot {}", line);
    }

    /** Das Fenster ist zu (oder kein Forge-Fenster mehr): Beim naechsten Oeffnen gilt das erste Lesen wieder als frisch */
    private static void closeReading() {
        if (!reading) return;
        reading = false;
        readTicks = 0;
        ForgeTracker.windowClosed();
    }

    /** Die sieben Plaetze des Fensters lesen und dem Merker geben ({@link ForgeTracker#processWindow}) */
    private static void readSlots(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!reading) {
            reading = true;
            readTicks = 0;
        }
        if (readTicks++ < READ_FIRST_TICKS || (readTicks - READ_FIRST_TICKS) % READ_EVERY_TICKS != 0) return;

        int filled = 0;
        List<ForgeWindowParser.Entry> entries = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            // Der eigene Rucksack unten im Fenster gehoert nicht zur Forge
            if (slot.container == client.player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) filled++;
            if (ForgeWindowParser.numberOf(slot.index) == 0) continue;
            boolean hasId = SkyBlockItems.idOf(stack) != null;
            ForgeWindowParser.Entry entry = ForgeWindowParser.read(slot.index, hasId, stack.isEmpty() ? "" : clean(stack.getHoverName().getString()),
                    stack.isEmpty() ? List.of() : SkyBlockItems.loreOf(stack));
            if (entry != null) entries.add(entry);
        }
        ForgeTracker.processWindow(entries, ForgeWindowParser.populated(filled), System.currentTimeMillis());
    }

    /** Darf dieses Fenster noch ins Log? Je Titel hoechstens {@link #MAX_OPENINGS_PER_TITLE} Mal, und hoechstens {@link #MAX_TITLES} Titel */
    static boolean mayLog(String title) {
        Integer bisher = openings.get(title);
        if (bisher == null) {
            if (openings.size() >= MAX_TITLES) return false;
            bisher = 0;
        }
        if (bisher >= MAX_OPENINGS_PER_TITLE) return false;
        openings.put(title, bisher + 1);
        return true;
    }

    /** Eine Zeile fuer das Log: Platz im Fenster, Gegenstand, Name und alle Zeilen darunter, durch " | " getrennt */
    static String describe(int index, String itemId, int count, String name, List<String> lore) {
        StringBuilder out = new StringBuilder();
        out.append(index).append(": ").append(itemId);
        if (count != 1) out.append(" x").append(count);
        out.append(" '").append(name).append('\'');
        for (String line : lore) {
            if (line.isEmpty()) continue;
            out.append(" | ").append(line);
        }
        return out.toString();
    }

    private static String clean(String text) {
        return text == null ? "" : COLOUR_CODE.matcher(text).replaceAll("").trim();
    }

    /** Fuer Tests: das Gedaechtnis der Oeffnungen leeren */
    static void reset() {
        openings.clear();
        openTitle = null;
        ticksOpen = 0;
        reading = false;
        readTicks = 0;
    }
}
