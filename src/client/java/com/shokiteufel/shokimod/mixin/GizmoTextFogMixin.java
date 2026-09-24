package com.shokiteufel.shokimod.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gizmo経由のワールド内テキスト(ボス座標ラベル等)はバニラだと
 * Font.DisplayMode.NORMAL で描画され、これはFOGバインドグループを持つ
 * パイプラインを使うため霧の色に文字色が引っ張られてしまう。
 * DisplayMode.SEE_THROUGH はFOGバインドグループを持たないため、
 * ここで描画モードを差し替えて霧の影響を受けないようにする。
 */
// 26.2: Der Text laeuft nicht mehr ueber DrawableGizmoPrimitives$Group#renderTexts,
// sondern ueber den Glyph-Besucher des GizmoFeatureRenderer
@Mixin(targets = "net.minecraft.client.renderer.feature.GizmoFeatureRenderer$1")
public class GizmoTextFogMixin {

    @ModifyArg(
            method = "acceptRenderable",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/font/TextRenderable;renderType(Lnet/minecraft/client/gui/Font$DisplayMode;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
            )
    )
    private Font.DisplayMode shokimod$disableFogForGizmoText(Font.DisplayMode mode) {
        return Font.DisplayMode.SEE_THROUGH;
    }
}
