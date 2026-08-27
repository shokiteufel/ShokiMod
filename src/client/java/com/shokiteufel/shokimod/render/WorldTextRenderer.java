package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ModConfig;
import com.shokiteufel.shokimod.handler.FloorDropHandler;
import com.shokiteufel.shokimod.scanner.NestTracker;
import com.shokiteufel.shokimod.scanner.SafariExtras;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.Vec3;

/** Kästen und Beschriftungen, die in der Welt stehen statt auf dem Bildschirm. */
public class WorldTextRenderer {

    /**
     * Nur der Rahmen, keine Füllung - wie in crittermod.
     * Eine gefüllte Box verdeckt genau das, was man sehen will.
     */
    private static final float MARKER_LINE_WIDTH = 2.0F;

    public static void render(Minecraft client) {
        if (client.player == null) return;
        renderSafariWalls();
        renderMounds();
        renderFloorDrops();
        renderNests();
    }

    // Bienenstoecke im Forest. Abgeerntete bleiben stehen, die zeigen wir nicht mehr
    private static void renderNests() {
        if (!NestTracker.isActive()) return;

        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.nestColorRGB();
        for (NestTracker.Nest nest : NestTracker.nests()) {
            if (!nest.unpunched()) continue;
            GizmoProperties box = Gizmos.cuboid(nest.pos(), GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Nest", nest.pos(), argb);
        }
    }

    // 地面に落ちている採取物。ブロックは置かれておらず、見た目は ItemDisplay の重なり
    private static void renderFloorDrops() {
        if (!FloorDropHandler.isActive()) return;

        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.floorDropColorRGB();
        for (BlockPos pos : FloorDropHandler.positions()) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Floor Drop", pos, argb);
        }
    }

    // 壊せる壁。まだ立っているものだけを出す
    private static void renderSafariWalls() {
        if (!SafariExtras.wallsActive()) return;

        Minecraft client = Minecraft.getInstance();
        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.wallColorRGB();
        for (BlockPos pos : SafariExtras.intactWalls(client)) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Wall", pos, argb);
        }
    }

    // Rockmite Mound。当たり判定から見つけるので、テクスチャ判定とは独立して効く
    private static void renderMounds() {
        if (!SafariExtras.moundsActive()) return;

        Minecraft client = Minecraft.getInstance();
        int argb = 0xFF000000 | ModConfig.INSTANCE.safari.moundColorRGB();
        for (BlockPos pos : SafariExtras.mounds(client)) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.stroke(argb, MARKER_LINE_WIDTH));
            box.setAlwaysOnTop();
            renderGizmoLabel("Mound", pos, argb);
        }
    }

    public static Vec3 labelPos(BlockPos renderPos) {
        return new Vec3(renderPos.getX() + 0.5, renderPos.getY() + 1.5, renderPos.getZ() + 0.5);
    }

    public static void renderGizmoLabel(String text, BlockPos renderPos, int argbColor) {
        Vec3 pos = labelPos(renderPos);

        // 距離に比例して拡大し、見かけの大きさを一定に保つ。
        // プレイヤーのtick座標を使うと20回/秒でしかスケールが更新されずカクつくため、
        // フレームごとに補間されるカメラ座標を基準にする
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        float textScale = (float) Math.max(0.02, cameraPos.distanceTo(pos) * 0.0025);

        TextGizmo.Style style = TextGizmo.Style.forColorAndCentered(argbColor)
                .withScale(textScale * 20.0F);
        GizmoProperties properties = Gizmos.billboardText(text, pos, style);
        properties.setAlwaysOnTop();
    }
}
