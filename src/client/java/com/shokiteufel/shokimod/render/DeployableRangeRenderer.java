package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ColourText;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployableHudCategory;
import com.shokiteufel.shokimod.handler.DeployableTimers;
import com.shokiteufel.shokimod.handler.DeployableTimers.Ring;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Der Reichweiten-Ring eines Deployables: nur der Rand, nichts gefuellt, in der eingestellten Farbe (die Hotspot-Kreise sind dagegen
 * gefuellt und tragen die Farbe ihrer Art).
 *
 * Welche es gibt, bestimmt {@link DeployableTimers#rings}: dieselben wie im Zeitkasten, mit der Reichweite der jeweiligen Sorte und
 * keiner im Mineshaft. Der Ring liegt waagerecht auf der Hoehe des Staenders, der Mitte der Reichweite. Gezeichnet wird als Linienzug
 * aus 120 Stuecken: Minecrafts fertiger Kreis (Gizmos.circle) hat wenige Ecken und sieht bei 30 Bloecken Radius eckig aus.
 */
public final class DeployableRangeRenderer {

    private static final int SEGMENTS = 120;
    private static final float LINE_WIDTH = 2.5f;
    /** Ein Hauch ueber dem Standpunkt, damit der Strich nicht in der Platte unter dem Staender verschwindet */
    private static final double LIFT = 0.05;
    private static final int DEFAULT_RGB = 0x55FF55;

    private DeployableRangeRenderer() {
    }

    public static void emitGizmos(Minecraft client) {
        if (client.player == null || !GameState.Server.isSkyblock() || !FeatureGate.deployableHud()) return;
        DeployableHudCategory cfg = ModConfig.INSTANCE.deployables.hud;
        if (cfg == null || !cfg.rangeRing) return;

        List<Ring> rings = DeployableTimers.rings(DeployableTimers.tickTime());
        if (rings.isEmpty()) return;

        int rgb = ColourText.rgb(cfg.ringColour, DEFAULT_RGB);
        for (Ring ring : rings) {
            // Die Ringe fremder Deployables halb so deutlich: Der eigene soll der sein, auf den man schaut
            int percent = ring.mine() ? cfg.ringOpacity : cfg.ringOpacity / 2;
            circle(ring, argb(percent, rgb), cfg.ringThroughWalls);
        }
    }

    private static void circle(Ring ring, int argb, boolean throughWalls) {
        Vec3 prev = onCircle(ring, 0.0);
        for (int i = 1; i <= SEGMENTS; i++) {
            Vec3 next = onCircle(ring, Math.PI * 2.0 * i / SEGMENTS);
            GizmoProperties line = Gizmos.line(prev, next, argb, LINE_WIDTH);
            if (throughWalls) line.setAlwaysOnTop();
            prev = next;
        }
    }

    private static Vec3 onCircle(Ring ring, double angle) {
        return new Vec3(ring.x() + Math.cos(angle) * ring.range(), ring.y() + LIFT, ring.z() + Math.sin(angle) * ring.range());
    }

    /** Deckkraft in Prozent auf die Farbe */
    static int argb(int percent, int rgb) {
        int alpha = (int) Math.round(Math.max(0, Math.min(100, percent)) * 2.55);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
