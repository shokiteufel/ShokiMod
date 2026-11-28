package com.shokiteufel.shokimod.mixin;

import com.shokiteufel.shokimod.handler.ShopValues;
import com.shokiteufel.shokimod.handler.TrashBooks;
import com.shokiteufel.shokimod.handler.VisitorWindow;
import com.shokiteufel.shokimod.handler.WindowHighlights;

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
        // Inventory > Bazaar: Purchase helper und Order helper markieren Plaetze des Behaelters
        WindowHighlights.onSlot(graphics, slot);
        // Inventory > NPC Shop Values: das Stueck, auf dessen Zeile in der Liste der Zeiger liegt, leuchtet auf
        ShopValues.onSlot(graphics, slot);
    }

    /**
     * Nach dem Stueck, noch vor der Beschreibung: Der gesperrte Knopf im Fenster eines Besuchers wird abgedunkelt, der bessere bekommt einen Rand. Das liegt hier und nicht
     * nach dem ganzen Fenster, weil die Beschreibung (Tooltip) am Ende des Fensters gezeichnet wird - was danach kaeme, laege ueber ihrem Text (bis 2.5.15 ein graues
     * Quadrat auf "Accept Offer"). Dieselbe Methode wie oben, dieselbe Signatur (in 26.1, 26.2 und 26.3 am Bytecode nachgesehen); RETURN gibt es in jeder Methode.
     */
    @Inject(method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V", at = @At("RETURN"))
    private void shokimod$afterSlot(GuiGraphicsExtractor graphics, Slot slot, int x, int y, CallbackInfo ci) {
        VisitorWindow.onSlotDrawn(graphics, slot);
        // Inventory > Bazaar > Order icons: das kleine Zeichen oben links im Platz, ueber dem Stueck
        WindowHighlights.onSlotDrawn(graphics, slot);
    }
}
