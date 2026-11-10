package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.dungeonkarte.Raster;
import com.shokiteufel.shokimod.dungeonkarte.Stockwerk;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Steht man gerade in einem Dungeon-Lauf? Die Antwort fuer Anzeigen, die waehrend eines Laufs weichen sollen (Profit Tracker: "Hide in Dungeons")
 * und fuer das Dungeon log - dazu, ob man im Boss-Raum steht (ausserhalb des Raster-Rechtecks) und in welchem Stockwerk.
 *
 * Erkannt wird der Dungeon wie bei "Hide Players" ({@link HidePlayers#detectDungeon}): Tab-Liste "Dungeon: Catacombs" oder Seitenleiste
 * "The Catacombs (F7)". Gelesen wird fuenfmal je Sekunde und nur, solange eine Einstellung die Antwort braucht - sonst kostet es nichts.
 */
public final class DungeonLauf {

    private static final int CHECK_TICKS = 4;
    /** Ausserhalb dieses Rechtecks (Bloecke) steht man nicht mehr im Raster, sondern im Boss-Raum oder sonst wo (wie bei der Funny Map) */
    private static final int BOSS_GRENZE_MIN = Raster.START - 40;
    private static final int BOSS_GRENZE_MAX = Raster.START + 10 * (Raster.RAUM / 2) + 40;

    private static boolean drin;
    private static boolean imBoss;
    private static Integer stockwerk;
    private static int ticks;

    private DungeonLauf() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DungeonLauf::tick);
    }

    /** Braucht gerade eine Einstellung die Antwort? */
    private static boolean gebraucht() {
        return ModConfig.INSTANCE.profit.hideInDungeons || FeatureGate.dungeonLog();
    }

    private static void tick(Minecraft client) {
        if (!gebraucht()) {
            zuruecksetzen();
            return;
        }
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (client == null || client.level == null || client.player == null) {
            zuruecksetzen();
            return;
        }
        List<String> seite = ScoreboardUtils.getSidebarLines(client);
        drin = HidePlayers.detectDungeon(TabListScanner.lastLines(), seite);
        if (!drin) {
            imBoss = false;
            stockwerk = null;
            return;
        }
        double x = client.player.getX();
        double z = client.player.getZ();
        imBoss = x < BOSS_GRENZE_MIN || x > BOSS_GRENZE_MAX || z < BOSS_GRENZE_MIN || z > BOSS_GRENZE_MAX;
        for (String zeile : seite) {
            Integer f = Stockwerk.aus(ScoreboardUtils.stripColor(zeile));
            if (f != null) {
                stockwerk = f;
                break;
            }
        }
    }

    private static void zuruecksetzen() {
        drin = false;
        imBoss = false;
        stockwerk = null;
    }

    /** Steht man in einem Dungeon? Gilt nur, solange eine Einstellung danach fragt; sonst immer false */
    public static boolean imDungeon() {
        return drin;
    }

    /** Steht man im Boss-Raum (ausserhalb des Rasters)? Gilt nur im Dungeon und solange eine Einstellung danach fragt */
    public static boolean imBoss() {
        return imBoss;
    }

    /** Das Stockwerk (0 = Eingang, 1 bis 7), oder null, wenn unbekannt oder nicht im Dungeon */
    public static Integer stockwerk() {
        return stockwerk;
    }
}
