package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.HidePlayers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fuer "Hide Players" (Reiter Odin): Eine Figur, die ausgeblendet werden soll, wird nicht gezeichnet.
 *
 * Wie Odin haengt es am Ende von {@code EntityRenderer.shouldRender} und macht aus "ja" ein "nein". Die Methode hat nicht
 * in jeder Version dieselben Parameter (26.1: fuenf; Odins neuerer Stand: sechs, mit den Teilticks), und ein Einsprung, dessen
 * Parameter nicht stimmen, bricht den Start ab. Deshalb stehen beide Formen hier, jede mit ihrer genauen Beschreibung: Gibt es
 * die Form in dieser Version nicht, passiert nichts ({@code require = 0}), und diese Datei steht in einer eigenen
 * Mixin-Konfiguration, die nicht zwingend ist - schlaegt dort etwas fehl, bleibt es bei einer Warnung im Log, und nur diese
 * Funktion fehlt.
 */
@Mixin(EntityRenderer.class)
public abstract class HidePlayersMixin {

    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void shokimod$hidePlayers(Entity entity, Frustum frustum, double x, double y, double z,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !HidePlayers.shouldRender(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void shokimod$hidePlayersWithPartialTick(Entity entity, Frustum frustum, double x, double y, double z, float partialTick,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !HidePlayers.shouldRender(entity)) cir.setReturnValue(false);
    }
}
