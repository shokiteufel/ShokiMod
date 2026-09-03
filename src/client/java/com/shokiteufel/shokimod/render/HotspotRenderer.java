package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.HotspotCategory;
import com.shokiteufel.shokimod.handler.HotspotTracker;
import com.shokiteufel.shokimod.handler.HotspotTracker.Hotspot;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;

/**
 * Der Kreis auf dem Wasser: Rand und Flaeche in der Farbe der Hotspot-Art.
 *
 * Gezeichnet wird nur, was der Tracker kennt und wozu er schon einen Radius hat.
 * Ohne Radius gibt es keinen Kreis - lieber kurz nichts als etwas Falsches.
 */
public final class HotspotRenderer {

    private static final float OUTLINE_WIDTH = 2.5f;

    private HotspotRenderer() {
    }

    public static void emitGizmos(Minecraft client) {
        if (client.player == null || !FeatureGate.hotspots()) return;
        HotspotCategory cfg = ModConfig.INSTANCE.fishing.hotspot;
        if (!cfg.circleOutline && !cfg.circleSurface) return;

        for (Hotspot hotspot : HotspotTracker.hotspots()) {
            if (hotspot.surface == null || hotspot.radius <= 0) continue;
            int outline = argb(cfg.outlineAlpha, hotspot.type.colour);
            int fill = argb(cfg.surfaceAlpha, hotspot.type.colour);
            GizmoStyle style;
            if (cfg.circleOutline && cfg.circleSurface) style = GizmoStyle.strokeAndFill(outline, OUTLINE_WIDTH, fill);
            else if (cfg.circleOutline) style = GizmoStyle.stroke(outline, OUTLINE_WIDTH);
            else style = GizmoStyle.fill(fill);
            Gizmos.circle(hotspot.surface, (float) hotspot.radius, style);
        }
    }

    /** Deckkraft in Prozent auf die Farbe */
    private static int argb(int percent, int rgb) {
        int alpha = (int) Math.round(Math.max(0, Math.min(100, percent)) * 2.55);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
