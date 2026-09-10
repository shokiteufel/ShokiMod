package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.scanner.PerformanceState;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Der einzige Weg, den Takt des Servers zu erfahren.
 *
 * Er schickt seine Uhrzeit alle zwanzig Ticks - bei vollem Takt also einmal je Sekunde.
 * Kommt sie spaeter, laeuft der Server langsamer. Gerechnet wird in
 * {@link PerformanceState}; hier wird nur mitgezaehlt, wann sie eintrifft.
 */
@Mixin(ClientPacketListener.class)
public class ServerTimeMixin {

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        PerformanceState.onTimeUpdate();
    }
}
