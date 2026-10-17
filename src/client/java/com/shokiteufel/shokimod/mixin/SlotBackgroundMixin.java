package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.TrashBooks;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ein Untergrund hinter dem Stueck in einem Slot - vor dem Zeichnen des Stuecks, damit es oben liegt.
 *
 * Dieselbe Stelle, die Feesh nimmt: in {@code extractSlot} der Aufruf, der das Stueck zeichnet. Sie ist in
 * 26.1, 26.2 und 26.3 dieselbe (am Bytecode nachgesehen). Der Mixin zeichnet nur und greift nicht ein.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class SlotBackgroundMixin {

    @Inject(method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void shokimod$beforeItem(GuiGraphicsExtractor graphics, Slot slot, int x, int y, CallbackInfo ci) {
        TrashBooks.onSlot(graphics, slot);
    }
}
