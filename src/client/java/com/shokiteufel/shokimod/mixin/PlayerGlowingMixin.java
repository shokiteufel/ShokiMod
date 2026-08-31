package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.render.EntityHighlightManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hypixel baut manche Mobs aus Spieler-Entities (NPCs). Player überschreibt beide Methoden
 * selbst, deshalb greift der Mixin auf Entity dort nicht.
 */
@Mixin(Player.class)
public class PlayerGlowingMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void forceHighlightGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (EntityHighlightManager.highlightedEntities.isEmpty()) return;
        if (EntityHighlightManager.highlightedEntities.contains((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void overrideGlowColor(CallbackInfoReturnable<Integer> cir) {
        if (EntityHighlightManager.customGlowColors.isEmpty()) return;
        Integer color = EntityHighlightManager.customGlowColors.get((Entity) (Object) this);
        if (color != null) cir.setReturnValue(color);
    }
}
