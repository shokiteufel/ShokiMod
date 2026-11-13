package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.Locale;
import java.util.Set;

/**
 * Blendet die Angelhaken anderer Spieler aus - nach Feeshs "Hide other players' fishing hooks".
 *
 * Herkunft: Feesh (Apache-2.0, Sleepy-Panda), {@code HideOtherPlayersHooks} und der Einsprung in {@code EntityRenderer.shouldRender}.
 * Uebernommen ist die Regel: Ein Haken, dessen Besitzer nicht der eigene Spieler ist, wird nicht gezeichnet (Haken und Schnur sind
 * dieselbe Figur), und nur in SkyBlock und nicht dort, wo niemand angelt. Anders ist das Erkennen des Ortes (die Gebietszeile der
 * Tab-Liste, wie bei den anderen Funktionen hier) und der Einsprung: Er laeuft ueber das vorhandene {@code HidePlayersMixin}, das beide
 * bekannten Formen von {@code shouldRender} versucht.
 *
 * {@link #hides} laeuft fuer jeden Haken in jedem Bild: erst die Einstellung, dann der Besitzer, der Ort zuletzt.
 */
public final class HideOtherHooks {

    /**
     * Wo niemand angelt (Feeshs {@code NO_FISHING_WORLDS}). Beide Schreibweisen stehen drin, die Feeshs und die der Gebietszeile
     * ("The Rift"/"Rift Dimension", "Dungeons"/"Catacombs", "Glacite Mineshaft(s)"), klein geschrieben verglichen.
     */
    private static final Set<String> NO_FISHING = Set.of(
            "the rift", "rift dimension", "garden", "kuudra's hollow", "kuudra", "dungeons", "dungeon", "catacombs", "dungeon hub",
            "the end", "glacite mineshafts", "glacite mineshaft", "safari");

    private HideOtherHooks() {
    }

    /** Soll dieser Haken nicht gezeichnet werden? Vom Einsprung in {@code EntityRenderer.shouldRender} gerufen */
    public static boolean hides(FishingHook hook) {
        if (!ModConfig.INSTANCE.fishing.hooks.hideOthers) return false;
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || hook.getPlayerOwner() == player) return false;
        return fishingWorld(GameState.Server.isSkyblock(), GameState.Server.map);
    }

    /**
     * Gilt der Ort? In SkyBlock und nicht in einem Gebiet ohne Angeln. Ein noch nicht gelesenes Gebiet ("Unknown", leer) zaehlt als
     * Angelort: Feesh blendet dann nichts aus, hier schon - sonst sah die Einstellung kaputt aus, solange die Gebietszeile fehlt, und
     * ein fremder Haken in einem Gebiet ohne Angeln kommt nicht vor.
     */
    public static boolean fishingWorld(boolean skyblock, String map) {
        if (!skyblock) return false;
        if (map == null || map.isBlank() || "Unknown".equals(map)) return true;
        return !NO_FISHING.contains(map.trim().toLowerCase(Locale.ROOT));
    }
}
