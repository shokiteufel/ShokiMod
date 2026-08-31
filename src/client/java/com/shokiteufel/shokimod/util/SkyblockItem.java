package com.shokiteufel.shokimod.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Die SkyBlock-Kennung eines Items - aus dem Item selbst, nicht aus dem Chat.
 *
 * Hypixel legt sie in denselben Zusatzdaten ab, die auch SkyHanni, Feesh und
 * SkyOcean auslesen: das Component CUSTOM_DATA traegt den alten NBT-Block
 * ExtraAttributes, und darin steht unter "id" die Kennung - HYPERION,
 * ENCHANTED_DIAMOND und so weiter.
 *
 * Der Chat nennt Namen, keine Kennungen: er faerbt sie ein, kuerzt sie, uebersetzt
 * Mengen in Worte. Am Item steht die Kennung unveraendert, egal wie die Meldung
 * dazu aussieht - und auch dann, wenn es gar keine Meldung gibt.
 */
public final class SkyblockItem {

    private static final String EXTRA_ATTRIBUTES = "ExtraAttributes";
    private static final String ID = "id";

    private SkyblockItem() {
    }

    /**
     * Die Kennung eines Stapels.
     *
     * Ohne SkyBlock-Kennung bleibt der Name aus der Vanilla-Registry - fuer normale
     * Bloecke ist das die richtige Antwort, und der Basar fuehrt sie unter genau
     * diesem Namen in Grossbuchstaben.
     */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag extra = data.copyTag().getCompound(EXTRA_ATTRIBUTES).orElse(null);
            if (extra != null) {
                String id = extra.getString(ID).orElse("");
                if (!id.isBlank()) return id;
            }
        }

        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(java.util.Locale.ROOT);
    }
}
