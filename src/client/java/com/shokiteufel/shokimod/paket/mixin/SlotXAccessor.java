package com.shokiteufel.shokimod.paket.mixin;

import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Die Lage eines Platzes im Fenster: {@code Slot.x} ist ein {@code public final int}, der Zweithand-Platz des Inventarfensters muss aber ein Stueck nach rechts
 * ruecken, damit der vierte Equipment-Platz seinen Ort bekommt (UI & Visuals > Show Equipment in Inventory). Per javap in den Jars von 26.3 nachgesehen.
 *
 * <p>Nicht erforderlich ({@code shokimod-pakete.mixins.json}, {@code require = 0}): Fehlt er, rueckt nichts, und die Plaetze werden nicht gezeichnet
 * ({@link com.shokiteufel.shokimod.equipment.EquipmentInventory} faengt den Fehlgriff ab und schaltet die Anzeige fuer die Sitzung aus).
 */
@Mixin(Slot.class)
public interface SlotXAccessor {

    @Mutable
    @Accessor("x")
    void shokimod$setX(int x);
}
