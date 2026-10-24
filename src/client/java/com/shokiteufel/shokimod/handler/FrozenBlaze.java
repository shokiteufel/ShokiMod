package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Traegt der Spieler das ganze Frozen-Blaze-Set? Davon haengen die Funktionen im Reiter Fishing > Frozen Blaze ab.
 *
 * Herkunft: Feesh (Apache-2.0, MoonTheSadFisher), {@code utils/PlayerUtils.kt} ({@code isInFrozenBlaze}). Uebernommen ist die Regel: Alle vier
 * Ruestungsplaetze tragen ein Stueck der Frozen Blaze - erkannt an der Kennung {@code FROZEN_BLAZE_HELMET/CHESTPLATE/LEGGINGS/BOOTS}, ersatzweise
 * am Namen - und das alle fuenf Ticks, nicht bei jeder Abfrage. Nachgebaut in Java; anders ist nur, dass das Ergebnis ausserhalb der Funktion
 * nirgends gebraucht wird: Ist keine der Funktionen an, liest der Tick gar nichts.
 */
public final class FrozenBlaze {

    private static final int CHECK_TICKS = 5;
    private static final String[] PIECE_IDS = {
            "FROZEN_BLAZE_HELMET", "FROZEN_BLAZE_CHESTPLATE", "FROZEN_BLAZE_LEGGINGS", "FROZEN_BLAZE_BOOTS"};
    private static final String[] PIECE_NAMES = {
            "frozen blaze helmet", "frozen blaze chestplate", "frozen blaze leggings", "frozen blaze boots"};
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static boolean wearing = false;
    private static int ticks = 0;

    private FrozenBlaze() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FrozenBlaze::tick);
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.frozenBlaze()) {
            wearing = false;
            return;
        }
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        wearing = client.player != null && fullSet(client.player);
    }

    /** Traegt der Spieler gerade das ganze Set? Nur zuverlaessig, solange eine der Funktionen an ist - sonst bleibt es falsch */
    public static boolean isWearing() {
        return wearing;
    }

    /** Sollen fremde Spieler gerade ausgeblendet werden? */
    public static boolean hidesPlayers() {
        return wearing && ModConfig.INSTANCE.fishing.frozenBlaze.hidePlayers;
    }

    static boolean fullSet(Player player) {
        for (EquipmentSlot slot : SLOTS) {
            if (!isPiece(player.getItemBySlot(slot))) return false;
        }
        return true;
    }

    /** Ein Stueck der Frozen Blaze? Kennung zuerst, sonst der Name - wie bei Feesh, fuer jeden Platz eines der vier Stuecke */
    public static boolean isPiece(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = SkyBlockItems.idOf(stack);
        if (id != null) {
            for (String piece : PIECE_IDS) {
                if (piece.equals(id)) return true;
            }
        }
        String name = stack.getHoverName().getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
        for (String piece : PIECE_NAMES) {
            if (name.contains(piece)) return true;
        }
        return false;
    }
}
