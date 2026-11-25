package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.FeatureGate;

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
 * Schreibt auf, was im Forge-Fenster steht - nur ins Log, die Forge selbst liest noch aus der Tab-Liste.
 *
 * Wunsch (25.11.): Die Zeit soll aus dem Forge-Fenster kommen statt aus der Tab-Liste, denn dort steht sie auf die Sekunde. Wie das Fenster
 * seine Plaetze beschreibt (Titel, Name und Zeilen eines laufenden, eines fertigen und eines leeren Platzes), kennt keine offene Mod und kein
 * Wiki: Die Mod raet es nicht, sondern schreibt es einmal auf. Je Fenstertitel mit "Forge" werden die ersten Oeffnungen festgehalten
 * ("[Forge] window ..."), eine Sekunde nach dem Oeffnen, wenn der Server die Plaetze geliefert hat.
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

    private static void tick(Minecraft client) {
        if (!FeatureGate.forge() || client == null || client.player == null
                || !(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            openTitle = null;
            return;
        }
        String title = clean(screen.getTitle().getString());
        if (!title.toLowerCase(Locale.ROOT).contains("forge")) {
            openTitle = null;
            return;
        }
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
    }
}
