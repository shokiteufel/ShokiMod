package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.ShokiMod;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Was eine Belohnungstruhe aus einem Dungeon oder von Kuudra gebracht und gekostet hat.
 *
 * Gelesen wird in dem Augenblick, in dem man auf "Open Reward Chest" klickt: Die Beute
 * liegt dann als Gegenstaende in der Truhe, der Preis steht in der Lore des Knopfes.
 * So machen es auch Skyblocker und SkyBlockAddons - die zeigen es nur vorher an, statt es
 * zu buchen (nachgelesen im Bytecode, kein Code uebernommen).
 *
 * Gebucht wird die Beute als Zugang, die Coins der Truhe als Ausgabe und der Dungeon
 * Chest Key, wenn die Truhe einen braucht, als verbrauchte Ware. Der Inventar-Vergleich
 * sieht davon nichts: Die Beute kommt durch das Truhen-Fenster, die Coins sind kein Item.
 */
public final class DungeonChestProfit {

    /** Der Knopf, der die Truhe oeffnet */
    private static final String OPEN_BUTTON = "Open Reward Chest";
    /** Knoepfe und Fuellmaterial in der Truhe: keine Beute */
    private static final java.util.Set<String> NOT_LOOT = java.util.Set.of(
            "Open Reward Chest", "Reroll Chest", "Close", "Go Back", "Rewards", "Reward Chest");
    private static final Pattern COINS = Pattern.compile("^([0-9][0-9,]*) Coins?$");
    private static final Pattern ESSENCE = Pattern.compile("^(?<type>[A-Za-z]+) Essence(?: x(?<amount>[0-9]+))?$");
    private static final Pattern KUUDRA_KEY = Pattern.compile("(?:(Hot|Burning|Fiery|Infernal) )?Kuudra Key");

    /** Zwei Klicks auf denselben Knopf kurz hintereinander sind einer */
    private static long lastBookedAt = 0L;
    private static final long DEBOUNCE_MILLIS = 2_500L;

    private DungeonChestProfit() {
    }

    /** Vom Klick-Mixin: ein Platz im offenen Fenster wurde angeklickt */
    public static void onSlotClick(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || screen == null || !GameState.Server.isSkyblock()) return;
        ItemStack clicked = slot.getItem();
        if (clicked == null || clicked.isEmpty()) return;
        if (!clicked.getHoverName().getString().trim().equals(OPEN_BUTTON)) return;
        if (!ProfitTracker.enabled()) return;

        long now = System.currentTimeMillis();
        if (now - lastBookedAt < DEBOUNCE_MILLIS) return;
        lastBookedAt = now;

        Map<String, Integer> loot = new LinkedHashMap<>();
        for (Slot other : screen.getMenu().slots) {
            // Nur die Truhe, nicht das eigene Inventar darunter
            if (other.container instanceof Inventory) continue;
            ItemStack stack = other.getItem();
            if (stack == null || stack.isEmpty() || other == slot) continue;
            collect(stack, loot);
        }

        long coins = 0L;
        String key = null;
        boolean costSeen = false;
        ItemLore lore = clicked.get(DataComponents.LORE);
        StringBuilder text = new StringBuilder();
        if (lore != null) {
            boolean inCost = false;
            for (net.minecraft.network.chat.Component line : lore.lines()) {
                String plain = line.getString().trim();
                text.append(plain).append(" | ");
                if (plain.equals("Cost")) {
                    inCost = true;
                    costSeen = true;
                    continue;
                }
                if (plain.isEmpty()) {
                    inCost = false;
                    continue;
                }
                if (plain.contains("Dungeon Chest Key")) key = "DUNGEON_CHEST_KEY";
                Matcher kuudra = KUUDRA_KEY.matcher(plain);
                if (kuudra.find()) key = kuudraKeyId(kuudra.group(1));
                if (inCost) {
                    Matcher c = COINS.matcher(plain);
                    if (c.matches()) coins = Long.parseLong(c.group(1).replace(",", ""));
                }
            }
        }

        ShokiMod.LOGGER.info("[DungeonChest] \"{}\" opened: loot={} cost={} coins key={}{}",
                screen.getTitle().getString(), loot, coins, key,
                costSeen ? "" : " (NO cost line found - lore: " + text + ")");
        ProfitTracker.chestOpened(loot, coins, key);
    }

    /** Eine Zeile Beute: Essence nach dem Namen, alles andere nach der SkyBlock-Kennung */
    private static void collect(ItemStack stack, Map<String, Integer> loot) {
        String name = stack.getHoverName().getString().trim();
        if (NOT_LOOT.contains(name)) return;
        if (isFiller(stack)) return;

        Matcher essence = ESSENCE.matcher(name);
        if (essence.matches()) {
            int amount = essence.group("amount") != null ? Integer.parseInt(essence.group("amount")) : stack.getCount();
            loot.merge("ESSENCE_" + essence.group("type").toUpperCase(Locale.ROOT), amount, Integer::sum);
            return;
        }
        String id = SkyBlockItems.idOf(stack);
        if (id == null || id.equals("SKYBLOCK_MENU")) return;
        loot.merge(id, Math.max(1, stack.getCount()), Integer::sum);
    }

    /** Glasscheiben und Barrieren fuellen die Luecken im Fenster */
    private static boolean isFiller(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.endsWith("glass_pane") || path.equals("barrier");
    }

    private static String kuudraKeyId(String tier) {
        if (tier == null) return "KUUDRA_TIER_KEY";
        return "KUUDRA_" + tier.toUpperCase(Locale.ROOT) + "_TIER_KEY";
    }
}
