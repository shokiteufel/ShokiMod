package com.shokiteufel.shokimod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Der Platz, ueber dem die Maus gerade steht.
 *
 * Das Feld ist geschuetzt, und die Methode, die es berechnet, ist privat. Es wird in jedem Bild neu gesetzt, ist also beim Klick der
 * Platz, den man gerade sieht.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("hoveredSlot")
    Slot shokimod$hoveredSlot();
}
