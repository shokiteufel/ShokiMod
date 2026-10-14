package com.shokiteufel.shokimod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shokiteufel.shokimod.render.PlayerSize;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Die Groesse der eigenen Figur: Das Matrix-Blatt wird skaliert, bevor der Koerper kommt.
 * Gerechnet wird in {@link PlayerSize}.
 */
@Mixin(AvatarRenderer.class)
public class PlayerSizeMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"))
    private void shokimod$scale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        PlayerSize.apply(state, poseStack);
    }
}
