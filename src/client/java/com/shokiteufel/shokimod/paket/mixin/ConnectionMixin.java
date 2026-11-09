package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.paket.PaketAusgang;
import com.shokiteufel.shokimod.paket.PaketDirekt;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zwei Stellen der Verbindung, nur lesend:
 * <ul>
 *   <li>{@code channelRead0}: Eingang jedes Pakets im Netzwerk-Thread. Anfang und Ende merken, ob das Spiel das Paket in die
 *       Warteschlange des Haupt-Threads gestellt hat; wenn nicht, bringt {@link PaketDirekt} es als eigenen Eintrag dorthin.</li>
 *   <li>{@code sendPacket}: die eine Stelle, durch die jedes gesendete Paket geht. Meldet es an {@link PaketAusgang}.</li>
 * </ul>
 * Zusammen mit {@code ListenerAndPacketMixin} und {@code PacketProcessorMixin} gehoert das zu den Dateien, die je
 * Minecraft-Version anders sein koennen. Nicht erforderlich ({@code shokimod-pakete.mixins.json}, require 0): Passt eine Stelle
 * in einer anderen Version nicht, laeuft die Mod ohne diesen Weg weiter und die Diagnose meldet es.
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Shadow
    @Final
    private PacketFlow receiving;

    @Shadow
    private volatile PacketListener packetListener;

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), require = 0)
    private void shokimod$lesenBeginn(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            PaketDirekt.beginn(this.packetListener);
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("RETURN"), require = 0)
    private void shokimod$lesenEnde(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (this.receiving == PacketFlow.CLIENTBOUND) {
            PaketDirekt.ende(packet);
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"), require = 0)
    private void shokimod$senden(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        PaketAusgang.gesendet(packet, this.receiving, this.packetListener);
    }
}
