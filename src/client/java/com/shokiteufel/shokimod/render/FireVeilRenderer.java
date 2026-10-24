package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ColourText;
import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.IqCategory;
import com.shokiteufel.shokimod.data.ModConfig.IqCategory.FireVeilRender;
import com.shokiteufel.shokimod.handler.FireVeil;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Der Wirkungskreis des Fire Veil Wand: ein dicker Ring auf dem Boden oder eine Wand aus Ringen und Strichen.
 *
 * Herkunft: IQ (Apache-2.0, PeHenrii und DarkJota), {@code FireVeilOverlayFeature} und {@code WorldRenderUtils}
 * ({@code drawThickCircleOutline}, {@code drawCircleWireframeWall}). Uebernommen sind die Masse - Radius 3,5 Bloecke, Ringdicke 0,08,
 * 60 Segmente, Wandhoehe 1 Block, drei Ringe auf Boden, halber und voller Hoehe, dazu senkrechte Striche (mindestens 8, hier 12), die
 * Mitte an der Spielerfigur (zwischen den Bildern ausgeglichen, 0,02 ueber dem Boden). Anders ist das Zeichnen selbst: IQ
 * bringt eine eigene Zeichenschicht mit, hier malt Minecrafts Gizmos-System (wie bei den Hotspot-Kreisen); "durch Waende" ist
 * dort {@code setAlwaysOnTop}.
 *
 * Gezeichnet wird nur in den 5 Sekunden nach dem Wurf und nur, wenn die Funktion an ist und nicht "Default" gewaehlt ist.
 */
public final class FireVeilRenderer {

    private static final double RADIUS = 3.5;
    private static final double RING_THICKNESS = 0.08;
    private static final int SEGMENTS = 60;
    private static final double WALL_HEIGHT = 1.0;
    private static final int WALL_MARKERS = Math.max(8, SEGMENTS / 5);
    private static final float LINE_WIDTH = 2.5f;

    private FireVeilRenderer() {
    }

    public static void emitGizmos(Minecraft client, float partialTicks) {
        if (client.player == null || !FeatureGate.fireVeil()) return;
        IqCategory cfg = ModConfig.INSTANCE.iq;
        if (cfg.fireVeilRender == FireVeilRender.DEFAULT) return;
        if (!FireVeil.isAbilityActive(System.currentTimeMillis())) return;

        Vec3 centre = client.player.getPosition(partialTicks).add(0.0, 0.02, 0.0);
        int argb = argb(cfg);
        if (cfg.fireVeilRender == FireVeilRender.WALL) wall(centre, argb, cfg.fireVeilThroughWalls);
        else ring(centre, argb, cfg.fireVeilThroughWalls);
    }

    /** Die Farbe samt Deckkraft in Prozent */
    static int argb(IqCategory cfg) {
        int rgb = ColourText.rgb(cfg.fireVeilColour, 0xFFAA00);
        int alpha = (int) Math.round(Math.max(0, Math.min(100, cfg.fireVeilOpacity)) * 2.55);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /** Ein Ring aus 60 schmalen Vierecken: von Radius - Dicke/2 bis Radius + Dicke/2 */
    private static void ring(Vec3 centre, int argb, boolean throughWalls) {
        double half = Math.min(RING_THICKNESS / 2.0, RADIUS);
        double inner = Math.max(0.0, RADIUS - half);
        double outer = RADIUS + half;
        GizmoStyle fill = GizmoStyle.fill(argb);
        Vec3 innerPrev = onCircle(centre, inner, 0.0);
        Vec3 outerPrev = onCircle(centre, outer, 0.0);
        for (int i = 1; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / SEGMENTS;
            Vec3 innerNext = onCircle(centre, inner, angle);
            Vec3 outerNext = onCircle(centre, outer, angle);
            // Beide Seiten, wie bei IQ (vier Dreiecke je Stueck): Eine Flaeche ist sonst nur von einer Seite zu sehen
            top(Gizmos.rect(innerPrev, outerPrev, outerNext, innerNext, fill), throughWalls);
            top(Gizmos.rect(innerNext, outerNext, outerPrev, innerPrev, fill), throughWalls);
            innerPrev = innerNext;
            outerPrev = outerNext;
        }
    }

    /** Drei Ringe (Boden, halbe und volle Hoehe) und senkrechte Striche dazwischen */
    private static void wall(Vec3 centre, int argb, boolean throughWalls) {
        for (double height : new double[]{0.0, WALL_HEIGHT * 0.5, WALL_HEIGHT}) {
            Vec3 level = centre.add(0.0, height, 0.0);
            Vec3 prev = onCircle(level, RADIUS, 0.0);
            for (int i = 1; i <= SEGMENTS; i++) {
                Vec3 next = onCircle(level, RADIUS, Math.PI * 2.0 * i / SEGMENTS);
                top(Gizmos.line(prev, next, argb, LINE_WIDTH), throughWalls);
                prev = next;
            }
        }
        for (int i = 0; i < WALL_MARKERS; i++) {
            Vec3 foot = onCircle(centre, RADIUS, Math.PI * 2.0 * i / WALL_MARKERS);
            top(Gizmos.line(foot, foot.add(0.0, WALL_HEIGHT, 0.0), argb, LINE_WIDTH), throughWalls);
        }
    }

    private static Vec3 onCircle(Vec3 centre, double radius, double angle) {
        return new Vec3(centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius);
    }

    private static void top(GizmoProperties props, boolean throughWalls) {
        if (throughWalls) props.setAlwaysOnTop();
    }
}
