package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.data.GameState;
import com.shokiteufel.shokimod.render.EntityHighlightManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Invisibug は完全に透明なアーマースタンドとして送られてくるため、エンティティの種類では見つけられない。
 * 唯一の手掛かりが本体から出る CRIT パーティクルなので、その座標をここで拾う。
 *
 * このメソッドはネットワークスレッドで呼ばれる。ワールドやエンティティに触ると壊れるので、
 * 座標を控えるだけに留め、実際の照合は tick 側(EntityHighlightManager)で行う。
 */
@Mixin(ClientPacketListener.class)
public class InvisibugParticleMixin {

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void shokimod$onCritParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (!GameState.Server.isMoongladeMarsh()) return;
        if (packet.getParticle().getType() != ParticleTypes.CRIT) return;

        EntityHighlightManager.onCritParticle(packet.getX(), packet.getY(), packet.getZ());
    }
}
