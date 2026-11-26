package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.util.ScoreboardUtils;
import com.shokiteufel.shokimod.util.SkyBlockItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Koeder, mit dem man gerade angelt: Bild, Anzahl und Name, solange man eine Angel haelt.
 *
 * Herkunft der Idee: SkyHanni (LGPL-2.1, "Fishing Bait Display"). Von dort stammt die Regel: Der Koeder liegt im letzten Platz der
 * Schnellzugriffsleiste (dem neunten, Index 8), und in seiner Beschreibung steht "Bait Remaining: 49". Liegt dort kein Koeder, steht "No Bait"
 * da. Die Umsetzung ist eigene Arbeit: Java, ein Lesen alle fuenf Ticks statt Ereignissen fuer jede Inventaraenderung, eigene Anzeige.
 */
public final class FishingBaitDisplay {

    /** Der Platz der Leiste, in den der Koeder gehoert (0 bis 8) */
    static final int BAIT_SLOT = 8;
    private static final int READ_EVERY_TICKS = 5;
    private static final Pattern REMAINING = Pattern.compile("^Bait Remaining: ([\\d,]+)$");

    /** Der Koeder: sein Bild (ein Stueck), seine Anzahl und sein Name in Farbcodes */
    public record Bait(ItemStack icon, int amount, String name) {
    }

    private static int ticks = 0;
    private static boolean holdingRod = false;
    private static Bait bait = null;

    private FishingBaitDisplay() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FishingBaitDisplay::tick);
    }

    /**
     * Wie viele Koeder noch da sind, nach der Beschreibung des Gegenstands - oder -1, wenn dort kein "Bait Remaining" steht und der
     * Gegenstand also kein Koeder ist. Die Zeilen kommen ohne Farbcodes.
     */
    static int amountOf(List<String> lore) {
        if (lore == null) return -1;
        for (String line : lore) {
            if (line == null) continue;
            Matcher m = REMAINING.matcher(line.trim());
            if (!m.matches()) continue;
            try {
                return Integer.parseInt(m.group(1).replace(",", ""));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private static void tick(Minecraft client) {
        if (!FeatureGate.baitDisplay()) {
            holdingRod = false;
            bait = null;
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || !GameState.Server.isSkyblock()) {
            holdingRod = false;
            bait = null;
            return;
        }
        boolean rod = player.getMainHandItem().is(Items.FISHING_ROD);
        // Beim Wechsel zur Angel sofort lesen, sonst im Takt
        if (rod == holdingRod && ++ticks < READ_EVERY_TICKS) return;
        ticks = 0;
        holdingRod = rod;
        if (!rod) {
            bait = null;
            return;
        }
        ItemStack stack = player.getInventory().getItem(BAIT_SLOT);
        int amount = stack.isEmpty() ? -1 : amountOf(SkyBlockItems.loreOf(stack));
        if (amount < 0) {
            bait = null;
            return;
        }
        ItemStack icon = stack.copy();
        icon.setCount(1);
        bait = new Bait(icon, amount, ScoreboardUtils.toLegacyString(stack.getHoverName()));
    }

    /** Haelt der Spieler eine Angel? Dann hat der Kasten etwas zu sagen: den Koeder oder "No Bait" */
    public static boolean holdingRod() {
        return holdingRod;
    }

    /** Der Koeder, oder null, wenn im letzten Platz keiner liegt */
    public static Bait current() {
        return bait;
    }
}
