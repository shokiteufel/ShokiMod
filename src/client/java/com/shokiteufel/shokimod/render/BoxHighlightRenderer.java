package com.shokiteufel.shokimod.render;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Die zweite Hervorhebung: ein Kasten um den Mob statt eines Umrisses.
 *
 * Der Glow braucht einen sichtbaren Koerper und faerbt nur dessen Kanten; der
 * Kasten zeichnet die Trefferbox und ist auch bei unsichtbaren oder winzigen
 * Mobs zu sehen. Beide duerfen zugleich an sein.
 *
 * Wer den Kasten bekommt, entscheidet der Tick (EntityHighlightManager); hier
 * wird nur gezeichnet. Die Box wird um den Bildanteil verschoben, den der Mob
 * seit dem letzten Tick zurueckgelegt hat - sonst hinkt sie ihm hinterher.
 */
public final class BoxHighlightRenderer {

    private static final float LINE_WIDTH = 2.0f;

    private BoxHighlightRenderer() {
    }

    public static void emitGizmos(Minecraft client, float partialTicks) {
        if (client.player == null) return;
        if (EntityHighlightManager.boxedEntities.isEmpty()) return;

        for (Map.Entry<Entity, Integer> entry : EntityHighlightManager.boxedEntities.entrySet()) {
            Entity entity = entry.getKey();
            if (entity.isRemoved()) continue;

            Vec3 shift = entity.getPosition(partialTicks).subtract(entity.position());
            AABB box = entity.getBoundingBox().move(shift);
            GizmoProperties props = Gizmos.cuboid(box, GizmoStyle.stroke(entry.getValue(), LINE_WIDTH));
            props.setAlwaysOnTop();
        }
    }
}
