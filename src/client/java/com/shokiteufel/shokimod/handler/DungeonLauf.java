package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Steht man gerade in einem Dungeon-Lauf? Die Antwort fuer Anzeigen, die waehrend eines Laufs weichen sollen (Profit Tracker: "Hide in Dungeons").
 *
 * Erkannt wird der Dungeon wie bei "Hide Players" ({@link HidePlayers#detectDungeon}): Tab-Liste "Dungeon: Catacombs" oder Seitenleiste
 * "The Catacombs (F7)". Gelesen wird fuenfmal je Sekunde und nur, solange eine Einstellung die Antwort braucht - sonst kostet es nichts.
 */
public final class DungeonLauf {

    private static final int CHECK_TICKS = 4;

    private static boolean drin;
    private static int ticks;

    private DungeonLauf() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DungeonLauf::tick);
    }

    /** Braucht gerade eine Einstellung die Antwort? */
    private static boolean gebraucht() {
        return ModConfig.INSTANCE.profit.hideInDungeons;
    }

    private static void tick(Minecraft client) {
        if (!gebraucht()) {
            drin = false;
            return;
        }
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (client == null || client.level == null || client.player == null) {
            drin = false;
            return;
        }
        drin = HidePlayers.detectDungeon(TabListScanner.lastLines(), ScoreboardUtils.getSidebarLines(client));
    }

    /** Steht man in einem Dungeon? Gilt nur, solange eine Einstellung danach fragt; sonst immer false */
    public static boolean imDungeon() {
        return drin;
    }
}
