package com.shokiteufel.shokimod.paket.mixin;

import com.shokiteufel.shokimod.equipment.EquipmentInventory;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * UI & Visuals > Show Equipment in Inventory: die vier Plaetze im Inventarfenster, rechts neben der Spielfigur.
 *
 * Die Stelle ist der Anfang von {@code InventoryScreen.extractLabels(GuiGraphicsExtractor, int, int)}: Das Spiel ruft sie in {@code extractContents} auf,
 * nachdem das Fenster samt Hintergrund gezeichnet ist (die Rahmen der Plaetze und die Spielfigur stehen schon) und die Zeichenflaeche auf die linke obere Ecke des
 * Fensters verschoben ist, aber noch bevor die Plaetze des Spiels und die Karte am Zeiger drankommen. Dort gezeichnet liegen die vier Rahmen unter dem Gegenstand
 * am Zeiger und unter den Gegenstaenden der Plaetze (Zweithand-Platz). Per javap in den Jars von 26.3 nachgesehen (Aufruf an Stelle 47 von
 * {@code AbstractContainerScreen.extractContents}); ein Fabric-Ereignis gibt es fuer diese Stelle nicht ({@code afterBackground} kommt vor dem Zeichnen der Fenstertextur).
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}, {@code require = 0}): Passt die Stelle nicht, fehlt nur die Anzeige. Der Mixin zeichnet und greift sonst
 * nicht ein; die Regeln stehen in {@link EquipmentInventory}.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryEquipmentMixin {

    @Inject(method = "extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), require = 0)
    private void shokimod$equipment(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        EquipmentInventory.drawInWindow((InventoryScreen) (Object) this, graphics, mouseX, mouseY);
    }
}
