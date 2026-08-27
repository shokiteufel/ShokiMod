package com.shokiteufel.shokimod.render.hud;

import com.shokiteufel.shokimod.data.Critters.Critter;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.SafariBiome;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.scanner.SafariExtras;
import com.shokiteufel.shokimod.session.SafariSession;
import com.shokiteufel.shokimod.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Was im Biom, in dem man gerade steht, noch fehlt - und was dort noch zu tun ist.
 *
 * Nachbau von crittermods MissingHud. Die Zeilen unter der Artenliste zaehlen genau
 * das, was die Marker in der Welt zeigen: Huegel, Waende und Bienenstoecke.
 */
public final class MissingHud {

    private static final int LABEL_COLOUR = 0xFFE6E6E6;
    private static final int DONE_COLOUR = 0xFF7CFF7C;

    private MissingHud() {
    }

    /** In welchem Biom man steht, oder null ausserhalb der Safari */
    public static SafariBiome currentBiome() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;

        BlockPos pos = client.player.blockPosition();
        if (EntityHighlightManager.inSafariCavern(pos)) return SafariBiome.CAVERN;
        if (EntityHighlightManager.inSafariIcy(pos)) return SafariBiome.ICY;
        if (EntityHighlightManager.inSafariForest(pos)) return SafariBiome.FOREST;
        return SafariBiome.HAUNTED;
    }

    public static HudPanel build() {
        SafariBiome biome = currentBiome();
        if (biome == null) return new HudPanel();

        SafariSession session = SessionManager.currentOrLast();
        boolean firstCatchIsEnough = ModConfig.INSTANCE.safari.firstCatchIsEnough;
        HudPanel panel = new HudPanel();

        List<Critter> missing = session == null
                ? com.shokiteufel.shokimod.data.Critters.inBiome(biome)
                : session.missingIn(biome, firstCatchIsEnough);

        if (missing.isEmpty()) {
            panel.title(biome.displayName() + " Biome - all caught", DONE_COLOUR);
        } else {
            panel.title(biome.displayName() + " Biome - " + missing.size() + " left", biomeText(biome));
            for (Critter critter : missing) {
                // Bei Arten mit Stueckzahl steht dahinter, wie viele noch fehlen
                int left = session == null
                        ? critter.required(firstCatchIsEnough)
                        : session.remaining(critter, firstCatchIsEnough);
                String suffix = left > 1 ? "  x" + left : "";
                panel.line(critter.rarity().colourCode() + critter.name() + suffix, 0xFFFFFFFF);
            }
        }

        appendMounds(panel, biome);
        appendWalls(panel, biome);
        appendNests(panel, biome);
        return panel;
    }

    private static void appendMounds(HudPanel panel, SafariBiome biome) {
        if (!ModConfig.INSTANCE.safari.showMoundCount) return;
        if (biome != SafariBiome.CAVERN) return;

        Minecraft client = Minecraft.getInstance();
        int standing = SafariExtras.mounds(client).size();
        // Keine in Reichweite heisst nicht "keine mehr da" - dann lieber gar nichts sagen
        if (standing == 0) return;

        panel.blank();
        panel.pair("Mounds nearby", String.valueOf(standing), LABEL_COLOUR, 0xFFFFFFFF);
    }

    private static void appendWalls(HudPanel panel, SafariBiome biome) {
        if (!ModConfig.INSTANCE.safari.showWallCount) return;
        if (biome != SafariBiome.CAVERN && biome != SafariBiome.ICY) return;

        Minecraft client = Minecraft.getInstance();
        int standing = SafariExtras.intactWalls(client).size();

        panel.blank();
        if (standing == 0) {
            panel.line("Walls all broken", DONE_COLOUR);
        } else {
            panel.pair("Walls to break", String.valueOf(standing), LABEL_COLOUR, 0xFFFFFFFF);
        }
    }

    private static void appendNests(HudPanel panel, SafariBiome biome) {
        if (!ModConfig.INSTANCE.safari.showNestCount) return;
        if (biome != SafariBiome.FOREST) return;

        List<NestTracker.Nest> nests = NestTracker.nests();
        if (nests.isEmpty()) return;

        long open = nests.stream().filter(NestTracker.Nest::unpunched).count();
        panel.blank();
        if (open == 0) {
            panel.line("All " + nests.size() + " nests punched", DONE_COLOUR);
        } else {
            panel.pair("Nests to punch", String.valueOf(open), LABEL_COLOUR, 0xFFFFFFFF);
        }
    }

    /** Die Biomfarben liegen als reines RGB vor; fuer Text muss das Alpha-Byte dazu */
    private static int biomeText(SafariBiome biome) {
        return 0xFF000000 | biome.textColour();
    }
}
