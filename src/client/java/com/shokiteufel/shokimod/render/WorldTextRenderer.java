package com.shokiteufel.shokimod.render;

import com.shokiteufel.shokimod.data.ModConfig;
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

    public static void render(Minecraft client) {
        if (client.player == null) return;
        renderSafariWalls();
        renderMounds();
    }

    // 壊せる壁。まだ立っているものだけを出す
    private static void renderSafariWalls() {
        if (!SafariExtras.wallsActive()) return;

        Minecraft client = Minecraft.getInstance();
        int rgb = ModConfig.INSTANCE.customize.wallColorRGB();
        for (BlockPos pos : SafariExtras.intactWalls(client)) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.fill(0x80000000 | rgb));
            box.setAlwaysOnTop();
            renderGizmoLabel("§bWall", pos, 0xFF000000 | rgb);
        }
    }

    // Rockmite Mound。当たり判定から見つけるので、テクスチャ判定とは独立して効く
    private static void renderMounds() {
        if (!SafariExtras.moundsActive()) return;

        Minecraft client = Minecraft.getInstance();
        int rgb = ModConfig.INSTANCE.customize.moundColorRGB();
        for (BlockPos pos : SafariExtras.mounds(client)) {
            GizmoProperties box = Gizmos.cuboid(pos, GizmoStyle.fill(0x80000000 | rgb));
            box.setAlwaysOnTop();
            renderGizmoLabel("§bMound", pos, 0xFF000000 | rgb);
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
