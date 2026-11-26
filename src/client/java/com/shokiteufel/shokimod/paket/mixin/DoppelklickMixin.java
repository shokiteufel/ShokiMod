package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.handler.FixDoubleClicks;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Misc > Fix item double clicks: das Block-Paket eines Rechtsklicks mit einem Blaze-Dolch oder einer Angel wird nicht gesendet.
 *
 * Derselbe Einsprung wie in {@code ConnectionMixin} ({@code sendPacket}, die eine Stelle, durch die jedes gesendete Paket geht), nur dass dieser
 * das Senden verhindern kann. Nicht erforderlich ({@code shokimod-pakete.mixins.json}, require 0): Passt die Stelle in einer anderen Version
 * nicht, laeuft die Mod ohne dieses Auslassen weiter.
 */
@Mixin(Connection.class)
public abstract class DoppelklickMixin {

    @Inject(method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void shokimod$doppelklick(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (packet instanceof ServerboundUseItemOnPacket use && FixDoubleClicks.shouldCancel(use)) {
            ci.cancel();
        }
    }
}
