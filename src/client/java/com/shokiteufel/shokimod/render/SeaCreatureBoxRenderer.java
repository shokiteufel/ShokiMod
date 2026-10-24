package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.FeatureGate;
import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.data.ModConfig.NearbyRareCategory;
import com.shokiteufel.shokimod.handler.SeaCreatureNear;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Der Kasten um einen seltenen Meeresbewohner in der Naehe: seine Trefferbox, in der Farbe seiner Seltenheit.
 *
 * Wer einen Kasten bekommt, entscheidet {@link SeaCreatureNear}; hier wird nur gezeichnet, und nur, wer
 * naeher ist als die eingestellte Reichweite - der Kasten soll zeigen, was neben einem schwimmt, und nicht jeden Bewohner im See.
 * Durch Waende ist er zu sehen, solange "Through walls" an ist (dann liegt er ueber allem, was davor steht); ohne das
 * verdecken ihn Bloecke wie jede andere Linie. Die Box wird um den Bildanteil verschoben, den der Mob seit dem letzten
 * Tick zurueckgelegt hat - sonst hinkt sie ihm hinterher (wie bei {@link BoxHighlightRenderer}).
 */
public final class SeaCreatureBoxRenderer {

    private static final float LINE_WIDTH = 3.0f;

    private SeaCreatureBoxRenderer() {
    }

    public static void emitGizmos(Minecraft client, float partialTicks) {
        if (client.player == null || client.level == null || !FeatureGate.nearbyRare()) return;
        NearbyRareCategory cfg = ModConfig.INSTANCE.fishing.nearby;
        if (!cfg.hitbox || !SeaCreatureNear.hasSightings()) return;

        for (SeaCreatureNear.Sighting sighting : SeaCreatureNear.sightings()) {
            Entity mob = client.level.getEntity(sighting.mobId);
            if (mob == null || mob.isRemoved()) continue;
            if (client.player.distanceTo(mob) > cfg.hitboxRange) continue;

            Vec3 shift = mob.getPosition(partialTicks).subtract(mob.position());
            AABB box = mob.getBoundingBox().move(shift);
            GizmoProperties props = Gizmos.cuboid(box, GizmoStyle.stroke(0xFF000000 | sighting.creature.colour(), LINE_WIDTH));
            if (cfg.hitboxThroughWalls) props.setAlwaysOnTop();
        }
    }
}
