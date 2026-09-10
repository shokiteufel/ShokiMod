package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.scanner.PerformanceState;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Die Antwort auf die eigene Ping-Anfrage.
 *
 * Der Server schickt die mitgesendete Zeit unveraendert zurueck; die Spanne bis jetzt
 * ist die Umlaufzeit. Gerechnet wird in {@link PerformanceState} - hier wird die Zahl
 * nur weitergereicht.
 */
@Mixin(ClientPacketListener.class)
public class PongResponseMixin {

    @Inject(method = "handlePongResponse", at = @At("TAIL"))
    private void onPong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PerformanceState.onPong(packet.time());
    }
}
