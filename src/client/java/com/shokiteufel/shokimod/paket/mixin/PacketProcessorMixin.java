package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.paket.PaketDirekt;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Merkt sich im Netzwerk-Thread, welches Paket das Spiel gerade in die Warteschlange des Haupt-Threads stellt. So weiss
 * {@link PaketDirekt}, welche Pakete das Spiel schon im Netzwerk-Thread erledigt hat. Nur lesend; aendert nichts am Einreihen.
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}, require 0).
 */
@Mixin(PacketProcessor.class)
public abstract class PacketProcessorMixin {

    @Inject(method = "scheduleIfPossible(Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), require = 0)
    private void shokimod$eingereiht(PacketListener listener, Packet<?> packet, CallbackInfo ci) {
        PaketDirekt.eingereiht(packet);
    }
}
