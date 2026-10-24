package com.shokiteufel.shokimod.handler;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConstants;
import com.shokiteufel.shokimod.scanner.TabListScanner;
import com.shokiteufel.shokimod.util.ScoreboardUtils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Blendet andere Spieler in der Naehe aus - nach Odins "Hide Players".
 *
 * Herkunft: Odin (BSD-3-Clause, Odtheking), {@code features/impl/render/HidePlayers.kt} und {@code EntityRendererMixin}.
 * Uebernommen ist die Regel: Ein Spieler wird nicht gezeichnet, wenn er ein echter Spieler ist (Version-4-Kennung - NPCs
 * haben eine andere), nicht man selbst, und entweder alle ausgeblendet werden oder er naeher als die Entfernung steht;
 * auf Wunsch nur im Dungeon. Nachgebaut in Java; anders ist das Erkennen des Dungeons (siehe {@link #detectDungeon}), die
 * Einstellung (eine Klappe im Reiter Odin) und der Einsprung ({@code HidePlayersMixin}, der beide bekannten Formen von
 * {@code shouldRender} versucht, weil sich die Parameter zwischen den Versionen aendern).
 *
 * {@link #shouldRender} laeuft fuer jede Figur in jedem Bild - es rechnet nichts aus, was sich nicht schnell lesen laesst; den
 * Dungeon prueft der Tick zweimal je Sekunde und merkt sich das Ergebnis.
 */
public final class HidePlayers {

    private static final int CHECK_TICKS = 10;
    private static final String CATACOMBS = "Catacombs";

    private static boolean inDungeons = false;
    private static int ticks = 0;

    private HidePlayers() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HidePlayers::tick);
    }

    private static void tick(Minecraft client) {
        ModConfig.OdinCategory cfg = ModConfig.INSTANCE.odin;
        if (!cfg.hidePlayers || !cfg.hidePlayersOnlyInDungeons) {
            inDungeons = false;
            return;
        }
        if (++ticks < CHECK_TICKS) return;
        ticks = 0;
        if (client.level == null || client.player == null) {
            inDungeons = false;
            return;
        }
        inDungeons = detectDungeon(TabListScanner.lastLines(), ScoreboardUtils.getSidebarLines(client));
    }

    /**
     * Steht man im Dungeon? Odin liest die Zeile der Tab-Liste, die mit "Area: " oder "Dungeon: " anfaengt und
     * "Catacombs" enthaelt. Hier zusaetzlich der Kasten der Seitenleiste ("The Catacombs (F7)") - er steht dort, solange man
     * im Dungeon ist, auch wenn die Tab-Liste gerade nicht gelesen wurde.
     */
    public static boolean detectDungeon(List<String> tabLines, List<String> sidebarLines) {
        if (tabLines != null) {
            for (String line : tabLines) {
                String text = line == null ? "" : line.trim();
                boolean areaLine = ModConstants.startsWithIgnoreCase(text, "Area:") || ModConstants.startsWithIgnoreCase(text, "Dungeon:");
                if (areaLine && ModConstants.containsIgnoreCase(text, CATACOMBS)) return true;
            }
        }
        if (sidebarLines != null) {
            for (String line : sidebarLines) {
                if (line != null && ModConstants.containsIgnoreCase(ScoreboardUtils.stripColor(line), "The " + CATACOMBS)) return true;
            }
        }
        return false;
    }

    /** Soll diese Figur gezeichnet werden? Vom Einsprung in {@code EntityRenderer.shouldRender} gerufen */
    public static boolean shouldRender(Entity entity) {
        ModConfig.OdinCategory cfg = ModConfig.INSTANCE.odin;
        if (!cfg.hidePlayers) return true;
        if (!(entity instanceof Player)) return true;
        // NPCs tragen keine Version-4-Kennung: Nur echte Spieler werden ausgeblendet
        if (entity.getUUID().version() != 4) return true;
        Minecraft client = Minecraft.getInstance();
        if (entity == client.player) return true;
        if (cfg.hidePlayersOnlyInDungeons && !inDungeons) return true;
        if (cfg.hidePlayersAll) return false;
        if (client.player == null) return true;
        double reach = cfg.hidePlayersDistance;
        return entity.distanceToSqr(client.player) > reach * reach;
    }
}
