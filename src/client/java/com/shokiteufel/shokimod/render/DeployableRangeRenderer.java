package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ColourText;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.DeployableHudCategory;
import com.shokiteufel.shokimod.data.ModConfig.RingShape;
import com.shokiteufel.shokimod.handler.DeployableTimers;
import com.shokiteufel.shokimod.handler.DeployableTimers.Ring;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Die Reichweite eines Deployables, gezeichnet: als Kugel um den Staender (wie die Lootshare-Reichweite, die Angel-Mods um seltene
 * Seekreaturen zeichnen) oder als flacher Ring. Nur der Rand; auf Wunsch faerbt eine Fuellung das Innere der Kugel leicht ein.
 *
 * Welche es gibt, bestimmt {@link DeployableTimers#rings}: dieselben wie im Zeitkasten, mit der Reichweite der jeweiligen Sorte und
 * keiner im Mineshaft. Die Farbe zeigt, ob man drinsteht: innen in der gewaehlten Farbe, ausserhalb in der zweiten (Vorgabe Weiss).
 * Gemessen wird wie bei der Reichweite selbst, als gerade Strecke von mir zum Staender.
 *
 * Gezeichnet wird als Linienzug und mit Vierecken (Minecrafts Gizmos): Minecrafts fertiger Kreis (Gizmos.circle) hat wenige Ecken und
 * sieht bei 30 Bloecken Radius eckig aus.
 */
public final class DeployableRangeRenderer {

    private static final int SEGMENTS = 120;
    private static final float LINE_WIDTH = 2.5f;
    /** Ein Hauch ueber dem Standpunkt, damit der Strich des flachen Rings nicht in der Platte unter dem Staender verschwindet */
    private static final double LIFT = 0.05;
    private static final int DEFAULT_RGB = 0x55FF55;
    private static final int DEFAULT_OUTSIDE_RGB = 0xFFFFFF;

    /** Die Kugel: Breitenkreise (ohne die Pole) und Laengenkreise von Pol zu Pol, jeder in so viele Stuecke zerlegt */
    private static final int STACKS = 16;
    private static final int SLICES = 24;
    private static final int PARALLEL_STEPS = 48;
    private static final int MERIDIAN_STEPS = 32;
    /** Die Fuellung: Streifen und Keile, je ein Viereck */
    private static final int FILL_STACKS = 12;
    private static final int FILL_SLICES = 24;
    /** Die Fuellung ist ein Fuenftel so deckend wie der Rand */
    private static final double FILL_FACTOR = 0.2;
    /** Weiter weg als Reichweite plus so viel wird nichts gezeichnet: Man sieht die Kugel dort nicht mehr */
    private static final double CULL_MARGIN = 160.0;

    /** Wie viele Linien und Vierecke das letzte Bild gezeichnet hat - fuer die Pruefung */
    private static int emitted = 0;

    private DeployableRangeRenderer() {
    }

    public static void emitGizmos(Minecraft client) {
        emitted = 0;
        if (client.player == null || !GameState.Server.isSkyblock() || !FeatureGate.deployableHud()) return;
        DeployableHudCategory cfg = ModConfig.INSTANCE.deployables.hud;
        if (cfg == null || !cfg.rangeRing) return;

        List<Ring> rings = DeployableTimers.rings(DeployableTimers.tickTime());
        if (rings.isEmpty()) return;

        Vec3 me = client.player.position();
        for (Ring ring : rings) {
            Vec3 centre = new Vec3(ring.x(), ring.y(), ring.z());
            double away = me.distanceTo(centre);
            if (away > ring.range() + CULL_MARGIN) continue;

            int rgb = rimRgb(away < ring.range(), cfg);
            // Die Ringe fremder Deployables halb so deutlich: Der eigene soll der sein, auf den man schaut
            int percent = ring.mine() ? cfg.ringOpacity : cfg.ringOpacity / 2;
            if (cfg.ringShape == RingShape.RING) {
                circle(ring, argb(percent, rgb), cfg.ringThroughWalls);
            } else {
                sphere(centre, ring.range(), argb(percent, rgb), cfg.ringFilled ? argb((int) Math.round(percent * FILL_FACTOR), rgb) : 0,
                        cfg.ringThroughWalls);
            }
        }
    }

    /** Die Farbe des Rands: innen die gewaehlte, ausserhalb der Reichweite die zweite */
    public static int rimRgb(boolean inside, DeployableHudCategory cfg) {
        return inside ? ColourText.rgb(cfg.ringColour, DEFAULT_RGB) : ColourText.rgb(cfg.ringColourOutside, DEFAULT_OUTSIDE_RGB);
    }

    /** Wie viele Linien und Vierecke das letzte Bild gezeichnet hat */
    public static int emittedLastFrame() {
        return emitted;
    }

    private static void circle(Ring ring, int argb, boolean throughWalls) {
        Vec3 prev = onCircle(ring, 0.0);
        for (int i = 1; i <= SEGMENTS; i++) {
            Vec3 next = onCircle(ring, Math.PI * 2.0 * i / SEGMENTS);
            line(prev, next, argb, throughWalls);
            prev = next;
        }
    }

    private static Vec3 onCircle(Ring ring, double angle) {
        return new Vec3(ring.x() + Math.cos(angle) * ring.range(), ring.y() + LIFT, ring.z() + Math.sin(angle) * ring.range());
    }

    /** Die Kugel: der Rand als Gitternetz, und mit fill != 0 dazu die Fuellung */
    private static void sphere(Vec3 centre, double radius, int rim, int fill, boolean throughWalls) {
        for (int i = 1; i < STACKS; i++) {
            double polar = Math.PI * i / STACKS;
            Vec3 prev = onSphere(centre, radius, polar, 0.0);
            for (int k = 1; k <= PARALLEL_STEPS; k++) {
                Vec3 next = onSphere(centre, radius, polar, Math.PI * 2.0 * k / PARALLEL_STEPS);
                line(prev, next, rim, throughWalls);
                prev = next;
            }
        }
        for (int j = 0; j < SLICES; j++) {
            double azimuth = Math.PI * 2.0 * j / SLICES;
            Vec3 prev = onSphere(centre, radius, 0.0, azimuth);
            for (int s = 1; s <= MERIDIAN_STEPS; s++) {
                Vec3 next = onSphere(centre, radius, Math.PI * s / MERIDIAN_STEPS, azimuth);
                line(prev, next, rim, throughWalls);
                prev = next;
            }
        }
        if (fill != 0) fillSphere(centre, radius, fill, throughWalls);
    }

    private static void fillSphere(Vec3 centre, double radius, int argb, boolean throughWalls) {
        GizmoStyle style = GizmoStyle.fill(argb);
        for (int i = 0; i < FILL_STACKS; i++) {
            double north = Math.PI * i / FILL_STACKS;
            double south = Math.PI * (i + 1) / FILL_STACKS;
            for (int j = 0; j < FILL_SLICES; j++) {
                double west = Math.PI * 2.0 * j / FILL_SLICES;
                double east = Math.PI * 2.0 * (j + 1) / FILL_SLICES;
                GizmoProperties quad = Gizmos.rect(onSphere(centre, radius, north, west), onSphere(centre, radius, north, east),
                        onSphere(centre, radius, south, east), onSphere(centre, radius, south, west), style);
                if (throughWalls) quad.setAlwaysOnTop();
                emitted++;
            }
        }
    }

    /** Ein Punkt der Kugel: polar vom Nordpol (0) zum Suedpol (PI), azimuth um die senkrechte Achse */
    private static Vec3 onSphere(Vec3 centre, double radius, double polar, double azimuth) {
        double ring = Math.sin(polar) * radius;
        return new Vec3(centre.x + Math.cos(azimuth) * ring, centre.y + Math.cos(polar) * radius, centre.z + Math.sin(azimuth) * ring);
    }

    private static void line(Vec3 from, Vec3 to, int argb, boolean throughWalls) {
        GizmoProperties line = Gizmos.line(from, to, argb, LINE_WIDTH);
        if (throughWalls) line.setAlwaysOnTop();
        emitted++;
    }

    /** Deckkraft in Prozent auf die Farbe */
    static int argb(int percent, int rgb) {
        int alpha = (int) Math.round(Math.max(0, Math.min(100, percent)) * 2.55);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
