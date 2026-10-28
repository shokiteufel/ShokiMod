package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.DungeonChestProfit;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Meldet jeden Klick auf einen Platz in einem Container-Fenster.
 *
 * Gebraucht fuer die Belohnungstruhen: Beute und Preis stehen nur im Fenster, und der
 * Augenblick, in dem sie ausgegeben werden, ist der Klick auf "Open Reward Chest".
 * Der Mixin liest nur und greift nicht ein.
 *
 * Die Signatur steht vollstaendig im Ziel: Auf 26.3 gibt es in AbstractContainerScreen zwei Methoden namens slotClicked (eine private mit
 * MouseButtonEvent statt der Maustaste als int). Mit dem blossen Namen versucht Mixin, beide zu bestuecken, und bricht bei der zweiten mit
 * "Invalid descriptor" ab - das stuerzt das Spiel beim Start ab, auch mit require = 0. Mit der Signatur trifft der Mixin nur die richtige
 * Methode; fehlt sie in einer anderen Spielversion, wird er (wegen require = 0) uebersprungen, statt das Spiel zu beenden.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class DungeonChestClickMixin {

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), require = 0)
    private void shokimod$slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput type, CallbackInfo ci) {
        DungeonChestProfit.onSlotClick((AbstractContainerScreen<?>) (Object) this, slot);
    }
}
