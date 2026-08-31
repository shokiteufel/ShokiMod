package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.EntityHighlightManager;
import com.shokiteufel.shokimod.util.CustomMobDebug;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityGlowingMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void forceHighlightGlowing(CallbackInfoReturnable<Boolean> cir) {
        // Diese Methode laeuft fuer jede Entity in jedem Bild. Ist nichts markiert,
        // kostet der Abbruch einen Feldzugriff statt eines Hash-Vergleichs
        if (EntityHighlightManager.highlightedEntities.isEmpty()) return;
        if (EntityHighlightManager.highlightedEntities.contains((Entity) (Object) this)) {
            cir.setReturnValue(true);
            CustomMobDebug.mixinHit((Entity) (Object) this);
        }
    }

    /**
     * バニラは「当たり判定の平均辺長 × 64 × 描画距離倍率」より遠いエンティティを描画しない。
     * Glow はエンティティが描画されて初めて輪郭が出るため、この距離を超えると
     * Tracer だけが表示されて Glow が消える、という食い違いが起きる。
     * 追跡中のモブに限り距離によるカリングを無効化し、両者の見え方を揃える。
     * (フラスタム外のカリングは別処理なので、画面外のエンティティは従来どおり描画されない)
     */
    @Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("HEAD"), cancellable = true)
    private void forceHighlightRenderDistance(double distanceSqr, CallbackInfoReturnable<Boolean> cir) {
        if (EntityHighlightManager.highlightedEntities.isEmpty()) return;
        if (EntityHighlightManager.highlightedEntities.contains((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void overrideGlowingColor(CallbackInfoReturnable<Integer> cir) {
        if (EntityHighlightManager.highlightedEntities.isEmpty()) return;
        Entity entity = (Entity) (Object) this;
        if (!EntityHighlightManager.highlightedEntities.contains(entity)) return;

        // 走査時に決めた色。registerHighlight が必ず入れるので、通常は null にならない
        Integer color = EntityHighlightManager.customGlowColors.get(entity);
        cir.setReturnValue(color != null ? color : 0xFFFFFF);
    }
}
