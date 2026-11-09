package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.paket.PaketAbgriff;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Die Stelle, an der das Spiel im Haupt-Thread ein Paket bearbeitet: {@code packet.handle(listener)} in
 * {@code PacketProcessor.ListenerAndPacket.handle()}. Meldet {@link PaketAbgriff#vor} unmittelbar davor und
 * {@link PaketAbgriff#nach} unmittelbar danach. Zusammen mit {@link PaketAbgriff} die einzige Datei, die je
 * Minecraft-Version anders sein kann.
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}): Schlaegt das Einhaengen fehl, laeuft der Rest der Mod
 * ohne Paket-Schicht weiter.
 */
@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public abstract class ListenerAndPacketMixin {

    @Shadow
    @Final
    private PacketListener listener;

    @Shadow
    @Final
    private Packet<?> packet;

    @Inject(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V",
            shift = At.Shift.BEFORE))
    private void shokimod$paketVor(CallbackInfo ci) {
        PaketAbgriff.vor(this.packet, this.listener);
    }

    @Inject(method = "handle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V",
            shift = At.Shift.AFTER))
    private void shokimod$paketNach(CallbackInfo ci) {
        PaketAbgriff.nach(this.packet);
    }
}
