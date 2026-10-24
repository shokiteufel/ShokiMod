package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.mixin.AbstractContainerScreenAccessor;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ein Item aus dem Inventar in den Profit Tracker nehmen: "Add" in der Ueberschrift des Kastens, dann einen Stapel anklicken.
 *
 * Der Tracker zaehlt, was ins Inventar kommt - aber nicht, was durch ein Fenster hereinkommt (Truhe, NPC, Craft, Auktionshaus), und
 * die Auswahlliste zeigt nur, was seit dem Reset angekommen ist. Was der Tracker also nicht bekommen hat, laesst sich hier von Hand
 * nachtragen, ohne die Menge zu erraten: Man sieht ja, was im Inventar liegt.
 *
 * Der Weg ist der von Skysofts Profit Tracker ("Add Item", Betriebsart "Inventory": ein Item im eigenen Inventar anklicken,
 * ProfitTrackerHudControls.wasInventoryItemAdded, LGPL-3.0, Akinsoft). Gebucht wird anders: Skysoft nimmt das Item in eine feste Liste
 * auf, hier zaehlt ohnehin alles - also kommt der Stapel als Fund in den Kasten und wird sichtbar gemacht (siehe
 * {@link ProfitTracker#takeIn}).
 *
 * Der Modus gilt nur fuer das Inventar, in dem er eingeschaltet wurde. Wer es schliesst, ist ihn los - ohne dass irgendwo ein
 * Haken stehen bleibt, der beim naechsten Oeffnen den ersten Klick verschluckt.
 */
public final class InventoryPick {

    /** Das Inventar, in dem der Modus eingeschaltet wurde - null, wenn er aus ist */
    private static Screen screen;

    private InventoryPick() {
    }

    /** Ist der Modus an - und im selben Inventar, das gerade offen ist? */
    public static boolean active() {
        Minecraft client = Minecraft.getInstance();
        return screen != null && client.gui.screen() == screen && ProfitTracker.enabled();
    }

    /** Der Knopf in der Ueberschrift: an, wenn er aus war, aus, wenn er an war */
    public static void toggle() {
        screen = active() ? null : Minecraft.getInstance().gui.screen();
    }

    /**
     * Ein Klick im Inventar. Nimmt den Stapel unter der Maus in den Tracker, wenn der Modus an ist.
     *
     * @param onePiece mit gedrueckter Umschalttaste: nur ein Stueck statt des ganzen Stapels
     * @return true, wenn der Klick verbraucht ist - dann nimmt das Spiel keinen Stapel auf
     */
    public static boolean handleClick(boolean onePiece) {
        if (!active()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !(client.gui.screen() instanceof AbstractContainerScreen<?> container)) return false;

        Slot slot = ((AbstractContainerScreenAccessor) container).shokimod$hoveredSlot();
        // Nur das eigene Inventar: Was in einer Truhe oder im Craftfeld liegt, gehoert dem Spieler noch nicht
        if (slot == null || slot.container != client.player.getInventory() || !slot.hasItem()) return false;

        ItemStack stack = slot.getItem();
        String itemId = SkyBlockItems.idOf(stack);
        if (itemId == null) {
            say(Component.literal("That item has no SkyBlock id - the tracker cannot take it.").withStyle(ChatFormatting.RED));
            return true;
        }

        int amount = onePiece ? 1 : stack.getCount();
        ProfitTracker.takeIn(itemId, amount);
        say(Component.literal("Added ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(ProfitTracker.nameOf(itemId) + " x" + amount).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" to the Profit tracker").withStyle(ChatFormatting.YELLOW)));
        return true;
    }

    private static void say(Component text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ShokiMod] ").withStyle(ChatFormatting.DARK_AQUA).append(text));
    }
}
